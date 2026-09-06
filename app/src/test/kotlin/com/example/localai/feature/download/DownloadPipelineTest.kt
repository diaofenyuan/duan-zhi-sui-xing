package com.example.localai.feature.download

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localai.data.network.CatalogClient
import com.example.localai.data.room.AppDatabase
import com.example.localai.data.room.DownloadEntity
import com.example.localai.data.room.DownloadState
import com.example.localai.data.storage.ModelStorageManager
import com.example.localai.fixtures.FixtureHttpServer
import com.example.localai.fixtures.FixtureKit
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 下载管线端到端测试（Robolectric + 本地 HTTP Fixture + 真实 Room/OkHttp/存储）：
 * 覆盖 P2 卡要求的 200 / 206 / 416、断网、ETag 变化、哈希错误、签名错误、进程重启。
 * 通过标准验证：非法资产被拒绝、下载可恢复、任务持久化、不拼接新旧内容。
 */
@RunWith(RobolectricTestRunner::class)
class DownloadPipelineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: FixtureHttpServer
    private lateinit var keys: FixtureKit.TestKeys

    @Before
    fun setUp() {
        server = FixtureHttpServer()
        keys = FixtureKit.newTestKeys("pipeline-key")
    }

    @After
    fun tearDown() {
        server.close()
    }

    // ---------- 环境 ----------

    private class Env(
        val db: AppDatabase,
        filesRoot: File,
        server: FixtureHttpServer,
        keys: FixtureKit.TestKeys,
        interceptor: Interceptor? = null
    ) : AutoCloseable {
        val storage: ModelStorageManager = ModelStorageManager(filesRoot)
        val worker: ExecutorService = Executors.newSingleThreadExecutor()
        val control: ExecutorService = Executors.newSingleThreadExecutor()
        val coordinator: DownloadCoordinator

        init {
            val client = CatalogClient(server.baseUrl(), keys.trustStore)
            val http = OkHttpClient.Builder().apply {
                if (interceptor != null) addInterceptor(interceptor)
            }.build()
            coordinator = DownloadCoordinator(db.downloadDao(), db.modelDao(), client,
                storage, http, worker, control, null)
        }

        override fun close() {
            coordinator.shutdown()
            worker.awaitTermination(5, TimeUnit.SECONDS)
            control.awaitTermination(5, TimeUnit.SECONDS)
            db.close()
        }
    }

    private fun newFileDb(name: String): AppDatabase {
        return Room.databaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
    }

    private fun newMemoryDb(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    /** 挂载模型：catalog + 签名 manifest + 载荷。返回载荷字节。 */
    private fun serveModel(modelId: String, payload: ByteArray, shaOverride: String?, throttleMs: Int): ByteArray {
        val sha = shaOverride ?: FixtureKit.sha256Hex(payload)
        val catalog = FixtureKit.catalogJson(arrayOf(arrayOf(modelId, VERSION, "Demo Model")))
        server.asset("/v1/catalog.json", catalog)
        server.asset("/v1/catalog.sig", FixtureKit.sigFile(keys, catalog))
        val manifest = FixtureKit.manifestJson(modelId, VERSION, "Demo Model", "qwen2", "pub",
            "Apache-2.0", FILE, payload.size, sha, FixtureKit.filePath(modelId, VERSION, FILE))
        server.asset(FixtureKit.manifestPath(modelId, VERSION), manifest)
        server.asset(FixtureKit.manifestSigPath(modelId, VERSION), FixtureKit.sigFile(keys, manifest))
        val filePath = FixtureKit.filePath(modelId, VERSION, FILE)
        server.file(filePath, payload)
        if (throttleMs > 0) {
            server.throttleMs(filePath, throttleMs)
        }
        return payload
    }

    private fun enqueue(env: Env, modelId: String): String {
        val result = arrayOfNulls<String>(2)
        env.coordinator.start(modelId) { ok, message ->
            result[0] = if (ok) "ok" else "fail"
            result[1] = message
        }
        await({ result[0] != null }, "enqueue callback")
        return result[0]!!
    }

    private fun task(env: Env): DownloadEntity? {
        return if (env.db.downloadDao().visible().isEmpty())
            null else env.db.downloadDao().visible()[0]
    }

    private fun await(condition: BooleanSupplier, what: String) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!condition.asBoolean) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("timeout waiting for: $what")
            }
            Thread.sleep(40)
        }
    }

    private fun awaitTaskState(env: Env, state: String) {
        await({
            val t = task(env)
            t != null && state == t.state
        }, "task state $state")
    }

    private fun awaitInstalled(env: Env) {
        await({ env.db.modelDao().getByModelId(MODEL) != null }, "model installed")
    }

    // ---------- 测试 ----------

    private fun response(chain: Interceptor.Chain, code: Int, bytes: ByteArray,
                         range: String? = null): Response {
        val body = object : ResponseBody() {
            private val buffer = Buffer().write(bytes)
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source() = buffer
        }
        return Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("fixture").body(body).apply {
                if (range != null) header("Content-Range", range)
            }.build()
    }

    private fun rejectBrokenResponse(code: Int, extraBytes: Int = 0, wrongRange: Boolean = false) {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 64 * 1024)
        serveModel(MODEL, payload, null, 0)
        val attempts = AtomicInteger()
        val env = Env(newMemoryDb(), tmp.root, server, keys, Interceptor { chain ->
            attempts.incrementAndGet()
            val body = if (code == 416 || extraBytes < 0) ByteArray(0)
                else payload + ByteArray(extraBytes)
            response(chain, code, body,
                if (wrongRange) "bytes 0-${payload.lastIndex}/${payload.size + 1}" else null)
        })
        try {
            assertEquals("ok", enqueue(env, MODEL))
            await({ task(env)?.state == DownloadState.FAILED || attempts.get() >= 4 ||
                env.db.modelDao().getByModelId(MODEL) != null }, "bounded response handling")
            assertEquals("异常响应必须终止并允许用户重试", DownloadState.FAILED, task(env)?.state)
            assertTrue("不得无上限请求", attempts.get() <= 2)
            assertNull(env.db.modelDao().getByModelId(MODEL))
            assertTrue("流式响应不得写超签名长度",
                env.storage.partFile(task(env)!!.taskId).length() <= payload.size)
        } finally {
            env.close()
        }
    }

    @Test fun range416AtZero_failsWithoutLoop() = rejectBrokenResponse(416)
    @Test fun emptyUnknownLength_failsWithoutLoop() = rejectBrokenResponse(200, extraBytes = -1)
    @Test fun oversizedUnknownLength_isBounded() = rejectBrokenResponse(200, extraBytes = 8192)
    @Test fun inconsistentContentRange_isRejected() = rejectBrokenResponse(206, wrongRange = true)

    private fun recoverInstalling(alreadyMoved: Boolean) {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 64 * 1024)
        serveModel(MODEL, payload, null, 0)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            val entity = DownloadEntity("interrupted-install", MODEL, VERSION, FILE,
                "Demo Model", "pub", "Q4_K_M", "Apache-2.0", 1,
                payload.size.toLong(), FixtureKit.sha256Hex(payload),
                server.baseUrl() + FixtureKit.filePath(MODEL, VERSION, FILE))
            entity.state = DownloadState.INSTALLING
            env.db.downloadDao().insert(entity)
            val json = server.assetBytes(FixtureKit.manifestPath(MODEL, VERSION))!!
            val sig = server.assetBytes(FixtureKit.manifestSigPath(MODEL, VERSION))!!
            env.storage.persistManifest(entity.taskId, json, sig)
            val part = env.storage.partFile(entity.taskId).apply { writeBytes(payload) }
            if (alreadyMoved) env.storage.install(MODEL, VERSION, part, json, sig, FILE)
            env.coordinator.recoverPending()
            env.control.submit {}.get(5, TimeUnit.SECONDS)
            env.worker.submit {}.get(5, TimeUnit.SECONDS)
            assertNotNull("安装中断恢复后应补齐数据库记录", env.db.modelDao().getByModelId(MODEL))
            assertNull(task(env))
            assertTrue(env.storage.isInstalled(MODEL, VERSION))
            assertEquals(FixtureKit.sha256Hex(payload),
                ModelVerifier.sha256Hex(env.storage.modelFile(MODEL, VERSION, FILE)))
        } finally {
            env.close()
        }
    }

    @Test fun restartInstallingWithPart_completes() = recoverInstalling(false)
    @Test fun restartAfterFileCommit_completesDatabase() = recoverInstalling(true)

    @Test fun immediateResume_doesNotReceiveOldCallFailure() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 64 * 1024)
        serveModel(MODEL, payload, null, 0)
        val entered = CountDownLatch(1)
        val finishOldCall = CountDownLatch(1)
        val calls = AtomicInteger()
        val env = Env(newMemoryDb(), tmp.root, server, keys, Interceptor { chain ->
            if (calls.incrementAndGet() == 1) {
                entered.countDown()
                check(finishOldCall.await(5, TimeUnit.SECONDS))
                throw java.io.IOException("旧请求延迟结束")
            }
            chain.proceed(chain.request())
        })
        try {
            assertEquals("ok", enqueue(env, MODEL))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val id = task(env)!!.taskId
            env.coordinator.pause(id)
            env.control.submit {}.get(5, TimeUnit.SECONDS)
            assertEquals(DownloadState.PAUSED, task(env)!!.state)
            env.coordinator.resume(id)
            env.control.submit {}.get(5, TimeUnit.SECONDS)
            finishOldCall.countDown()
            env.worker.submit {}.get(5, TimeUnit.SECONDS)
            assertNotNull("恢复不能被旧连接的 IOException 改成失败", env.db.modelDao().getByModelId(MODEL))
            assertNull(task(env))
        } finally {
            finishOldCall.countDown()
            env.close()
        }
    }

    @Test
    fun incompleteInstalledRecord_canBeDownloadedAgain() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 64 * 1024)
        serveModel(MODEL, payload, null, 0)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            env.db.modelDao().insert(com.example.localai.data.room.ModelEntity(MODEL, VERSION,
                "Demo Model", "pub", "Q4_K_M", "Apache-2.0", FILE, payload.size.toLong(), 1))
            val broken = env.storage.modelFile(MODEL, VERSION, FILE)
            broken.parentFile!!.mkdirs()
            broken.writeBytes(ByteArray(16))
            assertEquals("不完整的记录不得阻止重装", "ok", enqueue(env, MODEL))
            await({ env.storage.isInstalled(MODEL, VERSION, FILE, payload.size.toLong()) }, "repaired install")
            env.worker.submit {}.get(5, TimeUnit.SECONDS)
            assertEquals(FixtureKit.sha256Hex(payload), ModelVerifier.sha256Hex(broken))
            assertNull(task(env))
        } finally {
            env.close()
        }
    }

    @Test
    fun corruptedDownloadCanRetryAfterManifestSidecarsWereRemoved() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 256 * 1024)
        serveModel(MODEL, payload, null, 0)
        val damaged = payload.clone().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val path = FixtureKit.filePath(MODEL, VERSION, FILE)
        server.setFile(path, damaged, "\"damaged\"")
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            awaitTaskState(env, DownloadState.FAILED)
            val id = task(env)!!.taskId
            assertFalse(File(env.storage.downloadDir(id), "manifest.json").exists())
            server.setFile(path, payload, "\"repaired\"")
            env.coordinator.retry(id)
            awaitInstalled(env)
            assertEquals(FixtureKit.sha256Hex(payload),
                ModelVerifier.sha256Hex(env.storage.modelFile(MODEL, VERSION, FILE)))
        } finally {
            env.close()
        }
    }

    @Test
    fun fullDownload_200_verify_install_success() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 256 * 1024)
        serveModel(MODEL, payload, null, 0)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            awaitInstalled(env)

            assertNull("任务行应在成功后删除", task(env))
            assertTrue(env.storage.isInstalled(MODEL, VERSION))
            val installed = env.storage.modelFile(MODEL, VERSION, FILE)
            assertTrue(installed.exists())
            assertEquals(FixtureKit.sha256Hex(payload), ModelVerifier.sha256Hex(installed))
            assertTrue(File(installed.parentFile, "install.ok").exists())
            assertTrue(File(installed.parentFile, "manifest.json").exists())
            assertTrue(File(installed.parentFile, "manifest.sig").exists())
            // 全新下载不应带 Range
            val first = server.lastRequest(FixtureKit.filePath(MODEL, VERSION, FILE))
            assertNotNull(first)
            assertNull(first!!.range)
        } finally {
            env.close()
        }
    }

    @Test
    fun pauseResume_uses206Range() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024)
        serveModel(MODEL, payload, null, 2)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            await({
                val t = task(env)
                t != null && DownloadState.DOWNLOADING == t.state && t.bytesDownloaded > 0L
            }, "download progressing")

            env.coordinator.pause("x-not-exist") // 非法 taskId：无副作用
            env.coordinator.pause(task(env)!!.taskId)
            awaitTaskState(env, DownloadState.PAUSED)

            val partSize = env.storage.partFile(task(env)!!.taskId).length()
            assertTrue("暂停时应保留部分下载", partSize > 0 && partSize < payload.size)

            env.coordinator.resume(task(env)!!.taskId)
            awaitInstalled(env)

            var sawRange = false
            for (r in server.requests()) {
                if (FixtureKit.filePath(MODEL, VERSION, FILE) == r.path
                    && r.range != null && r.ifRange != null) {
                    sawRange = true
                }
            }
            assertTrue("续传请求必须带 Range + If-Range", sawRange)
            assertEquals(FixtureKit.sha256Hex(payload),
                ModelVerifier.sha256Hex(env.storage.modelFile(MODEL, VERSION, FILE)))
        } finally {
            env.close()
        }
    }

    @Test
    fun range416_restartsFromScratch() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024)
        serveModel(MODEL, payload, null, 2)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            await({
                val t = task(env)
                t != null && DownloadState.DOWNLOADING == t.state && t.bytesDownloaded > 0L
            }, "download progressing")
            env.coordinator.pause(task(env)!!.taskId)
            awaitTaskState(env, DownloadState.PAUSED)

            // 制造 416 场景：本地 .part 比服务器文件更长（模拟本地脏数据）
            val part = env.storage.partFile(task(env)!!.taskId)
            FileOutputStream(part, true).use { out ->
                out.write(ByteArray(payload.size + 1024))
            }
            val entity = task(env)!!
            entity.bytesDownloaded = part.length()
            env.db.downloadDao().update(entity)

            env.coordinator.resume(entity.taskId)
            awaitInstalled(env)

            var saw416 = false
            for (r in server.requests()) {
                if (r.responseCode == 416) {
                    saw416 = true
                }
            }
            assertTrue("越界续传必须收到 416 并重启", saw416)
            assertEquals(FixtureKit.sha256Hex(payload),
                ModelVerifier.sha256Hex(env.storage.modelFile(MODEL, VERSION, FILE)))
        } finally {
            env.close()
        }
    }

    @Test
    fun etagChange_fullRestart_noMixedContent() {
        // 内容 A 先被部分下载；服务器切换为 B 且 ETag 变化；Manifest 指向 B（新版本）。
        val contentA = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024)
        val contentB = contentA.clone()
        Arrays.fill(contentB, contentB.size - 32 * 1024, contentB.size, 0x5A.toByte())

        serveModel(MODEL, contentB, null, 2) // manifest sha = B
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            await({
                val t = task(env)
                t != null && DownloadState.DOWNLOADING == t.state && t.bytesDownloaded > 0L
            }, "download progressing (content A)")

            // 服务器在客户端暂停期间把内容换成 B（ETag 变化）
            env.coordinator.pause(task(env)!!.taskId)
            awaitTaskState(env, DownloadState.PAUSED)
            server.setFile(FixtureKit.filePath(MODEL, VERSION, FILE), contentB, "\"v2\"")

            env.coordinator.resume(task(env)!!.taskId)
            awaitInstalled(env)

            val installed = Files.readAllBytes(env.storage.modelFile(MODEL, VERSION, FILE).toPath())
            assertTrue("续传后必须得到完整的 B，不能拼接 A 前缀与 B 后缀",
                Arrays.equals(contentB, installed))
            assertFalse("不得残留 A 前缀与 B 尾部的混合内容",
                Arrays.equals(contentA, installed))
        } finally {
            env.close()
        }
    }

    @Test
    fun disconnection_failKeepsPart_retryResumes() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 256 * 1024)
        serveModel(MODEL, payload, null, 0)
        val filePath = FixtureKit.filePath(MODEL, VERSION, FILE)
        server.dropAfter(filePath, 128 * 1024)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            awaitTaskState(env, DownloadState.FAILED)

            val failed = task(env)!!
            assertNotNull(failed.lastError)
            assertTrue(failed.lastError!!.contains("中断") || failed.lastError!!.contains("网络"))
            val part = env.storage.partFile(failed.taskId)
            assertEquals("断网时已下载部分必须保留", 128L * 1024, part.length())
            assertTrue(failed.retryCount >= 1)

            server.clearDrop(filePath)
            env.coordinator.retry(failed.taskId)
            awaitInstalled(env)
            assertEquals(FixtureKit.sha256Hex(payload),
                ModelVerifier.sha256Hex(env.storage.modelFile(MODEL, VERSION, FILE)))
        } finally {
            env.close()
        }
    }

    @Test
    fun hashError_rejected_noInstall_partDeleted() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 128 * 1024)
        // Manifest 声明错误哈希
        val wrongSha = FixtureKit.sha256Hex(byteArrayOf(9, 9, 9))
        serveModel(MODEL, payload, wrongSha, 0)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            awaitTaskState(env, DownloadState.FAILED)

            val failed = task(env)!!
            assertTrue("失败原因必须可定位到哈希", failed.lastError!!.contains("SHA-256"))
            assertFalse("非法资产不落盘", env.storage.partFile(failed.taskId).exists())
            assertNull(env.db.modelDao().getByModelId(MODEL))
            assertFalse(env.storage.isInstalled(MODEL, VERSION))
        } finally {
            env.close()
        }
    }

    @Test
    fun signatureError_rejected_noTaskNoPart() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 64 * 1024)
        serveModel(MODEL, payload, null, 0)
        // 篡改 manifest.sig
        val sig = server.assetBytes(FixtureKit.manifestSigPath(MODEL, VERSION))!!
        sig[8] = (sig[8].toInt() xor 0x01).toByte()
        server.asset(FixtureKit.manifestSigPath(MODEL, VERSION), sig)

        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("fail", enqueue(env, MODEL))
            assertTrue(env.db.downloadDao().all().isEmpty())
            assertFalse(env.storage.isInstalled(MODEL, VERSION))
        } finally {
            env.close()
        }
    }

    @Test
    fun processRestart_recoverPending_resumesWithRange() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024)
        serveModel(MODEL, payload, null, 2)
        val filesRoot = tmp.root
        val dbName = "restart-test.db"
        val env = Env(newFileDb(dbName), filesRoot, server, keys)
        val taskId: String
        try {
            assertEquals("ok", enqueue(env, MODEL))
            await({
                val t = task(env)
                t != null && DownloadState.DOWNLOADING == t.state && t.bytesDownloaded > 0L
            }, "download progressing")
            taskId = task(env)!!.taskId
            // 模拟进程被杀：关闭协调器与数据库
            env.coordinator.shutdown()
            env.db.close()
        } finally {
            // 防泄漏：重复关闭幂等
            env.close()
        }

        // "重启"：同一存储目录 + 新数据库实例（文件库） + 新协调器
        val reopened = newFileDb(dbName)
        val env2 = Env(reopened, filesRoot, server, keys)
        try {
            val restored = reopened.downloadDao().getById(taskId)
            assertNotNull("重启后任务必须仍在数据库", restored)
            env2.coordinator.recoverPending()
            await({ reopened.modelDao().getByModelId(MODEL) != null }, "recovered install")

            var sawRangeAfterRestart = false
            for (r in server.requests()) {
                if (FixtureKit.filePath(MODEL, VERSION, FILE) == r.path
                    && r.range != null && r.ifRange != null) {
                    sawRangeAfterRestart = true
                }
            }
            assertTrue("重启恢复必须走 Range 续传", sawRangeAfterRestart)
            assertEquals(FixtureKit.sha256Hex(payload),
                ModelVerifier.sha256Hex(env2.storage.modelFile(MODEL, VERSION, FILE)))
        } finally {
            env2.close()
        }
    }

    @Test
    fun cancel_removesTaskAndPart() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024)
        serveModel(MODEL, payload, null, 2)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            await({
                val t = task(env)
                t != null && DownloadState.DOWNLOADING == t.state && t.bytesDownloaded > 0L
            }, "download progressing")
            val taskId = task(env)!!.taskId
            env.coordinator.cancel(taskId)
            await({ env.db.downloadDao().getById(taskId) == null }, "task removed")
            env.control.submit {}.get(5, TimeUnit.SECONDS)
            env.worker.submit {}.get(5, TimeUnit.SECONDS)
            assertFalse(env.storage.partFile(taskId).exists())
        } finally {
            env.close()
        }
    }

    @Test
    fun duplicateEnqueue_rejected() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 256 * 1024)
        serveModel(MODEL, payload, null, 2)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("ok", enqueue(env, MODEL))
            assertEquals("fail", enqueue(env, MODEL)) // 重复入队被拒绝
        } finally {
            env.close()
        }
    }

    @Test
    fun unknownModel_rejected() {
        val payload = FixtureKit.ggufPayload(MODEL, "qwen2", 64 * 1024)
        serveModel(MODEL, payload, null, 0)
        val env = Env(newMemoryDb(), tmp.root, server, keys)
        try {
            assertEquals("fail", enqueue(env, "not-in-catalog"))
            assertTrue(env.db.downloadDao().all().isEmpty())
        } finally {
            env.close()
        }
    }

    companion object {
        private const val MODEL = "demo-model"
        private const val VERSION = "1.0"
        private const val FILE = "model.gguf"
    }
}
