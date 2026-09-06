package com.example.localai.feature.chat

import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.model.ChatMessage
import com.google.android.material.button.MaterialButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 在真实 Android 页面上控制流式事件，稳定复现用户操作与迟到回调的交错。 */
@RunWith(AndroidJUnit4::class)
class ChatFragmentInstrumentedTest {
    @Test fun keepScreenSettingAppliesOnlyWhileChatGenerates() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("localai_settings", android.content.Context.MODE_PRIVATE)
        val previous = prefs.getBoolean("keep_screen", false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.openTab(R.id.nav_settings)
                    val toggle = activity.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.sw_keep_screen)
                    if (!toggle.isChecked) toggle.performClick()
                    activity.openTab(R.id.nav_chat)
                    val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                    chat.requireView().findViewById<android.view.View>(R.id.btn_new).performClick()
                    val engine = ControlledEngine()
                    (field(chat, "engine").get(chat) as ChatEngine).release()
                    field(chat, "engine").set(chat, engine)
                    field(chat, "currentModelId").set(chat, ApprovedModels.installedAsModelInfos(activity).first().id)
                    assertFalse(chat.requireView().keepScreenOn)
                    send(chat)
                    assertTrue("生成时常亮设置未生效", chat.requireView().keepScreenOn)
                    activity.openTab(R.id.nav_settings)
                    assertFalse("离开聊天后仍阻止熄屏", chat.requireView().keepScreenOn)
                    assertFalse("离开聊天仍在生成", field(chat, "generating").getBoolean(chat))
                }
            }
        } finally {
            prefs.edit().putBoolean("keep_screen", previous).commit()
        }
    }

    @Test fun deletedConversationDoesNotRemainInBackStackChat() {
        val repository = com.example.localai.data.ServiceLocator.chat()!!
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openTab(R.id.nav_chat)
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                chat.requireView().findViewById<android.view.View>(R.id.btn_new).performClick()
                val engine = ControlledEngine()
                (field(chat, "engine").get(chat) as ChatEngine).release()
                field(chat, "engine").set(chat, engine)
                field(chat, "currentModelId").set(chat, ApprovedModels.installedAsModelInfos(activity).first().id)
                send(chat, "delete UI regression")
                engine.listeners.last().onDelta("answer to delete")
                engine.listeners.last().onFinished(false)
                chat.requireView().findViewById<EditText>(R.id.input).setText("draft to delete")
                activity.push(HistoryFragment())
                activity.supportFragmentManager.executePendingTransactions()
            }
            val loaded = java.util.concurrent.CountDownLatch(1)
            var id = 0L
            repository.loadSession { session, _ -> id = session!!.conversationId; loaded.countDown() }
            assertTrue(loaded.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(id > 0)
            val deleted = java.util.concurrent.CountDownLatch(1)
            repository.deleteConversation(id) { error -> assertNull(error); deleted.countDown() }
            assertTrue(deleted.await(10, java.util.concurrent.TimeUnit.SECONDS))
            scenario.onActivity { activity ->
                activity.supportFragmentManager.popBackStackImmediate()
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                assertTrue("已删除正文仍显示在返回栈页面", adapter(chat).items().isEmpty())
                assertEquals("", chat.requireView().findViewById<EditText>(R.id.input).text.toString())
            }
        }
    }

    @Test fun processRestartRestoresSavedChat() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val phase = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("processRestorePhase")
        org.junit.Assume.assumeTrue("由外部驱动分两次进程执行", phase == "prepare" || phase == "verify")
        instrumentation.sendStatus(2, android.os.Bundle().apply {
            putString("stream", "process restore phase=$phase pid=${android.os.Process.myPid()}\n")
        })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openTab(R.id.nav_chat)
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                if (phase == "prepare") {
                    chat.requireView().findViewById<android.view.View>(R.id.btn_new).performClick()
                    val engine = ControlledEngine()
                    (field(chat, "engine").get(chat) as ChatEngine).release()
                    field(chat, "engine").set(chat, engine)
                    field(chat, "currentModelId").set(chat, ApprovedModels.installedAsModelInfos(activity).first().id)
                    send(chat, "进程恢复测试问题")
                    engine.listeners.last().onDelta("进程恢复测试回答")
                    engine.listeners.last().onFinished(false)
                    chat.requireView().findViewById<EditText>(R.id.input).setText("重启后继续编辑")
                }
            }
            if (phase == "verify") {
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
                var restored = false
                while (!restored && System.nanoTime() < deadline) {
                    scenario.onActivity { activity ->
                        val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                        restored = adapter(chat).items().filterIsInstance<ChatMessage>().map { it.text } ==
                            listOf("进程恢复测试问题", "进程恢复测试回答") &&
                            chat.requireView().findViewById<EditText>(R.id.input).text.toString() == "重启后继续编辑"
                        if (restored) assertTrue((field(chat, "engine").get(chat) as ChatEngine).isRealInference())
                    }
                    if (!restored) Thread.sleep(30)
                }
                assertTrue("新进程没有恢复完整消息、草稿和模型", restored)
            }
        }
        // 等待 onPause 提交完成，之后外部驱动结束进程；不在测试内清空用户数据库。
        val saved = java.util.concurrent.CountDownLatch(1)
        com.example.localai.data.ServiceLocator.chat()!!.loadSession { _, _ -> saved.countDown() }
        assertTrue(saved.await(10, java.util.concurrent.TimeUnit.SECONDS))
    }

    private class ControlledEngine : ChatEngine {
        val histories = mutableListOf<List<ChatMessage>>()
        val listeners = mutableListOf<ChatEngine.StreamListener>()
        var running = false
        override fun start(history: List<ChatMessage>, listener: ChatEngine.StreamListener) {
            histories.add(history.map { ChatMessage(it.role, it.text) })
            listeners.add(listener)
            running = true
            listener.onThinking()
        }
        override fun stop() { running = false; listeners.last().onFinished(true) }
        override fun release() { running = false }
        override fun isRunning() = running
        override fun isRealInference() = false
        override fun modeLabel() = "测试引擎"
    }

    private fun field(fragment: ChatFragment, name: String): java.lang.reflect.Field =
        fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun adapter(fragment: ChatFragment) = field(fragment, "adapter").get(fragment) as MessageAdapter

    private fun withChat(test: (MainActivity, ChatFragment, ControlledEngine) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openTab(R.id.nav_chat)
                val fragment = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                fragment.requireView().findViewById<android.view.View>(R.id.btn_new).performClick()
                val engine = ControlledEngine()
                (field(fragment, "engine").get(fragment) as ChatEngine).release()
                field(fragment, "engine").set(fragment, engine)
                val installed = ApprovedModels.installedAsModelInfos(activity).firstOrNull()
                checkNotNull(installed) { "先执行 StandaloneModelInstrumentedTest 安装真实模型，再运行已安装模型的页面回归" }
                field(fragment, "currentModelId").set(fragment, installed.id)
                test(activity, fragment, engine)
            }
        }
    }

    private fun send(fragment: ChatFragment, text: String = "UI regression question") {
        fragment.requireView().findViewById<EditText>(R.id.input).setText(text)
        fragment.requireView().findViewById<MaterialButton>(R.id.btn_send).performClick()
    }

    @Test fun newConversationRejectsLateTokensAndCompletion() = withChat { _, fragment, engine ->
        send(fragment)
        val old = engine.listeners.single()
        fragment.requireView().findViewById<android.view.View>(R.id.btn_new).performClick()
        old.onDelta("late token")
        old.onFinished(false)
        old.onError(1, "late error")
        assertTrue(adapter(fragment).items().isEmpty())
        assertFalse(field(fragment, "generating").getBoolean(fragment))
    }

    @Test fun retryIncludesSelectedQuestionAndDiscardsLaterAnswer() = withChat { _, fragment, engine ->
        send(fragment, "original question")
        engine.listeners.last().onDelta("old answer")
        engine.listeners.last().onFinished(false)
        val question = adapter(fragment).items()[0] as ChatMessage
        // 直接调用菜单最终处理入口，避免长按动画和系统菜单坐标影响回归时序。
        fragment.javaClass.getDeclaredMethod("retryFrom", ChatMessage::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(fragment, question, 0)
        assertEquals(2, engine.histories.size)
        assertEquals(listOf("original question"), engine.histories.last().map { it.text })
        assertEquals(ChatMessage.ROLE_USER, engine.histories.last().single().role)
    }

    @Test fun emptyResponseRemovesThinkingPlaceholder() = withChat { _, fragment, engine ->
        send(fragment)
        engine.listeners.last().onFinished(false)
        assertEquals(1, adapter(fragment).itemCount)
        assertTrue(adapter(fragment).items().all { it is ChatMessage })
    }

    @Test fun openingHistoryAndReturningKeepsMessagesAndDraft() = withChat { activity, fragment, engine ->
        send(fragment)
        engine.listeners.last().onDelta("partial answer")
        fragment.requireView().findViewById<EditText>(R.id.input).setText("unsent draft")
        activity.push(HistoryFragment())
        activity.supportFragmentManager.executePendingTransactions()
        engine.listeners.last().onDelta("late after leaving")
        activity.supportFragmentManager.popBackStackImmediate()
        assertEquals("unsent draft", fragment.requireView().findViewById<EditText>(R.id.input).text.toString())
        val messages = adapter(fragment).items().filterIsInstance<ChatMessage>()
        assertEquals(2, messages.size)
        assertTrue(messages[1].text.startsWith("partial answer"))
        assertFalse(messages[1].text.contains("late after leaving"))
        assertFalse(field(fragment, "generating").getBoolean(fragment))
    }

    @Test fun sendButtonReflectsEmptyInputAndStopAction() = withChat { _, fragment, engine ->
        val button = fragment.requireView().findViewById<MaterialButton>(R.id.btn_send)
        assertFalse(button.isEnabled)
        send(fragment)
        assertTrue(button.isEnabled)
        assertEquals(fragment.getString(R.string.action_stop), button.contentDescription)
        engine.listeners.last().onFinished(false)
        assertFalse(button.isEnabled)
        assertEquals(fragment.getString(R.string.action_send), button.contentDescription)
    }

    @Test fun recreationKeepsPartialAnswerDraftAndModel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var modelId = ""
            scenario.onActivity { activity ->
                activity.openTab(R.id.nav_chat)
                val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                chat.requireView().findViewById<android.view.View>(R.id.btn_new).performClick()
                val engine = ControlledEngine()
                (field(chat, "engine").get(chat) as ChatEngine).release()
                field(chat, "engine").set(chat, engine)
                modelId = ApprovedModels.installedAsModelInfos(activity).first().id
                field(chat, "currentModelId").set(chat, modelId)
                send(chat, "recreate question")
                engine.listeners.last().onDelta("partial before rotation")
                chat.requireView().findViewById<EditText>(R.id.input).setText("draft before rotation")
            }
            scenario.recreate()
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            var restored = false
            while (!restored && System.nanoTime() < deadline) {
                scenario.onActivity { activity ->
                    val chat = activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment
                    val messages = adapter(chat).items().filterIsInstance<ChatMessage>()
                    restored = messages.size == 2 && messages[0].text == "recreate question" &&
                        messages[1].text.startsWith("partial before rotation") &&
                        chat.requireView().findViewById<EditText>(R.id.input).text.toString() == "draft before rotation"
                    if (restored) {
                        assertEquals(modelId, field(chat, "currentModelId").get(chat))
                        assertFalse(field(chat, "generating").getBoolean(chat))
                    }
                }
                if (!restored) Thread.sleep(30)
            }
            assertTrue("Activity 重建丢失了消息或草稿", restored)
        }
    }
}
