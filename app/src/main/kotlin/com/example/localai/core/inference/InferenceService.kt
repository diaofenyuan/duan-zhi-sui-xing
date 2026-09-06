package com.example.localai.core.inference

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.IOException

/**
 * 独立推理进程服务（P3）：Native/llama.cpp 只在本进程加载，UI 进程经 Binder 驱动。
 * 不访问网络、不持有业务数据库；进程崩溃由 UI 侧 InferenceClient 以 ENGINE_CRASHED 恢复。
 *
 * 线程模型：
 *   - load/releaseSession 在 Binder 线程同步执行（可能耗时，客户端在工作线程调用）
 *   - start 后 Native 工作线程回调，统一 post 到服务主线程再 oneway 转发给客户端
 *   - 客户端死亡（DeadObjectException）时自动取消当前生成
 */
class InferenceService : Service() {

    private val lock = Object()
    private var handler: Handler? = null
    private var session: NativeSession? = null
    private var loaded: InferenceRequest? = null
    private val gson = Gson()

    private val binder = object : IInferenceService.Stub() {
        override fun load(request: InferenceRequest?): Int {
            if (request == null) {
                return NativeSession.ERR_NULL_ARGUMENT
            }
            val pathError = validatePath(request.modelPath)
            if (pathError != null) {
                Log.w(TAG, "load rejected: $pathError")
                return NativeSession.ERR_ILLEGAL_ARGUMENT
            }
            synchronized(lock) {
                releaseLocked()
                val created = NativeSession()
                try {
                    created.load(request.modelPath, request.contextLength, request.threadCount,
                        request.temperature, request.topP, request.maxNewTokens)
                } catch (e: NativeSession.NativeException) {
                    created.close()
                    Log.w(TAG, "native load failed, code=" + e.code)
                    return e.code
                } catch (e: IllegalStateException) {
                    created.close()
                    return NativeSession.ERR_INTERNAL
                }
                session = created
                loaded = request
                Log.i(TAG, "model loaded: id=" + request.modelId + " v=" + request.version)
                return NativeSession.OK
            }
        }

        override fun start(prompt: String, cb: IInferenceCallback?): Int {
            synchronized(lock) {
                val s = session
                if (s == null || cb == null) {
                    return NativeSession.ERR_WRONG_STATE
                }
                val callback = cb
                val listener = object : NativeSession.StreamListener {
                    override fun onDelta(text: String) {
                        dispatch {
                            try {
                                callback.onToken(text)
                            } catch (e: RemoteException) {
                                cancelDeadClient()
                            } catch (e: RuntimeException) {
                                cancelDeadClient()
                            }
                        }
                    }

                    override fun onFinished(reason: Int) {
                        dispatch {
                            try {
                                callback.onFinished(reason)
                            } catch (e: RemoteException) {
                                cancelDeadClient()
                            } catch (e: RuntimeException) {
                                cancelDeadClient()
                            }
                        }
                    }

                    override fun onError(code: Int, message: String) {
                        dispatch {
                            try {
                                callback.onError(code, message)
                            } catch (e: RemoteException) {
                                cancelDeadClient()
                            } catch (e: RuntimeException) {
                                cancelDeadClient()
                            }
                        }
                    }
                }
                try {
                    s.start(prompt, listener)
                    return NativeSession.OK
                } catch (e: NativeSession.NativeException) {
                    Log.w(TAG, "start failed, code=" + e.code)
                    return e.code
                } catch (e: IllegalStateException) {
                    return NativeSession.ERR_WRONG_STATE
                }
            }
        }

        override fun countTokens(prompt: String): Int {
            synchronized(lock) {
                val s = session ?: return -NativeSession.ERR_WRONG_STATE
                return try {
                    s.countTokens(prompt)
                } catch (e: NativeSession.NativeException) {
                    -e.code
                } catch (_: IllegalStateException) {
                    -NativeSession.ERR_WRONG_STATE
                }
            }
        }

        override fun stop(): Int {
            synchronized(lock) {
                val s = session
                if (s == null) {
                    return NativeSession.ERR_WRONG_STATE
                }
                try {
                    s.stop()
                    return NativeSession.OK
                } catch (e: NativeSession.NativeException) {
                    return e.code
                } catch (e: IllegalStateException) {
                    return NativeSession.ERR_WRONG_STATE
                }
            }
        }

        override fun getStats(): InferenceStats? {
            synchronized(lock) {
                val s = session
                if (s == null) {
                    return InferenceStats(InferenceStats.STATE_IDLE, 0, 0, -1, 0)
                }
                return try {
                    val o = gson.fromJson(s.getStats(), JsonObject::class.java)
                    InferenceStats(
                        if (o.has("state")) o.get("state").asString else InferenceStats.STATE_IDLE,
                        if (o.has("promptTokens")) o.get("promptTokens").asLong else 0,
                        if (o.has("genTokens")) o.get("genTokens").asLong else 0,
                        if (o.has("ttftMs")) o.get("ttftMs").asLong else -1,
                        if (o.has("elapsedMs")) o.get("elapsedMs").asLong else 0)
                } catch (e: RuntimeException) {
                    InferenceStats(InferenceStats.STATE_IDLE, 0, 0, -1, 0)
                }
            }
        }

        override fun getPid(): Int = android.os.Process.myPid()

        override fun releaseSession() {
            synchronized(lock) {
                releaseLocked()
            }
        }
    }

    private fun validatePath(path: String?): String? {
        if (path == null || path.isEmpty()) {
            return "empty path"
        }
        return try {
            val canonical = File(path).canonicalPath
            val root = filesDir.canonicalPath
            if (canonical != root && !canonical.startsWith(root + File.separator)) {
                "path outside app files dir"
            } else {
                null
            }
        } catch (e: IOException) {
            "unresolvable path"
        }
    }

    private fun releaseLocked() {
        val s = session
        if (s != null) {
            try {
                s.stop()
            } catch (ignored: RuntimeException) {
                // not running
            }
            s.close()
            session = null
            loaded = null
        }
    }

    private fun cancelDeadClient() {
        Log.w(TAG, "callback client died, cancelling generation")
        synchronized(lock) {
            val s = session
            if (s != null) {
                try {
                    s.stop()
                } catch (ignored: RuntimeException) {
                    // not running
                }
            }
        }
    }

    private fun dispatch(runnable: Runnable) {
        handler?.post(runnable)
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        Log.i(TAG, "inference service created in process " + android.os.Process.myPid())
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        synchronized(lock) {
            releaseLocked()
        }
        super.onDestroy()
        Log.i(TAG, "inference service destroyed")
    }

    companion object {
        private const val TAG = "InferenceService"
    }
}
