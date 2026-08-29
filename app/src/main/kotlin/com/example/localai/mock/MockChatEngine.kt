package com.example.localai.mock

import android.os.Handler
import android.os.Looper
import com.example.localai.feature.chat.ChatEngine
import com.example.localai.model.ChatMessage

/** 模拟流式对话引擎：先延迟（模拟 TTFT），再按小片段回调文本。 */
class MockChatEngine : ChatEngine {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var stoppedByUser = false
    private var listener: ChatEngine.StreamListener? = null

    @Synchronized
    override fun isRunning(): Boolean = running

    @Synchronized
    override fun start(history: List<ChatMessage>, listener: ChatEngine.StreamListener) {
        stopInternal()
        running = true
        stoppedByUser = false
        this.listener = listener
        listener.onThinking()

        val prompt = lastUserText(history)
        handler.postDelayed({
            val chunks = ReplyComposer.chunk(ReplyComposer.build(prompt))
            emit(chunks, 0, listener)
        }, TTFT_MS)
    }

    @Synchronized
    override fun stop() {
        val wasRunning = running
        if (wasRunning) {
            stoppedByUser = true
        }
        stopInternal()
        if (wasRunning) {
            listener?.onFinished(true)
        }
    }

    @Synchronized
    override fun release() {
        stop()
        listener = null
    }

    override fun isRealInference(): Boolean = false

    override fun modeLabel(): String = "演示模式"

    private fun lastUserText(history: List<ChatMessage>): String {
        for (i in history.size - 1 downTo 0) {
            if (history[i].role == ChatMessage.ROLE_USER) {
                return history[i].text
            }
        }
        return ""
    }

    private fun emit(chunks: Array<String>, index: Int, listener: ChatEngine.StreamListener) {
        handler.post {
            if (!running) {
                listener.onFinished(stoppedByUser)
                return@post
            }
            if (index >= chunks.size) {
                running = false
                listener.onFinished(false)
                return@post
            }
            listener.onDelta(chunks[index])
            handler.postDelayed({
                emit(chunks, index + 1, listener)
            }, 26L)
        }
    }

    private fun stopInternal() {
        handler.removeCallbacksAndMessages(null)
        running = false
    }

    companion object {
        private const val TTFT_MS = 550L
    }
}
