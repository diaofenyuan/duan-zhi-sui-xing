package com.example.localai.core.compatibility

import java.util.ArrayList

/**
 * 可解释的兼容性规则引擎（P4 版）。
 *
 * 输入：设备画像快照 + 模型目录条目关键字段。
 * 输出：{RECOMMENDED | RUNNABLE | HIGH_LOAD | UNSUPPORTED} + 原因列表 + 估算峰值内存。
 *
 * 重要边界：本阶段的峰值内存为粗粒度估算（权重 + 近似的 KV Cache），
 * 系数为经验值，未经过真机校准；真机实测数据于 P6 回填后校准。
 * 该引擎只做解释性建议，不阻塞任何真实载荷路径。
 */
object CompatibilityEngine {

    const val LEVEL_RECOMMENDED = "RECOMMENDED"
    const val LEVEL_RUNNABLE = "RUNNABLE"
    const val LEVEL_HIGH_LOAD = "HIGH_LOAD"
    const val LEVEL_UNSUPPORTED = "UNSUPPORTED"

    /** 设备画像快照（与 S023 DeviceProfiler 输出同构）。 */
    class DeviceSnapshot(
        @JvmField val androidApi: Int,
        @JvmField val deviceAbi: String,
        @JvmField val freeStorageBytes: Long,
        @JvmField val ramBytes: Long
    ) {
        @JvmField val unknownNote: String = "估算值，P6 真机校准后生效"
    }

    /** 模型侧关键约束（来自 ModelManifest runtime/files，客户端已验证结构）。 */
    class ModelConstraints(
        @JvmField val minAndroidApi: Int,
        abisArg: List<String>?,
        @JvmField val sizeBytes: Long,
        contextLengthArg: Long,
        parameterCountArg: Long
    ) {
        @JvmField val abis: List<String> = abisArg ?: ArrayList()
        @JvmField val contextLength: Long = maxOf(1L, contextLengthArg)
        @JvmField val parameterCount: Long = maxOf(1L, parameterCountArg)
    }

    class Result(
        @JvmField val level: String,
        @JvmField val reasons: List<String>,
        /** 估算峰值运行总内存（字节）。 */
        @JvmField val estimatedPeakBytes: Long
    ) {
        fun isUnsupported(): Boolean = LEVEL_UNSUPPORTED == level

        fun isRecommended(): Boolean = LEVEL_RECOMMENDED == level
    }

    @JvmStatic
    fun evaluate(device: DeviceSnapshot, model: ModelConstraints): Result {
        val reasons = ArrayList<String>()

        if (device.androidApi < model.minAndroidApi) {
            reasons.add("系统需 Android ${model.minAndroidApi} 或更高（当前 API ${device.androidApi}）")
        }
        val abiOk = model.abis.contains(device.deviceAbi)
        // 模拟器 x86_64 特例：应用为 arm64 真机设计，但 x86_64 ABI 的 Native 构建同样产出，
        // 便于模拟器验证真实推理链路；真机目录仍只面向 arm64-v8a。
        if (!abiOk && !(device.deviceAbi == "x86" || device.deviceAbi == "x86_64")) {
            reasons.add("模型不支持当前架构（需要 " + join(model.abis) + "，本机 " + device.deviceAbi + "）")
        }
        val freeLimit = model.sizeBytes * 2L + 300L * 1024L * 1024L
        if (device.freeStorageBytes < freeLimit) {
            reasons.add("可用存储不足（至少需要约 " + humanMb(freeLimit) + "，当前可用 "
                    + humanMb(device.freeStorageBytes) + "）")
        }

        val peak = estimatePeakBytes(model)
        val threshold =
            if (device.ramBytes > 0) peak.toDouble() / device.ramBytes.toDouble() else 2.0

        val level: String
        if (reasons.isNotEmpty()) {
            level = LEVEL_UNSUPPORTED
        } else if (threshold <= 0.5) {
            level = LEVEL_RECOMMENDED
        } else if (threshold <= 0.77) {
            level = LEVEL_RUNNABLE
            reasons.add("内存余量中等，建议缩短上下文或降低单轮输出长度")
        } else if (threshold <= 1.0) {
            level = LEVEL_HIGH_LOAD
            reasons.add("峰值估算接近设备内存上限，可能变慢、发热或触发降级")
        } else {
            level = LEVEL_UNSUPPORTED
            reasons.add("估算峰值内存 " + humanMb(peak) + " 超过设备可用内存 " + humanMb(device.ramBytes))
        }
        return Result(level, reasons, peak)
    }

    /**
     * 粗粒度峰值估算：权重 + KV Cache 近似。
     * kv ≈ 2 × layer × kvHead×headDim × ctx × 2B；用 每 1B 参数 ≈ 0.35 layer 关系对
     * llama 家族近似（3B≈28 层、1.5B≈28 层、9B≈32 层均落在该范围内）。
     * 该近似为演示档，P6 真机实测后以实测 RSS/PSS 校准。
     */
    @JvmStatic
    fun estimatePeakBytes(m: ModelConstraints): Long {
        val paramsB = m.parameterCount / 1e9
        val layers = 14 + paramsB * 2.0
        val kvBytes = 2 * layers * (if (paramsB > 4) 1024 else 512) * m.contextLength * 2.0
        val weight = m.sizeBytes.toDouble()
        return (weight * 1.15 + kvBytes).toLong()
    }

    private fun humanMb(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
    }

    private fun join(inp: List<String>): String {
        val sb = StringBuilder()
        for (i in inp.indices) {
            if (i > 0) {
                sb.append("/")
            }
            sb.append(inp[i])
        }
        return if (sb.isEmpty()) "unknown" else sb.toString()
    }
}
