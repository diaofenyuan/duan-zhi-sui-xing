package com.example.localai.feature.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.example.localai.core.inference.ApprovedModels;
import com.example.localai.mock.MockChatEngine;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;

/** 引擎选择逻辑：批准模型已安装 -> 真实推理；否则演示模式。 */
@RunWith(RobolectricTestRunner.class)
public class ChatEngineProviderTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void create_withoutInstalledApprovedModel_returnsMockEngine() {
        ChatEngine engine = ChatEngineProvider.create(context, "qwen3-4b");
        assertTrue(engine instanceof MockChatEngine);
        assertFalse(engine.isRealInference());
        assertEquals("演示模式", engine.modeLabel());
    }

    @Test
    public void create_withInstalledApprovedModel_returnsRealEngine() throws Exception {
        ApprovedModels.Approved approved = ApprovedModels.SMOLLM_135M;
        File modelFile = ApprovedModels.modelFile(context, approved);
        File parent = modelFile.getParentFile();
        assertTrue(parent.exists() || parent.mkdirs());
        try (FileOutputStream out = new FileOutputStream(modelFile)) {
            out.write(new byte[16]);
        }

        ChatEngine engine = ChatEngineProvider.create(context, approved.modelId);
        assertTrue(engine instanceof RealChatEngine);
        assertTrue(engine.isRealInference());
        assertEquals("本地推理", engine.modeLabel());

        modelFile.delete();
    }

    @Test
    public void isInstalled_requiresNonEmptyFile() {
        assertFalse(ApprovedModels.isInstalled(context, "smollm-135m-instruct"));
        assertFalse(ApprovedModels.isInstalled(context, "unknown-id"));
    }
}
