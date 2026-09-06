package com.example.localai.feature.chat

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localai.data.room.AppDatabase
import com.example.localai.model.ChatMessage
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/** ChatRepository 会话持久化测试（Room 内存库 + Robolectric）。 */
@RunWith(RobolectricTestRunner::class)
class ChatRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ChatRepository

    /** 执行主线程队列中的 pending runnable，直到 latch 归零或超时。 */
    private fun idleUntil(latch: CountDownLatch) {
        val deadline = System.currentTimeMillis() + 10_000
        while (latch.count > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("timeout waiting for callback; count=" + latch.count,
            latch.count == 0L)
    }

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleMessages(): List<ChatMessage> {
        val messages = ArrayList<ChatMessage>()
        messages.add(ChatMessage(ChatMessage.ROLE_USER, "什么是 KV Cache"))
        messages.add(ChatMessage(ChatMessage.ROLE_BOT, "这是回复"))
        return messages
    }

    @Test
    fun sessionSnapshotsReuseIdBeforeUiReceivesFirstCallback() {
        repository.saveSession("editing", 0, "model", "first draft", "title", sampleMessages(), null)
        repository.saveSession("editing", 0, "model", "latest draft", "title", sampleMessages(), null)
        val latch = CountDownLatch(1)
        var restored: ChatRepository.Session? = null
        repository.loadSession { session, error ->
            assertEquals(null, error)
            restored = session
            latch.countDown()
        }
        idleUntil(latch)
        assertEquals(1, database.conversationDao().all().size)
        assertEquals("latest draft", restored!!.draft)
        assertEquals(2, restored!!.messages.size)
        assertTrue(restored!!.conversationId > 0)
    }

    @Test
    fun draftOnlySessionRestoresWithoutCreatingEmptyHistory() {
        repository.saveSession("draft-only", 0, "model", "尚未发送的中文", "", emptyList(), null)
        val latch = CountDownLatch(1)
        repository.loadSession { session, error ->
            assertEquals(null, error)
            assertEquals("尚未发送的中文", session!!.draft)
            assertEquals("model", session.modelId)
            assertTrue(session.messages.isEmpty())
            latch.countDown()
        }
        idleUntil(latch)
        assertTrue(database.conversationDao().all().isEmpty())
    }

    @Test
    fun sessionFailureRollsBackDraftAndMessagesTogether() {
        repository.saveSession("atomic", 0, "model", "saved draft", "title", sampleMessages(), null)
        val ready = CountDownLatch(1)
        repository.loadSession { _, _ -> ready.countDown() }
        idleUntil(ready)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_session BEFORE INSERT ON chat_session BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
        val latch = CountDownLatch(1)
        repository.saveSession("atomic", 0, "model", "new draft", "new title", listOf(ChatMessage(0, "new message")),
            object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) { org.junit.Assert.fail("应回滚失败事务") }
                override fun onError(message: String?) { latch.countDown() }
            })
        idleUntil(latch)
        val loaded = CountDownLatch(1)
        repository.loadSession { session, error ->
            assertEquals(null, error)
            assertEquals("saved draft", session!!.draft)
            assertEquals(sampleMessages().map { it.text }, session.messages.map { it.text })
            loaded.countDown()
        }
        idleUntil(loaded)
    }

    @Test
    fun deletingHistoryAlsoRemovesActiveSession() {
        repository.saveSession("deleted", 0, "model", "private draft", "title", sampleMessages(), null)
        val saved = CountDownLatch(1)
        var id = 0L
        repository.loadSession { session, _ -> id = session!!.conversationId; saved.countDown() }
        idleUntil(saved)
        repository.deleteConversation(id)
        val loaded = CountDownLatch(1)
        repository.loadSession { session, error ->
            assertEquals(null, error)
            assertEquals(null, session)
            loaded.countDown()
        }
        idleUntil(loaded)
        assertTrue(database.messageDao().messagesFor(id).isEmpty())
    }

    @Test
    fun clearingHistoryRejectsQueuedSnapshotWithoutAssignedId() {
        repository.saveSession("old-page", 0, "model", "draft", "title", sampleMessages(), null)
        repository.clearAll()
        val rejected = CountDownLatch(1)
        repository.saveSession("old-page", 0, "model", "draft", "title", sampleMessages(),
            object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) { org.junit.Assert.fail("迟到快照复活了已删除历史") }
                override fun onError(message: String?) { rejected.countDown() }
            })
        idleUntil(rejected)
        assertTrue(database.conversationDao().all().isEmpty())
        assertEquals(null, database.chatSessionDao().current())
    }

    @Test
    fun failedClearReportsErrorAndPreservesHistory() {
        repository.saveSession("kept", 0, "model", "saved draft", "title", sampleMessages(), null)
        val ready = CountDownLatch(1)
        repository.loadSession { _, _ -> ready.countDown() }
        idleUntil(ready)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_clear BEFORE DELETE ON conversations BEGIN SELECT RAISE(ABORT, 'cannot delete'); END")
        val completed = CountDownLatch(1)
        repository.clearAll { error -> assertNotNull(error); completed.countDown() }
        idleUntil(completed)
        assertEquals(1, database.conversationDao().all().size)
        assertEquals("saved draft", database.chatSessionDao().current().draft)
    }

    @Test
    fun saveCreatesConversationAndMessages() {
        val savedId = longArrayOf(-1)
        val latch = CountDownLatch(1)
        repository.saveConversation(0L, "smollm-135m-instruct", "什么是 KV Cache",
            sampleMessages(), object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) {
                    savedId[0] = conversationId
                    latch.countDown()
                }

                override fun onError(message: String?) {
                    latch.countDown()
                }
            })
        idleUntil(latch)
        assertTrue(savedId[0] > 0)

        val holder = MessagesCallbackHolder()
        repository.loadMessages(savedId[0], holder)
        idleUntil(holder.latch)
        assertNotNull(holder.messages)
        assertEquals(2, holder.messages!!.size)
        assertEquals(1, database.conversationDao().all().size)
        assertEquals("什么是 KV Cache", database.conversationDao().getById(savedId[0]).title)
    }

    @Test
    fun updateConversationOverwritesMessages() {
        val savedId = longArrayOf(-1)
        val latch = CountDownLatch(1)
        repository.saveConversation(0L, "smollm-135m-instruct", "标题一",
            sampleMessages(), object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) {
                    savedId[0] = conversationId
                    latch.countDown()
                }

                override fun onError(message: String?) {
                    latch.countDown()
                }
            })
        idleUntil(latch)

        val updated = ArrayList<ChatMessage>()
        updated.add(ChatMessage(ChatMessage.ROLE_USER, "下一轮"))
        val second = CountDownLatch(1)
        repository.saveConversation(savedId[0], "smollm-135m-instruct", "新的标题",
            updated, object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) {
                    second.countDown()
                }

                override fun onError(message: String?) {
                    second.countDown()
                }
            })
        idleUntil(second)

        val holder = MessagesCallbackHolder()
        repository.loadMessages(savedId[0], holder)
        idleUntil(holder.latch)
        assertEquals(1, holder.messages!!.size)
        assertEquals("新的标题", database.conversationDao().all()[0].title)
    }

    @Test
    fun deleteConversationCascadesMessages() {
        val savedId = longArrayOf(-1)
        val latch = CountDownLatch(1)
        repository.saveConversation(0L, "m", "标题", sampleMessages(),
            object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) {
                    savedId[0] = conversationId
                    latch.countDown()
                }

                override fun onError(message: String?) {
                    latch.countDown()
                }
            })
        idleUntil(latch)

        repository.deleteConversation(savedId[0])
        Thread.sleep(500)
        assertEquals(0, database.conversationDao().all().size)
        assertEquals(0, database.messageDao().messagesFor(savedId[0]).size)
    }

    private fun save(id: Long, title: String, messages: List<ChatMessage>, succeeds: Boolean = true): Long {
        var savedId = -1L
        var error: String? = null
        val latch = CountDownLatch(1)
        repository.saveConversation(id, "m", title, messages,
            object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) {
                    savedId = conversationId
                    latch.countDown()
                }

                override fun onError(message: String?) {
                    error = message
                    latch.countDown()
                }
            })
        idleUntil(latch)
        if (succeeds) {
            assertEquals(null, error)
            assertTrue(savedId >= 0)
        } else {
            assertEquals(-1L, savedId)
            assertNotNull(error)
        }
        return savedId
    }

    @Test
    fun failedReplacementKeepsPreviousConversationAndMessages() {
        val id = save(0, "原始标题", sampleMessages())
        // 在第二条消息落库时注入 SQLite 故障，验证整次保存可以回滚。
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_message BEFORE INSERT ON messages
            WHEN NEW.content = '拒绝写入'
            BEGIN SELECT RAISE(ABORT, 'injected write failure'); END
        """.trimIndent())
        save(id, "不应保存的标题", listOf(
            ChatMessage(ChatMessage.ROLE_USER, "第一条新消息"),
            ChatMessage(ChatMessage.ROLE_BOT, "拒绝写入")
        ), succeeds = false)
        assertEquals("原始标题", database.conversationDao().getById(id).title)
        assertEquals(sampleMessages().map { it.text }, database.messageDao().messagesFor(id).map { it.content })
    }

    @Test
    fun failedNewConversationLeavesNoOrphanRow() {
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_message BEFORE INSERT ON messages
            BEGIN SELECT RAISE(ABORT, 'injected write failure'); END
        """.trimIndent())
        save(0, "失败的新会话", sampleMessages(), succeeds = false)
        assertTrue(database.conversationDao().all().isEmpty())
    }

    @Test
    fun deletingLastMessageRemovesSavedConversation() {
        val id = save(0, "标题", sampleMessages())
        assertEquals(0L, save(id, "标题", emptyList()))
        assertTrue(database.conversationDao().all().isEmpty())
        assertTrue(database.messageDao().messagesFor(id).isEmpty())
    }

    @Test
    fun staleSaveDoesNotResurrectDeletedConversation() {
        val id = save(0, "标题", sampleMessages())
        database.conversationDao().deleteById(id)
        save(id, "过期保存", sampleMessages(), succeeds = false)
        assertTrue(database.conversationDao().all().isEmpty())
    }

    inner class MessagesCallbackHolder : ChatRepository.MessagesCallback {
        var messages: List<ChatMessage>? = null
        var error: String? = null
        val latch = CountDownLatch(1)

        override fun onResult(messages: List<ChatMessage>?, error: String?) {
            this.messages = messages
            this.error = error
            latch.countDown()
        }
    }
}
