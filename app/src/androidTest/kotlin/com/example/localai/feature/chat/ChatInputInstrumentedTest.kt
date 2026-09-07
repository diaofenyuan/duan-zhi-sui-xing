package com.example.localai.feature.chat

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.MainActivity
import com.example.localai.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用当前设备的真实输入法和触摸事件，不触发模型生成或删除已有会话。 */
@RunWith(AndroidJUnit4::class)
class ChatInputInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun navigationDismissesKeyboardAndPreservesChineseDraft() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openTab(R.id.nav_chat) }
            await(scenario) { it.findViewById<EditText>(R.id.input).isEnabled }
            var previous = ""
            scenario.onActivity { previous = it.findViewById<EditText>(R.id.input).text.toString() }
            val draft = "中文草稿：你好，世界！\n第二行包含标点与表情🙂"
            try {
                scenario.onActivity { it.findViewById<EditText>(R.id.input).setText(draft) }
                showKeyboard(scenario)
                await(scenario) { it.findViewById<View>(R.id.bottom_nav).visibility == View.GONE }
                scenario.onActivity { it.openTab(R.id.nav_settings) }
                await(scenario) { !keyboardVisible(it) }
                await(scenario) { it.findViewById<View>(R.id.bottom_nav).visibility == View.VISIBLE }
                scenario.onActivity { activity ->
                    activity.openTab(R.id.nav_chat)
                    val input = activity.findViewById<EditText>(R.id.input)
                    assertEquals(draft, input.text.toString())
                    assertFalse("回到聊天不应自动抢占键盘焦点", input.hasFocus())
                }
                showKeyboard(scenario)
                scenario.onActivity { it.findViewById<View>(R.id.btn_history).performClick() }
                await(scenario) { !keyboardVisible(it) }
                scenario.onActivity { it.supportFragmentManager.popBackStackImmediate() }
                await(scenario) {
                    it.findViewById<EditText>(R.id.input)?.text?.toString() == draft
                }
            } finally {
                scenario.onActivity { activity ->
                    activity.openTab(R.id.nav_chat)
                    activity.findViewById<EditText>(R.id.input).setText(previous)
                    activity.openTab(R.id.nav_settings)
                }
            }
        }
    }

    @Test fun chatActionsHaveFullTouchTargetsAndHistoryEdgeResponds() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openTab(R.id.nav_chat) }
            instrumentation.waitForIdleSync()
            var x = 0f
            var y = 0f
            scenario.onActivity { activity ->
                val minimum = 48 * activity.resources.displayMetrics.density
                for (id in listOf(R.id.btn_new, R.id.btn_history, R.id.btn_switch, R.id.btn_send)) {
                    val button = activity.findViewById<View>(id)
                    assertTrue("操作点击区域不足 48dp", button.width >= minimum - 1 && button.height >= minimum - 1)
                    assertFalse("操作缺少中文说明", button.contentDescription.isNullOrBlank())
                }
                val button = activity.findViewById<View>(R.id.btn_history)
                val position = IntArray(2)
                button.getLocationOnScreen(position)
                x = position[0] + button.width / 2f
                y = position[1] + 2f
            }
            val time = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0)
                instrumentation.sendPointerSync(event)
                event.recycle()
            }
            await(scenario) { it.supportFragmentManager.findFragmentById(R.id.container) is HistoryFragment }
        }
    }

    private fun showKeyboard(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val input = activity.findViewById<EditText>(R.id.input)
            input.requestFocus()
            activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        await(scenario) { keyboardVisible(it) }
    }

    private fun keyboardVisible(activity: MainActivity): Boolean =
        ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun await(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var passed = false
        while (!passed && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { passed = predicate(it) }
            if (!passed) SystemClock.sleep(40)
        }
        assertTrue("页面或输入法未在限定时间达到预期状态", passed)
    }
}
