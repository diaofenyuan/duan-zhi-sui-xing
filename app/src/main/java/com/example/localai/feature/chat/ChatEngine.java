package com.example.localai.feature.chat;

import com.example.localai.model.ChatMessage;

import java.util.List;

/**
 * 对话引擎抽象（P3）：演示模式（MockChatEngine）与真实本地推理
 * （RealChatEngine）共用同一回调协议，聊天页不感知差异。
 */
public interface ChatEngine {

    interface StreamListener {
        /** 首个片段到达前触发一次，用于展示"正在思考"。 */
        void onThinking();

        /** 每次增量片段。 */
        void onDelta(String delta);

        /** 正常结束或被用户停止时触发一次；stopped=true 表示用户主动停止。 */
        void onFinished(boolean stopped);

        /** 生成失败（结构化错误码 + 脱敏可读信息）。 */
        void onError(int code, String message);
    }

    boolean isRunning();

    /** history 为完整会话消息（含本轮用户输入），由引擎自行组织提示格式。 */
    void start(List<ChatMessage> history, StreamListener listener);

    void stop();

    /** 释放引擎持有的资源（解绑服务等）；幂等。 */
    void release();

    /** 是否为真实本地推理。 */
    boolean isRealInference();

    /** 显示在聊天页标题的模式标识。 */
    String modeLabel();
}
