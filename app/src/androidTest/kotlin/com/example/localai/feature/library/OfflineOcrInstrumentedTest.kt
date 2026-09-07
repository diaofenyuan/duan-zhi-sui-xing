package com.example.localai.feature.library

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class OfflineOcrInstrumentedTest {
    @Test fun recognizesChineseReportAndNumbersLocally() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image); canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 46f; typeface = Typeface.DEFAULT }
        listOf("项目工作计划", "张明周五提交实验报告", "王红周六完成项目验收", "联系电话：13800138000").forEachIndexed { i, line ->
            canvas.drawText(line, 70f, 130f + i * 130, paint)
        }
        val file = File(context.getExternalFilesDir(null), "ocr-sample.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        try {
            val result = OfflineOcr(context).recognize(image)
            val text = result.lines.joinToString("\n") { it.text }
            assertTrue("中文识别结果：$text", text.contains("实验报告"))
            assertTrue("数字识别结果：$text", text.contains("13800138000"))
            assertTrue(text.contains("项目"))
            assertTrue(result.elapsedMs > 0)
            assertTrue("识别耗时超过 20 秒：${result.elapsedMs}", result.elapsedMs < 20_000)
            android.util.Log.i("LocalOcrQA", "lines=${result.lines.size}, elapsedMs=${result.elapsedMs}")
        } finally { image.recycle() }
    }
    @Test fun blankImageDoesNotFabricateTextAndCancelledRunStops() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val blank = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            assertTrue(OfflineOcr(context).recognize(blank).lines.isEmpty())
            var stopped = false
            try { OfflineOcr(context).recognize(blank, AtomicBoolean(true)) }
            catch (_: java.util.concurrent.CancellationException) { stopped = true }
            assertTrue(stopped)
        } finally { blank.recycle() }
    }
}
