package com.example.localai.core.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UI 进程的推理客户端（P3）：绑定 :inference 进程服务，处理
 * 流式回调主线程分发、Binder death（ENGINE_CRASHED）与服务重启恢复。
 */
class InferenceClient(context: Context) {

    /** 全部回调在主线程派发。 */
    interface Events {
        fun onStateChanged(state: Int)

        fun onToken(batch: String)

        fun onFinished(reason: Int)

        fun onError(code: Int, message: String)
    }

    private val appContext: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val bound = AtomicBoolean(false)

    @Volatile
    private var state = STATE_IDLE

    @Volatile
    private var events: Events? = null
    private var lastRequest: InferenceRequest? = null
    private var service: IInferenceService? = null

    private val callbackStub = object : IInferenceCallback.Stub() {
        override fun onToken(batch: String) {
            main.post {
                events?.onToken(batch)
            }
        }

        override fun onFinished(reason: Int) {
            main.post {
                state = STATE_READY
                events?.onFinished(reason)
            }
        }

        override fun onError(code: Int, message: String) {
            main.post {
                state = STATE_READY
                events?.onError(code, message)
            }
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "inference process died")
        main.post { handleCrash() }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "inference service connected")
            val connected = IInferenceService.Stub.asInterface(binder)
            try {
                binder?.linkToDeath(deathRecipient, 0)
            } catch (e: RemoteException) {
                main.post { handleCrash() }
                return
            }
            service = connected
            val request = lastRequest
            if (request == null) {
                setState(STATE_READY)
                return
            }
            setState(STATE_LOADING)
            io.execute {
                try {
                    val code = connected.load(request)
                    if (code == NativeSession.OK) {
                        main.post { setState(STATE_READY) }
                    } else {
                        main.post {
                            setState(STATE_IDLE)
                            events?.onError(code, describeLoadError(code))
                        }
                    }
                } catch (e: RemoteException) {
                    main.post { handleCrash() }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "inference service disconnected")
            service = null
            main.post { handleCrash() }
        }
    }

    /** 替换事件接收者（多轮生成复用同一连接时使用）。 */
    @Synchronized
    fun setEvents(events: Events) {
        this.events = events
    }

    @Synchronized
    fun connect(request: InferenceRequest?, events: Events) {
        this.lastRequest = request
        this.events = events
        if (state == STATE_READY || state == STATE_RUNNING || state == STATE_BINDING) {
            return
        }
        setState(STATE_BINDING)
        val intent = Intent(appContext, InferenceService::class.java)
        try {
            bound.set(appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        } catch (e: RuntimeException) {
            Log.e(TAG, "bindService failed", e)
            main.post { handleCrash() }
        }
        if (!bound.get()) {
            main.post { handleCrash() }
        }
    }

    /** 重启服务（崩溃恢复）：重新绑定并重载同一请求。 */
    @Synchronized
    fun restart(request: InferenceRequest?, events: Events) {
        release()
        this.lastRequest = request
        this.events = events
        connect(request, events)
    }

    @Synchronized
    fun start(prompt: String) {
        val s = service
        if (s == null) {
            events?.onError(ERR_ENGINE_CRASHED, "推理服务未连接")
            return
        }
        setState(STATE_RUNNING)
        io.execute {
            try {
                val code = s.start(prompt, callbackStub)
                if (code != NativeSession.OK) {
                    main.post {
                        setState(STATE_READY)
                        events?.onError(code, describeLoadError(code))
                    }
                }
            } catch (e: RemoteException) {
                main.post { handleCrash() }
            }
        }
    }

    @Synchronized
    fun stop() {
        val s = service ?: return
        io.execute {
            try {
                val code = s.stop()
                if (code != NativeSession.OK && code != NativeSession.ERR_WRONG_STATE) {
                    main.post {
                        events?.onError(code, describeLoadError(code))
                    }
                }
            } catch (e: RemoteException) {
                main.post { handleCrash() }
            }
        }
    }

    fun getStats(): InferenceStats? {
        val s = service ?: return null
        return try {
            s.getStats()
        } catch (e: RemoteException) {
            main.post { handleCrash() }
            null
        }
    }

    /** 释放：取消生成、解绑并通知服务释放模型资源。 */
    @Synchronized
    fun release() {
        val s = service
        if (s != null) {
            try {
                s.releaseSession()
            } catch (ignored: RemoteException) {
                // process already gone
            }
            service = null
        }
        try {
            if (bound.getAndSet(false)) {
                appContext.unbindService(connection)
            }
        } catch (ignored: RuntimeException) {
            // not bound
        }
        state = STATE_RELEASED
    }

    fun getState(): Int = state

    private fun handleCrash() {
        service = null
        setState(STATE_CRASHED)
        events?.onError(ERR_ENGINE_CRASHED, "推理进程已退出，请重试")
    }

    private fun setState(next: Int) {
        if (state != next) {
            state = next
            events?.onStateChanged(next)
        }
    }

    companion object {
        private const val TAG = "InferenceClient"

        /** 推理进程崩溃（Binder death / 未捕获异常）。 */
        @JvmField val ERR_ENGINE_CRASHED = 1199

        @JvmField val STATE_IDLE = 0
        @JvmField val STATE_BINDING = 1
        @JvmField val STATE_LOADING = 2
        @JvmField val STATE_READY = 3
        @JvmField val STATE_RUNNING = 4
        @JvmField val STATE_CRASHED = 5
        @JvmField val STATE_RELEASED = 6

        private fun describeLoadError(code: Int): String {
            return when (code) {
                NativeSession.ERR_MODEL_LOAD_FAILED -> "模型文件加载失败（文件无效或不兼容）"
                NativeSession.ERR_CONTEXT_CREATE_FAILED -> "推理上下文创建失败（内存不足）"
                NativeSession.ERR_TOKENIZE_FAILED -> "输入文本分词失败"
                NativeSession.ERR_WRONG_STATE -> "推理服务状态异常"
                NativeSession.ERR_ILLEGAL_ARGUMENT -> "模型路径不合法"
                else -> "推理失败（code=$code）"
            }
        }
    }
}
