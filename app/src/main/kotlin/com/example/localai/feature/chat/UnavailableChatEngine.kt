package com.example.localai.feature.chat

import com.example.localai.core.inference.NativeSession
import com.example.localai.model.ChatMessage

/** 不可用状态保留统一接口，但不合成回复，也不创建推理线程。 */
class UnavailableChatEngine : ChatEngine {
    override fun start(history: List<ChatMessage>, listener: ChatEngine.StreamListener) {
        listener.onError(NativeSession.ERR_MODEL_LOAD_FAILED, "模型尚未安装或已失效，请到模型市场下载后重试")
    }
    override fun isRunning() = false
    override fun stop() = Unit
    override fun release() = Unit
    override fun isRealInference() = false
    override fun modeLabel() = "模型不可用"
}
