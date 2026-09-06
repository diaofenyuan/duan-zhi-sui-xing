package com.example.localai.feature.market

import com.example.localai.core.compatibility.CompatibilityEngine
import com.example.localai.feature.download.DownloadRepository
import com.example.localai.model.ModelInfo
import java.util.ArrayList
import java.util.Collections
import java.util.Locale

/**
 * 市场展示映射（纯逻辑可单测）：把签名目录条目 + 设备画像 -> ModelInfo，
 * 兼容性结论由 CompatibilityEngine 计算，安装状态来自 Room（CatalogItem.installed）。
 *
 * 注意：TPS/TTFT/内存为「估算值」（引擎估算 + 未实测 0），真实数值待 P6 真机基线；
 * 市场只展示「推荐/可运行/高负载/不支持」的估算结论，不对性能做承诺。
 */
object MarketModels {

    @JvmStatic
    fun map(view: DownloadRepository.CatalogView?,
            device: CompatibilityEngine.DeviceSnapshot,
            runtimeContext: (DownloadRepository.CatalogItem) -> Long = { minOf(it.contextLength, 2048L) }): List<ModelInfo> {
        if (view == null || view.models == null) {
            return Collections.emptyList()
        }
        val result = ArrayList<ModelInfo>()
        for (item in view.models) {
            val resultCompat = CompatibilityEngine.evaluate(device,
                CompatibilityEngine.ModelConstraints(
                    item.minAndroidApi, item.abis, item.sizeBytes,
                    runtimeContext(item), item.parameterCount), requireDownloadSpace = !item.installed)

            val info = ModelInfo(
                item.modelId!!, item.displayName!!,
                if (item.publisher == null) "" else item.publisher,
                paramsLabel(item.parameterCount), paramsB(item.parameterCount),
                item.quantization, sizeLabel(item.sizeBytes), item.sizeBytes,
                contextLabel(item.contextLength), primaryTask(item.tasks),
                if (item.languages == null || item.languages.isEmpty())
                    ModelInfo.langs("英文") else item.languages,
                item.licenseSpdx,
                if (item.description == null) "" else item.description,
                resultCompat.level,
                resultCompat.reasons.joinToString("；"),
                0, 0.0, (resultCompat.estimatedPeakBytes / (1024 * 1024)).toInt(),
                if (item.updatedAt == null) "" else item.updatedAt,
                gradIndex(item.modelId),
                item.installed)
            info.weightStatus = item.weightStatus
            info.isDemo = item.isDemo()
            info.sourceUrl = item.sourceUrl
            info.licenseUrl = item.licenseUrl
            info.chatTemplate = item.chatTemplate
            info.minAndroidApi = item.minAndroidApi
            info.estimatedPeakMb = (resultCompat.estimatedPeakBytes / (1024 * 1024)).toInt()
            result.add(info)
        }
        return result
    }

    @JvmStatic
    fun sizeLabel(sizeBytes: Long): String {
        val mb = sizeBytes / (1024.0 * 1024.0)
        if (mb >= 1024) {
            return String.format(Locale.US, "%.1f GB", mb / 1024.0)
        }
        return String.format(Locale.US, "%d MB", Math.round(mb))
    }

    @JvmStatic
    fun paramsLabel(parameterCount: Long): String {
        val b = parameterCount / 1e9
        if (b < 1) {
            return String.format(Locale.US, "%.2fB", b)
        }
        return String.format(Locale.US, "%.1fB", b)
    }

    private fun paramsB(parameterCount: Long): Double {
        return parameterCount / 1e9
    }

    @JvmStatic
    fun contextLabel(contextLength: Long): String {
        val k = contextLength / 1024
        if (k >= 1 && contextLength % 1024 == 0L) {
            return k.toString() + "K"
        }
        if (contextLength < 1024) {
            return contextLength.toString() + ""
        }
        return String.format(Locale.US, "%.1fK", contextLength / 1024.0)
    }

    private fun primaryTask(tasks: List<String>?): String {
        if (tasks != null && tasks.contains(ModelInfo.TASK_CODE)) {
            return ModelInfo.TASK_CODE
        }
        return ModelInfo.TASK_TEXT
    }

    private fun gradIndex(modelId: String?): Int {
        if (modelId == null) {
            return 0
        }
        var h = 0
        for (i in modelId.indices) {
            h = (h * 31 + modelId[i].code) and 0x7fffffff
        }
        return h % 4
    }
}
