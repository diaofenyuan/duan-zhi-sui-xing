package com.example.localai.core.inference

import android.content.Context
import com.example.localai.data.storage.ModelStorageManager
import com.example.localai.model.ModelInfo
import java.io.File
import java.util.ArrayList

/**
 * P3 批准的推理模型注册表（qa/fixtures/approved-model.json 的代码镜像）。
 * 只有列表中的模型允许进入真实推理路径；其它模型继续走演示模式。
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
        @JvmField val maxNewTokens: Int
    )

    @JvmField
    val SMOLLM_135M = Approved(
        "smollm-135m-instruct", "2026.08.1",
        "SmolLM-135M-Instruct-Q4_K_M.gguf",
        "SmolLM-135M-Instruct", "HuggingFaceTB", "Apache-2.0",
        105_453_984L, 2048, 4, 0.7f, 0.9f, 256)

    private val ALL = arrayOf(SMOLLM_135M)

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

    /** 模型文件真实存在即视为已安装（不依赖 install.ok 之外的额外状态）。 */
    @JvmStatic
    fun isInstalled(context: Context, modelId: String?): Boolean {
        val approved = byId(modelId) ?: return false
        val file = modelFile(context, approved)
        return file.isFile && file.length() > 0
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
                "0.14B", 0.14, "Q4_K_M", "100 MB", a.sizeBytes,
                (a.contextLength / 1024).toString() + "K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("英文"), a.license,
                "经批准的最小真实 GGUF 模型，用于本地推理链路验收。",
                ModelInfo.COMPAT_RECOMMENDED, "", 0, 0.0, 0,
                "2026-08-23", 0, true))
        }
        return result
    }
}
