package com.example.localai.feature.chat

import android.content.Context
import com.example.localai.core.inference.ApprovedModels

/**
 * 对话引擎工厂：当前模型是"已安装的批准模型"时启用真实本地推理，
 * 未安装或不支持的模型返回明确错误，绝不以预设文案冒充模型输出。
 */
object ChatEngineProvider {

    @JvmStatic
    fun create(context: Context, modelId: String?): ChatEngine {
        val approved = ApprovedModels.byId(modelId)
        if (approved != null && ApprovedModels.isInstalled(context, modelId)) {
            return RealChatEngine(context, approved)
        }
        return UnavailableChatEngine()
    }
}
