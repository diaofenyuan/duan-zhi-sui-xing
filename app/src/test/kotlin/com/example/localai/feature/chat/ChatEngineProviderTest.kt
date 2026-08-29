package com.example.localai.feature.chat

import android.content.Context
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.mock.MockChatEngine
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 引擎选择逻辑：批准模型已安装 -> 真实推理；否则演示模式。 */
@RunWith(RobolectricTestRunner::class)
class ChatEngineProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun create_withoutInstalledApprovedModel_returnsMockEngine() {
        val engine = ChatEngineProvider.create(context, "qwen3-4b")
        assertTrue(engine is MockChatEngine)
        assertFalse(engine.isRealInference())
        assertEquals("演示模式", engine.modeLabel())
    }

    @Test
    fun create_withInstalledApprovedModel_returnsRealEngine() {
        val approved = ApprovedModels.SMOLLM_135M
        val modelFile = ApprovedModels.modelFile(context, approved)
        val parent = modelFile.parentFile!!
        assertTrue(parent.exists() || parent.mkdirs())
        FileOutputStream(modelFile).use { out ->
            out.write(ByteArray(16))
        }

        val engine = ChatEngineProvider.create(context, approved.modelId)
        assertTrue(engine is RealChatEngine)
        assertTrue(engine.isRealInference())
        assertEquals("本地推理", engine.modeLabel())

        modelFile.delete()
    }

    @Test
    fun isInstalled_requiresNonEmptyFile() {
        assertFalse(ApprovedModels.isInstalled(context, "smollm-135m-instruct"))
        assertFalse(ApprovedModels.isInstalled(context, "unknown-id"))
    }
}
