package com.example.localai.feature.chat

import android.content.Context
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.mock.MockChatEngine

/**
 * 对话引擎工厂：当前模型是"已安装的批准模型"时启用真实本地推理，
 * 否则回退演示模式（P1 行为保持不变）。
 */
object ChatEngineProvider {

    @JvmStatic
    fun create(context: Context, modelId: String?): ChatEngine {
        val approved = ApprovedModels.byId(modelId)
        if (approved != null && ApprovedModels.isInstalled(context, modelId)) {
            return RealChatEngine(context, approved)
        }
        return MockChatEngine()
    }
}
