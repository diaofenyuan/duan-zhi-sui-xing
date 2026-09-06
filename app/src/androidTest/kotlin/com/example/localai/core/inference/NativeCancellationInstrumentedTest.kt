package com.example.localai.core.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.feature.chat.RealChatEngine
import com.example.localai.model.ChatMessage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 用当前设备真实模型覆盖启动、预填充和停止完成时的 Native 状态。 */
@RunWith(AndroidJUnit4::class)
class NativeCancellationInstrumentedTest {
    private fun prompt(text: String) = RealChatEngine.buildPrompt(listOf(ChatMessage(ChatMessage.ROLE_USER, text)))
    private fun session(): NativeSession {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = ApprovedModels.QWEN_05B
        assertTrue(ApprovedModels.isInstalled(context, model.modelId))
        return NativeSession().apply {
            load(ApprovedModels.modelFile(context, model).absolutePath, 512, 2, 0f, 0.9f, 32)
        }
    }
    private class Result(private val session: NativeSession) : NativeSession.StreamListener {
        val done = CountDownLatch(1)
        var reason = -1
        var error = 0
        var terminalState = ""
        var text = ""
        override fun onDelta(delta: String) { text += delta }
        override fun onFinished(reason: Int) {
            this.reason = reason
            terminalState = JSONObject(session.stats).getString("state")
            done.countDown()
        }
        override fun onError(code: Int, message: String) {
            error = code
            terminalState = JSONObject(session.stats).getString("state")
            done.countDown()
        }
    }

    @Test fun immediateStopIsNotLostAndCompletionMeansReady() {
        session().use { session ->
            repeat(5) { cycle ->
                val result = Result(session)
                session.start(prompt("请从一开始逐个列出数字，直到一百。"), result)
                session.stop()
                assertTrue("第 $cycle 轮停止未及时结束", result.done.await(10, TimeUnit.SECONDS))
                assertEquals(0, result.error)
                assertEquals("刚启动的停止请求被忽略", NativeSession.FINISH_STOPPED, result.reason)
                assertEquals("终止回调必须代表可进入下一轮", "READY", result.terminalState)
                assertEquals(0, JSONObject(session.stats).getLong("genTokens"))
            }
        }
    }

    @Test fun stopDuringPrefillThenGenerateAgain() {
        session().use { session ->
            val cancelled = Result(session)
            session.start(prompt("请阅读这段文字再总结。" + "这是中文阅读材料。".repeat(30)), cancelled)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (JSONObject(session.stats).getLong("promptTokens") == 0L && System.nanoTime() < deadline) Thread.sleep(1)
            assertTrue("未进入提示处理", JSONObject(session.stats).getLong("promptTokens") > 0)
            session.stop()
            assertTrue(cancelled.done.await(10, TimeUnit.SECONDS))
            assertEquals(0, cancelled.error)
            assertEquals(NativeSession.FINISH_STOPPED, cancelled.reason)
            assertEquals("READY", cancelled.terminalState)
            assertEquals(0, JSONObject(session.stats).getLong("genTokens"))

            val next = Result(session)
            session.start(prompt("请用中文说你好。"), next)
            assertTrue(next.done.await(30, TimeUnit.SECONDS))
            assertEquals(0, next.error)
            assertEquals(NativeSession.FINISH_END, next.reason)
            assertEquals("READY", next.terminalState)
            assertTrue(next.text.any { it in '\u4e00'..'\u9fff' })
        }
    }
}
