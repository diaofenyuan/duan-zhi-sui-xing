package com.example.localai.core.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.feature.chat.ChatHistoryTrimmer
import com.example.localai.feature.chat.RealChatEngine
import com.example.localai.model.ChatMessage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 必须使用当前设备已安装的 Qwen，实际覆盖分词、上下文边界和再次生成。 */
@RunWith(AndroidJUnit4::class)
class ContextBudgetInstrumentedTest {
    private class Result : NativeSession.StreamListener {
        val done = CountDownLatch(1)
        var error = 0
        override fun onDelta(text: String) {}
        override fun onFinished(reason: Int) { done.countDown() }
        override fun onError(code: Int, message: String) { error = code; done.countDown() }
    }

    @Test fun exactTokenizerTrimsWholeTurnsAndRejectsOversizedInputWithoutLosingSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = ApprovedModels.QWEN_05B
        assertTrue("需要当前模拟器已安装的 Qwen", ApprovedModels.isInstalled(context, model.modelId))
        NativeSession().use { session ->
            session.load(ApprovedModels.modelFile(context, model).absolutePath, 512, 2, 0.7f, 0.9f, 32)
            val oversized = RealChatEngine.buildPrompt(listOf(ChatMessage(ChatMessage.ROLE_USER,
                "这是一段必须完整保留的输入。".repeat(200))))
            assertTrue(session.countTokens(oversized) > 512)
            val rejected = Result()
            session.start(oversized, rejected)
            assertTrue(rejected.done.await(20, TimeUnit.SECONDS))
            assertEquals(NativeSession.ERR_INPUT_TOO_LONG, rejected.error)
            val readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (JSONObject(session.stats).getString("state") != "READY" && System.nanoTime() < readyDeadline) Thread.sleep(10)

            val history = mutableListOf<ChatMessage>()
            repeat(25) {
                history.add(ChatMessage(ChatMessage.ROLE_USER, "第 $it 轮：请介绍中文写作和阅读方法。"))
                history.add(ChatMessage(ChatMessage.ROLE_BOT, "可以先阅读一段文章，再写下关键观点并尝试总结。"))
            }
            history.add(ChatMessage(ChatMessage.ROLE_USER, "😀请用一句中文问候我。"))
            val trimmed = ChatHistoryTrimmer.truncate(history, 512 - 32 - 8, session::countTokens)
            assertFalse(trimmed.inputTooLong)
            assertTrue(trimmed.droppedCount > 0)
            assertEquals(ChatMessage.ROLE_USER, trimmed.kept.first().role)
            assertEquals(history.last().text, trimmed.kept.last().text)
            assertEquals(51, history.size)
            val prompt = RealChatEngine.buildPrompt(trimmed.kept)
            val expectedTokens = session.countTokens(prompt)
            assertTrue(expectedTokens <= 472)
            val generated = Result()
            session.start(prompt, generated)
            assertTrue("预算内生成超时", generated.done.await(3, TimeUnit.MINUTES))
            assertEquals(0, generated.error)
            val stats = JSONObject(session.stats)
            assertEquals(expectedTokens.toLong(), stats.getLong("promptTokens"))
            assertTrue(stats.getLong("genTokens") in 1..32)
        }
    }
}
