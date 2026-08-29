package com.example.localai.data.network

import com.example.localai.fixtures.FixtureHttpServer
import com.example.localai.fixtures.FixtureKit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** CatalogClient 协议测试：目录/Manifest 拉取与签名验证、404/篡改拒绝。 */
class CatalogClientTest {

    private lateinit var server: FixtureHttpServer
    private lateinit var keys: FixtureKit.TestKeys

    @Before
    fun setUp() {
        server = FixtureHttpServer()
        keys = FixtureKit.newTestKeys("catalog-test-key")
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun client(): CatalogClient = CatalogClient(server.baseUrl(), keys.trustStore)

    @Test
    fun fetchCatalog_validSignature() {
        val catalog = FixtureKit.catalogJson(arrayOf(
            arrayOf("m1", "1.0", "Model One"),
            arrayOf("m2", "1.0", "Model Two")))
        server.asset("/v1/catalog.json", catalog)
        server.asset("/v1/catalog.sig", FixtureKit.sigFile(keys, catalog))

        val result = client().fetchCatalog()
        assertEquals(2, result.models!!.size)
        assertEquals("m1", result.models!![0].modelId)
    }

    @Test
    fun fetchCatalog_tamperedCatalog_rejected() {
        val catalog = FixtureKit.catalogJson(arrayOf(arrayOf("m1", "1.0", "Model One")))
        val tampered = catalog.clone()
        tampered[10] = (tampered[10].toInt() xor 0x01).toByte()
        server.asset("/v1/catalog.json", tampered)
        server.asset("/v1/catalog.sig", FixtureKit.sigFile(keys, catalog))

        try {
            client().fetchCatalog()
            fail("expected CatalogException")
        } catch (e: CatalogException) {
            assertEquals(CatalogException.Code.BAD_SIGNATURE, e.code())
        }
    }

    @Test
    fun fetchCatalog_missingResource_notFound() {
        try {
            client().fetchCatalog()
            fail("expected CatalogException")
        } catch (e: CatalogException) {
            assertEquals(CatalogException.Code.NOT_FOUND, e.code())
        }
    }

    @Test
    fun fetchManifest_valid() {
        val modelId = "m1"
        val version = "1.0"
        val payload = FixtureKit.ggufPayload(modelId, "qwen2", 4096)
        val manifest = FixtureKit.manifestJson(modelId, version, "Model One", "qwen2", "pub",
            "Apache-2.0", "model.gguf", payload.size, FixtureKit.sha256Hex(payload),
            FixtureKit.filePath(modelId, version, "model.gguf"))
        server.asset(FixtureKit.manifestPath(modelId, version), manifest)
        server.asset(FixtureKit.manifestSigPath(modelId, version), FixtureKit.sigFile(keys, manifest))

        val result = client().fetchManifest(modelId, version)
        assertEquals("Model One", result.displayName)
        assertEquals("qwen2", result.architecture)
        assertEquals(payload.size.toLong(), result.primaryFile()!!.sizeBytes)
        assertEquals(FixtureKit.sha256Hex(payload), result.primaryFile()!!.sha256)
    }

    @Test
    fun fetchManifest_tamperedSig_rejected() {
        val modelId = "m1"
        val version = "1.0"
        val payload = FixtureKit.ggufPayload(modelId, "qwen2", 4096)
        val manifest = FixtureKit.manifestJson(modelId, version, "Model One", "qwen2", "pub",
            "Apache-2.0", "model.gguf", payload.size, FixtureKit.sha256Hex(payload),
            FixtureKit.filePath(modelId, version, "model.gguf"))
        val sig = FixtureKit.sigFile(keys, manifest)
        sig[10] = (sig[10].toInt() xor 0x01).toByte()
        server.asset(FixtureKit.manifestPath(modelId, version), manifest)
        server.asset(FixtureKit.manifestSigPath(modelId, version), sig)

        try {
            client().fetchManifest(modelId, version)
            fail("expected CatalogException")
        } catch (e: CatalogException) {
            assertEquals(CatalogException.Code.BAD_SIGNATURE, e.code())
        }
    }

    @Test
    fun fetchManifest_schemaInvalid_rejected() {
        val modelId = "m1"
        val version = "1.0"
        val payload = FixtureKit.ggufPayload(modelId, "qwen2", 4096)
        // 缺少 sha256 的 manifest（结构非法）
        val manifest = FixtureKit.manifestJson(modelId, version, "Model One", "qwen2", "pub",
            "Apache-2.0", "model.gguf", payload.size, "not-a-sha",
            FixtureKit.filePath(modelId, version, "model.gguf"))
        server.asset(FixtureKit.manifestPath(modelId, version), manifest)
        server.asset(FixtureKit.manifestSigPath(modelId, version), FixtureKit.sigFile(keys, manifest))

        try {
            client().fetchManifest(modelId, version)
            fail("expected CatalogException")
        } catch (e: CatalogException) {
            assertEquals(CatalogException.Code.SCHEMA_INVALID, e.code())
        }
    }

    @Test
    fun resolveUrl_relativePath() {
        val client = client()
        assertEquals(server.baseUrl() + "/v1/a", client.resolveUrl("/v1/a"))
        assertEquals("https://cdn.example.com/x", client.resolveUrl("https://cdn.example.com/x"))
    }

    @Test
    fun manifestBundle_carriesRawBytes() {
        val modelId = "m1"
        val version = "1.0"
        val payload = FixtureKit.ggufPayload(modelId, "qwen2", 4096)
        val manifest = FixtureKit.manifestJson(modelId, version, "Model One", "qwen2", "pub",
            "Apache-2.0", "model.gguf", payload.size, FixtureKit.sha256Hex(payload),
            FixtureKit.filePath(modelId, version, "model.gguf"))
        server.asset(FixtureKit.manifestPath(modelId, version), manifest)
        server.asset(FixtureKit.manifestSigPath(modelId, version), FixtureKit.sigFile(keys, manifest))

        val bundle = client().fetchManifestBundle(modelId, version)
        assertNotNull(bundle.manifest)
        assertTrue(bundle.json.isNotEmpty())
        assertTrue(bundle.sig.isNotEmpty())
    }
}
