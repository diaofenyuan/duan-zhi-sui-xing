package com.example.localai.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import ai.onnxruntime.*
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

data class OcrLine(val text: String, val confidence: Float, val box: Rect)
data class OcrResult(val lines: List<OcrLine>, val elapsedMs: Long)

/** PP-OCRv4 检测与识别都使用本地 CPU；这里只处理横排文字，不猜测无法识别的内容。 */
class OfflineOcr(private val context: Context) {
    fun recognize(bitmap: Bitmap, cancelled: AtomicBoolean = AtomicBoolean(false)): OcrResult {
        val started = android.os.SystemClock.elapsedRealtime()
        val env = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
            env.createSession(context.assets.open("ocr/det.onnx").use { it.readBytes() }, options).use { detector ->
                checkCancelled(cancelled)
                val boxes = detect(env, detector, bitmap)
                require(boxes.size <= 120) { "图片文字超过 120 行，请分区域截图后识别" }
                checkCancelled(cancelled)
                env.createSession(context.assets.open("ocr/rec.onnx").use { it.readBytes() }, options).use { recognizer ->
                    val raw = recognizer.metadata.customMetadata["character"] ?: error("识别字典缺失，请重新安装")
                    val dictionary = listOf("") + raw.trimEnd('\n', '\r').split('\n').map { it.trimEnd('\r') } + listOf(" ")
                    val lines = mutableListOf<OcrLine>()
                    for (box in boxes) {
                        checkCancelled(cancelled)
                        val crop = Bitmap.createBitmap(bitmap, box.left, box.top, box.width(), box.height())
                        try {
                            val (text, confidence) = recognizeLine(env, recognizer, crop, dictionary)
                            if (text.isNotBlank() && confidence >= 0.45f) lines.add(OcrLine(text, confidence, box))
                        } finally { if (crop !== bitmap) crop.recycle() }
                    }
                    return OcrResult(lines, android.os.SystemClock.elapsedRealtime() - started)
                }
            }
        }
    }
    private fun checkCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get()) throw java.util.concurrent.CancellationException()
    }
    private fun tensor(env: OrtEnvironment, bitmap: Bitmap, width: Int, height: Int, detection: Boolean): OnnxTensor {
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height); scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()
        val values = FloatArray(3 * pixels.size)
        val means = floatArrayOf(0.485f, 0.456f, 0.406f)
        val stds = floatArrayOf(0.229f, 0.224f, 0.225f)
        pixels.forEachIndexed { index, pixel ->
            val alpha = Color.alpha(pixel) / 255f
            for (channel in 0..2) {
                val component = (pixel shr (16 - 8 * channel)) and 255
                val v = (component * alpha + 255f * (1f - alpha)) / 255f
                values[channel * pixels.size + index] = if (detection) (v - means[channel]) / stds[channel] else (v - 0.5f) / 0.5f
            }
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(values), longArrayOf(1, 3, height.toLong(), width.toLong()))
    }
    private fun detect(env: OrtEnvironment, session: OrtSession, bitmap: Bitmap): List<Rect> {
        val scale = min(1f, 960f / max(bitmap.width, bitmap.height))
        val width = max(32, ((bitmap.width * scale / 32).roundToInt()) * 32)
        val height = max(32, ((bitmap.height * scale / 32).roundToInt()) * 32)
        tensor(env, bitmap, width, height, true).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val map = (result[0].value as Array<Array<Array<FloatArray>>>)[0][0]
                return components(map).map { box ->
                    val x = bitmap.width.toFloat() / map[0].size; val y = bitmap.height.toFloat() / map.size
                    Rect(floor(box.left * x).toInt().coerceAtLeast(0), floor(box.top * y).toInt().coerceAtLeast(0),
                        ceil(box.right * x).toInt().coerceAtMost(bitmap.width), ceil(box.bottom * y).toInt().coerceAtMost(bitmap.height))
                }.filter { it.width() > 3 && it.height() > 3 }.sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
            }
        }
    }
    private fun components(map: Array<FloatArray>): List<Rect> {
        val h = map.size; val w = map[0].size
        val seen = BooleanArray(w * h); val queue = IntArray(w * h)
        val boxes = mutableListOf<Rect>()
        for (y in 0 until h) for (x in 0 until w) {
            val start = y * w + x
            if (seen[start] || map[y][x] < 0.3f) continue
            var head = 0; var tail = 1; queue[0] = start; seen[start] = true
            var left = x; var right = x; var top = y; var bottom = y; var score = 0f
            while (head < tail) {
                val point = queue[head++]; val px = point % w; val py = point / w
                left = min(left, px); right = max(right, px); top = min(top, py); bottom = max(bottom, py); score += map[py][px]
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx; val ny = py + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val next = ny * w + nx
                    if (!seen[next] && map[ny][nx] >= 0.3f) { seen[next] = true; queue[tail++] = next }
                }
            }
            if (tail < 12 || score / tail < 0.55f || bottom - top < 3) continue
            // 检测概率图覆盖字心，向外扩展以恢复完整笔画与行边缘。
            val padding = max(2, ((bottom - top) * 0.55f).roundToInt())
            boxes.add(Rect((left - padding).coerceAtLeast(0), (top - padding).coerceAtLeast(0),
                (right + padding + 1).coerceAtMost(w), (bottom + padding + 1).coerceAtMost(h)))
        }
        return boxes.sortedBy { it.top }
    }
    private fun recognizeLine(env: OrtEnvironment, session: OrtSession, bitmap: Bitmap, dictionary: List<String>): Pair<String, Float> {
        val width = (48f * bitmap.width / bitmap.height).roundToInt().coerceIn(16, 1600)
        tensor(env, bitmap, width, 48, false).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val rows = (result[0].value as Array<Array<FloatArray>>)[0]
                require(rows.firstOrNull()?.size == dictionary.size) { "识别模型与字典不匹配" }
                var last = -1; var confidence = 0f; var count = 0
                val text = StringBuilder()
                for (probabilities in rows) {
                    var best = 0
                    for (i in 1 until probabilities.size) if (probabilities[i] > probabilities[best]) best = i
                    if (best != 0 && best != last) { text.append(dictionary[best]); confidence += probabilities[best]; count++ }
                    last = best
                }
                return text.toString().trim() to if (count == 0) 0f else confidence / count
            }
        }
    }
}
