package com.example.localai.feature.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.localai.core.inference.ApprovedModels

/** 每轮开始时读取设备状态，参数变化时由引擎重新加载，避免配置只停留在设置页。 */
object InferencePolicy {
    const val PREFS = "localai_settings"
    const val KEY_MODE = "mode"
    const val KEY_KEEP_SCREEN = "keep_screen"
    const val KEY_CONTEXT = "context_length"
    const val KEY_GPU_LAYERS = "gpu_layers"
    const val MIN_CONTEXT = 512
    const val MAX_CONTEXT = 32768
    data class Parameters(val contextLength: Int, val threads: Int, val maxNewTokens: Int, val gpuLayers: Int = 0)

    fun current(context: Context, model: ApprovedModels.Approved): Parameters {
        val power = context.getSystemService(PowerManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        val hot = Build.VERSION.SDK_INT >= 29 &&
            (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return select(model, prefs.getString(KEY_MODE, "auto"),
            Runtime.getRuntime().availableProcessors(), power?.isPowerSaveMode == true, hot || memory.lowMemory,
            prefs.getInt(KEY_CONTEXT, 0), prefs.getInt(KEY_GPU_LAYERS, 0))
    }

    fun select(model: ApprovedModels.Approved, mode: String?, processors: Int,
               powerSave: Boolean, constrained: Boolean, manualContext: Int = 0, gpuLayers: Int = 0): Parameters {
        val saver = mode == "saver" || constrained || (mode != "balanced" && powerSave)
        // 手动上下文独立于性能档位；只按模型真实上限裁剪，避免设置后被静默降回 1K。
        val context = if (manualContext > 0) minOf(manualContext.coerceIn(MIN_CONTEXT, MAX_CONTEXT), model.maxContextLength)
            else minOf(model.contextLength, if (saver) 1024 else 2048)
        return Parameters(context,
            minOf(model.threadCount, processors.coerceAtLeast(1), if (saver) 2 else 4),
            minOf(model.maxNewTokens, if (saver) 128 else 256), gpuLayers.coerceIn(-1, 256))
    }

    fun keepScreenOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KEEP_SCREEN, false)
}
