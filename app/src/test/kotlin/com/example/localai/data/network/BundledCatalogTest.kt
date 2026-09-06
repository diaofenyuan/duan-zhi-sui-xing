package com.example.localai.data.network

import com.example.localai.core.inference.ApprovedModels
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 与 APK 使用同一批资产，离线检查签名、固定版本、运行参数和许可证完整性。 */
class BundledCatalogTest {
    private val assets = File("src/main/assets")
    private fun client(mutate: (String, ByteArray) -> ByteArray = { _, bytes -> bytes }) =
        CatalogClient("https://huggingface.co", OkHttpClient(), TrustedKeys.get()) { path ->
            mutate(path, File(assets, "catalog$path").readBytes())
        }

    @Test fun allBundledModelsAreSignedAndRunnableWithoutCatalogServer() {
        val client = client()
        val entries = client.fetchCatalog().models!!
        assertTrue(entries.isNotEmpty())
        for (entry in entries) {
            val manifest = client.fetchManifest(entry.modelId!!, entry.version!!)
            val approved = ApprovedModels.byId(entry.modelId)!!
            assertTrue(manifest.isApproved())
            assertEquals("chatml", manifest.chatTemplate)
            assertEquals(approved.contextLength.toLong(), manifest.contextLength)
            assertEquals(approved.version, manifest.version)
            assertEquals(approved.fileName, manifest.primaryFile()!!.name)
            assertEquals(approved.sizeBytes, manifest.primaryFile()!!.sizeBytes)
            assertEquals(null, manifest.validate())
            for (url in manifest.primaryFile()!!.urls!!) {
                assertTrue(url.startsWith("https://"))
                assertTrue(url.contains("/resolve/9217f5db79a29953eb74d5343926648285ec7e67/"))
            }
        }
        assertTrue(File(assets, "licenses/Qwen2.5-LICENSE.txt").readText().contains("Apache License"))
        assertNull(TrustedKeys.get().publicKey("release-2026-01-dev"))
    }

    @Test fun modifiedBundledManifestIsRejected() {
        val client = client { path, bytes ->
            if (path.endsWith("manifest.json")) bytes + byteArrayOf(32) else bytes
        }
        val error = assertThrows(CatalogException::class.java) {
            client.fetchManifest(ApprovedModels.QWEN_05B.modelId, ApprovedModels.QWEN_05B.version)
        }
        assertEquals(CatalogException.Code.BAD_SIGNATURE, error.code())
    }

    @Test fun pathTraversalIsRejectedBeforeReadingAssets() {
        val error = assertThrows(CatalogException::class.java) { client().fetchManifest("../licenses", "..") }
        assertEquals(CatalogException.Code.SCHEMA_INVALID, error.code())
    }
}
