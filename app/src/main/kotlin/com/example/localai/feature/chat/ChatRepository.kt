package com.example.localai.feature.chat

import android.os.Handler
import android.os.Looper
import com.example.localai.data.room.ConversationDao
import com.example.localai.data.room.ConversationEntity
import com.example.localai.data.room.MessageDao
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
class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {

    fun interface Listener {
        fun onConversationsChanged()
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
     * 标题仅在非空时更新；消息全量替换（先删后插，无残留）。
     */
    fun saveConversation(conversationId: Long, modelId: String, title: String?,
                         messages: List<ChatMessage>?, callback: ConversationSavedCallback?) {
        if (messages == null || messages.isEmpty()) {
            callback?.onSaved(conversationId)
            return
        }
        executor.execute {
            var cid = conversationId
            try {
                val existing = if (cid > 0) conversationDao.getById(cid) else null
                val conv = existing ?: ConversationEntity("", modelId).also {
                    cid = conversationDao.insert(it)
                }
                val trimmed = title?.trim()
                if (trimmed != null && trimmed.isNotEmpty()) {
                    conv.title = trimmed
                }
                conv.updatedAt = System.currentTimeMillis()
                conversationDao.update(conv)
                messageDao.deleteForConversation(cid)
                for (m in messages) {
                    messageDao.insert(MessageEntity(cid,
                        if (m.role == ChatMessage.ROLE_USER)
                            MessageEntity.ROLE_USER else MessageEntity.ROLE_BOT,
                        m.text))
                }
            } catch (e: RuntimeException) {
                conversationsCache = conversationDao.all()
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

    fun deleteConversation(conversationId: Long) {
        executor.execute {
            conversationDao.deleteById(conversationId)
            conversationsCache = conversationDao.all()
            runOnMain {
                for (l in snapshotListeners()) {
                    l.onConversationsChanged()
                }
            }
        }
    }

    fun clearAll() {
        executor.execute {
            messageDao.clear()
            conversationDao.clear()
            conversationsCache = conversationDao.all()
            runOnMain {
                for (l in snapshotListeners()) {
                    l.onConversationsChanged()
                }
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
