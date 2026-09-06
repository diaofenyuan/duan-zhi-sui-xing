package com.example.localai.feature.chat

import android.os.SystemClock
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.data.ServiceLocator
import com.example.localai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HistoryChineseInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun titlesKeepRareChineseCharactersAndCompleteEmoji() {
        for (character in listOf("𠮷", "🙂", "👨‍👩‍👧‍👦", "👍🏽", "🇨🇳", "e\u0301")) {
            assertEquals(character, ConversationText.initial(character + "中文"))
            assertEquals("中".repeat(23) + character,
                ConversationText.title("中".repeat(23) + character + "后续文字"))
        }
        assertEquals("你好 世界 中文", ConversationText.title("  你好\n世界\t中文　"))
        assertEquals("", ConversationText.title("\n　"))
    }

    @Test fun historyUsesChineseModelNameAndDeletesOnlyConfirmedConversation() {
        val repository = ServiceLocator.chat()!!
        val created = mutableListOf<Long>()
        try {
            val title = "👨‍👩‍👧‍👦 中文历史验收"
            val target = createConversation(repository, title).also { created.add(it) }
            createConversation(repository, "保留的另一条测试会话").also { created.add(it) }
            val otherIds = repository.conversations().map { it.id }.filter { it != target }.toSet()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.openTab(R.id.nav_settings)
                    activity.push(HistoryFragment())
                    activity.supportFragmentManager.executePendingTransactions()
                }
                await {
                    ServiceLocator.downloads()!!.catalogView().models.any { it.modelId == "qwen2.5-0.5b-instruct" }
                }
                val position = repository.conversations().indexOfFirst { it.id == target }
                scenario.onActivity { it.findViewById<RecyclerView>(R.id.list).scrollToPosition(position) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val row = activity.findViewById<RecyclerView>(R.id.list).findViewHolderForAdapterPosition(position)!!.itemView
                    assertEquals("👨‍👩‍👧‍👦", row.findViewById<TextView>(R.id.text_icon).text.toString())
                    assertTrue(row.findViewById<TextView>(R.id.text_meta).text.contains("中文轻量助手"))
                    row.findViewById<View>(R.id.btn_more).performClick()
                    val dialog = dialogRoot()
                    val message = dialog.findViewById<TextView>(android.R.id.message).text.toString()
                    assertTrue(message.contains("仅删除当前选中的会话"))
                    assertTrue(message.contains("其他会话和已安装模型不受影响"))
                    assertFalse(message.contains("所有对话记录"))
                    dialog.findViewById<View>(android.R.id.button2).performClick()
                    assertTrue(repository.conversations().any { it.id == target })
                    row.findViewById<View>(R.id.btn_more).performClick()
                    dialogRoot().findViewById<View>(android.R.id.button1).performClick()
                }
                await { repository.conversations().none { it.id == target } }
                assertEquals(otherIds, repository.conversations().map { it.id }.toSet())
            }
        } finally {
            // 只清理本用例新增的记录，保留原会话、草稿和模型。
            for (id in created) {
                val done = CountDownLatch(1)
                repository.deleteConversation(id) { done.countDown() }
                assertTrue(done.await(10, TimeUnit.SECONDS))
            }
        }
    }

    private fun createConversation(repository: ChatRepository, title: String): Long {
        val done = CountDownLatch(1)
        var id = 0L
        var error: String? = null
        repository.saveConversation(0, "qwen2.5-0.5b-instruct", title,
            listOf(ChatMessage(ChatMessage.ROLE_USER, title)), object : ChatRepository.ConversationSavedCallback {
                override fun onSaved(conversationId: Long) { id = conversationId; done.countDown() }
                override fun onError(message: String?) { error = message; done.countDown() }
            })
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertNull(error)
        assertTrue(id > 0)
        return id
    }

    private fun dialogRoot(): View = WindowInspector.getGlobalWindowViews()
        .first { it.findViewById<View>(android.R.id.button1)?.isShown == true }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!predicate() && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(40)
        }
        assertTrue("历史页面未达到预期状态", predicate())
    }
}
