package com.example.localai.feature.chat;

import android.content.Context;

import com.example.localai.core.inference.ApprovedModels;
import com.example.localai.mock.MockChatEngine;

/**
 * 对话引擎工厂：当前模型是"已安装的批准模型"时启用真实本地推理，
 * 否则回退演示模式（P1 行为保持不变）。
 */
public final class ChatEngineProvider {

    private ChatEngineProvider() {
    }

    public static ChatEngine create(Context context, String modelId) {
        ApprovedModels.Approved approved = ApprovedModels.byId(modelId);
        if (approved != null && ApprovedModels.isInstalled(context, modelId)) {
            return new RealChatEngine(context, approved);
        }
        return new MockChatEngine();
    }
}
