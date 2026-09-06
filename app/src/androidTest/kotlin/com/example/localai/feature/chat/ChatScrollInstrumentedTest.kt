package com.example.localai.feature.chat

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.room.Room
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 用独立内存库和受控流式片段验证真实触摸滚动，不写用户的会话或模型。 */
@RunWith(AndroidJUnit4::class)
class ChatScrollInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun readingHistoryStaysPutAndLatestButtonResumesFollowing() {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("localai_chat", android.content.Context.MODE_PRIVATE)
        val previousModel = preferences.getString("current_model", null)
        ServiceLocator.init(context)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = ChatRepository(database)
        val singleton = ServiceLocator::class.java.getDeclaredField("instance").apply { isAccessible = true }.get(null)
        val repositoryField = ServiceLocator::class.java.getDeclaredField("chatRepository").apply { isAccessible = true }
        val original = repositoryField.get(singleton)
        try {
            val ready = CountDownLatch(1)
            repository.saveSession("scroll-test", 0, "", "保留中文草稿🙂", "滚动测试",
                (0 until 30).map { ChatMessage(it % 2, "历史消息 $it\n这是需要保留的上下文。") }, null)
            repository.loadSession { _, error -> assertNull(error); ready.countDown() }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            repositoryField.set(singleton, repository)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.openTab(R.id.nav_chat) }
                await(scenario) { it.findViewById<EditText>(R.id.input)?.text.toString() == "保留中文草稿🙂" }
                scenario.onActivity {
                    chat(it).onThinking()
                    chat(it).onDelta((1..65).joinToString("\n") { line -> "长回复第 $line 行，测试中文流式阅读。" })
                }
                await(scenario) { !list(it).canScrollVertically(1) }

                var bounds = IntArray(4)
                scenario.onActivity {
                    val view = list(it)
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    bounds = intArrayOf(location[0], location[1], view.width, view.height)
                }
                swipeDown(bounds)
                await(scenario) {
                    list(it).scrollState == RecyclerView.SCROLL_STATE_IDLE &&
                        it.findViewById<View>(R.id.btn_latest_message).isShown
                }
                var anchor = Pair(0, 0)
                scenario.onActivity {
                    anchor = anchor(list(it))
                    repeat(8) { _ -> chat(it).onDelta("\n新增内容不应打断阅读。") }
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(350)
                scenario.onActivity {
                    assertEquals("生成把正在阅读的位置拉走了", anchor, anchor(list(it)))
                    assertTrue(it.findViewById<View>(R.id.btn_latest_message).isShown)
                }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(context.cacheDir, "chat-scroll-review.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
                scenario.onActivity { list(it).scrollToPosition(0) }
                await(scenario) { (list(it).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() == 0 }
                var target = IntArray(2)
                scenario.onActivity {
                    val button = it.findViewById<View>(R.id.btn_latest_message)
                    button.getLocationOnScreen(target)
                    target[0] += button.width / 2
                    target[1] += button.height / 2
                }
                val tappedAt = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(tappedAt, SystemClock.uptimeMillis(), action,
                        target[0].toFloat(), target[1].toFloat(), 0)
                    instrumentation.sendPointerSync(event)
                    event.recycle()
                }
                await(scenario) { !list(it).canScrollVertically(1) }
                scenario.onActivity {
                    assertFalse(it.findViewById<View>(R.id.btn_latest_message).isShown)
                    chat(it).onDelta("\n恢复跟随后的新片段。")
                }
                await(scenario) { !list(it).canScrollVertically(1) }
                scenario.onActivity { chat(it).onFinished(true) }
                scenario.recreate()
                await(scenario) { it.findViewById<EditText>(R.id.input)?.text.toString() == "保留中文草稿🙂" }
                scenario.onActivity {
                    val messages = (list(it).adapter as MessageAdapter).items().filterIsInstance<ChatMessage>()
                    assertTrue(messages.last().text.endsWith("（已停止生成）"))
                    assertTrue(messages.last().text.contains("恢复跟随后的新片段"))
                }
            }
        } finally {
            repositoryField.set(singleton, original)
            preferences.edit().apply {
                if (previousModel == null) remove("current_model") else putString("current_model", previousModel)
            }.commit()
            val drained = CountDownLatch(1)
            repository.loadSession { _, _ -> drained.countDown() }
            assertTrue(drained.await(10, TimeUnit.SECONDS))
            database.close()
        }
    }

    private fun chat(activity: MainActivity) =
        activity.supportFragmentManager.findFragmentByTag("chat") as ChatFragment

    private fun list(activity: MainActivity): RecyclerView = activity.findViewById(R.id.messages)

    private fun anchor(list: RecyclerView): Pair<Int, Int> {
        val layout = list.layoutManager as LinearLayoutManager
        val position = layout.findFirstVisibleItemPosition()
        return position to layout.getDecoratedTop(layout.findViewByPosition(position)!!)
    }

    private fun swipeDown(bounds: IntArray) {
        val downTime = SystemClock.uptimeMillis()
        val x = bounds[0] + bounds[2] * 0.65f
        val start = bounds[1] + bounds[3] * 0.25f
        for (step in 0..20) {
            val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 20 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x, start + bounds[3] * 0.5f * step / 20, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
            SystemClock.sleep(20)
        }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, predicate: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var passed = false
        while (!passed && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { passed = predicate(it) }
            if (!passed) SystemClock.sleep(40)
        }
        assertTrue("聊天页面未达到预期状态", passed)
    }
}
