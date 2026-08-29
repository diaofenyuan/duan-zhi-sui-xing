package com.example.localai.feature.download

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localai.data.network.CatalogClient
import com.example.localai.data.room.AppDatabase
import com.example.localai.data.room.DownloadDao
import com.example.localai.data.room.DownloadEntity
import com.example.localai.data.room.DownloadState
import com.example.localai.data.storage.ModelStorageManager
import com.example.localai.fixtures.FixtureHttpServer
import com.example.localai.fixtures.FixtureKit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** DownloadRepository 缓存/事件链路测试（排查 UI 不刷新问题用，亦作为回归防护）。 */
@RunWith(RobolectricTestRunner::class)
class DownloadRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var server: FixtureHttpServer
    private lateinit var keys: FixtureKit.TestKeys

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = FixtureHttpServer()
        keys = FixtureKit.newTestKeys("repo-key")
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun buildRepo(): DownloadRepository {
        val storage = ModelStorageManager(tmp.root)
        val client = CatalogClient(server.baseUrl(), keys.trustStore)
        val http = OkHttpClient.Builder().build()
        val coordinator = DownloadCoordinator(
            db.downloadDao(), db.modelDao(), client, storage, http,
            Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor(), null)
        val repo = DownloadRepository(db.downloadDao(), db.modelDao(),
            storage, client, coordinator)
        coordinator.attachListener { repo.onCoordinatorChanged() }
        return repo
    }

    @Test
    fun failedTask_presentInSnapshotAfterLoadInitial() {
        val dao: DownloadDao = db.downloadDao()
        val entity = DownloadEntity("t-fail", "m1", "1.0", "f.gguf",
            "Model", "pub", "q", "l", 1L, 100L, "s", "u")
        entity.state = DownloadState.FAILED
        entity.lastError = "网络中断：测试"
        dao.insert(entity)

        val repo = buildRepo()
        val downloadsEvents = AtomicInteger()
        repo.register(object : DownloadRepository.Listener {
            override fun onDownloadsChanged() {
                downloadsEvents.incrementAndGet()
            }

            override fun onCatalogChanged() {
            }
        })
        repo.loadInitial()

        val deadline = System.currentTimeMillis() + 10_000
        while (repo.tasks().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals("FAILED 任务必须出现在任务快照", 1, repo.tasks().size)
        assertEquals(DownloadState.FAILED, repo.tasks()[0].entity.state)
        assertTrue("必须至少派发一次 onDownloadsChanged", downloadsEvents.get() >= 1)
    }

    @Test
    fun catalogItems_installedFlagReflectsModelDao() {
        val payload = FixtureKit.ggufPayload("m1", "qwen2", 64 * 1024)
        val catalog = FixtureKit.catalogJson(arrayOf(arrayOf("m1", "1.0", "Model One")))
        server.asset("/v1/catalog.json", catalog)
        server.asset("/v1/catalog.sig", FixtureKit.sigFile(keys, catalog))
        val manifest = FixtureKit.manifestJson("m1", "1.0", "Model One", "qwen2", "pub",
            "Apache-2.0", "f.gguf", payload.size, FixtureKit.sha256Hex(payload), "/v1/m1/f.gguf")
        server.asset("/v1/models/m1/1.0/manifest.json", manifest)
        server.asset("/v1/models/m1/1.0/manifest.sig", FixtureKit.sigFile(keys, manifest))

        val repo = buildRepo()
        repo.refreshCatalog()
        val deadline = System.currentTimeMillis() + 10_000
        while (!repo.catalogView().isReady() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(repo.catalogView().isReady())
        assertEquals(1, repo.catalogView().models.size)
        assertEquals("m1", repo.catalogView().models[0].modelId)
    }
}
