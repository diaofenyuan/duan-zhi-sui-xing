package com.example.localai.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.ModelEntity
import com.example.localai.model.ChatMessage
import com.example.localai.model.ModelInfo
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.util.ArrayList

/**
 * 本地对话页：流式输出（演示/真实推理双引擎）、停止、复制/重试/删除、
 * 模型切换（真实已安装模型）与会话持久化（Room：新建/自动保存/恢复）。
 */
class ChatFragment : Fragment(), ChatEngine.StreamListener {

    private lateinit var adapter: MessageAdapter
    private lateinit var engine: ChatEngine

    private lateinit var messagesView: RecyclerView
    private lateinit var emptyWrap: View
    private lateinit var inputView: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var modelTitle: TextView
    private lateinit var statusDot: View

    private var streamingBot: ChatMessage? = null
    private var botPosition = -1
    private var generating = false

    /** 当前会话 id（<=0 表示尚未持久化的新会话）。 */
    private var conversationId = 0L
    private var currentModelId = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        currentModelId = prefs().getString(KEY_MODEL, "") ?: ""
        engine = ChatEngineProvider.create(requireContext(), currentModelId)
        adapter = MessageAdapter { message, position -> showMessageMenu(message, position) }

        messagesView = view.findViewById(R.id.messages)
        val lm = LinearLayoutManager(requireContext())
        lm.stackFromEnd = true
        messagesView.layoutManager = lm
        messagesView.adapter = adapter

        emptyWrap = view.findViewById(R.id.empty_wrap)
        inputView = view.findViewById(R.id.input)
        btnSend = view.findViewById(R.id.btn_send)
        modelTitle = view.findViewById(R.id.text_model)
        statusDot = view.findViewById(R.id.status_dot)

