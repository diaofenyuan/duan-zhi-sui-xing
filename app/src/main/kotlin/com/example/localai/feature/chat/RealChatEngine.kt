package com.example.localai.feature.chat

import android.content.Context
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.core.inference.InferenceClient
import com.example.localai.core.inference.NativeSession
import com.example.localai.core.inference.InferenceRequest
import com.example.localai.feature.settings.InferencePolicy
import com.example.localai.model.ChatMessage

/**
 * 真实本地推理引擎：把完整会话历史组织为批准模型使用的 ChatML 提示，
 * 经 InferenceClient 驱动独立推理进程，转发流式事件。
 */
class RealChatEngine(context: Context, private val approved: ApprovedModels.Approved) : ChatEngine {

    private val appContext: Context = context.applicationContext
    private val client = InferenceClient(appContext)
    private var listener: ChatEngine.StreamListener? = null
    private var running = false
    private var pendingPrompt: String? = null
    private var generation = 0L
    private var loadedParameters: InferencePolicy.Parameters? = null
    private var releasing = false
    private var modelStateListener: (() -> Unit)? = null

    override fun setModelStateListener(listener: (() -> Unit)?) { modelStateListener = listener }

    override fun modelState(): ChatEngine.ModelState = when {
        releasing -> ChatEngine.ModelState.RELEASING
        client.getState() == InferenceClient.STATE_BINDING ||
            client.getState() == InferenceClient.STATE_LOADING -> ChatEngine.ModelState.LOADING
        client.getState() == InferenceClient.STATE_RUNNING -> ChatEngine.ModelState.GENERATING
        client.getState() == InferenceClient.STATE_READY -> ChatEngine.ModelState.LOADED
        client.getState() == InferenceClient.STATE_CRASHED -> ChatEngine.ModelState.ERROR
        else -> ChatEngine.ModelState.UNLOADED
    }

    fun client(): InferenceClient = client

    @Synchronized
    override fun isRunning(): Boolean = running

    @Synchronized
    override fun start(history: List<ChatMessage>, listener: ChatEngine.StreamListener) {
        if (running) {
            listener.onError(NativeSession.ERR_WRONG_STATE, "上一轮生成尚未结束")
            return
        }
        val currentGeneration = ++generation
        releasing = false
        this.listener = listener
        val parameters = InferencePolicy.current(appContext, approved)
        val snapshot = history.map { ChatMessage(it.role, it.text) }
        val request = InferenceRequest("chat-" + System.nanoTime(), approved.modelId, approved.version,
            ApprovedModels.modelFile(appContext, approved).absolutePath, parameters.contextLength,
            parameters.threads, approved.temperature, approved.topP, parameters.maxNewTokens)
        running = true
        pendingPrompt = ""
        listener.onThinking()
        if (!running || generation != currentGeneration) return
        val preparePrompt = {
            client.withTokenizer({ countTokens ->
                val trimmed = ChatHistoryTrimmer.truncate(snapshot,
                    parameters.contextLength - parameters.maxNewTokens - 8, countTokens)
                if (trimmed.inputTooLong) throw NativeSession.NativeException(
                    NativeSession.ERR_INPUT_TOO_LONG, "input exceeds context budget")
                trimmed
            }) { trimmed ->
                if (running && generation == currentGeneration) {
                    if (trimmed.droppedCount > 0) this.listener?.onContextTrimmed(trimmed.droppedCount)
                    pendingPrompt = null
                    client.start(buildPrompt(trimmed.kept))
                }
            }
        }
        val events = object : InferenceClient.Events {
            override fun onStateChanged(state: Int) {
                if (generation != currentGeneration) return
                modelStateListener?.invoke()
                if (state == InferenceClient.STATE_READY && running && generation == currentGeneration) {
                    if (pendingPrompt != null) preparePrompt()
                }
            }

            override fun onToken(batch: String) {
                if (!running || generation != currentGeneration) return
                val l = this@RealChatEngine.listener
                l?.onDelta(batch)
            }

            override fun onFinished(reason: Int) {
                if (!running || generation != currentGeneration) return
                running = false
                pendingPrompt = null
                val l = this@RealChatEngine.listener
                this@RealChatEngine.listener = null
                l?.onFinished(reason == NativeSession.FINISH_STOPPED)
                modelStateListener?.invoke()
            }

            override fun onError(code: Int, message: String) {
                if (!running || generation != currentGeneration) return
                running = false
                pendingPrompt = null
                val l = this@RealChatEngine.listener
                this@RealChatEngine.listener = null
                l?.onError(code, message)
                modelStateListener?.invoke()
            }
        }
        if (client.getState() == InferenceClient.STATE_READY && loadedParameters == parameters) {
            client.setEvents(events)
            preparePrompt()
        } else {
            loadedParameters = parameters
            client.restart(request, events)
        }
        modelStateListener?.invoke()
    }

    @Synchronized
    override fun stop() {
        if (running) {
            if (pendingPrompt != null) {
                // 加载尚无 Native 生成回调，直接结束本轮并使延迟的 READY 失效。
                val receiver = listener
                release()
                receiver?.onFinished(true)
            } else {
                client.stop()
            }
        }
    }

    @Synchronized
    override fun release() {
        val releasedGeneration = ++generation
        releasing = releasing || modelState() in setOf(ChatEngine.ModelState.LOADING,
            ChatEngine.ModelState.LOADED, ChatEngine.ModelState.GENERATING)
        running = false
        pendingPrompt = null
        listener = null
        loadedParameters = null
        client.release {
            // 旧释放确认不得覆盖用户已开始的下一轮加载状态。
            if (generation == releasedGeneration) {
                releasing = false
                modelStateListener?.invoke()
            }
        }
        modelStateListener?.invoke()
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
