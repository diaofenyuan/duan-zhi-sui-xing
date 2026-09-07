package com.example.localai.feature.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.TaskResultEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LibraryFlowInstrumentedTest {
    @Test fun interruptedChecklistRestoresStreamCheckpoint() {
        val repository = ServiceLocator.library()!!
        val workspace = await<Long> { repository.addWorkspace("中断恢复验收", it) }
        try {
            val draft = TaskResultEntity().apply {
                workspaceId = workspace; kind = "todo"; title = "待办草稿"; status = "running"
                input = "周四复核报告，周五提交材料"; output = "- 周四复核报告\n- 周五提交材料"
            }
            val id = await<Long> { repository.save(draft, it) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var fragment: TaskFragment
                scenario.onActivity { activity -> fragment = TaskFragment.open(id); activity.push(fragment) }
                waitFor(scenario) { fragment.isAdded && ViewModelProvider(fragment)[TaskViewModel::class.java].ready }
                scenario.onActivity {
                    val model = ViewModelProvider(fragment)[TaskViewModel::class.java]
                    assertEquals(2, model.items.size)
                    assertEquals("周四复核报告", model.items.first().text)
                    assertEquals("interrupted", model.record.status)
                    model.save()
                }
                val saved = await<TaskResultEntity?> { repository.result(id, it) }!!
                assertTrue(saved.output.contains("周五提交材料"))
            }
        } finally { await<Unit> { repository.deleteWorkspace(workspace, it) } }
    }
    private fun <T> await(start: ((Result<T>) -> Unit) -> Unit): T {
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        start { result = it; latch.countDown() }
        assertTrue("数据库操作超时", latch.await(20, TimeUnit.SECONDS))
        return result!!.getOrThrow()
    }
    private fun waitFor(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        var ready = false
        while (!ready && System.nanoTime() < deadline) {
            scenario.onActivity { ready = condition(it) }
            if (!ready) Thread.sleep(100)
        }
        assertTrue("任务没有按时完成", ready)
    }
    @Test fun offlineQuestionCitationAndEditableChecklistSurviveReopen() {
        val repository = ServiceLocator.library()!!
        val workspace = await<Long> { repository.addWorkspace("链路验收", it) }
        try {
            val original = "项目计划：张明负责整理实验数据，周五前提交报告。王红负责准备演示，周六上午进行验收。"
            val source = await<Long> { repository.importText(workspace, "项目计划.txt", original, it) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var fragment: TaskFragment
                lateinit var model: TaskViewModel
                scenario.onActivity { activity ->
                    activity.openTab(R.id.nav_chat)
                    fragment = TaskFragment.create("qa", workspace, longArrayOf(source), "谁负责整理实验数据？")
                    activity.push(fragment)
                }
                waitFor(scenario) { fragment.isAdded && ViewModelProvider(fragment)[TaskViewModel::class.java].ready }
                scenario.onActivity { model = ViewModelProvider(fragment)[TaskViewModel::class.java]; model.generate() }
                waitFor(scenario) { !model.running }
                scenario.onActivity {
                    assertEquals(model.status, "complete", model.record.status)
                    assertTrue("回答没有回答资料中的负责人：${model.record.output}", model.record.output.contains("张明"))
                    assertEquals(original, model.citations.first().excerpt)
                    assertEquals(source, model.citations.first().sourceId)
                    assertEquals(1, model.citations.first().page)
                }
                waitFor(scenario) { model.record.id > 0 }
                val answerId = model.record.id
                scenario.recreate()
                waitFor(scenario) { activity ->
                    val restored = activity.supportFragmentManager.findFragmentById(R.id.container) as? TaskFragment
                    restored != null && ViewModelProvider(restored)[TaskViewModel::class.java].ready
                }
                scenario.onActivity { activity ->
                    fragment = activity.supportFragmentManager.findFragmentById(R.id.container) as TaskFragment
                    model = ViewModelProvider(fragment)[TaskViewModel::class.java]
                    assertEquals(answerId, model.record.id)
                    activity.push(SourceFragment.create(source, model.citations.first()))
                }
                waitFor(scenario) { it.supportFragmentManager.findFragmentById(R.id.container) is SourceFragment }
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.popBackStackImmediate()
                    fragment = TaskFragment.create("todo", workspace, input = original)
                    activity.push(fragment)
                }
                waitFor(scenario) { fragment.isAdded && ViewModelProvider(fragment)[TaskViewModel::class.java].ready }
                scenario.onActivity { model = ViewModelProvider(fragment)[TaskViewModel::class.java]; model.generate() }
                waitFor(scenario) { !model.running }
                scenario.onActivity {
                    assertEquals(model.status, "complete", model.record.status)
                    assertTrue("没有生成真实待办：${model.record.originalOutput}", model.items.any { it.text.contains("报告") })
                    model.editItem(0, text = "周四复核实验报告", checked = true); model.save()
                }
                waitFor(scenario) { model.record.id > 0 }
                // 使用仓库队列屏障，确保编辑保存落盘后再重新打开。
                val saved = await<TaskResultEntity?> { repository.result(model.record.id, it) }!!
                assertTrue(saved.checklistJson.contains("周四复核实验报告")); assertTrue(saved.checklistJson.contains("true"))
                scenario.onActivity { it.push(TaskFragment.open(saved.id)) }
                waitFor(scenario) { activity ->
                    val current = activity.supportFragmentManager.findFragmentById(R.id.container) as? TaskFragment
                    current != null && ViewModelProvider(current)[TaskViewModel::class.java].ready
                }
                scenario.onActivity { activity ->
                    val restored = ViewModelProvider(activity.supportFragmentManager.findFragmentById(R.id.container)!!)[TaskViewModel::class.java]
                    assertTrue(restored.items.first().checked)
                    assertEquals("周四复核实验报告", restored.items.first().text)
                }
            }
        } finally { await<Unit> { repository.deleteWorkspace(workspace, it) } }
    }

    @Test fun importsPdfPageTextWithoutNetworkAndRejectsBlankScan() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ServiceLocator.library()!!
        val workspace = await<Long> { repository.addWorkspace("PDF 验收", it) }
        val file = File(context.cacheDir, "library-test.pdf")
        try {
            PDFBoxResourceLoader.init(context)
            PDDocument().use { doc ->
                repeat(2) { index ->
                    val page = PDPage(); doc.addPage(page)
                    PDPageContentStream(doc, page).use { stream ->
                        stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 12f)
                        stream.newLineAtOffset(50f, 700f); stream.showText("Page ${index + 1}: Project deadline Friday."); stream.endText()
                    }
                }
                doc.save(file)
            }
            val id = await<Long> { repository.importFile(workspace, Uri.fromFile(file), it) }
            val source = await<com.example.localai.data.room.SourceEntity?> { repository.source(id, it) }!!
            assertEquals(listOf(1, 2), LibraryContent.pages(source).map { it.number })
            assertTrue(LibraryContent.pages(source)[1].text.contains("Page 2"))
            PDDocument().use { it.addPage(PDPage()); it.save(file) }
            var error: Throwable? = null
            try { await<Long> { repository.importFile(workspace, Uri.fromFile(file), it) } } catch (e: Throwable) { error = e }
            assertTrue(error?.message.orEmpty().contains("没有提取到文字"))
        } finally { file.delete(); await<Unit> { repository.deleteWorkspace(workspace, it) } }
    }
}
