package com.example.localai.mock;

import android.os.Handler;
import android.os.Looper;

import com.example.localai.feature.chat.ChatEngine;
import com.example.localai.model.ChatMessage;

import java.util.List;

/** 模拟流式对话引擎：先延迟（模拟 TTFT），再按小片段回调文本。 */
public final class MockChatEngine implements ChatEngine {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private boolean stoppedByUser = false;
    private StreamListener listener;

    private static final long TTFT_MS = 550;

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    @Override
    public synchronized void start(final List<ChatMessage> history, final StreamListener listener) {
        stopInternal();
        running = true;
        stoppedByUser = false;
        this.listener = listener;
        listener.onThinking();

        String prompt = lastUserText(history);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String[] chunks = ReplyComposer.chunk(ReplyComposer.build(prompt));
                emit(chunks, 0, listener);
            }
        }, TTFT_MS);
    }

    @Override
    public synchronized void stop() {
        boolean wasRunning = running;
        if (wasRunning) {
            stoppedByUser = true;
        }
        stopInternal();
        if (wasRunning && listener != null) {
            listener.onFinished(true);
        }
    }

    @Override
    public synchronized void release() {
        stop();
        listener = null;
    }

    @Override
    public boolean isRealInference() {
        return false;
    }

    @Override
    public String modeLabel() {
        return "演示模式";
    }

    private static String lastUserText(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role == ChatMessage.ROLE_USER) {
                return history.get(i).text;
            }
        }
        return "";
    }

    private void emit(final String[] chunks, final int index, final StreamListener listener) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    listener.onFinished(stoppedByUser);
                    return;
                }
                if (index >= chunks.length) {
                    running = false;
                    listener.onFinished(false);
                    return;
                }
                listener.onDelta(chunks[index]);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        emit(chunks, index + 1, listener);
                    }
                }, 26);
            }
        });
    }

    private void stopInternal() {
        handler.removeCallbacksAndMessages(null);
        running = false;
    }
}
