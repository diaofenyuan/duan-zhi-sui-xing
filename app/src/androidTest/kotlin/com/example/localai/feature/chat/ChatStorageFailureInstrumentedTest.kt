package com.example.localai.feature.chat

import android.database.sqlite.SQLiteFullException
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.AppDatabase
import com.example.localai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 故障仅注入独立内存库的 DAO，不损坏应用数据库、不占满设备存储。 */
@RunWith(AndroidJUnit4::class)
class ChatStorageFailureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun committedSaveAndDeleteSurviveHistoryRefreshFailure() = withRepository { repository ->
        val failure = AtomicReference<Pair<String, RuntimeException>?>(null)
        inject(repository, "conversationDao", failure)
        val first = save(repository, "before", 0)
        failure.set("all" to RuntimeException("SELECT private_content FROM /data/user/0/private.db"))
        val saved = save(repository, "after", first)
        assertEquals(first, saved)
        assertEquals("暂时无法访问本机会话，请稍后重试", repository.historyError)
        val loaded = CountDownLatch(1)
        repository.loadSession { session, error ->
            assertNull(error)
            assertEquals("after", session!!.draft)
            loaded.countDown()
        }
        waitFor(loaded)
        val deleted = CountDownLatch(1)
        repository.deleteConversation(first) { error -> assertNull(error); deleted.countDown() }
        waitFor(deleted)
        assertTrue(repository.conversations().isEmpty())

        failure.set("insert" to SQLiteFullException("database or disk is full"))
        val rejected = CountDownLatch(1)
        repository.saveConversation(0, "test", "title", messages(), object : ChatRepository.ConversationSavedCallback {
            override fun onSaved(conversationId: Long) { fail("磁盘写入失败不应报成功") }
            override fun onError(message: String?) {
                assertEquals("设备存储空间不足，请先腾出空间后重试", message)
                rejected.countDown()
            }
        })
        waitFor(rejected)
    }

    @Test fun historyAndChatShowChineseErrorsAndRecoverThroughRetry() = withRepository { repository ->
        save(repository, "保留的中文草稿", 0)
        val historyFailure = AtomicReference<Pair<String, RuntimeException>?>(null)
        val sessionFailure = AtomicReference<Pair<String, RuntimeException>?>(null)
        inject(repository, "conversationDao", historyFailure)
        inject(repository, "sessionDao", sessionFailure)
        val singleton = ServiceLocator::class.java.getDeclaredField("instance").apply { isAccessible = true }.get(null)
        val field = ServiceLocator::class.java.getDeclaredField("chatRepository").apply { isAccessible = true }
        val original = field.get(singleton)
        try {
            field.set(singleton, repository)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.openTab(R.id.nav_settings)
                    activity.push(HistoryFragment())
                    activity.supportFragmentManager.executePendingTransactions()
                }
                historyFailure.set("all" to RuntimeException("no such table: conversations"))
                repository.refresh()
                await(scenario) { it.findViewById<TextView>(R.id.es_title)?.text == "会话历史加载失败" }
                scenario.onActivity { activity ->
                    assertEquals("暂时无法访问本机会话，请稍后重试", activity.findViewById<TextView>(R.id.es_subtitle).text.toString())
                    historyFailure.set(null)
                    activity.findViewById<View>(R.id.es_action).performClick()
                }
                await(scenario) { it.findViewById<View>(R.id.list)?.isShown == true }
                assertEquals(1, repository.conversations().size)

                // 空 message 的异常仍必须是失败，否则恢复逻辑可能把空会话写回数据库。
                sessionFailure.set("current" to RuntimeException())
                scenario.onActivity { it.openTab(R.id.nav_chat) }
                await(scenario) {
                    it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text
                        ?.contains("恢复会话失败：暂时无法访问本机会话") == true
                }
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<EditText>(R.id.input).isEnabled)
                    sessionFailure.set(null)
                    activity.findViewById<View>(com.google.android.material.R.id.snackbar_action).performClick()
                }
                await(scenario) {
                    val input = it.findViewById<EditText>(R.id.input)
                    input.isEnabled && input.text.toString() == "保留的中文草稿"
                }
                sessionFailure.set("save" to SQLiteFullException("database or disk is full"))
                scenario.onActivity { it.findViewById<EditText>(R.id.input).setText("重试后保存的中文草稿") }
                await(scenario) {
                    it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text
                        ?.contains("会话保存失败：设备存储空间不足") == true
                }
                assertDraft(repository, "保留的中文草稿")
                scenario.onActivity { activity ->
                    sessionFailure.set(null)
                    activity.findViewById<View>(com.google.android.material.R.id.snackbar_action).performClick()
                }
                await(scenario) {
                    it.findViewById<View>(com.google.android.material.R.id.snackbar_text)?.isShown != true
                }
                assertDraft(repository, "重试后保存的中文草稿")
            }
        } finally {
            field.set(singleton, original)
        }
    }

    private fun withRepository(test: (ChatRepository) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = ChatRepository(database)
        try { test(repository) } finally {
            val drained = CountDownLatch(1)
            repository.loadSession { _, _ -> drained.countDown() }
            waitFor(drained)
            database.close()
        }
    }

    private fun inject(repository: ChatRepository, fieldName: String,
                       failure: AtomicReference<Pair<String, RuntimeException>?>) {
        val field = ChatRepository::class.java.getDeclaredField(fieldName).apply { isAccessible = true }
        val delegate = field.get(repository)
        field.set(repository, Proxy.newProxyInstance(field.type.classLoader, arrayOf(field.type)) { _, method, arguments ->
            failure.get()?.let { if (it.first == method.name) throw it.second }
            try { method.invoke(delegate, *(arguments ?: emptyArray())) }
            catch (error: InvocationTargetException) { throw error.targetException }
        })
    }

    private fun save(repository: ChatRepository, draft: String, id: Long): Long {
        var saved = 0L
        val done = CountDownLatch(1)
        repository.saveSession("test-session", id, "test", draft, "中文测试", messages(), object : ChatRepository.ConversationSavedCallback {
            override fun onSaved(conversationId: Long) { saved = conversationId; done.countDown() }
            override fun onError(message: String?) { fail(message) }
        })
        waitFor(done)
        return saved
    }

    private fun messages() = listOf(ChatMessage(ChatMessage.ROLE_USER, "保留的中文正文"))
    private fun assertDraft(repository: ChatRepository, expected: String) {
        val done = CountDownLatch(1)
        repository.loadSession { session, error ->
            assertNull(error)
            assertEquals(expected, session!!.draft)
            done.countDown()
        }
        waitFor(done)
    }
    private fun waitFor(latch: CountDownLatch) { assertTrue(latch.await(10, TimeUnit.SECONDS)) }
    private fun await(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var passed = false
        while (!passed && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { passed = predicate(it) }
            if (!passed) SystemClock.sleep(40)
        }
        assertTrue("页面未显示预期的异常或恢复状态", passed)
    }
}
