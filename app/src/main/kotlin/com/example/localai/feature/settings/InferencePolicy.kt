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
    data class Parameters(val contextLength: Int, val threads: Int, val maxNewTokens: Int)

    fun current(context: Context, model: ApprovedModels.Approved): Parameters {
        val power = context.getSystemService(PowerManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        val hot = Build.VERSION.SDK_INT >= 29 &&
            (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE
        return select(model, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, "auto"),
            Runtime.getRuntime().availableProcessors(), power?.isPowerSaveMode == true, hot || memory.lowMemory)
    }

    fun select(model: ApprovedModels.Approved, mode: String?, processors: Int,
               powerSave: Boolean, constrained: Boolean): Parameters {
        val saver = mode == "saver" || constrained || (mode != "balanced" && powerSave)
        return Parameters(minOf(model.contextLength, if (saver) 1024 else 2048),
            minOf(model.threadCount, processors.coerceAtLeast(1), if (saver) 2 else 4),
            minOf(model.maxNewTokens, if (saver) 128 else 256))
    }

    fun keepScreenOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KEEP_SCREEN, false)
}
