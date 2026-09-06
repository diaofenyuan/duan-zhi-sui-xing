package com.example.localai.feature.chat

import android.os.Handler
import android.os.Looper
import com.example.localai.data.room.AppDatabase
import com.example.localai.data.room.ConversationEntity
import com.example.localai.data.room.ChatSessionEntity
import com.example.localai.data.room.MessageEntity
import com.example.localai.model.ChatMessage
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 会话/消息持久化门面（P4）：UI 永远只在主线程调用快照方法；
 * DAO 访问全部在单线程 executor 上执行，完成后主线程回调。
 * 保存语义为「全量覆盖当前会话」（标题 + 消息列表），空白会话不落库。
 */
class ChatRepository(private val database: AppDatabase) {
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val sessionDao = database.chatSessionDao()
    // 只由数据库工作线程访问，阻止删除后已排队但尚未回填主键的旧页面重新建会话。
    private val deletedSessionTokens = HashSet<String>()

    class Session(val token: String, val conversationId: Long, val modelId: String,
                  val draft: String, val messages: List<ChatMessage>)

    /** 同一编辑 token 的连续快照在事务内复用主键，不依赖旧页面是否收到保存回调。 */
    fun saveSession(token: String, conversationId: Long, modelId: String, draft: String,
                    title: String, messages: List<ChatMessage>, callback: ConversationSavedCallback?) {
        val snapshot = messages.map { ChatMessage(it.role, it.text) }
        executor.execute {
            var cid = conversationId
            try {
                database.runInTransaction {
                    check(token !in deletedSessionTokens) { "会话已删除，请新建会话" }
                    val previous = sessionDao.current()
                    if (previous?.token == token) cid = previous.conversationId
                    cid = writeConversation(cid, modelId, title, snapshot)
                    sessionDao.save(ChatSessionEntity().also {
                        it.token = token
                        it.conversationId = cid
                        it.modelId = modelId
                        it.draft = draft
                    })
                }
                conversationsCache = conversationDao.all()
                val saved = cid
                runOnMain {
                    for (listener in snapshotListeners()) listener.onConversationsChanged()
                    callback?.onSaved(saved)
                }
            } catch (e: RuntimeException) {
                runOnMain { callback?.onError(e.message) }
            }
        }
    }

    fun loadSession(callback: (Session?, String?) -> Unit) {
        executor.execute {
            try {
                var result: Session? = null
                database.runInTransaction {
                    val row = sessionDao.current() ?: return@runInTransaction
                    if (row.conversationId > 0 && conversationDao.getById(row.conversationId) == null) {
                        sessionDao.clear()
                        return@runInTransaction
                    }
                    result = Session(row.token, row.conversationId, row.modelId, row.draft,
                        readMessages(row.conversationId))
                }
                val snapshot = result
                runOnMain { callback(snapshot, null) }
            } catch (e: RuntimeException) {
                runOnMain { callback(null, e.message) }
            }
        }
    }

    fun loadConversation(id: Long, callback: (Session?, String?) -> Unit) {
        executor.execute {
            try {
                var result: Session? = null
                database.runInTransaction {
                    val conversation = checkNotNull(conversationDao.getById(id)) { "会话不存在或已删除" }
                    result = Session(java.util.UUID.randomUUID().toString(), id,
                        conversation.modelId ?: "", "", readMessages(id))
                }
                val snapshot = result
                runOnMain { callback(snapshot, null) }
            } catch (e: RuntimeException) {
                runOnMain { callback(null, e.message) }
            }
        }
    }

    /** 由外层事务同时提交正文和编辑状态。 */
    private fun writeConversation(id: Long, modelId: String, title: String?, snapshot: List<ChatMessage>): Long {
        if (snapshot.isEmpty()) {
            if (id > 0) conversationDao.deleteById(id)
            return 0
        }
        val existing = if (id > 0) conversationDao.getById(id) else null
        check(id <= 0 || existing != null) { "会话已删除，请新建会话" }
        val conv = existing ?: ConversationEntity("", modelId).also { it.id = conversationDao.insert(it) }
        val trimmed = title?.trim()
        if (!trimmed.isNullOrEmpty()) conv.title = trimmed
        conv.updatedAt = System.currentTimeMillis()
        conversationDao.update(conv)
        messageDao.deleteForConversation(conv.id)
        for (message in snapshot) {
            messageDao.insert(MessageEntity(conv.id,
                if (message.role == ChatMessage.ROLE_USER) MessageEntity.ROLE_USER else MessageEntity.ROLE_BOT,
                message.text))
        }
        return conv.id
    }

