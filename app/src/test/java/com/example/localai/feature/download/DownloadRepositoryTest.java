package com.example.localai.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.localai.data.network.CatalogClient;
import com.example.localai.data.room.AppDatabase;
import com.example.localai.data.room.DownloadDao;
import com.example.localai.data.room.DownloadEntity;
import com.example.localai.data.room.DownloadState;
import com.example.localai.data.storage.ModelStorageManager;
import com.example.localai.fixtures.FixtureHttpServer;
import com.example.localai.fixtures.FixtureKit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;

/** DownloadRepository 缓存/事件链路测试（排查 UI 不刷新问题用，亦作为回归防护）。 */
@RunWith(RobolectricTestRunner.class)
public class DownloadRepositoryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private AppDatabase db;
    private FixtureHttpServer server;
    private FixtureKit.TestKeys keys;

    @Before
    public void setUp() throws Exception {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
                        AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        server = new FixtureHttpServer();
        keys = FixtureKit.newTestKeys("repo-key");
    }

    @After
    public void tearDown() {
        db.close();
        server.close();
    }

    private DownloadRepository buildRepo() {
        ModelStorageManager storage = new ModelStorageManager(tmp.getRoot());
        CatalogClient client = new CatalogClient(server.baseUrl(), keys.trustStore);
        OkHttpClient http = new OkHttpClient.Builder().build();
        DownloadCoordinator coordinator = new DownloadCoordinator(
                db.downloadDao(), db.modelDao(), client, storage, http,
                Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor(), null);
        DownloadRepository repo = new DownloadRepository(db.downloadDao(), db.modelDao(),
                storage, client, coordinator);
        coordinator.attachListener(repo::onCoordinatorChanged);
        return repo;
    }

    @Test
    public void failedTask_presentInSnapshotAfterLoadInitial() throws Exception {
        DownloadDao dao = db.downloadDao();
        DownloadEntity entity = new DownloadEntity("t-fail", "m1", "1.0", "f.gguf",
                "Model", "pub", "q", "l", 1L, 100, "s", "u");
        entity.state = DownloadState.FAILED;
        entity.lastError = "网络中断：测试";
        dao.insert(entity);

        DownloadRepository repo = buildRepo();
        AtomicInteger downloadsEvents = new AtomicInteger();
        repo.register(new DownloadRepository.Listener() {
            @Override
            public void onDownloadsChanged() {
                downloadsEvents.incrementAndGet();
            }

            @Override
            public void onCatalogChanged() {
            }
        });
        repo.loadInitial();

        long deadline = System.currentTimeMillis() + 10_000;
        while (repo.tasks().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals("FAILED 任务必须出现在任务快照", 1, repo.tasks().size());
        assertEquals(DownloadState.FAILED, repo.tasks().get(0).entity.state);
        assertTrue("必须至少派发一次 onDownloadsChanged", downloadsEvents.get() >= 1);
    }

    @Test
    public void catalogItems_installedFlagReflectsModelDao() throws Exception {
        byte[] payload = FixtureKit.ggufPayload("m1", "qwen2", 64 * 1024);
        byte[] catalog = FixtureKit.catalogJson(new String[][]{{"m1", "1.0", "Model One"}});
        server.asset("/v1/catalog.json", catalog);
        server.asset("/v1/catalog.sig", FixtureKit.sigFile(keys, catalog));
        byte[] manifest = FixtureKit.manifestJson("m1", "1.0", "Model One", "qwen2", "pub",
                "Apache-2.0", "f.gguf", payload.length, FixtureKit.sha256Hex(payload), "/v1/m1/f.gguf");
        server.asset("/v1/models/m1/1.0/manifest.json", manifest);
        server.asset("/v1/models/m1/1.0/manifest.sig", FixtureKit.sigFile(keys, manifest));

        DownloadRepository repo = buildRepo();
        repo.refreshCatalog();
        long deadline = System.currentTimeMillis() + 10_000;
        while (!repo.catalogView().isReady() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(repo.catalogView().isReady());
        assertEquals(1, repo.catalogView().models.size());
        assertEquals("m1", repo.catalogView().models.get(0).modelId);
    }
}
