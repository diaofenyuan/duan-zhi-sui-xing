package com.example.localai.core.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.feature.chat.RealChatEngine
import com.example.localai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 使用真实模型和 JNI 回调检查中文、表情及连续两轮的输出完整性。 */
@RunWith(AndroidJUnit4::class)
class UnicodeStreamInstrumentedTest {
    @Test fun realModelStreamsChineseAndEmojiWithoutCorruptingCharacters() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = ApprovedModels.QWEN_05B
        assertTrue(ApprovedModels.isInstalled(context, model.modelId))
        NativeSession().use { session ->
            session.load(ApprovedModels.modelFile(context, model).absolutePath, 512, 2, 0f, 0.9f, 64)
            repeat(2) {
                val text = StringBuilder()
                val done = CountDownLatch(1)
                var error = 0
                var malformed = false
                val prompt = RealChatEngine.buildPrompt(listOf(ChatMessage(ChatMessage.ROLE_USER,
                    "请原样输出以下内容，不要解释：你好，世界！😀🌍")))
                session.start(prompt, object : NativeSession.StreamListener {
                    override fun onDelta(delta: String) {
                        text.append(delta)
                        var offset = 0
                        while (offset < delta.length) {
                            val ch = delta[offset]
                            if (Character.isHighSurrogate(ch)) {
                                if (offset + 1 >= delta.length || !Character.isLowSurrogate(delta[offset + 1])) malformed = true
                                else offset++
                            } else if (Character.isLowSurrogate(ch) || ch == '\uFFFD') malformed = true
                            offset++
                        }
                    }
                    override fun onFinished(reason: Int) { done.countDown() }
                    override fun onError(code: Int, message: String) { error = code; done.countDown() }
                })
                assertTrue(done.await(90, TimeUnit.SECONDS))
                assertEquals(0, error)
                assertFalse("分段回调出现破损字符：$text", malformed)
                assertTrue("未收到预期中文：$text", text.contains("你好"))
                assertTrue("未收到真实模型生成的表情：$text", text.contains("😀") || text.contains("🌍"))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (org.json.JSONObject(session.stats).getString("state") != "READY" && System.nanoTime() < deadline) Thread.sleep(10)
            }
        }
    }
}