    private fun readMessages(id: Long) = messageDao.messagesFor(id).map {
        ChatMessage(if (it.role == MessageEntity.ROLE_USER) ChatMessage.ROLE_USER else ChatMessage.ROLE_BOT, it.content)
    }

    fun interface Listener {
        fun onConversationsChanged()

        /** null 表示用户清空全部历史，也应清除尚未发送的当前草稿。 */
        fun onConversationsDeleted(ids: Set<Long>?) {}
    }

    interface ConversationSavedCallback {
        fun onSaved(conversationId: Long)

        fun onError(message: String?)
    }

    fun interface MessagesCallback {
        fun onResult(messages: List<ChatMessage>?, error: String?)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "localai-chat").apply { isDaemon = true }
    }
    private val listeners = ArrayList<Listener>()

    @Volatile
    private var conversationsCache: List<ConversationEntity> = Collections.emptyList()

    fun register(listener: Listener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
        runOnMain { listener.onConversationsChanged() }
    }

    fun unregister(listener: Listener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun refresh() {
        executor.execute {
            conversationsCache = conversationDao.all()
            runOnMain {
                for (l in snapshotListeners()) {
                    l.onConversationsChanged()
                }
            }
        }
    }

    fun conversations(): List<ConversationEntity> = conversationsCache

    /**
     * 保存会话：conversationId <= 0 时自动创建新会话并回调新 id；
     * 标题仅在非空时更新；会话与消息作为同一事务保存，写入失败时保留原记录。
     */
    fun saveConversation(conversationId: Long, modelId: String, title: String?,
                         messages: List<ChatMessage>?, callback: ConversationSavedCallback?) {
        if (messages == null) {
            runOnMain { callback?.onSaved(conversationId) }
            return
        }
        // 流式消息可原地修改，必须在提交任务时复制内容，避免后台读到下一轮状态。
        val snapshot = messages.map { ChatMessage(it.role, it.text) }
        executor.execute {
            var cid = conversationId
            try {
                database.runInTransaction {
                    cid = writeConversation(cid, modelId, title, snapshot)
                }
            } catch (e: RuntimeException) {
                callback?.let { cb ->
                    runOnMain { cb.onError(e.message) }
                }
                return@execute
            }
            val savedCid = cid
            conversationsCache = conversationDao.all()
            runOnMain {
                for (l in snapshotListeners()) {
                    l.onConversationsChanged()
                }
                callback?.onSaved(savedCid)
            }
        }
    }

    fun loadMessages(conversationId: Long, callback: MessagesCallback) {
        executor.execute {
            try {
                val result = ArrayList<ChatMessage>()
                for (m in messageDao.messagesFor(conversationId)) {
                    result.add(ChatMessage(
                        if (MessageEntity.ROLE_USER == m.role)
                            ChatMessage.ROLE_USER else ChatMessage.ROLE_BOT,
                        m.content))
                }
                runOnMain { callback.onResult(result, null) }
            } catch (e: RuntimeException) {
                runOnMain { callback.onResult(null, e.message) }
            }
        }
    }

    fun deleteConversation(conversationId: Long, callback: ((String?) -> Unit)? = null) =
        deleteConversations(setOf(conversationId), callback)

    fun clearAll(callback: ((String?) -> Unit)? = null) = deleteConversations(null, callback)

    private fun deleteConversations(ids: Set<Long>?, callback: ((String?) -> Unit)?) {
        executor.execute {
            try {
                var deletedToken: String? = null
                database.runInTransaction {
                    val session = sessionDao.current()
                    if (ids == null || session?.conversationId in ids) deletedToken = session?.token
                    if (ids == null) {
                        conversationDao.clear()
                        sessionDao.clear()
                    } else for (id in ids) {
                        conversationDao.deleteById(id)
                        sessionDao.clearForConversation(id)
                    }
                }
                deletedToken?.let { deletedSessionTokens.add(it) }
                conversationsCache = conversationDao.all()
                runOnMain {
                    for (listener in snapshotListeners()) {
                        listener.onConversationsDeleted(ids)
                        listener.onConversationsChanged()
                    }
                    callback?.invoke(null)
                }
            } catch (e: RuntimeException) {
                runOnMain { callback?.invoke(e.message ?: "数据库写入失败") }
            }
        }
    }

    private fun snapshotListeners(): List<Listener> {
        synchronized(listeners) {
            return ArrayList(listeners)
        }
    }

    private fun runOnMain(runnable: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }
}
