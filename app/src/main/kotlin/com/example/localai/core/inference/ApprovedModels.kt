package com.example.localai.core.inference

import android.content.Context
import com.example.localai.data.storage.ModelStorageManager
import com.example.localai.model.ModelInfo
import java.io.File
import java.util.ArrayList

/**
 * 已验证模板与运行参数的推理模型注册表；未知模型不允许生成模拟回复。
 * 文件按 ModelStorageManager 布局存放：files/models/{modelId}/{version}/{fileName}。
 */
object ApprovedModels {

    class Approved(
        @JvmField val modelId: String,
        @JvmField val version: String,
        @JvmField val fileName: String,
        @JvmField val displayName: String,
        @JvmField val publisher: String,
        @JvmField val license: String,
        @JvmField val sizeBytes: Long,
        @JvmField val contextLength: Int,
        @JvmField val threadCount: Int,
        @JvmField val temperature: Float,
        @JvmField val topP: Float,
        @JvmField val maxNewTokens: Int,
        @JvmField val parameterCount: Long = 135_000_000L,
        @JvmField val languages: List<String> = ModelInfo.langs("英文"),
        @JvmField val maxContextLength: Int = contextLength,
        @JvmField val quantization: String = "Q4_K_M",
        @JvmField val task: String = ModelInfo.TASK_TEXT
    )

    @JvmField
    val SMOLLM_135M = Approved(
        "smollm-135m-instruct", "2026.08.1",
        "SmolLM-135M-Instruct-Q4_K_M.gguf",
        "SmolLM-135M-Instruct", "HuggingFaceTB", "Apache-2.0",
        105_453_984L, 2048, 4, 0.7f, 0.9f, 256)

    @JvmField
    val QWEN_05B = Approved(
        "qwen2.5-0.5b-instruct", "2026.09.1", "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        "Qwen2.5-0.5B-Instruct · Q4_K_M", "Qwen", "Apache-2.0", 491_400_032L,
        2048, 4, 0.7f, 0.9f, 256, 494_032_768L, ModelInfo.langs("中文", "英文"), 32768)

    private val ALL = arrayOf(QWEN_05B, SMOLLM_135M,
        Approved("qwen2.5-0.5b-instruct-q8", "2026.09.1", "qwen2.5-0.5b-instruct-q8_0.gguf",
            "Qwen2.5-0.5B-Instruct · Q8_0", "Qwen", "Apache-2.0", 675710816L,
            2048, 4, 0.7f, 0.9f, 256, 494032768L, ModelInfo.langs("中文", "英文"),
            32768, "Q8_0", ModelInfo.TASK_TEXT),
        Approved("qwen2.5-1.5b-instruct", "2026.09.1", "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "Qwen2.5-1.5B-Instruct · Q4_K_M", "Qwen", "Apache-2.0", 1117320736L,
            2048, 4, 0.7f, 0.9f, 256, 1543714304L, ModelInfo.langs("中文", "英文"),
            32768, "Q4_K_M", ModelInfo.TASK_TEXT),
        Approved("qwen2.5-1.5b-instruct-q8", "2026.09.1", "qwen2.5-1.5b-instruct-q8_0.gguf",
            "Qwen2.5-1.5B-Instruct · Q8_0", "Qwen", "Apache-2.0", 1894532128L,
            2048, 4, 0.7f, 0.9f, 256, 1543714304L, ModelInfo.langs("中文", "英文"),
            32768, "Q8_0", ModelInfo.TASK_TEXT),
        Approved("qwen2.5-coder-0.5b-instruct", "2026.09.1", "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
            "Qwen2.5-Coder-0.5B-Instruct · Q4_K_M", "Qwen", "Apache-2.0", 491400064L,
            2048, 4, 0.7f, 0.9f, 256, 494032768L, ModelInfo.langs("中文", "英文"),
            32768, "Q4_K_M", ModelInfo.TASK_CODE),
        Approved("qwen2.5-coder-0.5b-instruct-q8", "2026.09.1", "qwen2.5-coder-0.5b-instruct-q8_0.gguf",
            "Qwen2.5-Coder-0.5B-Instruct · Q8_0", "Qwen", "Apache-2.0", 675710848L,
            2048, 4, 0.7f, 0.9f, 256, 494032768L, ModelInfo.langs("中文", "英文"),
            32768, "Q8_0", ModelInfo.TASK_CODE),
        Approved("qwen2.5-coder-1.5b-instruct", "2026.09.1", "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            "Qwen2.5-Coder-1.5B-Instruct · Q4_K_M", "Qwen", "Apache-2.0", 1117320768L,
            2048, 4, 0.7f, 0.9f, 256, 1543714304L, ModelInfo.langs("中文", "英文"),
            32768, "Q4_K_M", ModelInfo.TASK_CODE),
        Approved("qwen2.5-coder-1.5b-instruct-q8", "2026.09.1", "qwen2.5-coder-1.5b-instruct-q8_0.gguf",
            "Qwen2.5-Coder-1.5B-Instruct · Q8_0", "Qwen", "Apache-2.0", 1894532160L,
            2048, 4, 0.7f, 0.9f, 256, 1543714304L, ModelInfo.langs("中文", "英文"),
            32768, "Q8_0", ModelInfo.TASK_CODE)
    )

    @JvmStatic
    fun byId(modelId: String?): Approved? {
        if (modelId == null) {
            return null
        }
        for (a in ALL) {
            if (a.modelId == modelId) {
                return a
            }
        }
        return null
    }

    @JvmStatic
    fun modelFile(context: Context, approved: Approved): File {
        val storage = ModelStorageManager(context.filesDir)
        return storage.modelFile(approved.modelId, approved.version, approved.fileName)
    }

    /** 完整长度与安装完成标记同时存在，排除断点文件和未完成安装。 */
    @JvmStatic
    fun isInstalled(context: Context, modelId: String?): Boolean {
        val approved = byId(modelId) ?: return false
        val file = modelFile(context, approved)
        return file.isFile && file.length() == approved.sizeBytes &&
            ModelStorageManager(context.filesDir).isInstalled(approved.modelId, approved.version)
    }

    @JvmStatic
    fun requestFor(context: Context, approved: Approved, requestId: String): InferenceRequest {
        return InferenceRequest(
            requestId, approved.modelId, approved.version,
            modelFile(context, approved).absolutePath,
            approved.contextLength, approved.threadCount,
            approved.temperature, approved.topP, approved.maxNewTokens)
    }

    /** 已安装的批准模型转 ModelInfo（供聊天页模型选择器展示）。 */
    @JvmStatic
    fun installedAsModelInfos(context: Context): List<ModelInfo> {
        val result = ArrayList<ModelInfo>()
        for (a in ALL) {
            if (!isInstalled(context, a.modelId)) {
                continue
            }
            result.add(ModelInfo(
                a.modelId, a.displayName, a.publisher,
                com.example.localai.feature.market.MarketModels.paramsLabel(a.parameterCount),
                a.parameterCount / 1e9, a.quantization, com.example.localai.common.Fmt.humanBytes(a.sizeBytes), a.sizeBytes,
                com.example.localai.feature.market.MarketModels.contextLabel(
                    com.example.localai.feature.settings.InferencePolicy.current(context, a).contextLength.toLong()),
                a.task, a.languages, a.license,
                "已安装，可离线运行的本地模型。",
                ModelInfo.COMPAT_RECOMMENDED, "", 0, 0.0, 0,
                "2026-08-23", 0, true))
        }
        return result
    }
}
