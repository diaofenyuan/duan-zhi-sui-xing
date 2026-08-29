package com.example.localai.feature.chat

import com.example.localai.model.ChatMessage

/**
 * 对话引擎抽象（P3）：演示模式（MockChatEngine）与真实本地推理
 * （RealChatEngine）共用同一回调协议，聊天页不感知差异。
 */
interface ChatEngine {

    interface StreamListener {
        /** 首个片段到达前触发一次，用于展示"正在思考"。 */
        fun onThinking()

        /** 每次增量片段。 */
        fun onDelta(delta: String)

        /** 正常结束或被用户停止时触发一次；stopped=true 表示用户主动停止。 */
        fun onFinished(stopped: Boolean)

        /** 生成失败（结构化错误码 + 脱敏可读信息）。 */
        fun onError(code: Int, message: String)
    }

    fun isRunning(): Boolean

    /** history 为完整会话消息（含本轮用户输入），由引擎自行组织提示格式。 */
    fun start(history: List<ChatMessage>, listener: StreamListener)

    fun stop()

    /** 释放引擎持有的资源（解绑服务等）；幂等。 */
    fun release()

    /** 是否为真实本地推理。 */
    fun isRealInference(): Boolean

    /** 显示在聊天页标题的模式标识。 */
    fun modeLabel(): String
}
