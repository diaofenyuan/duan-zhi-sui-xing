package com.example.localai.feature.chat;

import android.content.Context;

import com.example.localai.core.inference.ApprovedModels;
import com.example.localai.core.inference.InferenceClient;
import com.example.localai.core.inference.InferenceRequest;
import com.example.localai.core.inference.NativeSession;
import com.example.localai.model.ChatMessage;

import java.util.List;

/**
 * 真实本地推理引擎：把完整会话历史组织为 SmolLM ChatML 提示，
 * 经 InferenceClient 驱动独立推理进程，转发流式事件。
 */
public final class RealChatEngine implements ChatEngine {

    private final Context appContext;
    private final InferenceClient client;
    private final ApprovedModels.Approved approved;
    private StreamListener listener;
    private boolean running = false;
    private String pendingPrompt;

    public RealChatEngine(Context context, ApprovedModels.Approved approved) {
        this.appContext = context.getApplicationContext();
        this.client = new InferenceClient(appContext);
        this.approved = approved;
    }

    public InferenceClient client() {
        return client;
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    public synchronized void start(List<ChatMessage> history, StreamListener listener) {
        this.listener = listener;
        String prompt = buildPrompt(history);
        InferenceRequest request = ApprovedModels.requestFor(
                appContext, approved, "chat-" + System.nanoTime());
        running = true;
        listener.onThinking();
        InferenceClient.Events events = new InferenceClient.Events() {
            @Override
            public void onStateChanged(int state) {
                if (state == InferenceClient.STATE_READY) {
                    String queued;
                    synchronized (RealChatEngine.this) {
                        queued = pendingPrompt;
                        pendingPrompt = null;
                    }
                    if (queued != null) {
                        client.start(queued);
                    }
                }
            }

            @Override
            public void onToken(String batch) {
                StreamListener l = RealChatEngine.this.listener;
                if (l != null) {
                    l.onDelta(batch);
                }
            }

            @Override
            public void onFinished(int reason) {
                running = false;
                StreamListener l = RealChatEngine.this.listener;
                if (l != null) {
                    l.onFinished(reason == NativeSession.FINISH_STOPPED);
                }
            }

            @Override
            public void onError(int code, String message) {
                running = false;
                StreamListener l = RealChatEngine.this.listener;
                if (l != null) {
                    l.onError(code, message);
                }
            }
        };
        if (client.getState() == InferenceClient.STATE_READY) {
            client.start(prompt);
        } else {
            pendingPrompt = prompt;
            client.connect(request, events);
        }
    }

    @Override
    public synchronized void stop() {
        if (running) {
            client.stop();
        }
    }

    @Override
    public synchronized void release() {
        running = false;
        client.release();
        listener = null;
    }

    @Override
    public boolean isRealInference() {
        return true;
    }

    @Override
    public String modeLabel() {
        return "本地推理";
    }

    /** SmolLM ChatML 风格提示（im_start/im_end 特殊 token 随模型词表解析）。 */
    static String buildPrompt(List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder(512);
        for (ChatMessage m : history) {
            if (m.role == ChatMessage.ROLE_USER) {
                sb.append("<|im_start|>user\n").append(m.text).append("<|im_end|>\n");
            } else {
                sb.append("<|im_start|>assistant\n").append(m.text).append("<|im_end|>\n");
            }
        }
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }
}
