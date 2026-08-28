package com.example.localai.core.compatibility;

import java.util.ArrayList;
import java.util.List;

/**
 * 可解释的兼容性规则引擎（P4 版，纯 Java 可单测）。
 *
 * 输入：设备画像快照 + 模型目录条目关键字段。
 * 输出：{RECOMMENDED | RUNNABLE | HIGH_LOAD | UNSUPPORTED} + 原因列表 + 估算峰值内存。
 *
 * 重要边界：本阶段的峰值内存为粗粒度估算（权重 + 近似的 KV Cache），
 * 系数为经验值，未经过真机校准；真机实测数据于 P6 回填后校准。
 * 该引擎只做解释性建议，不阻塞任何真实载荷路径。
 */
public final class CompatibilityEngine {

    public static final String LEVEL_RECOMMENDED = "RECOMMENDED";
    public static final String LEVEL_RUNNABLE = "RUNNABLE";
    public static final String LEVEL_HIGH_LOAD = "HIGH_LOAD";
    public static final String LEVEL_UNSUPPORTED = "UNSUPPORTED";

    /** 设备画像快照（与 S023 DeviceProfiler 输出同构；P4 为构造纯 Java 引擎设为可测结构）。 */
    public static final class DeviceSnapshot {
        public final int androidApi;
        public final String deviceAbi;
        public final long freeStorageBytes;
        public final long ramBytes;
        public final String unknownNote;

        public DeviceSnapshot(int androidApi, String deviceAbi, long freeStorageBytes, long ramBytes) {
            this.androidApi = androidApi;
            this.deviceAbi = deviceAbi;
            this.freeStorageBytes = freeStorageBytes;
            this.ramBytes = ramBytes;
            this.unknownNote = "估算值，P6 真机校准后生效";
        }
    }

    /** 模型侧关键约束（来自 ModelManifest runtime/files，客户端已验证结构。）。 */
    public static final class ModelConstraints {
        public final int minAndroidApi;
        public final List<String> abis;
        public final long sizeBytes;
        public final long contextLength;
        public final long parameterCount;

        public ModelConstraints(int minAndroidApi, List<String> abis,
                                long sizeBytes, long contextLength, long parameterCount) {
            this.minAndroidApi = minAndroidApi;
            this.abis = abis == null ? new ArrayList<String>() : abis;
            this.sizeBytes = sizeBytes;
            this.contextLength = Math.max(1, contextLength);
            this.parameterCount = Math.max(1, parameterCount);
        }
    }

    public static final class Result {
        public final String level;
        public final List<String> reasons;
        /** 估算峰值运行总内存（字节）。 */
        public final long estimatedPeakBytes;

        Result(String level, List<String> reasons, long estimatedPeakBytes) {
            this.level = level;
            this.reasons = reasons;
            this.estimatedPeakBytes = estimatedPeakBytes;
        }

        public boolean isUnsupported() {
            return LEVEL_UNSUPPORTED.equals(level);
        }

        public boolean isRecommended() {
            return LEVEL_RECOMMENDED.equals(level);
        }
    }

    private CompatibilityEngine() {
    }

    public static Result evaluate(DeviceSnapshot device, ModelConstraints model) {
        List<String> reasons = new ArrayList<>();

        if (device.androidApi < model.minAndroidApi) {
            reasons.add("系统需 Android " + model.minAndroidApi + " 或更高（当前 API " + device.androidApi + "）");
        }
        boolean abiOk = model.abis != null && model.abis.contains(device.deviceAbi);
        // 模拟器 x86_64 特例：应用为 arm64 真机设计，但 x86_64 ABI 的 Native 构建同样产出，
        // 便于模拟器验证真实推理链路；真机目录仍只面向 arm64-v8a。
        if (!abiOk && !("x86".equals(device.deviceAbi) || "x86_64".equals(device.deviceAbi))) {
            reasons.add("模型不支持当前架构（需要 " + join(model.abis) + "，本机 " + device.deviceAbi + "）");
        }
        long freeLimit = model.sizeBytes * 2L + 300L * 1024L * 1024L;
        if (device.freeStorageBytes < freeLimit) {
            reasons.add("可用存储不足（至少需要约 " + humanMb(freeLimit) + "，当前可用 "
                    + humanMb(device.freeStorageBytes) + "）");
        }

        long peak = estimatePeakBytes(model);
        double threshold =
                device.ramBytes > 0 ? (double) peak / (double) device.ramBytes : 2.0;

        String level;
        if (!reasons.isEmpty()) {
            level = LEVEL_UNSUPPORTED;
        } else if (threshold <= 0.5) {
            level = LEVEL_RECOMMENDED;
        } else if (threshold <= 0.77) {
            level = LEVEL_RUNNABLE;
            reasons.add("内存余量中等，建议缩短上下文或降低单轮输出长度");
        } else if (threshold <= 1.0) {
            level = LEVEL_HIGH_LOAD;
            reasons.add("峰值估算接近设备内存上限，可能变慢、发热或触发降级");
        } else {
            level = LEVEL_UNSUPPORTED;
            reasons.add("估算峰值内存 " + humanMb(peak) + " 超过设备可用内存 " + humanMb(device.ramBytes));
        }
        return new Result(level, reasons, peak);
    }

    /**
     * 粗粒度峰值估算：权重 + KV Cache 近似。
     * kv ≈ 2 × layer × kvHead×headDim × ctx × 2B；用 每 1B 参数 ≈ 0.35 layer 关系对
     * llama 家族近似（3B≈28 层、1.5B≈28 层、9B≈32 层均落在该范围内）。
     * 该近似为演示档，P6 真机实测后以实测 RSS/PSS 校准。
     */
    public static long estimatePeakBytes(ModelConstraints m) {
        double paramsB = m.parameterCount / 1e9;
        double layers = 14 + paramsB * 2.0;
        double kvBytes = 2 * layers * (paramsB > 4 ? 1024 : 512) * m.contextLength * 2.0;
        double weight = m.sizeBytes;
        return (long) (weight * 1.15 + kvBytes);
    }

    private static String humanMb(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return mb >= 1024 ? String.format("%.1f GB", mb / 1024.0) : String.format("%.0f MB", mb);
    }

    private static String join(List<String> in) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < in.size(); i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append(in.get(i));
        }
        return sb.length() == 0 ? "unknown" : sb.toString();
    }
}
