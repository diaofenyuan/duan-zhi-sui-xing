package com.example.localai.core.inference;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI 进程的推理客户端（P3）：绑定 :inference 进程服务，处理
 * 流式回调主线程分发、Binder death（ENGINE_CRASHED）与服务重启恢复。
 */
public final class InferenceClient {

    private static final String TAG = "InferenceClient";

    /** 推理进程崩溃（Binder death / 未捕获异常）。 */
    public static final int ERR_ENGINE_CRASHED = 1199;

    public static final int STATE_IDLE = 0;
    public static final int STATE_BINDING = 1;
    public static final int STATE_LOADING = 2;
    public static final int STATE_READY = 3;
    public static final int STATE_RUNNING = 4;
    public static final int STATE_CRASHED = 5;
    public static final int STATE_RELEASED = 6;

    /** 全部回调在主线程派发。 */
    public interface Events {
        void onStateChanged(int state);

        void onToken(String batch);

        void onFinished(int reason);

        void onError(int code, String message);
    }

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean bound = new AtomicBoolean(false);

    private volatile int state = STATE_IDLE;
    private volatile Events events;
    private InferenceRequest lastRequest;
    private IInferenceService service;

    private final IInferenceCallback.Stub callbackStub = new IInferenceCallback.Stub() {
        @Override
        public void onToken(String batch) {
            main.post(() -> {
                Events e = events;
                if (e != null) {
                    e.onToken(batch);
                }
            });
        }

        @Override
        public void onFinished(int reason) {
            main.post(() -> {
                state = STATE_READY;
                Events e = events;
                if (e != null) {
                    e.onFinished(reason);
                }
            });
        }

        @Override
        public void onError(int code, String message) {
            main.post(() -> {
                state = STATE_READY;
                Events e = events;
                if (e != null) {
                    e.onError(code, message);
                }
            });
        }
    };

    private final IBinder.DeathRecipient deathRecipient = () -> {
        Log.w(TAG, "inference process died");
        main.post(this::handleCrash);
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "inference service connected");
            IInferenceService connected = IInferenceService.Stub.asInterface(binder);
            try {
                binder.linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                main.post(InferenceClient.this::handleCrash);
                return;
            }
            service = connected;
            final InferenceRequest request = lastRequest;
            if (request == null) {
                setState(STATE_READY);
                return;
            }
            setState(STATE_LOADING);
            io.execute(() -> {
                try {
                    int code = connected.load(request);
                    if (code == NativeSession.OK) {
                        main.post(() -> setState(STATE_READY));
                    } else {
                        main.post(() -> {
                            setState(STATE_IDLE);
                            Events e = events;
                            if (e != null) {
                                e.onError(code, describeLoadError(code));
                            }
                        });
                    }
                } catch (RemoteException e) {
                    main.post(InferenceClient.this::handleCrash);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "inference service disconnected");
            service = null;
            main.post(InferenceClient.this::handleCrash);
        }
    };

    public InferenceClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** 替换事件接收者（多轮生成复用同一连接时使用）。 */
    public synchronized void setEvents(Events events) {
        this.events = events;
    }

    public synchronized void connect(InferenceRequest request, Events events) {
        this.lastRequest = request;
        this.events = events;
        if (state == STATE_READY || state == STATE_RUNNING || state == STATE_BINDING) {
            return;
        }
        setState(STATE_BINDING);
        Intent intent = new Intent(appContext, InferenceService.class);
        try {
            bound.set(appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE));
        } catch (RuntimeException e) {
            Log.e(TAG, "bindService failed", e);
            main.post(this::handleCrash);
        }
        if (!bound.get()) {
            main.post(this::handleCrash);
        }
    }

    /** 重启服务（崩溃恢复）：重新绑定并重载同一请求。 */
    public synchronized void restart(InferenceRequest request, Events events) {
        release();
        this.lastRequest = request;
        this.events = events;
        connect(request, events);
    }

    public synchronized void start(String prompt) {
        IInferenceService s = service;
        if (s == null) {
            Events e = events;
            if (e != null) {
                e.onError(ERR_ENGINE_CRASHED, "推理服务未连接");
            }
            return;
        }
        setState(STATE_RUNNING);
        io.execute(() -> {
            try {
                int code = s.start(prompt, callbackStub);
                if (code != NativeSession.OK) {
                    main.post(() -> {
                        setState(STATE_READY);
                        Events e = events;
                        if (e != null) {
                            e.onError(code, describeLoadError(code));
                        }
                    });
                }
            } catch (RemoteException e) {
                main.post(this::handleCrash);
            }
        });
    }

    public synchronized void stop() {
        IInferenceService s = service;
        if (s == null) {
            return;
        }
        io.execute(() -> {
            try {
                int code = s.stop();
                if (code != NativeSession.OK && code != NativeSession.ERR_WRONG_STATE) {
                    main.post(() -> {
                        Events e = events;
                        if (e != null) {
                            e.onError(code, describeLoadError(code));
                        }
                    });
                }
            } catch (RemoteException e) {
                main.post(this::handleCrash);
            }
        });
    }

    public InferenceStats getStats() {
        IInferenceService s = service;
        if (s == null) {
            return null;
        }
        try {
            return s.getStats();
        } catch (RemoteException e) {
            main.post(this::handleCrash);
            return null;
        }
    }

    /** 释放：取消生成、解绑并通知服务释放模型资源。 */
    public synchronized void release() {
        IInferenceService s = service;
        if (s != null) {
            try {
                s.releaseSession();
            } catch (RemoteException ignored) {
                // process already gone
            }
            service = null;
        }
        try {
            if (bound.getAndSet(false)) {
                appContext.unbindService(connection);
            }
        } catch (RuntimeException ignored) {
            // not bound
        }
        state = STATE_RELEASED;
    }

    public int getState() {
        return state;
    }

    private void handleCrash() {
        service = null;
        setState(STATE_CRASHED);
        Events e = events;
        if (e != null) {
            e.onError(ERR_ENGINE_CRASHED, "推理进程已退出，请重试");
        }
    }

    private void setState(int next) {
        if (state != next) {
            state = next;
            Events e = events;
            if (e != null) {
                e.onStateChanged(next);
            }
        }
    }

    private static String describeLoadError(int code) {
        switch (code) {
            case NativeSession.ERR_MODEL_LOAD_FAILED:
                return "模型文件加载失败（文件无效或不兼容）";
            case NativeSession.ERR_CONTEXT_CREATE_FAILED:
                return "推理上下文创建失败（内存不足）";
            case NativeSession.ERR_TOKENIZE_FAILED:
                return "输入文本分词失败";
            case NativeSession.ERR_WRONG_STATE:
                return "推理服务状态异常";
            case NativeSession.ERR_ILLEGAL_ARGUMENT:
                return "模型路径不合法";
            default:
                return "推理失败（code=" + code + "）";
        }
    }
}
