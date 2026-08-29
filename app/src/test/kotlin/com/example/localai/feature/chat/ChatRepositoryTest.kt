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
        repository = ChatRepository(database.conversationDao(), database.messageDao())
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
