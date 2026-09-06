package com.example.localai.feature.chat

import android.content.Context
import com.example.localai.core.inference.ApprovedModels
import java.io.RandomAccessFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 引擎选择逻辑：仅完整安装的支持模型可推理，不能回退模拟回复。 */
@RunWith(RobolectricTestRunner::class)
class ChatEngineProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun create_withoutInstalledApprovedModel_returnsUnavailableEngine() {
        val engine = ChatEngineProvider.create(context, "qwen3-4b")
        assertTrue(engine is UnavailableChatEngine)
        assertFalse(engine.isRealInference())
        assertEquals("模型不可用", engine.modeLabel())
    }

    @Test
    fun create_withInstalledApprovedModel_returnsRealEngine() {
        val approved = ApprovedModels.SMOLLM_135M
        val modelFile = ApprovedModels.modelFile(context, approved)
        val parent = modelFile.parentFile!!
        assertTrue(parent.exists() || parent.mkdirs())
        RandomAccessFile(modelFile, "rw").use { out ->
            out.setLength(approved.sizeBytes)
        }
        assertFalse(ApprovedModels.isInstalled(context, approved.modelId))
        val marker = File(parent, "install.ok").apply { writeText("ok\n") }

        val engine = ChatEngineProvider.create(context, approved.modelId)
        assertTrue(engine is RealChatEngine)
        assertTrue(engine.isRealInference())
        assertEquals("本地推理", engine.modeLabel())

        modelFile.delete()
        marker.delete()
    }

    @Test
    fun isInstalled_requiresNonEmptyFile() {
        assertFalse(ApprovedModels.isInstalled(context, "smollm-135m-instruct"))
        assertFalse(ApprovedModels.isInstalled(context, "unknown-id"))
    }
}
