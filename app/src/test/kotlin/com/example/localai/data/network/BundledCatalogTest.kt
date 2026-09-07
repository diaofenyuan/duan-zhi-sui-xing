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
        assertEquals(8, entries.size)
        for (entry in entries) {
            val manifest = client.fetchManifest(entry.modelId!!, entry.version!!)
            val approved = ApprovedModels.byId(entry.modelId)!!
            assertTrue(manifest.isApproved())
            assertEquals("chatml", manifest.chatTemplate)
            assertEquals(approved.maxContextLength.toLong(), manifest.contextLength)
            assertEquals(approved.quantization, manifest.quantization)
            assertEquals(approved.version, manifest.version)
            assertEquals(approved.fileName, manifest.primaryFile()!!.name)
            assertEquals(approved.sizeBytes, manifest.primaryFile()!!.sizeBytes)
            assertEquals(null, manifest.validate())
            for (url in manifest.primaryFile()!!.urls!!) {
                assertTrue(url.startsWith("https://"))
                val revision = manifest.source!!.url!!.substringAfterLast("/tree/")
                assertTrue(revision.matches(Regex("[0-9a-f]{40}")))
                assertTrue(url.contains("/resolve/$revision/"))
            }
        }
        assertTrue(File(assets, "licenses/Qwen2.5-LICENSE.txt").readText().contains("Apache License"))
        assertNull(TrustedKeys.get().publicKey("release-2026-01-dev"))
    }

    @Test fun bundledCodeModelsAppearInCodeFilter() {
        val client = client()
        val catalog = com.example.localai.feature.download.DownloadRepository.CatalogView().apply {
            models = client.fetchCatalog().models!!.map { entry ->
                val m = client.fetchManifest(entry.modelId!!, entry.version!!)
                com.example.localai.feature.download.DownloadRepository.CatalogItem(
                    m.modelId, m.version, m.displayName, m.description, m.source?.publisher,
                    m.license?.spdx, m.license?.url, m.source?.url,
                    m.primaryFile()!!.sizeBytes, m.quantization, m.parameterCount, m.contextLength,
                    m.tasks, m.languages, m.weightStatus, m.chatTemplate, m.updatedAt,
                    m.runtime?.minAndroidApi ?: 0, m.runtime?.abis, false)
            }
        }
        val models = com.example.localai.feature.market.MarketModels.map(catalog,
            com.example.localai.core.compatibility.CompatibilityEngine.DeviceSnapshot(
                36, "arm64-v8a", 64L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024))
        val code = com.example.localai.mock.Filters.apply(models, "",
            com.example.localai.model.ModelInfo.TASK_CODE, "ALL", "ALL")
        assertEquals(4, code.size)
        assertTrue(code.all { it.name.contains("Coder") })
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
