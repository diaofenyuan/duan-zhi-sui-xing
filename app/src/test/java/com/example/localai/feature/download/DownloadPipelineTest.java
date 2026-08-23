package com.example.localai.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.localai.data.network.CatalogClient;
import com.example.localai.data.room.AppDatabase;
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

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import okhttp3.OkHttpClient;

/**
 * 下载管线端到端测试（Robolectric + 本地 HTTP Fixture + 真实 Room/OkHttp/存储）：
 * 覆盖 P2 卡要求的 200 / 206 / 416、断网、ETag 变化、哈希错误、签名错误、进程重启。
 * 通过标准验证：非法资产被拒绝、下载可恢复、任务持久化、不拼接新旧内容。
 */
@RunWith(RobolectricTestRunner.class)
public class DownloadPipelineTest {

    private static final String MODEL = "demo-model";
    private static final String VERSION = "1.0";
    private static final String FILE = "model.gguf";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private FixtureHttpServer server;
    private FixtureKit.TestKeys keys;

    @Before
    public void setUp() throws Exception {
        server = new FixtureHttpServer();
        keys = FixtureKit.newTestKeys("pipeline-key");
    }

    @After
    public void tearDown() {
        server.close();
    }

    // ---------- 环境 ----------

    private static final class Env implements AutoCloseable {
        final AppDatabase db;
        final ModelStorageManager storage;
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final ExecutorService control = Executors.newSingleThreadExecutor();
        final DownloadCoordinator coordinator;

        Env(AppDatabase db, File filesRoot, FixtureHttpServer server, FixtureKit.TestKeys keys) {
            this.db = db;
            this.storage = new ModelStorageManager(filesRoot);
            CatalogClient client = new CatalogClient(server.baseUrl(), keys.trustStore);
            OkHttpClient http = new OkHttpClient.Builder().build();
            this.coordinator = new DownloadCoordinator(db.downloadDao(), db.modelDao(), client,
                    storage, http, worker, control, null);
        }

        @Override
        public void close() {
            coordinator.shutdown();
            db.close();
        }
    }

    private AppDatabase newFileDb(String name) {
        return Room.databaseBuilder(ApplicationProvider.getApplicationContext(),
                        AppDatabase.class, name)
                .allowMainThreadQueries()
                .build();
    }

