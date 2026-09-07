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
import com.example.localai.common.ModelDisplay
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.data.ServiceLocator
import com.example.localai.model.ChatMessage
import com.example.localai.model.ModelInfo
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.util.ArrayList

/**
 * 本地对话页：流式推理、停止、复制/重试/删除、
 * 模型切换（真实已安装模型）与会话持久化（Room：新建/自动保存/恢复）。
 */
class ChatFragment : Fragment(), ChatEngine.StreamListener {

    private lateinit var adapter: MessageAdapter
    private lateinit var engine: ChatEngine

    private lateinit var messagesView: RecyclerView
    private lateinit var btnLatestMessage: View
    private var followLatest = true
    private var userScrolling = false
    private val followScroll = Runnable {
        if (view != null && followLatest && !userScrolling && adapter.itemCount > 0) {
            val layout = messagesView.layoutManager as LinearLayoutManager
            val last = layout.findViewByPosition(adapter.itemCount - 1)
            if (last == null) {
                messagesView.smoothScrollToPosition(adapter.itemCount - 1)
            } else {
                // 单条长回复也必须跟随到底部，不能仅判断最后一项是否已出现在屏幕中。
                val remaining = layout.getDecoratedBottom(last) - messagesView.height + messagesView.paddingBottom
                if (remaining > 0) messagesView.smoothScrollBy(0, remaining)
            }
        }
    }
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                userScrolling = true
                followLatest = false
                recyclerView.removeCallbacks(followScroll)
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (userScrolling) {
                    userScrolling = false
                    followLatest = isNearBottom()
                }
                // 从很早的历史跳回时，先定位末条，再对齐超长气泡的底部。
                if (followLatest) scrollToBottom()
            }
            updateLatestButton()
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateLatestButton()
        }
    }
    private lateinit var emptyWrap: View
    private lateinit var inputView: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var modelTitle: TextView
    private lateinit var modelStateText: TextView
    private lateinit var btnUnloadModel: MaterialButton
    private lateinit var statusDot: View

    private var streamingBot: ChatMessage? = null
    private var botPosition = -1
    private var generating = false

    /** 当前会话 id（<=0 表示尚未持久化的新会话）。 */
    private var conversationId = 0L
    private var currentModelId = ""
    private var streamEpoch = 0L
    private var conversationEpoch = 0L
    private var sessionToken = java.util.UUID.randomUUID().toString()
    private var loadingHistory = false
    private var draft = ""
    private var checkpointPending = false
    private var saveErrorBar: Snackbar? = null
    private val checkpoint = Runnable {
        checkpointPending = false
        if (view != null && !loadingHistory) persistConversation()
    }
    private val historyListener = object : ChatRepository.Listener {
        override fun onConversationsChanged() {}
        override fun onConversationsDeleted(ids: Set<Long>?) {
            if (ids == null || conversationId in ids) {
                // 删除后的迟到生成/保存回调失效；返回栈中的页面也要清除内存正文。
                streamEpoch++
                engine.release()
                generating = false
                streamingBot = null
                botPosition = -1
                startNewConversation()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val restore = !::adapter.isInitialized
        if (restore) currentModelId = prefs().getString(KEY_MODEL, "") ?: ""
        engine = ChatEngineProvider.create(requireContext(), currentModelId)
        if (restore) {
            adapter = MessageAdapter { message, position -> showMessageMenu(message, position) }
        }

        messagesView = view.findViewById(R.id.messages)
        val lm = LinearLayoutManager(requireContext())
        lm.stackFromEnd = true
        messagesView.layoutManager = lm
        messagesView.adapter = adapter
        messagesView.addOnScrollListener(scrollListener)
        btnLatestMessage = view.findViewById(R.id.btn_latest_message)
        btnLatestMessage.setOnClickListener { scrollToBottom(force = true) }

        emptyWrap = view.findViewById(R.id.empty_wrap)
        inputView = view.findViewById(R.id.input)
        // 草稿由数据库和正文一起恢复，避免系统旧的视图状态覆盖异步加载结果。
        inputView.isSaveEnabled = false
        btnSend = view.findViewById(R.id.btn_send)
        modelTitle = view.findViewById(R.id.text_model)
        statusDot = view.findViewById(R.id.status_dot)
        modelStateText = view.findViewById(R.id.text_model_state)
        btnUnloadModel = view.findViewById(R.id.btn_unload_model)
        observeModelState()
        btnUnloadModel.setOnClickListener {
            if (!generating && engine.modelState() == ChatEngine.ModelState.LOADED) engine.release()
        }

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
                draft = s.toString()
                if (!generating) {
                    refreshSendState(s.toString().trim().isEmpty())
                }
                scheduleCheckpoint()
            }
        })

        view.findViewById<View>(R.id.btn_history).setOnClickListener {
            if (activity is MainActivity) {
                (activity as MainActivity).push(HistoryFragment())
            }
        }
        view.findViewById<View>(R.id.btn_new).setOnClickListener { startNewConversation() }
        // 大字体或窄屏把次要操作收进菜单，为模型名称保留完整阅读空间。
        val compactHeader = resources.configuration.screenWidthDp / resources.configuration.fontScale < 300
        view.findViewById<View>(R.id.btn_new).visibility = if (compactHeader) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.btn_history).visibility = if (compactHeader) View.GONE else View.VISIBLE
        modelTitle.setOnClickListener { showModelPicker() }
        view.findViewById<View>(R.id.btn_switch).setOnClickListener { anchor ->
            androidx.appcompat.widget.PopupMenu(requireContext(), anchor).apply {
                if (compactHeader) {
                    menu.add(R.string.action_new_conversation).setOnMenuItemClickListener { startNewConversation(); true }
                    menu.add(R.string.history_title).setOnMenuItemClickListener {
                        (activity as? MainActivity)?.push(HistoryFragment()); true
                    }
                }
                menu.add(R.string.picker_title).setOnMenuItemClickListener { showModelPicker(); true }
                menu.add("资料与工作区").setOnMenuItemClickListener {
                    (activity as? MainActivity)?.push(com.example.localai.feature.library.LibraryFragment()); true
                }
                menu.add("保存对话到工作区").setOnMenuItemClickListener { saveToWorkspace(); true }
                if (!generating && engine.modelState() == ChatEngine.ModelState.LOADED) {
                    menu.add(R.string.model_release_action).setOnMenuItemClickListener { engine.release(); true }
                }
                show()
            }
        }
        view.findViewById<View>(R.id.btn_get_model).setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_market)
        }
        view.findViewById<View>(R.id.btn_materials).setOnClickListener {
            (activity as? MainActivity)?.push(com.example.localai.feature.library.LibraryFragment())
        }
        for ((id, kind) in listOf(R.id.prompt_think to "summary", R.id.prompt_write to "rewrite", R.id.prompt_todo to "todo")) {
            view.findViewById<View>(id).setOnClickListener {
                (activity as? MainActivity)?.push(com.example.localai.feature.library.TaskFragment.create(kind, input = inputView.text.toString()))
            }
        }

        refreshHeader()
        inputView.setText(draft)
        refreshSendState(draft.trim().isEmpty())
        updateEmptyState()
        ServiceLocator.chat()?.register(historyListener)
        if (restore) restoreSession()
    }

    override fun onResume() {
        super.onResume()
        consumePendingNavigation()
        ensureModelSelected()
        refreshHeader()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden && view != null) {
            interruptGeneration()
            persistConversation()
            view?.keepScreenOn = false
        }
        // Tab 切换走 show/hide，不触发 onResume；重新可见时需刷新模型状态
        if (!hidden && view != null) {
            consumePendingNavigation()
            ensureModelSelected()
            refreshHeader()
        }
    }

    override fun onDestroyView() {
        saveErrorBar?.dismiss()
        saveErrorBar = null
        view?.removeCallbacks(checkpoint)
        checkpointPending = false
        draft = inputView.text.toString()
        interruptGeneration()
        engine.setModelStateListener(null)
        messagesView.adapter = null
        messagesView.removeCallbacks(followScroll)
        messagesView.removeOnScrollListener(scrollListener)
        userScrolling = false
        super.onDestroyView()
    }

    override fun onPause() {
        // 页面退场动画会延迟 onDestroyView，先终止生成，避免这段时间仍接收旧消息。
        if (view != null) {
            view?.keepScreenOn = false
            view?.removeCallbacks(checkpoint)
            checkpointPending = false
            draft = inputView.text.toString()
            interruptGeneration()
            if (!loadingHistory) persistConversation()
        }
        super.onPause()
    }

    override fun onDestroy() {
        ServiceLocator.chat()?.unregister(historyListener)
        super.onDestroy()
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
        interruptGeneration()
        val epoch = ++conversationEpoch
        loadingHistory = true
        refreshSendState(inputView.text.toString().trim().isEmpty())
        repository.loadConversation(id) { session, error ->
            if (conversationEpoch != epoch) return@loadConversation
            loadingHistory = false
            if (session == null || error != null) {
                this.view?.let { Snackbar.make(it, "会话加载失败，请重试", Snackbar.LENGTH_SHORT).show() }
            } else {
                applySession(session)
                persistConversation()
            }
            if (view != null) {
                updateEmptyState()
                refreshHeader()
                refreshSendState(inputView.text.toString().trim().isEmpty())
            }
        }
    }

    private fun restoreSession() {
        val repository = ServiceLocator.chat() ?: return
        val epoch = ++conversationEpoch
        loadingHistory = true
        refreshSendState(true)
        repository.loadSession { session, error ->
            if (epoch != conversationEpoch) return@loadSession
            // 读取失败时保持只读，避免空页面的自动保存覆盖尚未成功读取的会话。
            loadingHistory = error != null
            if (session != null) applySession(session)
            if (view != null) {
                if (error != null) Snackbar.make(requireView(), "恢复会话失败：$error", Snackbar.LENGTH_INDEFINITE)
                    .setAction("重试") { restoreSession() }.show()
                updateEmptyState()
                refreshSendState(inputView.text.toString().trim().isEmpty())
            }
        }
    }

    private fun applySession(session: ChatRepository.Session) {
        sessionToken = session.token
        conversationId = session.conversationId
        currentModelId = session.modelId
        draft = session.draft
        adapter.submit(session.messages)
        followLatest = true
        if (view != null) {
            inputView.setText(draft)
            inputView.setSelection(inputView.text.length)
            applyModel(currentModelId, false)
            ensureModelSelected()
            scrollToBottom(force = true)
        }
    }

    private fun scheduleCheckpoint() {
        if (loadingHistory || checkpointPending || view == null) return
        checkpointPending = true
        view?.postDelayed(checkpoint, 500)
    }

    private fun startNewConversation() {
        interruptGeneration()
        conversationEpoch++
        sessionToken = java.util.UUID.randomUUID().toString()
        loadingHistory = false
        adapter.submit(ArrayList())
        followLatest = true
        conversationId = 0
        draft = ""
        if (view != null) {
            inputView.setText("")
            updateEmptyState()
            refreshSendState(true)
        }
        persistConversation()
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

    /** 选择器只展示已完成校验安装且引擎支持的模型。 */
    private fun installedModelInfos(): List<ModelInfo> {
        return ApprovedModels.installedAsModelInfos(requireContext())
    }

    private fun installedModelInfo(modelId: String): ModelInfo? {
        for (m in installedModelInfos()) {
            if (m.id == modelId) {
                return m
            }
        }
        return null
    }

    private fun setCurrentModel(modelId: String) {
        if (modelId == currentModelId) return
        startNewConversation()
        applyModel(modelId, true)
        persistConversation()
    }

    /** 从未选过模型时自动选中第一个已安装模型，避免已安装却提示"未安装模型"。 */
    private fun ensureModelSelected() {
        if (ApprovedModels.isInstalled(requireContext(), currentModelId)) {
            if (!engine.isRealInference()) applyModel(currentModelId, false)
            return
        }
        val installed = installedModelInfos()
        if (installed.isEmpty()) {
            currentModelId = ""
            engine.setModelStateListener(null)
            engine.release()
            engine = UnavailableChatEngine()
            return
        }
        applyModel(installed[0].id, false)
    }

    private fun applyModel(modelId: String, announce: Boolean) {
        interruptGeneration()
        currentModelId = modelId
        prefs().edit().putString(KEY_MODEL, modelId).apply()
        engine.setModelStateListener(null)
        engine.release()
        engine = ChatEngineProvider.create(requireContext(), modelId)
        observeModelState()
        refreshHeader()
        if (announce) {
            val model = installedModelInfo(modelId)
            if (model != null) {
                Snackbar.make(requireView(), model.name, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeModelState() {
        val observed = engine
        observed.setModelStateListener {
            if (view != null && engine === observed) refreshHeader()
        }
    }

    private fun refreshHeader() {
        val approved = resolveApproved()
        val state = engine.modelState()
        modelTitle.text = approved?.displayName?.let(ModelDisplay::name) ?: getString(R.string.chat_no_model)
        modelTitle.contentDescription = approved?.displayName ?: getString(R.string.picker_title)
        modelStateText.visibility = if (approved != null) View.VISIBLE else View.GONE
        btnUnloadModel.visibility = View.GONE
        view?.findViewById<View>(R.id.btn_get_model)?.visibility = if (approved == null) View.VISIBLE else View.GONE
        view?.findViewById<View>(R.id.prompt_think)?.visibility = if (approved != null) View.VISIBLE else View.GONE
        view?.findViewById<View>(R.id.prompt_write)?.visibility = if (approved != null) View.VISIBLE else View.GONE
        view?.findViewById<TextView>(R.id.empty_subtitle)?.setText(
            if (approved == null) R.string.chat_get_model else R.string.chat_empty_sub)
        modelStateText.setText(when (state) {
            ChatEngine.ModelState.UNLOADED -> R.string.chat_model_unloaded
            ChatEngine.ModelState.LOADING -> R.string.chat_model_loading
            ChatEngine.ModelState.LOADED -> R.string.chat_model_loaded
            ChatEngine.ModelState.GENERATING -> R.string.chat_generating
            ChatEngine.ModelState.RELEASING -> R.string.chat_model_releasing
            ChatEngine.ModelState.ERROR -> R.string.chat_model_error
        })
        btnUnloadModel.isEnabled = approved != null && !generating && state == ChatEngine.ModelState.LOADED
        setStatusDot(if (approved != null && state in setOf(ChatEngine.ModelState.LOADED,
                ChatEngine.ModelState.GENERATING)) R.color.status_success
            else if (state == ChatEngine.ModelState.ERROR) R.color.status_warn else R.color.text_tertiary)
    }

    /** 绿色仅代表模型已加载，下载完成本身不代表推理已就绪。 */
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
        view?.keepScreenOn = generating && !isHidden && isResumed &&
            com.example.localai.feature.settings.InferencePolicy.keepScreenOn(requireContext())
        inputView.isEnabled = !loadingHistory
        btnSend.isEnabled = generating || (!emptyInput && !loadingHistory)
        btnSend.alpha = if (btnSend.isEnabled) 1f else 0.38f
        btnSend.contentDescription = getString(if (generating) R.string.action_stop else R.string.action_send)
        btnSend.setIconResource(if (generating) R.drawable.ic_stop else R.drawable.ic_send)
        btnSend.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(),
                if (generating) R.color.status_danger else R.color.md_primary))
    }

    private fun sendInput() {
        if (generating || loadingHistory) return
        val text = inputView.text.toString().trim()
        if (text.isEmpty()) {
            return
        }
        if (currentModelId.isEmpty() || resolveApproved() == null) {
            Snackbar.make(messagesView, R.string.picker_empty, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_goto_market) { (activity as? MainActivity)?.openTab(R.id.nav_market) }.show()
            return
        }
        inputView.setText("")
        adapter.items().add(ChatMessage(ChatMessage.ROLE_USER, text))
        adapter.notifyItemInserted(adapter.itemCount - 1)
        scrollToBottom(force = true)
        updateEmptyState()

        persistConversation()

        generating = true
        refreshSendState(false)
        refreshHeader()
        engine.start(historySnapshot(), generationListener())
    }

    /** 旧引擎或旧页面排队的回调不得写入新会话。 */
    private fun generationListener(): ChatEngine.StreamListener {
        val epoch = ++streamEpoch
        return object : ChatEngine.StreamListener {
            private fun active() = epoch == streamEpoch && generating && view != null
            override fun onThinking() { if (active()) this@ChatFragment.onThinking() }
            override fun onContextTrimmed(droppedCount: Int) {
                if (active()) Snackbar.make(messagesView, R.string.chat_context_trimmed, Snackbar.LENGTH_LONG).show()
            }
            override fun onDelta(delta: String) { if (active()) this@ChatFragment.onDelta(delta) }
            override fun onFinished(stopped: Boolean) { if (active()) this@ChatFragment.onFinished(stopped) }
            override fun onError(code: Int, message: String) { if (active()) this@ChatFragment.onError(code, message) }
        }
    }

    private fun interruptGeneration() {
        streamEpoch++
        engine.release()
        if (generating) onFinished(true)
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
        preserveReadingPosition()
        adapter.items().add(MessageAdapter.TYPING)
        botPosition = adapter.itemCount - 1
        adapter.notifyItemInserted(botPosition)
        scrollToBottom()
        scheduleCheckpoint()
    }

    override fun onDelta(delta: String) {
        preserveReadingPosition()
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
        scheduleCheckpoint()
    }

    override fun onFinished(stopped: Boolean) {
        preserveReadingPosition()
        if (stopped) {
            val bot = streamingBot
            if (bot != null) {
                bot.text += "\n（已停止生成）"
                adapter.updateLast()
            }
        }
        if (botPosition >= 0 && adapter.items().getOrNull(botPosition) === MessageAdapter.TYPING) {
            adapter.items().removeAt(botPosition)
            adapter.notifyItemRemoved(botPosition)
        }
        streamingBot = null
        botPosition = -1
        generating = false
        refreshSendState(inputView.text.toString().trim().isEmpty())
        refreshHeader()
        persistConversation()
    }

    override fun onError(code: Int, message: String) {
        preserveReadingPosition()
        if (code == com.example.localai.core.inference.NativeSession.ERR_INPUT_TOO_LONG && inputView.text.isEmpty()) {
            // 保留正文，并在没有新草稿时恢复输入，便于直接缩短后重发。
            val original = historySnapshot().lastOrNull { it.role == ChatMessage.ROLE_USER }?.text
            if (original != null) inputView.setText(original)
        }
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

    /** 提交完整快照，由仓库增量写入（新会话自动创建并回填 id）。 */
    private fun persistConversation() {
        if (loadingHistory) return
        val repository = ServiceLocator.chat() ?: return
        val epoch = conversationEpoch
        if (view != null) draft = inputView.text.toString()
        repository.saveSession(sessionToken, conversationId, currentModelId, draft, deriveTitle(), historySnapshot(),
            object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) {
                    if (conversationEpoch != epoch) return
                    saveErrorBar?.dismiss()
                    saveErrorBar = null
                    this@ChatFragment.conversationId = conversationId
                }

                override fun onError(message: String?) {
                    if (conversationEpoch != epoch) return
                    view?.let {
                        saveErrorBar = Snackbar.make(it, "会话保存失败：" + message, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.action_retry) {
                                if (conversationEpoch == epoch) persistConversation()
                            }.also { bar -> bar.show() }
                    }
                }
            })
    }

    private fun deriveTitle(): String {
        for (o in adapter.items()) {
            if (o is ChatMessage) {
                val text = o.text
                if (text.isNotEmpty()) {
                    return ConversationText.title(text)
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
        menu.menu.findItem(R.id.action_delete_msg).isVisible = !generating && !loadingHistory
        menu.setOnMenuItemClickListener { item: MenuItem ->
            val currentPosition = adapter.items().indexOf(message)
            if (item.itemId != R.id.action_copy &&
                (generating || loadingHistory || currentPosition < 0)) return@setOnMenuItemClickListener true
            when (item.itemId) {
                R.id.action_copy -> copyToClipboard(message.text)
                R.id.action_delete_msg -> {
                    adapter.items().removeAt(currentPosition)
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    persistConversation()
                }
                R.id.action_retry -> retryFrom(message, currentPosition)
            }
            true
        }
        menu.show()
    }

    private fun retryFrom(userMessage: ChatMessage, position: Int) {
        if (generating || loadingHistory || adapter.items().getOrNull(position) !== userMessage) return
        // 重试保留被选中的用户问题，仅丢弃该问题之后的内容。
        while (adapter.items().size > position + 1) {
            adapter.items().removeAt(adapter.items().size - 1)
        }
        adapter.notifyDataSetChanged()
        scrollToBottom(force = true)
        generating = true
        refreshSendState(false)
        refreshHeader()
        engine.start(historySnapshot(), generationListener())
    }

    private fun copyToClipboard(text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("message", text))
        Snackbar.make(messagesView, R.string.toast_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun updateEmptyState() {
        emptyWrap.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        updateLatestButton()
    }

    private fun isNearBottom(): Boolean {
        val layout = messagesView.layoutManager as LinearLayoutManager
        if (adapter.itemCount == 0) return true
        val last = layout.findViewByPosition(adapter.itemCount - 1) ?: return false
        val remaining = layout.getDecoratedBottom(last) - messagesView.height + messagesView.paddingBottom
        return remaining <= 48 * resources.displayMetrics.density
    }

    private fun preserveReadingPosition() {
        if (followLatest) return
        val layout = messagesView.layoutManager as LinearLayoutManager
        val first = layout.findFirstVisibleItemPosition()
        val anchor = layout.findViewByPosition(first) ?: return
        // stackFromEnd 会按长气泡的底边重新定位；锁定顶边偏移才能让新增正文不挤走阅读位置。
        layout.scrollToPositionWithOffset(first, layout.getDecoratedTop(anchor) - messagesView.paddingTop)
    }

    private fun updateLatestButton() {
        btnLatestMessage.visibility = if (!followLatest && adapter.itemCount > 0 &&
            messagesView.canScrollVertically(1)) View.VISIBLE else View.GONE
    }

    private fun scrollToBottom(force: Boolean = false) {
        if (force) {
            userScrolling = false
            followLatest = true
            messagesView.stopScroll()
        }
        updateLatestButton()
        // 排队的跟随也受用户滚动状态约束，拖动后不会被迟到的流式回调拉回底部。
        messagesView.removeCallbacks(followScroll)
        if (followLatest && !userScrolling) messagesView.postOnAnimation(followScroll)
    }

    private fun saveToWorkspace() {
        val messages = adapter.items().filterIsInstance<ChatMessage>()
        if (messages.isEmpty()) { Snackbar.make(messagesView, "还没有可保存的对话", Snackbar.LENGTH_SHORT).show(); return }
        val snapshot = messages.joinToString("\n\n") { (if (it.role == ChatMessage.ROLE_USER) "我：" else "回复：") + it.text }
        val repository = ServiceLocator.library()!!
        repository.workspaces { r ->
            if (view == null) return@workspaces
            r.onSuccess { spaces -> androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("保存对话快照")
                .setItems(spaces.map { it.title }.toTypedArray()) { _, index ->
                    val result = com.example.localai.data.room.TaskResultEntity().apply {
                        workspaceId = spaces[index].id; kind = "chat"; title = messages.first().text.take(30)
                        output = snapshot; originalOutput = snapshot; modelId = currentModelId; status = "complete"
                    }
                    repository.save(result) { saved -> if (view != null) Snackbar.make(messagesView,
                        if (saved.isSuccess) "已保存到工作区" else "保存失败，请重试", Snackbar.LENGTH_LONG).show() }
                }.show() }
        }
    }

    private fun prefs(): SharedPreferences =
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "localai_chat"
        private const val KEY_MODEL = "current_model"
    }
}
