package com.example.localai.feature.chat

import android.widget.EditText
import android.widget.TextView
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
    @Test fun oversizedQuestionRestoresDraftAndNextQuestionWorks() {
        val original = "这是一段必须完整保留、不能静默删除的问题。".repeat(300)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(ApprovedModels.isInstalled(activity, ApprovedModels.QWEN_05B.modelId))
                activity.openTab(R.id.nav_chat)
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                chat.requireView().findViewById<android.view.View>(R.id.btn_new).performClick()
                chat.requireView().findViewById<EditText>(R.id.input).setText(original)
                chat.requireView().findViewById<android.view.View>(R.id.btn_send).performClick()
            }
            fun awaitIdle() {
                val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
                var idle = false
                while (!idle && System.nanoTime() < deadline) {
                    scenario.onActivity { activity ->
                        val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                        idle = !chat.javaClass.getDeclaredField("generating").apply { isAccessible = true }.getBoolean(chat)
                    }
                    if (!idle) Thread.sleep(100)
                }
                assertTrue("页面未结束本轮", idle)
            }
            awaitIdle()
            scenario.onActivity { activity ->
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                val adapter = chat.javaClass.getDeclaredField("adapter").apply { isAccessible = true }.get(chat) as MessageAdapter
                val messages = adapter.items().filterIsInstance<ChatMessage>()
                assertEquals(original, messages.first().text)
                assertTrue(messages.last().text.contains("问题超出模型上下文"))
                assertEquals(original, chat.requireView().findViewById<EditText>(R.id.input).text.toString())
                chat.requireView().findViewById<EditText>(R.id.input).setText("请只用一句中文问候我。")
                chat.requireView().findViewById<android.view.View>(R.id.btn_send).performClick()
            }
            awaitIdle()
            scenario.onActivity { activity ->
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                val adapter = chat.javaClass.getDeclaredField("adapter").apply { isAccessible = true }.get(chat) as MessageAdapter
                val messages = adapter.items().filterIsInstance<ChatMessage>()
                assertEquals("旧问题原文必须保留", original, messages.first().text)
                assertEquals(4, messages.size)
                assertFalse(messages.last().text.startsWith("生成失败："))
                assertTrue(messages.last().text.any { it in '\u4e00'..'\u9fff' })
            }
        }
    }

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
            var messageCount = 0
            scenario.onActivity { activity ->
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                val view = chat.requireView()
                assertEquals(activity.getString(R.string.chat_model_loaded),
                    view.findViewById<TextView>(R.id.text_model_state).text.toString())
                val adapter = chat.javaClass.getDeclaredField("adapter").apply { isAccessible = true }.get(chat) as MessageAdapter
                messageCount = adapter.itemCount
                view.findViewById<EditText>(R.id.input).setText("请再用一句中文介绍你能做什么。")
                val unload = view.findViewById<android.view.View>(R.id.btn_unload_model)
                assertTrue(unload.isEnabled)
                unload.performClick()
                assertFalse(unload.isEnabled)
                assertEquals(activity.getString(R.string.chat_model_releasing),
                    view.findViewById<TextView>(R.id.text_model_state).text.toString())
            }
            val releaseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var released = false
            while (!released && System.nanoTime() < releaseDeadline) {
                scenario.onActivity { activity ->
                    val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                    released = chat.requireView().findViewById<TextView>(R.id.text_model_state).text.toString() ==
                        activity.getString(R.string.chat_model_unloaded)
                }
                if (!released) Thread.sleep(50)
            }
            assertTrue("Native 释放后页面没有更新", released)
            scenario.onActivity { activity ->
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                assertTrue("释放不能删除已下载模型", ApprovedModels.isInstalled(activity, ApprovedModels.QWEN_05B.modelId))
                val adapter = chat.javaClass.getDeclaredField("adapter").apply { isAccessible = true }.get(chat) as MessageAdapter
                assertEquals("释放不能删除聊天正文", messageCount, adapter.itemCount)
                assertFalse(chat.requireView().findViewById<EditText>(R.id.input).text.isEmpty())
                chat.requireView().findViewById<android.view.View>(R.id.btn_send).performClick()
            }
            val reloadDeadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
            var reloaded = false
            while (!reloaded && System.nanoTime() < reloadDeadline) {
                scenario.onActivity { activity ->
                    val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                    val adapter = chat.javaClass.getDeclaredField("adapter").apply { isAccessible = true }.get(chat) as MessageAdapter
                    val answers = adapter.items().filterIsInstance<ChatMessage>().filter { it.role == ChatMessage.ROLE_BOT }
                    assertFalse("重新加载后推理失败", answers.any { it.text.startsWith("生成失败：") })
                    val running = chat.javaClass.getDeclaredField("generating").apply { isAccessible = true }.getBoolean(chat)
                    reloaded = !running && answers.size == 2 && answers.last().text.any { it in '\u4e00'..'\u9fff' }
                }
                if (!reloaded) Thread.sleep(100)
            }
            assertTrue("释放后未能重新加载并回答", reloaded)
        }
    }
}
