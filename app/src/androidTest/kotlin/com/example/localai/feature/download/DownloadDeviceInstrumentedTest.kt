package com.example.localai.feature.download

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.data.network.CatalogConfig
import com.example.localai.data.room.AppDatabase
import com.example.localai.data.room.DownloadEntity
import com.example.localai.data.room.DownloadState
import com.example.localai.data.storage.ModelStorageManager
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 使用当前设备的网络和私有文件系统，测试目录独立于用户模型与数据库。 */
@RunWith(AndroidJUnit4::class)
class DownloadDeviceInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withDownloads(test: (DownloadCoordinator, AppDatabase, ModelStorageManager,
                                    java.util.concurrent.ExecutorService, java.util.concurrent.ExecutorService) -> Unit) {
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "download-device-").toFile()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val worker = Executors.newSingleThreadExecutor()
        val control = Executors.newSingleThreadExecutor()
        val http = OkHttpClient()
        val storage = ModelStorageManager(root)
        val coordinator = DownloadCoordinator(db.downloadDao(), db.modelDao(), CatalogConfig.create(context, http),
            storage, http, worker, control, null)
        try {
            test(coordinator, db, storage, worker, control)
        } finally {
            coordinator.shutdown()
            assertTrue(worker.awaitTermination(15, TimeUnit.SECONDS))
            assertTrue(control.awaitTermination(15, TimeUnit.SECONDS))
            db.close()
            root.deleteRecursively()
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (!condition()) {
            assertTrue(message, System.nanoTime() < deadline)
            Thread.sleep(20)
        }
    }

    @Test fun httpsDownload_canPauseImmediatelyResumeAndCancel() = withDownloads { coordinator, db, storage, worker, control ->
        coordinator.start(ApprovedModels.QWEN_05B.modelId) { ok, message -> assertTrue(message, ok) }
        await("真实 HTTPS 下载没有开始") {
            val row = db.downloadDao().visible().firstOrNull()
            assertFalse(row?.lastError, row?.state == DownloadState.FAILED)
            row?.state == DownloadState.DOWNLOADING && row.bytesDownloaded > 0
        }
        val id = db.downloadDao().visible().first().taskId
        coordinator.pause(id)
        control.submit {}.get(5, TimeUnit.SECONDS)
        assertEquals(DownloadState.PAUSED, db.downloadDao().getById(id).state)
        val beforeResume = storage.partFile(id).length()
        coordinator.resume(id)
        await("暂停后恢复未继续传输") {
            val row = db.downloadDao().getById(id)
            assertFalse(row?.lastError, row?.state == DownloadState.FAILED)
            row?.state == DownloadState.DOWNLOADING && row.bytesDownloaded > beforeResume + 512 * 1024
        }
        coordinator.cancel(id)
        control.submit {}.get(5, TimeUnit.SECONDS)
        worker.submit {}.get(15, TimeUnit.SECONDS)
        assertNull(db.downloadDao().getById(id))
        assertFalse(storage.partFile(id).exists())
        assertTrue(db.modelDao().all().isEmpty())
    }

    @Test fun interruptedInstall_recoversBeforeAndAfterFileCommit() = withDownloads { coordinator, db, storage, worker, control ->
        val model = ApprovedModels.QWEN_05B
        assertTrue("需要先安装真实 Qwen 模型", ApprovedModels.isInstalled(context, model.modelId))
        val bundle = CatalogConfig.create(context, OkHttpClient()).fetchManifestBundle(model.modelId, model.version)
        val file = bundle.manifest.primaryFile()!!
        for (committed in listOf(false, true)) {
            val id = "interrupted-$committed"
            val entity = DownloadEntity(id, model.modelId, model.version, file.name,
                "Qwen", "Qwen", "Q4_K_M", "Apache-2.0", bundle.manifest.parameterCount,
                file.sizeBytes, file.sha256, file.urls!!.first())
            entity.state = DownloadState.INSTALLING
            db.downloadDao().insert(entity)
            storage.persistManifest(id, bundle.json, bundle.sig)
            // 故障注入复用本机权重；部分 Android 文件系统禁止硬链接，需退回复制。
            val part = storage.partFile(id)
            val source = ApprovedModels.modelFile(context, model).toPath()
            try {
                Files.createLink(part.toPath(), source)
            } catch (e: java.io.IOException) {
                Files.copy(source, part.toPath())
            }
            if (committed) storage.install(model.modelId, model.version, part, bundle.json, bundle.sig, file.name!!)
            coordinator.recoverPending()
            control.submit {}.get(5, TimeUnit.SECONDS)
            worker.submit {}.get(60, TimeUnit.SECONDS)
            assertNull("恢复后任务未完成", db.downloadDao().getById(id))
            assertNotNull(db.modelDao().getByModelId(model.modelId))
            assertTrue(storage.isInstalled(model.modelId, model.version))
            assertEquals(file.sizeBytes, storage.modelFile(model.modelId, model.version, file.name!!).length())
        }
        assertTrue("测试不得破坏用户已安装权重", ApprovedModels.isInstalled(context, model.modelId))
    }
}