        btnSend.setOnClickListener {
            if (generating) {
                engine.stop()
            } else {
                sendInput()
            }
        }
        inputView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, st: Int, c: Int, a: Int) {
            }

            override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                if (!generating) {
                    refreshSendState(s.toString().trim().isEmpty())
                }
            }
        })

        view.findViewById<View>(R.id.btn_history).setOnClickListener {
            if (activity is MainActivity) {
                (activity as MainActivity).push(HistoryFragment())
            }
        }
        view.findViewById<View>(R.id.btn_new).setOnClickListener { startNewConversation() }
        view.findViewById<View>(R.id.btn_switch).setOnClickListener { showModelPicker() }

        refreshHeader()
        refreshSendState(true)
    }

    override fun onResume() {
        super.onResume()
        consumePendingNavigation()
        ensureModelSelected()
        refreshHeader()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Tab 切换走 show/hide，不触发 onResume；重新可见时需刷新模型状态
        if (!hidden) {
            consumePendingNavigation()
            ensureModelSelected()
            refreshHeader()
        }
    }

    override fun onDestroyView() {
        engine.release()
        super.onDestroyView()
    }

    private fun consumePendingNavigation() {
        val a = activity as? MainActivity ?: return
        val pendingConversation = a.consumePendingConversation()
        if (pendingConversation > 0) {
            loadConversation(pendingConversation)
        }
        val pendingModel = a.consumePendingChatModel()
        if (pendingModel != null && pendingModel != currentModelId) {
            setCurrentModel(pendingModel)
        }
        val prefill = a.consumeChatPrefill()
        if (prefill != null) {
            // 详情页示例指令：如果是新会话，预填输入框
            if (adapter.items().isEmpty()) {
                inputView.setText(prefill)
                inputView.setSelection(inputView.text.length)
                refreshSendState(false)
            }
        }
    }

    private fun loadConversation(id: Long) {
        val repository = ServiceLocator.chat() ?: return
        repository.loadMessages(id) { messages, _ ->
            if (!isAdded) {
                return@loadMessages
            }
            adapter.submit(ArrayList())
            if (messages != null) {
                for (m in messages) {
                    adapter.items().add(m)
                }
                adapter.notifyDataSetChanged()
            }
            conversationId = id
            updateEmptyState()
            refreshHeader()
        }
    }

    private fun startNewConversation() {
        adapter.submit(ArrayList())
        conversationId = 0
        updateEmptyState()
    }

    private fun showModelPicker() {
        val installed = installedModelInfos()
        var current = installedModelInfo(currentModelId)
        if (current == null && installed.isNotEmpty()) {
            current = installed[0]
        }
        ModelPickerSheet(installed,
            current?.id ?: "",
            ModelPickerSheet.Callback { model -> setCurrentModel(model.id) })
            .show(parentFragmentManager, "picker")
    }

    /** 真实已安装模型：Room installed_models ∪ 批准模型文件存在。 */
    private fun installedModelInfos(): List<ModelInfo> {
        val result = ArrayList<ModelInfo>()
        val installed = ServiceLocator.downloads()?.installed() ?: ArrayList<ModelEntity>()
        for (entity in installed) {
            result.add(entityToInfo(entity))
        }
        if (ApprovedModels.isInstalled(requireContext(), ApprovedModels.SMOLLM_135M.modelId)) {
            var present = false
            for (m in result) {
                if (m.id == ApprovedModels.SMOLLM_135M.modelId) {
                    present = true
                    break
                }
            }
            if (!present) {
                result.add(approvedToInfo())
            }
        }
        return result
    }

    private fun installedModelInfo(modelId: String): ModelInfo? {
        for (m in installedModelInfos()) {
            if (m.id == modelId) {
                return m
            }
        }
        return null
    }

    private fun entityToInfo(entity: ModelEntity): ModelInfo {
        return ModelInfo(
            entity.modelId, if (entity.displayName == null) entity.modelId else entity.displayName,
            if (entity.publisher == null) "" else entity.publisher,
            "0.1B", 0.1, if (entity.quantization == null) "" else entity.quantization,
            "0 MB", entity.sizeBytes, "1K", ModelInfo.TASK_TEXT,
            ModelInfo.langs("英文"), if (entity.licenseSpdx == null) "" else entity.licenseSpdx,
            "", ModelInfo.COMPAT_RECOMMENDED, "", 0, 0.0, 0, "", 0, true)
    }

    private fun approvedToInfo(): ModelInfo {
        return ApprovedModels.installedAsModelInfos(requireContext())[0]
    }

    private fun setCurrentModel(modelId: String) {
        applyModel(modelId, true)
    }

    /** 从未选过模型时自动选中第一个已安装模型，避免已安装却提示"未安装模型"。 */
    private fun ensureModelSelected() {
        if (currentModelId.isNotEmpty()) {
            return
        }
        val installed = installedModelInfos()
        if (installed.isEmpty()) {
            return
        }
        applyModel(installed[0].id, false)
    }

    private fun applyModel(modelId: String, announce: Boolean) {
        currentModelId = modelId
        prefs().edit().putString(KEY_MODEL, modelId).apply()
        engine.release()
        engine = ChatEngineProvider.create(requireContext(), modelId)
        refreshHeader()
        if (announce) {
            val model = installedModelInfo(modelId)
            if (model != null) {
                Snackbar.make(requireView(), model.name, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshHeader() {
        if (currentModelId.isEmpty()) {
            modelTitle.setText(R.string.chat_no_model)
            setStatusDot(R.color.status_warn)
            return
        }
        val approved = resolveApproved()
        val state = if (generating) getString(R.string.chat_generating)
        else getString(R.string.chat_ready)
        val label = engine.modeLabel()
        if (approved != null) {
            modelTitle.text = approved.displayName + " · " + label + " · " + state
        } else {
            val model = installedModelInfo(currentModelId)
            if (model == null) {
                modelTitle.setText(R.string.chat_no_model)
            } else {
                modelTitle.text = model.name + " · " + label + " · " + state
            }
        }
        setStatusDot(if (resolveApproved() != null) R.color.status_success else R.color.status_warn)
    }

    /** 头部状态点：按语义着色（就绪绿 / 未安装琥珀）。 */
    private fun setStatusDot(colorRes: Int) {
        statusDot.setBackgroundResource(R.drawable.bg_dot)
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), colorRes))
    }

    /** 当前选中的批准模型（已安装且文件存在）；否则 null。 */
    private fun resolveApproved(): ApprovedModels.Approved? {
        val approved = ApprovedModels.byId(currentModelId)
        if (approved != null && ApprovedModels.isInstalled(requireContext(), approved.modelId)) {
            return approved
        }
        return null
    }

    private fun refreshSendState(emptyInput: Boolean) {
        btnSend.setIconResource(if (generating) R.drawable.ic_stop else R.drawable.ic_send)
        btnSend.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(),
                if (generating) R.color.status_danger else R.color.md_primary))
    }

    private fun sendInput() {
        val text = inputView.text.toString().trim()
        if (text.isEmpty()) {
            return
        }
        if (currentModelId.isEmpty()) {
            Snackbar.make(messagesView, R.string.picker_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        inputView.setText("")
        adapter.items().add(ChatMessage(ChatMessage.ROLE_USER, text))
        adapter.notifyItemInserted(adapter.itemCount - 1)
        scrollToBottom()
        updateEmptyState()

        generating = true
        refreshSendState(false)
        refreshHeader()
        engine.start(historySnapshot(), this)
    }

    /** 当前会话的完整消息列表（含刚加入的用户消息）。 */
    private fun historySnapshot(): List<ChatMessage> {
        val history = ArrayList<ChatMessage>()
        for (o in adapter.items()) {
            if (o is ChatMessage) {
                history.add(o)
            }
        }
        return history
    }

    override fun onThinking() {
        adapter.items().add(MessageAdapter.TYPING)
        botPosition = adapter.itemCount - 1
        adapter.notifyItemInserted(botPosition)
        scrollToBottom()
    }

    override fun onDelta(delta: String) {
        val bot = streamingBot
        if (bot == null) {
            if (botPosition >= 0 && botPosition < adapter.items().size) {
                adapter.items().removeAt(botPosition)
                adapter.notifyItemRemoved(botPosition)
            }
            val newBot = ChatMessage(ChatMessage.ROLE_BOT, delta)
            streamingBot = newBot
            adapter.items().add(newBot)
            botPosition = adapter.itemCount - 1
            adapter.notifyItemInserted(botPosition)
        } else {
            bot.text += delta
            adapter.updateLast()
        }
        scrollToBottom()
    }

    override fun onFinished(stopped: Boolean) {
        if (stopped) {
            val bot = streamingBot
            if (bot != null) {
                bot.text += "\n（已停止生成）"
                adapter.updateLast()
            } else if (botPosition >= 0 && botPosition < adapter.items().size) {
                adapter.items().removeAt(botPosition)
                adapter.notifyItemRemoved(botPosition)
            }
        }
        streamingBot = null
        botPosition = -1
        generating = false
        refreshSendState(inputView.text.toString().trim().isEmpty())
        refreshHeader()
        persistConversation()
    }

    override fun onError(code: Int, message: String) {
        if (botPosition >= 0 && botPosition < adapter.items().size) {
            adapter.items().removeAt(botPosition)
            adapter.notifyItemRemoved(botPosition)
        }
        adapter.items().add(ChatMessage(ChatMessage.ROLE_BOT,
            "生成失败：" + message + "（" + code + "）"))
        adapter.notifyItemInserted(adapter.itemCount - 1)
        scrollToBottom()
        streamingBot = null
        botPosition = -1
        generating = false
        refreshSendState(inputView.text.toString().trim().isEmpty())
        refreshHeader()
        persistConversation()
    }

    /** 全量覆盖保存会话（新会话自动创建并回填 id）。 */
    private fun persistConversation() {
        val repository = ServiceLocator.chat() ?: return
        repository.saveConversation(conversationId, currentModelId, deriveTitle(), historySnapshot(),
            object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) {
                    this@ChatFragment.conversationId = conversationId
                }

                override fun onError(message: String?) {
                    Snackbar.make(requireView(),
                        "会话保存失败：" + message, Snackbar.LENGTH_SHORT).show()
                }
            })
        repository.refresh()
    }

    private fun deriveTitle(): String {
        for (o in adapter.items()) {
            if (o is ChatMessage) {
                val text = o.text
                if (text.isNotEmpty()) {
                    return if (text.length > 24) text.substring(0, 24) else text
                }
            }
        }
        return ""
    }

    private fun showMessageMenu(message: ChatMessage, position: Int) {
        val menu = PopupMenu(requireContext(), messagesView)
        menu.menuInflater.inflate(R.menu.menu_message, menu.menu)
        menu.menu.findItem(R.id.action_retry).isVisible =
            message.role == ChatMessage.ROLE_USER && !generating
        menu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_copy -> copyToClipboard(message.text)
                R.id.action_delete_msg -> {
                    adapter.items().removeAt(position)
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    persistConversation()
                }
                R.id.action_retry -> retryFrom(message, position)
            }
            true
        }
        menu.show()
    }

    private fun retryFrom(userMessage: ChatMessage, position: Int) {
        while (adapter.items().size > position) {
            adapter.items().removeAt(adapter.items().size - 1)
        }
        adapter.notifyDataSetChanged()
        generating = true
        refreshSendState(false)
        refreshHeader()
        engine.start(historySnapshot(), this)
    }

    private fun copyToClipboard(text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("message", text))
        Snackbar.make(messagesView, R.string.toast_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun updateEmptyState() {
        emptyWrap.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun scrollToBottom() {
        messagesView.post {
            messagesView.smoothScrollToPosition(maxOf(0, adapter.itemCount - 1))
        }
    }

    private fun prefs(): SharedPreferences =
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "localai_chat"
        private const val KEY_MODEL = "current_model"
    }
}
