package com.example.localai.feature.library

import android.app.Application
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class OcrViewModel(app: Application) : AndroidViewModel(app) {
    val changes = MutableLiveData(0)
    var bitmap: Bitmap? = null; private set
    var text = ""
    var status = "选择图片后开始识别"
    var running = false; private set
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "localai-ocr") }
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    private var cleared = false
    private var cacheFile: File? = null

    fun load(uri: Uri) {
        if (running) return
        running = true; cancelled.set(false); status = "正在读取图片…"; notifyChanged()
        worker.execute {
            val result = runCatching {
                val app = getApplication<Application>()
                val file = File.createTempFile("ocr-", ".image", app.cacheDir)
                try {
                    app.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { out ->
                        val buffer = ByteArray(8192); var total = 0
                        while (true) {
                            if (cancelled.get()) throw java.util.concurrent.CancellationException()
                            val n = input.read(buffer); if (n < 0) break
                            total += n; require(total <= 20 * 1024 * 1024) { "图片超过 20 MB，请压缩或截图后选择" }
                            out.write(buffer, 0, n)
                        }
                    } } ?: error("无法读取图片，请重新选择")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法解码图片，请使用 JPG 或 PNG" }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                        inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
                    }) ?: error("图片读取失败")
                    val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)
                    val matrix = Matrix().apply {
                        when (orientation) {
                            2 -> setScale(-1f, 1f)
                            3 -> setRotate(180f)
                            4 -> setScale(1f, -1f)
                            5 -> { setRotate(90f); postScale(-1f, 1f) }
                            6 -> setRotate(90f)
                            7 -> { setRotate(-90f); postScale(-1f, 1f) }
                            8 -> setRotate(-90f)
                        }
                    }
                    val image = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    if (image !== decoded) decoded.recycle()
                    cacheFile?.delete(); cacheFile = file
                    image
                } catch (e: Throwable) { file.delete(); throw e }
            }
            main.post {
                running = false
                if (cleared) { result.getOrNull()?.recycle(); cleanup(); return@post }
                result.onSuccess { image -> bitmap?.recycle(); bitmap = image; text = ""; status = "图片已就绪，可旋转调整方向" }
                    .onFailure { status = if (it is java.util.concurrent.CancellationException) "已取消" else it.message ?: "读取失败，请换一张图片" }
                notifyChanged()
            }
        }
    }
    fun rotate() {
        val source = bitmap ?: return
        if (running) return
        bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(90f) }, true)
        if (source !== bitmap) source.recycle()
        status = "方向已调整，可重新识别"; notifyChanged()
    }
    fun recognize() {
        val image = bitmap ?: return
        if (running) return
        running = true; cancelled.set(false); status = "正在本机识别…"; notifyChanged()
        worker.execute {
            val result = runCatching { OfflineOcr(getApplication()).recognize(image, cancelled) }
            main.post {
                running = false
                if (cleared) { cleanup(); return@post }
                result.onSuccess {
                    text = it.lines.joinToString("\n") { line -> line.text }
                    status = if (text.isBlank()) "没有识别到清晰文字，请裁剪文字区域或调整方向后重试"
                    else String.format(java.util.Locale.CHINA, "识别 %d 行 · 本机耗时 %.1f 秒\n请核对姓名、数字和日期后保存。", it.lines.size, it.elapsedMs / 1000.0)
                }.onFailure {
                    status = when (it) {
                        is java.util.concurrent.CancellationException -> "已停止识别"
                        is IllegalArgumentException -> it.message ?: "图片不适合识别"
                        else -> "识别失败，请换一张清晰图片后重试"
                    }
                }
                notifyChanged()
            }
        }
    }
    fun cancel() { cancelled.set(true); status = "正在停止…"; notifyChanged() }
    private fun notifyChanged() { changes.value = (changes.value ?: 0) + 1 }
    private fun cleanup() { bitmap?.recycle(); bitmap = null; cacheFile?.delete(); worker.shutdown() }
    override fun onCleared() { cleared = true; cancelled.set(true); if (!running) cleanup(); super.onCleared() }
}
