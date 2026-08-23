package com.example.localai.core.inference;

import android.content.Context;

import com.example.localai.data.storage.ModelStorageManager;
import com.example.localai.model.ModelInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * P3 批准的推理模型注册表（qa/fixtures/approved-model.json 的代码镜像）。
 * 只有列表中的模型允许进入真实推理路径；其它模型继续走演示模式。
 * 文件按 ModelStorageManager 布局存放：files/models/{modelId}/{version}/{fileName}。
 */
public final class ApprovedModels {

    public static final class Approved {
        public final String modelId;
        public final String version;
        public final String fileName;
        public final String displayName;
        public final String publisher;
        public final String license;
        public final long sizeBytes;
        public final int contextLength;
        public final int threadCount;
        public final float temperature;
        public final float topP;
        public final int maxNewTokens;

        Approved(String modelId, String version, String fileName, String displayName,
                 String publisher, String license, long sizeBytes, int contextLength,
                 int threadCount, float temperature, float topP, int maxNewTokens) {
            this.modelId = modelId;
            this.version = version;
            this.fileName = fileName;
            this.displayName = displayName;
            this.publisher = publisher;
            this.license = license;
            this.sizeBytes = sizeBytes;
            this.contextLength = contextLength;
            this.threadCount = threadCount;
            this.temperature = temperature;
            this.topP = topP;
            this.maxNewTokens = maxNewTokens;
        }
    }

    public static final Approved SMOLLM_135M = new Approved(
            "smollm-135m-instruct", "2026.08.1",
            "SmolLM-135M-Instruct-Q4_K_M.gguf",
            "SmolLM-135M-Instruct", "HuggingFaceTB", "Apache-2.0",
            105_453_984L, 2048, 4, 0.7f, 0.9f, 256);

    private static final Approved[] ALL = {SMOLLM_135M};

    private ApprovedModels() {
    }

    public static Approved byId(String modelId) {
        if (modelId == null) {
            return null;
        }
        for (Approved a : ALL) {
            if (a.modelId.equals(modelId)) {
                return a;
            }
        }
        return null;
    }

    public static File modelFile(Context context, Approved approved) {
        ModelStorageManager storage = new ModelStorageManager(context.getFilesDir());
        return storage.modelFile(approved.modelId, approved.version, approved.fileName);
    }

    /** 模型文件真实存在即视为已安装（不依赖 install.ok 之外的额外状态）。 */
    public static boolean isInstalled(Context context, String modelId) {
        Approved approved = byId(modelId);
        if (approved == null) {
            return false;
        }
        File file = modelFile(context, approved);
        return file.isFile() && file.length() > 0;
    }

    public static InferenceRequest requestFor(Context context, Approved approved,
                                              String requestId) {
        return new InferenceRequest(
                requestId, approved.modelId, approved.version,
                modelFile(context, approved).getAbsolutePath(),
                approved.contextLength, approved.threadCount,
                approved.temperature, approved.topP, approved.maxNewTokens);
    }

    /** 已安装的批准模型转 ModelInfo（供聊天页模型选择器展示）。 */
    public static List<ModelInfo> installedAsModelInfos(Context context) {
        List<ModelInfo> result = new ArrayList<>();
        for (Approved a : ALL) {
            if (!isInstalled(context, a.modelId)) {
                continue;
            }
            result.add(new ModelInfo(
                    a.modelId, a.displayName, a.publisher,
                    "0.14B", 0.14, "Q4_K_M", "100 MB", a.sizeBytes,
                    (a.contextLength / 1024) + "K",
                    ModelInfo.TASK_TEXT, ModelInfo.langs("英文"), a.license,
                    "经批准的最小真实 GGUF 模型，用于本地推理链路验收。",
                    ModelInfo.COMPAT_RECOMMENDED, "", 0, 0, 0,
                    "2026-08-23", 0, true));
        }
        return result;
    }
}
