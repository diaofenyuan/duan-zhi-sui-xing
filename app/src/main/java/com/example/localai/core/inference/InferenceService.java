package com.example.localai.core.inference;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;

/**
 * 独立推理进程服务（P3）：Native/llama.cpp 只在本进程加载，UI 进程经 Binder 驱动。
 * 不访问网络、不持有业务数据库；进程崩溃由 UI 侧 InferenceClient 以 ENGINE_CRASHED 恢复。
 *
 * 线程模型：
 *   - load/releaseSession 在 Binder 线程同步执行（可能耗时，客户端在工作线程调用）
 *   - start 后 Native 工作线程回调，统一 post 到服务主线程再 oneway 转发给客户端
 *   - 客户端死亡（DeadObjectException）时自动取消当前生成
 */
public class InferenceService extends Service {

    private static final String TAG = "InferenceService";

    private final Object lock = new Object();
    private Handler handler;
    private NativeSession session;
    private InferenceRequest loaded;
    private Gson gson = new Gson();

    private final IInferenceService.Stub binder = new IInferenceService.Stub() {
        @Override
        public int load(InferenceRequest request) {
            if (request == null) {
                return NativeSession.ERR_NULL_ARGUMENT;
            }
            String pathError = validatePath(request.modelPath);
            if (pathError != null) {
                Log.w(TAG, "load rejected: " + pathError);
                return NativeSession.ERR_ILLEGAL_ARGUMENT;
            }
            synchronized (lock) {
                releaseLocked();
                NativeSession created = new NativeSession();
                try {
                    created.load(request.modelPath, request.contextLength, request.threadCount,
                            request.temperature, request.topP, request.maxNewTokens);
                } catch (NativeSession.NativeException e) {
                    created.close();
                    Log.w(TAG, "native load failed, code=" + e.getCode());
                    return e.getCode();
                } catch (IllegalStateException e) {
                    created.close();
                    return NativeSession.ERR_INTERNAL;
                }
                session = created;
                loaded = request;
                Log.i(TAG, "model loaded: id=" + request.modelId + " v=" + request.version);
                return NativeSession.OK;
            }
        }

        @Override
        public int start(String prompt, IInferenceCallback cb) {
            synchronized (lock) {
                if (session == null || cb == null) {
                    return NativeSession.ERR_WRONG_STATE;
                }
                final IInferenceCallback callback = cb;
                NativeSession.StreamListener listener = new NativeSession.StreamListener() {
                    @Override
                    public void onDelta(String text) {
                        dispatch(() -> {
                            try {
                                callback.onToken(text);
                            } catch (RemoteException | RuntimeException e) {
                                cancelDeadClient();
                            }
                        });
                    }

                    @Override
                    public void onFinished(int reason) {
                        dispatch(() -> {
                            try {
                                callback.onFinished(reason);
                            } catch (RemoteException | RuntimeException e) {
                                cancelDeadClient();
                            }
                        });
                    }

                    @Override
                    public void onError(int code, String message) {
                        dispatch(() -> {
                            try {
                                callback.onError(code, message);
                            } catch (RemoteException | RuntimeException e) {
                                cancelDeadClient();
                            }
                        });
                    }
                };
                try {
                    session.start(prompt, listener);
                    return NativeSession.OK;
                } catch (NativeSession.NativeException e) {
                    Log.w(TAG, "start failed, code=" + e.getCode());
                    return e.getCode();
                } catch (IllegalStateException e) {
                    return NativeSession.ERR_WRONG_STATE;
                }
            }
        }

        @Override
        public int stop() {
            synchronized (lock) {
                if (session == null) {
                    return NativeSession.ERR_WRONG_STATE;
                }
                try {
                    session.stop();
                    return NativeSession.OK;
                } catch (NativeSession.NativeException e) {
                    return e.getCode();
                } catch (IllegalStateException e) {
                    return NativeSession.ERR_WRONG_STATE;
                }
            }
        }

        @Override
        public InferenceStats getStats() {
            synchronized (lock) {
                if (session == null) {
                    return new InferenceStats(InferenceStats.STATE_IDLE, 0, 0, -1, 0);
                }
                try {
                    JsonObject o = gson.fromJson(session.getStats(), JsonObject.class);
                    return new InferenceStats(
                            o.has("state") ? o.get("state").getAsString() : InferenceStats.STATE_IDLE,
                            o.has("promptTokens") ? o.get("promptTokens").getAsLong() : 0,
                            o.has("genTokens") ? o.get("genTokens").getAsLong() : 0,
                            o.has("ttftMs") ? o.get("ttftMs").getAsLong() : -1,
                            o.has("elapsedMs") ? o.get("elapsedMs").getAsLong() : 0);
                } catch (RuntimeException e) {
                    return new InferenceStats(InferenceStats.STATE_IDLE, 0, 0, -1, 0);
                }
            }
        }

        @Override
        public int getPid() {
            return android.os.Process.myPid();
        }

        @Override
        public void releaseSession() {
            synchronized (lock) {
                releaseLocked();
            }
        }
    };

    private String validatePath(String path) {
        if (path == null || path.isEmpty()) {
            return "empty path";
        }
        try {
            String canonical = new File(path).getCanonicalPath();
            String root = getFilesDir().getCanonicalPath();
            if (!canonical.equals(root) && !canonical.startsWith(root + File.separator)) {
                return "path outside app files dir";
            }
        } catch (IOException e) {
            return "unresolvable path";
        }
        return null;
    }

    private void releaseLocked() {
        if (session != null) {
            try {
                session.stop();
            } catch (RuntimeException ignored) {
                // not running
            }
            session.close();
            session = null;
            loaded = null;
        }
    }

    private void cancelDeadClient() {
        Log.w(TAG, "callback client died, cancelling generation");
        synchronized (lock) {
            if (session != null) {
                try {
                    session.stop();
                } catch (RuntimeException ignored) {
                    // not running
                }
            }
        }
    }

    private void dispatch(Runnable runnable) {
        if (handler != null) {
            handler.post(runnable);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "inference service created in process " + android.os.Process.myPid());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        synchronized (lock) {
            releaseLocked();
        }
        super.onDestroy();
        Log.i(TAG, "inference service destroyed");
    }
}
