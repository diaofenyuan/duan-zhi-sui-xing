package com.example.localai.feature.chat

import android.content.Context
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.core.inference.InferenceClient
import com.example.localai.core.inference.InferenceRequest
import com.example.localai.core.inference.NativeSession
import com.example.localai.model.ChatMessage

/**
 * 真实本地推理引擎：把完整会话历史组织为 SmolLM ChatML 提示，
 * 经 InferenceClient 驱动独立推理进程，转发流式事件。
 */
class RealChatEngine(context: Context, private val approved: ApprovedModels.Approved) : ChatEngine {

    private val appContext: Context = context.applicationContext
    private val client = InferenceClient(appContext)
    private var listener: ChatEngine.StreamListener? = null
    private var running = false
    private var pendingPrompt: String? = null

    fun client(): InferenceClient = client

    @Synchronized
    override fun isRunning(): Boolean = running

    @Synchronized
    override fun start(history: List<ChatMessage>, listener: ChatEngine.StreamListener) {
        this.listener = listener
        val trimmed = ChatHistoryTrimmer.truncate(history, approved.contextLength)
        val prompt = buildPrompt(trimmed.kept)
        val request = ApprovedModels.requestFor(
            appContext, approved, "chat-" + System.nanoTime())
        running = true
        listener.onThinking()
        val events = object : InferenceClient.Events {
            override fun onStateChanged(state: Int) {
                if (state == InferenceClient.STATE_READY) {
                    val queued: String?
                    synchronized(this@RealChatEngine) {
                        queued = pendingPrompt
                        pendingPrompt = null
                    }
                    if (queued != null) {
                        client.start(queued)
                    }
                }
            }

            override fun onToken(batch: String) {
                val l = this@RealChatEngine.listener
                l?.onDelta(batch)
            }

            override fun onFinished(reason: Int) {
                running = false
                val l = this@RealChatEngine.listener
                l?.onFinished(reason == NativeSession.FINISH_STOPPED)
            }

            override fun onError(code: Int, message: String) {
                running = false
                val l = this@RealChatEngine.listener
                l?.onError(code, message)
            }
        }
        if (client.getState() == InferenceClient.STATE_READY) {
            client.start(prompt)
        } else {
            pendingPrompt = prompt
            client.connect(request, events)
        }
    }

    @Synchronized
    override fun stop() {
        if (running) {
            client.stop()
        }
    }

    @Synchronized
    override fun release() {
        running = false
        client.release()
        listener = null
    }

    override fun isRealInference(): Boolean = true

    override fun modeLabel(): String = "本地推理"

    companion object {
        /** SmolLM ChatML 风格提示（im_start/im_end 特殊 token 随模型词表解析）。 */
        @JvmStatic
        fun buildPrompt(history: List<ChatMessage>): String {
            val sb = StringBuilder(512)
            for (m in history) {
                if (m.role == ChatMessage.ROLE_USER) {
                    sb.append("<|im_start|>user\n").append(m.text).append("<|im_end|>\n")
                } else {
                    sb.append("<|im_start|>assistant\n").append(m.text).append("<|im_end|>\n")
                }
            }
            sb.append("<|im_start|>assistant\n")
            return sb.toString()
        }
    }
}