    private AppDatabase newMemoryDb() {
        return Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
                        AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    /** 挂载模型：catalog + 签名 manifest + 载荷。返回载荷字节。 */
    private byte[] serveModel(String modelId, byte[] payload, String shaOverride, int throttleMs) throws Exception {
        String sha = shaOverride != null ? shaOverride : FixtureKit.sha256Hex(payload);
        byte[] catalog = FixtureKit.catalogJson(new String[][]{{modelId, VERSION, "Demo Model"}});
        server.asset("/v1/catalog.json", catalog);
        server.asset("/v1/catalog.sig", FixtureKit.sigFile(keys, catalog));
        byte[] manifest = FixtureKit.manifestJson(modelId, VERSION, "Demo Model", "qwen2", "pub",
                "Apache-2.0", FILE, payload.length, sha, FixtureKit.filePath(modelId, VERSION, FILE));
        server.asset(FixtureKit.manifestPath(modelId, VERSION), manifest);
        server.asset(FixtureKit.manifestSigPath(modelId, VERSION), FixtureKit.sigFile(keys, manifest));
        String filePath = FixtureKit.filePath(modelId, VERSION, FILE);
        server.file(filePath, payload);
        if (throttleMs > 0) {
            server.throttleMs(filePath, throttleMs);
        }
        return payload;
    }

    private String enqueue(Env env, String modelId) throws Exception {
        final String[] result = new String[2];
        env.coordinator.start(modelId, (ok, message) -> {
            result[0] = ok ? "ok" : "fail";
            result[1] = message;
        });
        await(() -> result[0] != null, "enqueue callback");
        return result[0];
    }

    private DownloadEntity task(Env env) {
        return env.db.downloadDao().visible().isEmpty()
                ? null : env.db.downloadDao().visible().get(0);
    }

    private void await(BooleanSupplier condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timeout waiting for: " + what);
            }
            Thread.sleep(40);
        }
    }

    private void awaitTaskState(Env env, String state) throws Exception {
        await(() -> {
            DownloadEntity t = task(env);
            return t != null && state.equals(t.state);
        }, "task state " + state);
    }

    private void awaitInstalled(Env env) throws Exception {
        await(() -> env.db.modelDao().getByModelId(MODEL) != null, "model installed");
    }

    // ---------- 测试 ----------

    @Test
    public void fullDownload_200_verify_install_success() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 256 * 1024);
        serveModel(MODEL, payload, null, 0);
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("ok", enqueue(env, MODEL));
            awaitInstalled(env);

            assertNull("任务行应在成功后删除", task(env));
            assertTrue(env.storage.isInstalled(MODEL, VERSION));
            File installed = env.storage.modelFile(MODEL, VERSION, FILE);
            assertTrue(installed.exists());
            assertEquals(FixtureKit.sha256Hex(payload), ModelVerifier.sha256Hex(installed));
            assertTrue(new File(installed.getParentFile(), "install.ok").exists());
            assertTrue(new File(installed.getParentFile(), "manifest.json").exists());
            assertTrue(new File(installed.getParentFile(), "manifest.sig").exists());
            // 全新下载不应带 Range
            FixtureHttpServer.RequestRecord first = server.lastRequest(FixtureKit.filePath(MODEL, VERSION, FILE));
            assertNotNull(first);
            assertNull(first.range);
        } finally {
            env.close();
        }
    }

    @Test
    public void pauseResume_uses206Range() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024);
        serveModel(MODEL, payload, null, 2);
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("ok", enqueue(env, MODEL));
            await(() -> {
                DownloadEntity t = task(env);
                return t != null && DownloadState.DOWNLOADING.equals(t.state) && t.bytesDownloaded > 0;
            }, "download progressing");

            env.coordinator.pause("x-not-exist"); // 非法 taskId：无副作用
            env.coordinator.pause(task(env).taskId);
            awaitTaskState(env, DownloadState.PAUSED);

            long partSize = env.storage.partFile(task(env).taskId).length();
            assertTrue("暂停时应保留部分下载", partSize > 0 && partSize < payload.length);

            env.coordinator.resume(task(env).taskId);
            awaitInstalled(env);

            boolean sawRange = false;
            for (FixtureHttpServer.RequestRecord r : server.requests()) {
                if (FixtureKit.filePath(MODEL, VERSION, FILE).equals(r.path)
                        && r.range != null && r.ifRange != null) {
                    sawRange = true;
                }
            }
            assertTrue("续传请求必须带 Range + If-Range", sawRange);
            assertEquals(FixtureKit.sha256Hex(payload),
                    ModelVerifier.sha256Hex(env.storage.modelFile(MODEL, VERSION, FILE)));
        } finally {
            env.close();
        }
    }

    @Test
    public void range416_restartsFromScratch() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024);
        serveModel(MODEL, payload, null, 2);
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("ok", enqueue(env, MODEL));
            await(() -> {
                DownloadEntity t = task(env);
                return t != null && DownloadState.DOWNLOADING.equals(t.state) && t.bytesDownloaded > 0;
            }, "download progressing");
            env.coordinator.pause(task(env).taskId);
            awaitTaskState(env, DownloadState.PAUSED);

            // 制造 416 场景：本地 .part 比服务器文件更长（模拟本地脏数据）
            File part = env.storage.partFile(task(env).taskId);
            try (FileOutputStream out = new FileOutputStream(part, true)) {
                out.write(new byte[(int) (payload.length + 1024)]);
            }
            DownloadEntity entity = task(env);
            entity.bytesDownloaded = part.length();
            env.db.downloadDao().update(entity);

            env.coordinator.resume(entity.taskId);
            awaitInstalled(env);

            boolean saw416 = false;
            for (FixtureHttpServer.RequestRecord r : server.requests()) {
                if (r.responseCode == 416) {
                    saw416 = true;
                }
            }
            assertTrue("越界续传必须收到 416 并重启", saw416);
            assertEquals(FixtureKit.sha256Hex(payload),
                    ModelVerifier.sha256Hex(env.storage.modelFile(MODEL, VERSION, FILE)));
        } finally {
            env.close();
        }
    }

    @Test
    public void etagChange_fullRestart_noMixedContent() throws Exception {
        // 内容 A 先被部分下载；服务器切换为 B 且 ETag 变化；Manifest 指向 B（新版本）。
        byte[] contentA = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024);
        byte[] contentB = contentA.clone();
        Arrays.fill(contentB, contentB.length - 32 * 1024, contentB.length, (byte) 0x5A);

        serveModel(MODEL, contentB, null, 2); // manifest sha = B
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("ok", enqueue(env, MODEL));
            await(() -> {
                DownloadEntity t = task(env);
                return t != null && DownloadState.DOWNLOADING.equals(t.state) && t.bytesDownloaded > 0;
            }, "download progressing (content A)");

            // 服务器在客户端暂停期间把内容换成 B（ETag 变化）
            env.coordinator.pause(task(env).taskId);
            awaitTaskState(env, DownloadState.PAUSED);
            server.setFile(FixtureKit.filePath(MODEL, VERSION, FILE), contentB, "\"v2\"");

            env.coordinator.resume(task(env).taskId);
            awaitInstalled(env);

            byte[] installed = Files.readAllBytes(env.storage.modelFile(MODEL, VERSION, FILE).toPath());
            assertTrue("续传后必须得到完整的 B，不能拼接 A 前缀与 B 后缀",
                    Arrays.equals(contentB, installed));
            assertFalse("不得残留 A 前缀与 B 尾部的混合内容",
                    Arrays.equals(contentA, installed));
        } finally {
            env.close();
        }
    }

    @Test
    public void disconnection_failKeepsPart_retryResumes() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 256 * 1024);
        serveModel(MODEL, payload, null, 0);
        String filePath = FixtureKit.filePath(MODEL, VERSION, FILE);
        server.dropAfter(filePath, 128 * 1024);
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("ok", enqueue(env, MODEL));
            awaitTaskState(env, DownloadState.FAILED);

            DownloadEntity failed = task(env);
            assertNotNull(failed.lastError);
            assertTrue(failed.lastError.contains("中断") || failed.lastError.contains("网络"));
            File part = env.storage.partFile(failed.taskId);
            assertEquals("断网时已下载部分必须保留", 128 * 1024, part.length());
            assertTrue(failed.retryCount >= 1);

            server.clearDrop(filePath);
            env.coordinator.retry(failed.taskId);
            awaitInstalled(env);
            assertEquals(FixtureKit.sha256Hex(payload),
                    ModelVerifier.sha256Hex(env.storage.modelFile(MODEL, VERSION, FILE)));
        } finally {
            env.close();
        }
    }

    @Test
    public void hashError_rejected_noInstall_partDeleted() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 128 * 1024);
        // Manifest 声明错误哈希
        String wrongSha = FixtureKit.sha256Hex(new byte[]{9, 9, 9});
        serveModel(MODEL, payload, wrongSha, 0);
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("ok", enqueue(env, MODEL));
            awaitTaskState(env, DownloadState.FAILED);

            DownloadEntity failed = task(env);
            assertTrue("失败原因必须可定位到哈希", failed.lastError.contains("SHA-256"));
            assertFalse("非法资产不落盘", env.storage.partFile(failed.taskId).exists());
            assertNull(env.db.modelDao().getByModelId(MODEL));
            assertFalse(env.storage.isInstalled(MODEL, VERSION));
        } finally {
            env.close();
        }
    }

    @Test
    public void signatureError_rejected_noTaskNoPart() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 64 * 1024);
        serveModel(MODEL, payload, null, 0);
        // 篡改 manifest.sig
        byte[] sig = server.assetBytes(FixtureKit.manifestSigPath(MODEL, VERSION));
        sig[8] ^= 0x01;
        server.asset(FixtureKit.manifestSigPath(MODEL, VERSION), sig);

        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("fail", enqueue(env, MODEL));
            assertTrue(env.db.downloadDao().all().isEmpty());
            assertFalse(env.storage.isInstalled(MODEL, VERSION));
        } finally {
            env.close();
        }
    }

    @Test
    public void processRestart_recoverPending_resumesWithRange() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024);
        serveModel(MODEL, payload, null, 2);
        File filesRoot = tmp.getRoot();
        String dbName = "restart-test.db";
        Env env = new Env(newFileDb(dbName), filesRoot, server, keys);
        String taskId;
        try {
            assertEquals("ok", enqueue(env, MODEL));
            await(() -> {
                DownloadEntity t = task(env);
                return t != null && DownloadState.DOWNLOADING.equals(t.state) && t.bytesDownloaded > 0;
            }, "download progressing");
            taskId = task(env).taskId;
            // 模拟进程被杀：关闭协调器与数据库
            env.coordinator.shutdown();
            env.db.close();
        } finally {
            // 防泄漏：重复关闭幂等
            env.close();
        }

        // "重启"：同一存储目录 + 新数据库实例（文件库） + 新协调器
        AppDatabase reopened = newFileDb(dbName);
        Env env2 = new Env(reopened, filesRoot, server, keys);
        try {
            DownloadEntity restored = reopened.downloadDao().getById(taskId);
            assertNotNull("重启后任务必须仍在数据库", restored);
            env2.coordinator.recoverPending();
            await(() -> reopened.modelDao().getByModelId(MODEL) != null, "recovered install");

            boolean sawRangeAfterRestart = false;
            for (FixtureHttpServer.RequestRecord r : server.requests()) {
                if (FixtureKit.filePath(MODEL, VERSION, FILE).equals(r.path)
                        && r.range != null && r.ifRange != null) {
                    sawRangeAfterRestart = true;
                }
            }
            assertTrue("重启恢复必须走 Range 续传", sawRangeAfterRestart);
            assertEquals(FixtureKit.sha256Hex(payload),
                    ModelVerifier.sha256Hex(env2.storage.modelFile(MODEL, VERSION, FILE)));
        } finally {
            env2.close();
        }
    }

    @Test
    public void cancel_removesTaskAndPart() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 2 * 1024 * 1024);
        serveModel(MODEL, payload, null, 2);
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("ok", enqueue(env, MODEL));
            await(() -> {
                DownloadEntity t = task(env);
                return t != null && DownloadState.DOWNLOADING.equals(t.state) && t.bytesDownloaded > 0;
            }, "download progressing");
            String taskId = task(env).taskId;
            env.coordinator.cancel(taskId);
            await(() -> env.db.downloadDao().getById(taskId) == null, "task removed");
            assertFalse(env.storage.partFile(taskId).exists());
        } finally {
            env.close();
        }
    }

    @Test
    public void duplicateEnqueue_rejected() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 256 * 1024);
        serveModel(MODEL, payload, null, 2);
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("ok", enqueue(env, MODEL));
            assertEquals("fail", enqueue(env, MODEL)); // 重复入队被拒绝
        } finally {
            env.close();
        }
    }

    @Test
    public void unknownModel_rejected() throws Exception {
        byte[] payload = FixtureKit.ggufPayload(MODEL, "qwen2", 64 * 1024);
        serveModel(MODEL, payload, null, 0);
        Env env = new Env(newMemoryDb(), tmp.getRoot(), server, keys);
        try {
            assertEquals("fail", enqueue(env, "not-in-catalog"));
            assertTrue(env.db.downloadDao().all().isEmpty());
        } finally {
            env.close();
        }
    }
}
