package com.example.localai.data.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 原子安装/回滚/删除/孤儿清理测试（S017）。 */
class ModelStorageManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storage: ModelStorageManager

    @Before
    fun setUp() {
        storage = ModelStorageManager(tmp.root)
    }

    private fun newPart(taskId: String, content: ByteArray): File {
        val part = storage.partFile(taskId)
        FileOutputStream(part).use { out -> out.write(content) }
        return part
    }

    @Test
    fun failedCommit_keepsVerifiedSourceForRetry() {
        val part = newPart("retry", "verified payload".toByteArray())
        File(tmp.root, "models").mkdirs()
        File(tmp.root, "models/m1").writeText("阻止目标目录创建")
        try {
            storage.install("m1", "1.0", part, "{}".toByteArray(), "sig".toByteArray(), "model.gguf")
            org.junit.Assert.fail("应模拟安装提交失败")
        } catch (expected: java.io.IOException) {
            assertEquals("verified payload", part.readText())
            assertFalse(storage.isInstalled("m1", "1.0"))
        }
    }

    @Test
    fun install_producesCompleteLayout() {
        val manifest = "{\"a\":1}".toByteArray(StandardCharsets.UTF_8)
        val sig = "sig".toByteArray(StandardCharsets.UTF_8)
        val part = newPart("t1", "payload".toByteArray(StandardCharsets.UTF_8))

        storage.install("m1", "1.0", part, manifest, sig, "model.gguf")

        assertTrue(storage.isInstalled("m1", "1.0"))
        val dir = storage.modelDir("m1", "1.0")
        assertTrue(File(dir, "install.ok").exists())
        assertTrue(File(dir, "model.gguf").exists())
        assertEquals("payload", String(Files.readAllBytes(File(dir, "model.gguf").toPath())))
        assertEquals("{\"a\":1}", String(Files.readAllBytes(File(dir, "manifest.json").toPath())))
        // part 已移走
        assertFalse(part.exists())
    }

    @Test
    fun install_sameVersion_replace() {
        val manifest = "m1".toByteArray(StandardCharsets.UTF_8)
        storage.install("m1", "1.0", newPart("t1", "v1".toByteArray(StandardCharsets.UTF_8)),
            manifest, "s".toByteArray(), "model.gguf")
        storage.install("m1", "1.0", newPart("t2", "v2".toByteArray(StandardCharsets.UTF_8)),
            manifest, "s".toByteArray(), "model.gguf")
        val gguf = storage.modelFile("m1", "1.0", "model.gguf")
        assertEquals("v2", String(Files.readAllBytes(gguf.toPath())))
        assertTrue(storage.isInstalled("m1", "1.0"))
    }

    @Test
    fun stagedWithoutInstallOk_notVisible() {
        // 模拟中断：staging 目录存在但无 install.ok 且未 rename
        val staging = File(tmp.root, "models/.staging/interrupted")
        assertTrue(staging.mkdirs())
        FileOutputStream(File(staging, "model.gguf")).use { out ->
            out.write("partial".toByteArray(StandardCharsets.UTF_8))
        }
        assertFalse(storage.isInstalled("m1", "1.0"))

        storage.cleanup()
        assertFalse(staging.exists())
    }

    @Test
    fun cleanup_keepsRollbackWithInstallOk() {
        val rollback = File(tmp.root, "models/.rollback/keepme")
        assertTrue(rollback.mkdirs())
        FileOutputStream(File(rollback, "install.ok")).use { out ->
            out.write("ok".toByteArray(StandardCharsets.UTF_8))
        }
        storage.cleanup()
        assertTrue("含 install.ok 的回滚遗留应保留（可手工恢复旧版本）", rollback.exists())
    }

    @Test
    fun delete_removesModel() {
        val manifest = "m".toByteArray(StandardCharsets.UTF_8)
        storage.install("m1", "1.0", newPart("t1", "payload".toByteArray(StandardCharsets.UTF_8)),
            manifest, "s".toByteArray(), "model.gguf")
        storage.delete("m1", "1.0")
        assertFalse(storage.isInstalled("m1", "1.0"))
        assertFalse(storage.modelDir("m1", "1.0").exists())
    }

    @Test
    fun manifestPersist_roundtrip() {
        val manifest = "manifest-bytes".toByteArray(StandardCharsets.UTF_8)
        val sig = "sig-bytes".toByteArray(StandardCharsets.UTF_8)
        storage.persistManifest("t9", manifest, sig)
        assertEquals("manifest-bytes", String(storage.readManifest("t9"), StandardCharsets.UTF_8))
        assertEquals("sig-bytes", String(storage.readManifestSig("t9"), StandardCharsets.UTF_8))
    }

    @Test
    fun removeDownloadDir_cleansPart() {
        newPart("t10", "x".toByteArray(StandardCharsets.UTF_8))
        storage.removeDownloadDir("t10")
        assertFalse(storage.partFile("t10").exists())
    }
}
