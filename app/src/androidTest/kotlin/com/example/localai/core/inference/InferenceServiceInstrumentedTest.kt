package com.example.localai.core.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT_SEC = 90L

/**
 * P3 仪器化验收：独立推理进程的错误回调、重复 stop/release、
 * 服务杀死后的 ENGINE_CRASHED 与重启恢复。
 * 真实模型用例依赖批准模型文件已安装到设备（qa/fixtures/approved-model.json），
 * 文件缺失时以 Assume 跳过。
 */
@RunWith(AndroidJUnit4::class)
class InferenceServiceInstrumentedTest {

    private lateinit var context: Context
    private var client: InferenceClient? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        client?.let {
            it.release()
            client = null
        }
    }

    /** 收集事件 + 门闩；回调全部在主线程派发，单线程追加安全。 */
    private class Recorder : InferenceClient.Events {
        val ready = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val error = CountDownLatch(1)
        val tokenLatch = CountDownLatch(1)
        val tokens = StringBuilder()
        val finishReason = AtomicInteger(-1)
        val errorCode = AtomicInteger(-1)
        val errorMessage = AtomicReference("")

        override fun onStateChanged(state: Int) {
            if (state == InferenceClient.STATE_READY) {
                ready.countDown()
            }
        }

        override fun onToken(batch: String) {
            tokens.append(batch)
            tokenLatch.countDown()
        }

        override fun onFinished(reason: Int) {
            finishReason.set(reason)
            finished.countDown()
        }

        override fun onError(code: Int, message: String) {
            errorCode.set(code)
            errorMessage.set(message)
            error.countDown()
        }

        fun awaitFinished(): Boolean = finished.await(TIMEOUT_SEC, TimeUnit.SECONDS)
    }

    private fun requestFor(path: String): InferenceRequest {
        return InferenceRequest("test", "test-model", "test",
            path, 512, 4, 0.7f, 0.9f, 64)
    }

    /**
     * 与产品路径（RealChatEngine.buildPrompt）一致的 ChatML 格式提示。
     * 裸提示对指令模型不稳定（可能直接输出 EOS），测试统一走模板。
     */
    private fun chatml(user: String): String {
        return "<|im_start|>user\n" + user + "<|im_end|>\n<|im_start|>assistant\n"
    }

    private fun approvedRequest(): InferenceRequest {
        return ApprovedModels.requestFor(context, ApprovedModels.SMOLLM_135M, "test-approved")
    }

    /** 限定输出上限的批准模型请求：模拟器性能波动下仍能在时限内结束。 */
    private fun smallApprovedRequest(maxNewTokens: Int): InferenceRequest {
        val a = ApprovedModels.SMOLLM_135M
        return InferenceRequest("test-approved", a.modelId, a.version,
            ApprovedModels.modelFile(context, a).absolutePath,
            a.contextLength, a.threadCount, a.temperature, a.topP, maxNewTokens)
    }

    private fun assumeApprovedInstalled() {
        Assume.assumeTrue("批准模型文件未安装，跳过（qa/fixtures/approved-model.json）",
            ApprovedModels.isInstalled(context, ApprovedModels.SMOLLM_135M.modelId))
    }

    private fun writeGarbageModel(): File {
        val dir = File(context.filesDir, "models/bad/1")
        assertTrue(dir.exists() || dir.mkdirs())
        val file = File(dir, "bad.gguf")
        FileOutputStream(file).use { out ->
            val junk = ByteArray(32 * 1024)
            for (i in junk.indices) {
                junk[i] = (i * 31).toByte()
            }
            out.write(junk)
        }
        return file
    }

    /** 独立绑定一次服务读取其进程 pid（同 UID 杀死推理进程用）。 */
    private fun readServicePid(): Int {
        val pidHolder = intArrayOf(-1)
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    try {
                        pidHolder[0] = IInferenceService.Stub.asInterface(binder).getPid()
                    } catch (ignored: Exception) {
                        // keep -1
                    }
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                }
            }
        val ok = context.bindService(
            Intent(context, InferenceService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!ok) {
            return -1
        }
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) return -1
            return pidHolder[0]
        } finally {
            context.unbindService(connection)
        }
    }

    @Test
    fun invalidModelFile_returnsModelLoadFailedCode() {
        val junk = writeGarbageModel()
        client = InferenceClient(context)
        val recorder = Recorder()
        client!!.connect(requestFor(junk.absolutePath), recorder)

        assertTrue("expected structured error", recorder.error.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        assertEquals(NativeSession.ERR_MODEL_LOAD_FAILED, recorder.errorCode.get())
        assertTrue(recorder.errorMessage.get().contains("模型"))
    }

    @Test
    fun pathOutsideFilesDir_rejectedWithIllegalArgument() {
        client = InferenceClient(context)
        val recorder = Recorder()
        client!!.connect(requestFor("/sdcard/Download/outside.gguf"), recorder)

        assertTrue(recorder.error.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        assertEquals(NativeSession.ERR_ILLEGAL_ARGUMENT, recorder.errorCode.get())
    }

    @Test
    fun serviceRunsInSeparateProcess() {
        assumeApprovedInstalled()
        client = InferenceClient(context)
        val recorder = Recorder()
        client!!.connect(smallApprovedRequest(64), recorder)
        assertTrue(recorder.ready.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        val servicePid = readServicePid()
        assertTrue("service pid not found", servicePid > 0)
        assertNotEquals(Process.myPid(), servicePid)
    }

    @Test
    fun realModel_generateThenStopMidStream_thenRepeatedStopRelease() {
        assumeApprovedInstalled()
        client = InferenceClient(context)
        val first = Recorder()
        client!!.connect(smallApprovedRequest(48), first)
        assertTrue(first.ready.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        client!!.start(chatml("Hello, introduce yourself briefly."))
        assertTrue(first.awaitFinished())
        assertEquals(NativeSession.FINISH_END, first.finishReason.get())
        assertTrue("expected streamed tokens", first.tokens.length > 0)

        val second = Recorder()
        client!!.setEvents(second)
        client!!.start(chatml("Keep talking, list everything you know, do not stop."))
        assertTrue("expected at least one token", second.tokenLatch.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        client!!.stop()
        assertTrue(second.awaitFinished())
        assertEquals(NativeSession.FINISH_STOPPED, second.finishReason.get())

        client!!.stop()    // 未运行时 stop：WRONG_STATE 路径，不崩溃
        client!!.stop()    // 重复 stop
        client!!.release() // 释放
        client!!.release() // 重复释放幂等
    }

    @Test
    fun realModel_serviceKill_engineCrashed_thenRestartRecovers() {
        assumeApprovedInstalled()
        client = InferenceClient(context)
        val first = Recorder()
        client!!.connect(smallApprovedRequest(48), first)
        assertTrue(first.ready.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        client!!.start(chatml("Say something."))
        assertTrue(first.awaitFinished())

        val pid = readServicePid()
        assertTrue("service pid not found", pid > 0)
        val crash = Recorder()
        client!!.setEvents(crash)
        Process.killProcess(pid) // 先注册事件再杀进程，避免快设备漏接死亡回调。
        assertTrue("expected ENGINE_CRASHED", crash.error.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        assertEquals(InferenceClient.ERR_ENGINE_CRASHED, crash.errorCode.get())

        val recovery = Recorder()
        client!!.restart(smallApprovedRequest(48), recovery)
        assertTrue("expected restart to reload model",
            recovery.ready.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        client!!.start(chatml("Are you back? Reply yes."))
        assertTrue(recovery.awaitFinished())
        assertEquals(NativeSession.FINISH_END, recovery.finishReason.get())
        assertTrue(recovery.tokens.length > 0)
    }

    @Test
    fun realModel_repeatedGenerateCycles_doNotCrash() {
        assumeApprovedInstalled()
        client = InferenceClient(context)
        val recorder = Recorder()
        client!!.connect(smallApprovedRequest(64), recorder)
        assertTrue(recorder.ready.await(TIMEOUT_SEC, TimeUnit.SECONDS))
        for (i in 0 until 3) {
            val cycle = Recorder()
            client!!.setEvents(cycle)
            client!!.start(chatml("Cycle " + i))
            assertTrue("cycle " + i + " did not finish", cycle.awaitFinished())
        }
        val stats = client!!.getStats()
        assertTrue(stats != null && stats.genTokens > 0)
        client!!.release()
        client!!.release()
    }
}
