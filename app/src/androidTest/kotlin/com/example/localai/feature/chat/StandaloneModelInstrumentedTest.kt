package com.example.localai.feature.chat

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.core.inference.InferenceClient
import com.example.localai.core.inference.InferenceRequest
import com.example.localai.core.inference.NativeSession
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.DownloadState
import com.example.localai.feature.download.ModelVerifier
import com.example.localai.model.ChatMessage
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 显式启用的网络验收：走产品仓库下载官方模型，不通过 adb 手工安装模型。 */
@RunWith(AndroidJUnit4::class)
class StandaloneModelInstrumentedTest {
    @Test fun signedCatalogDownloadsAndGeneratesChinese() {
        assumeTrue("仅在明确执行模型下载验收时启用", InstrumentationRegistry.getArguments().getString("allowModelDownload") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (InstrumentationRegistry.getArguments().getString("requireOffline") == "true") {
            val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
            assertNull("离线验收时仍存在默认网络", connectivity.activeNetwork)
        }
        val model = ApprovedModels.QWEN_05B
        ServiceLocator.init(context)
        val repository = ServiceLocator.downloads()!!
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10)
        while (!repository.catalogView().isReady() && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue(repository.catalogView().isReady())
        assertTrue(repository.catalogView().models.any { it.modelId == model.modelId && it.isApproved() })
        if (!ApprovedModels.isInstalled(context, model.modelId)) {
            val queued = CountDownLatch(1)
            val queueError = AtomicReference<String?>()
            repository.enqueue(model.modelId) { ok, message ->
                if (!ok) queueError.set(message)
                queued.countDown()
            }
            assertTrue(queued.await(30, TimeUnit.SECONDS))
            assertNull(queueError.get())
            var lastStatus = 0L
            while (!ApprovedModels.isInstalled(context, model.modelId) && System.nanoTime() < deadline) {
                val task = repository.tasks().firstOrNull { it.entity.modelId == model.modelId }?.entity
                assertFalse("下载失败：${task?.lastError}", task?.state == DownloadState.FAILED)
                val now = System.nanoTime()
                if (now - lastStatus > TimeUnit.SECONDS.toNanos(10)) {
                    instrumentation.sendStatus(2, Bundle().apply {
                        putString("stream", "model download bytes=${task?.bytesDownloaded ?: 0}, state=${task?.state}\n")
                    })
                    lastStatus = now
                }
                Thread.sleep(250)
            }
        }
        assertTrue("模型没有完成安装", ApprovedModels.isInstalled(context, model.modelId))
        val file = ApprovedModels.modelFile(context, model)
        assertEquals("74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db", ModelVerifier.sha256Hex(file))
        assertEquals("qwen2", ModelVerifier.probeGguf(file).architecture)

        val ready = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val error = AtomicReference<String?>()
        val answer = StringBuilder()
        val client = InferenceClient(context)
        try {
            client.connect(InferenceRequest("standalone", model.modelId, model.version, file.absolutePath,
                model.contextLength, 4, 0.3f, 0.9f, 80), object : InferenceClient.Events {
                override fun onStateChanged(state: Int) { if (state == InferenceClient.STATE_READY) ready.countDown() }
                override fun onToken(batch: String) { answer.append(batch) }
                override fun onFinished(reason: Int) {
                    if (reason != NativeSession.FINISH_END) error.set("unexpected finish=$reason")
                    finished.countDown()
                }
                override fun onError(code: Int, message: String) {
                    error.set("$code: $message")
                    ready.countDown()
                    finished.countDown()
                }
            })
            assertTrue(ready.await(90, TimeUnit.SECONDS))
            assertNull(error.get())
            client.start(RealChatEngine.buildPrompt(listOf(ChatMessage(ChatMessage.ROLE_USER, "请用一句中文介绍你能做什么。"))))
            assertTrue(finished.await(180, TimeUnit.SECONDS))
            assertNull(error.get())
            assertTrue("没有生成中文内容", answer.toString().any { it in '\u4e00'..'\u9fff' })
        } finally {
            client.release()
        }
    }
}
