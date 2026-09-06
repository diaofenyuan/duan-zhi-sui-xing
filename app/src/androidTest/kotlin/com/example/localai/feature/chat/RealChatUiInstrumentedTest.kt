package com.example.localai.feature.chat

import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** 保留实际引擎，从页面发送按钮到中文回答完成，不替换推理实现。 */
@RunWith(AndroidJUnit4::class)
class RealChatUiInstrumentedTest {
    @Test fun sendChineseThroughVisibleChat() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue("需要已安装的 Qwen 模型", ApprovedModels.isInstalled(activity, ApprovedModels.QWEN_05B.modelId))
                activity.openTab(R.id.nav_chat)
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                chat.requireView().findViewById<android.view.View>(R.id.btn_new).performClick()
                chat.requireView().findViewById<EditText>(R.id.input).setText("请用一句中文介绍你能做什么。")
                chat.requireView().findViewById<android.view.View>(R.id.btn_send).performClick()
            }
            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
            var completed = false
            var chinese = false
            while (!completed && System.nanoTime() < deadline) {
                scenario.onActivity { activity ->
                    val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                    val type = chat.javaClass
                    val running = type.getDeclaredField("generating").apply { isAccessible = true }.getBoolean(chat)
                    val adapter = type.getDeclaredField("adapter").apply { isAccessible = true }.get(chat) as MessageAdapter
                    val answers = adapter.items().filterIsInstance<ChatMessage>().filter { it.role == ChatMessage.ROLE_BOT }
                    assertFalse("页面报告推理失败：${answers.lastOrNull()?.text}", answers.any { it.text.startsWith("生成失败：") })
                    chinese = answers.any { message -> message.text.any { it in '\u4e00'..'\u9fff' } }
                    completed = !running
                }
                if (!completed) Thread.sleep(100)
            }
            assertTrue("页面推理未在期限内结束", completed)
            assertTrue("页面未显示真实中文回答", chinese)
        }
    }
}
