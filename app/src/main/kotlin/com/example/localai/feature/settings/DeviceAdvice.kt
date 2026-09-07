package com.example.localai.feature.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.localai.core.device.DeviceProfiler
import com.example.localai.core.inference.InferenceStats
import com.example.localai.model.ModelInfo

object DeviceAdvice {
    fun recommend(models: List<ModelInfo>, mode: String?, availableMb: Long): ModelInfo? {
        val candidates = models.filter { it.task == ModelInfo.TASK_TEXT &&
            it.compat in setOf(ModelInfo.COMPAT_RECOMMENDED, ModelInfo.COMPAT_RUNNABLE) &&
            (availableMb <= 0 || it.estimatedPeakMb < availableMb * 0.75) }
        return if (mode == "balanced") candidates.sortedWith(compareByDescending<ModelInfo> { it.paramsB }.thenBy { it.sizeBytes }).firstOrNull()
        else candidates.minByOrNull { it.sizeBytes }
    }
    fun pressure(context: Context): String {
        val power = context.getSystemService(PowerManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
        return when {
            Build.VERSION.SDK_INT >= 29 && (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE ->
                "设备较热，已降低生成负载。建议暂停片刻，或切换省电模式。"
            memory.lowMemory -> "可用内存紧张，已降低生成负载。建议关闭其他应用或使用更小模型。"
            power?.isPowerSaveMode == true -> "系统处于省电状态。较长资料可以分段处理。"
            else -> ""
        }
    }
    fun record(context: Context, modelId: String, parameters: InferencePolicy.Parameters, stats: InferenceStats?) {
        if (stats == null || stats.genTokens <= 0 || stats.elapsedMs <= 0 || stats.ttftMs < 0) return
        context.getSharedPreferences("localai_measurements", 0).edit()
            .putString("model", modelId).putLong("time", System.currentTimeMillis())
            .putLong("ttft", stats.ttftMs).putLong("elapsed", stats.elapsedMs)
            .putLong("tokens", stats.genTokens).putInt("context", parameters.contextLength).apply()
    }
    fun measurement(context: Context): String {
        val p = context.getSharedPreferences("localai_measurements", 0)
        if (!p.contains("time")) return "还没有完成的运行记录。完成一次对话或任务后，这里会显示本机实测数据。"
        val model = com.example.localai.core.inference.ApprovedModels.byId(p.getString("model", ""))
        val time = java.text.SimpleDateFormat("yyyy年M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(p.getLong("time", 0)))
        return "${model?.displayName ?: "本地模型"}\n$time\n" +
            String.format(java.util.Locale.CHINA, "首字 %.1f 秒 · 总耗时 %.1f 秒\n输出 %d 个 token · 上下文 %d\n首字耗时不含模型加载；这是一次实际运行记录，结果会随输入和设备状态变化。",
                p.getLong("ttft", 0) / 1000.0, p.getLong("elapsed", 0) / 1000.0, p.getLong("tokens", 0), p.getInt("context", 0))
    }
}
