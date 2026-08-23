package com.example.localai.data.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 原子安装/回滚/删除/孤儿清理测试（S017）。 */
public class ModelStorageManagerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ModelStorageManager storage;

    @Before
    public void setUp() {
        storage = new ModelStorageManager(tmp.getRoot());
    }

    private File newPart(String taskId, byte[] content) throws Exception {
        File part = storage.partFile(taskId);
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(content);
        }
        return part;
    }

    @Test
    public void install_producesCompleteLayout() throws Exception {
        byte[] manifest = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] sig = "sig".getBytes(StandardCharsets.UTF_8);
        File part = newPart("t1", "payload".getBytes(StandardCharsets.UTF_8));

        storage.install("m1", "1.0", part, manifest, sig, "model.gguf");

        assertTrue(storage.isInstalled("m1", "1.0"));
        File dir = storage.modelDir("m1", "1.0");
        assertTrue(new File(dir, "install.ok").exists());
        assertTrue(new File(dir, "model.gguf").exists());
        assertEquals("payload", new String(Files.readAllBytes(new File(dir, "model.gguf").toPath())));
        assertEquals("{\"a\":1}", new String(Files.readAllBytes(new File(dir, "manifest.json").toPath())));
        // part 已移走
        assertFalse(part.exists());
    }

    @Test
    public void install_sameVersion_replace() throws Exception {
        byte[] manifest = "m1".getBytes(StandardCharsets.UTF_8);
        storage.install("m1", "1.0", newPart("t1", "v1".getBytes(StandardCharsets.UTF_8)),
                manifest, "s".getBytes(), "model.gguf");
        storage.install("m1", "1.0", newPart("t2", "v2".getBytes(StandardCharsets.UTF_8)),
                manifest, "s".getBytes(), "model.gguf");
        File gguf = storage.modelFile("m1", "1.0", "model.gguf");
        assertEquals("v2", new String(Files.readAllBytes(gguf.toPath())));
        assertTrue(storage.isInstalled("m1", "1.0"));
    }

    @Test
    public void stagedWithoutInstallOk_notVisible() throws Exception {
        // 模拟中断：staging 目录存在但无 install.ok 且未 rename
        File staging = new File(tmp.getRoot(), "models/.staging/interrupted");
        assertTrue(staging.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(staging, "model.gguf"))) {
            out.write("partial".getBytes(StandardCharsets.UTF_8));
        }
        assertFalse(storage.isInstalled("m1", "1.0"));

        storage.cleanup();
        assertFalse(staging.exists());
    }

    @Test
    public void cleanup_keepsRollbackWithInstallOk() throws Exception {
        File rollback = new File(tmp.getRoot(), "models/.rollback/keepme");
        assertTrue(rollback.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(rollback, "install.ok"))) {
            out.write("ok".getBytes(StandardCharsets.UTF_8));
        }
        storage.cleanup();
        assertTrue("含 install.ok 的回滚遗留应保留（可手工恢复旧版本）", rollback.exists());
    }

    @Test
    public void delete_removesModel() throws Exception {
        byte[] manifest = "m".getBytes(StandardCharsets.UTF_8);
        storage.install("m1", "1.0", newPart("t1", "payload".getBytes(StandardCharsets.UTF_8)),
                manifest, "s".getBytes(), "model.gguf");
        storage.delete("m1", "1.0");
        assertFalse(storage.isInstalled("m1", "1.0"));
        assertFalse(storage.modelDir("m1", "1.0").exists());
    }

    @Test
    public void manifestPersist_roundtrip() throws Exception {
        byte[] manifest = "manifest-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] sig = "sig-bytes".getBytes(StandardCharsets.UTF_8);
        storage.persistManifest("t9", manifest, sig);
        assertEquals("manifest-bytes", new String(storage.readManifest("t9"), StandardCharsets.UTF_8));
        assertEquals("sig-bytes", new String(storage.readManifestSig("t9"), StandardCharsets.UTF_8));
    }

    @Test
    public void removeDownloadDir_cleansPart() throws Exception {
        newPart("t10", "x".getBytes(StandardCharsets.UTF_8));
        storage.removeDownloadDir("t10");
        assertFalse(storage.partFile("t10").exists());
    }
}
