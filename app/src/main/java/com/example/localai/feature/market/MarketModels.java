package com.example.localai.feature.market;

import com.example.localai.core.compatibility.CompatibilityEngine;
import com.example.localai.feature.download.DownloadRepository;
import com.example.localai.model.ModelInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 市场展示映射（纯 Java 可单测）：把签名目录条目 + 设备画像 -> ModelInfo，
 * 兼容性结论由 CompatibilityEngine 计算，安装状态来自 Room（CatalogItem.installed）。
 *
 * 注意：TPS/TTFT/内存为「估算值」（引擎估算 + 未实测 0），真实数值待 P6 真机基线；
 * 市场只展示「推荐/可运行/高负载/不支持」的估算结论，不对性能做承诺。
 */
public final class MarketModels {

    private MarketModels() {
    }

    public static List<ModelInfo> map(DownloadRepository.CatalogView view,
                                      CompatibilityEngine.DeviceSnapshot device) {
        if (view == null || view.models == null) {
            return Collections.emptyList();
        }
        List<ModelInfo> result = new ArrayList<>();
        for (DownloadRepository.CatalogItem item : view.models) {
            CompatibilityEngine.Result resultCompat = CompatibilityEngine.evaluate(device,
                    new CompatibilityEngine.ModelConstraints(
                            item.minAndroidApi, item.abis, item.sizeBytes,
                            item.contextLength, item.parameterCount));

            ModelInfo info = new ModelInfo(
                    item.modelId, item.displayName, item.publisher == null ? "" : item.publisher,
                    paramsLabel(item.parameterCount), paramsB(item.parameterCount),
                    item.quantization, sizeLabel(item.sizeBytes), item.sizeBytes,
                    contextLabel(item.contextLength), primaryTask(item.tasks),
                    item.languages == null || item.languages.isEmpty()
                            ? ModelInfo.langs("英文") : item.languages,
                    item.licenseSpdx,
                    item.description == null ? "" : item.description,
                    resultCompat.level,
                    String.join("；", resultCompat.reasons),
                    0, 0, (int) (resultCompat.estimatedPeakBytes / (1024 * 1024)),
                    item.updatedAt == null ? "" : item.updatedAt,
                    gradIndex(item.modelId),
                    item.installed);
            info.weightStatus = item.weightStatus;
            info.isDemo = item.isDemo();
            info.sourceUrl = item.sourceUrl;
            info.licenseUrl = item.licenseUrl;
            info.chatTemplate = item.chatTemplate;
            info.minAndroidApi = item.minAndroidApi;
            info.estimatedPeakMb = (int) (resultCompat.estimatedPeakBytes / (1024 * 1024));
            result.add(info);
        }
        return result;
    }

    public static String sizeLabel(long sizeBytes) {
        double mb = sizeBytes / (1024.0 * 1024.0);
        if (mb >= 1024) {
            return String.format(Locale.US, "%.1f GB", mb / 1024.0);
        }
        return String.format(Locale.US, "%d MB", Math.round(mb));
    }

    public static String paramsLabel(long parameterCount) {
        double b = parameterCount / 1e9;
        if (b < 1) {
            return String.format(Locale.US, "%.2fB", b);
        }
        return String.format(Locale.US, "%.1fB", b);
    }

    private static double paramsB(long parameterCount) {
        return parameterCount / 1e9;
    }

    public static String contextLabel(long contextLength) {
        long k = contextLength / 1024;
        if (k >= 1 && contextLength % 1024 == 0) {
            return k + "K";
        }
        if (contextLength < 1024) {
            return contextLength + "";
        }
        return String.format(Locale.US, "%.1fK", contextLength / 1024.0);
    }

    private static String primaryTask(List<String> tasks) {
        if (tasks != null && tasks.contains(ModelInfo.TASK_CODE)) {
            return ModelInfo.TASK_CODE;
        }
        return ModelInfo.TASK_TEXT;
    }

    private static int gradIndex(String modelId) {
        if (modelId == null) {
            return 0;
        }
        int h = 0;
        for (int i = 0; i < modelId.length(); i++) {
            h = (h * 31 + modelId.charAt(i)) & 0x7fffffff;
        }
        return h % 4;
    }
}
