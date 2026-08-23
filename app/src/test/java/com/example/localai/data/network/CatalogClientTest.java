package com.example.localai.data.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.localai.fixtures.FixtureHttpServer;
import com.example.localai.fixtures.FixtureKit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * CatalogClient 协议测试：目录/Manifest 拉取与签名验证、404/篡改拒绝。
 */
public class CatalogClientTest {

    private FixtureHttpServer server;
    private FixtureKit.TestKeys keys;

    @Before
    public void setUp() throws Exception {
        server = new FixtureHttpServer();
        keys = FixtureKit.newTestKeys("catalog-test-key");
    }

    @After
    public void tearDown() {
        server.close();
    }

    private CatalogClient client() {
        return new CatalogClient(server.baseUrl(), keys.trustStore);
    }

    @Test
    public void fetchCatalog_validSignature() throws Exception {
        byte[] catalog = FixtureKit.catalogJson(new String[][]{
                {"m1", "1.0", "Model One"},
                {"m2", "1.0", "Model Two"}
        });
        server.asset("/v1/catalog.json", catalog);
        server.asset("/v1/catalog.sig", FixtureKit.sigFile(keys, catalog));

        Catalog result = client().fetchCatalog();
        assertEquals(2, result.models.size());
        assertEquals("m1", result.models.get(0).modelId);
    }

    @Test
    public void fetchCatalog_tamperedCatalog_rejected() throws Exception {
        byte[] catalog = FixtureKit.catalogJson(new String[][]{{"m1", "1.0", "Model One"}});
        byte[] tampered = catalog.clone();
        tampered[10] ^= 0x01;
        server.asset("/v1/catalog.json", tampered);
        server.asset("/v1/catalog.sig", FixtureKit.sigFile(keys, catalog));

        try {
            client().fetchCatalog();
            fail("expected CatalogException");
        } catch (CatalogException e) {
            assertEquals(CatalogException.Code.BAD_SIGNATURE, e.code());
        }
    }

    @Test
    public void fetchCatalog_missingResource_notFound() throws Exception {
        try {
            client().fetchCatalog();
            fail("expected CatalogException");
        } catch (CatalogException e) {
            assertEquals(CatalogException.Code.NOT_FOUND, e.code());
        }
    }

    @Test
    public void fetchManifest_valid() throws Exception {
        String modelId = "m1";
        String version = "1.0";
        byte[] payload = FixtureKit.ggufPayload(modelId, "qwen2", 4096);
        byte[] manifest = FixtureKit.manifestJson(modelId, version, "Model One", "qwen2", "pub",
                "Apache-2.0", "model.gguf", payload.length, FixtureKit.sha256Hex(payload),
                FixtureKit.filePath(modelId, version, "model.gguf"));
        server.asset(FixtureKit.manifestPath(modelId, version), manifest);
        server.asset(FixtureKit.manifestSigPath(modelId, version), FixtureKit.sigFile(keys, manifest));

        ModelManifest result = client().fetchManifest(modelId, version);
        assertEquals("Model One", result.displayName);
        assertEquals("qwen2", result.architecture);
        assertEquals(payload.length, result.primaryFile().sizeBytes);
        assertEquals(FixtureKit.sha256Hex(payload), result.primaryFile().sha256);
    }

    @Test
    public void fetchManifest_tamperedSig_rejected() throws Exception {
        String modelId = "m1";
        String version = "1.0";
        byte[] payload = FixtureKit.ggufPayload(modelId, "qwen2", 4096);
        byte[] manifest = FixtureKit.manifestJson(modelId, version, "Model One", "qwen2", "pub",
                "Apache-2.0", "model.gguf", payload.length, FixtureKit.sha256Hex(payload),
                FixtureKit.filePath(modelId, version, "model.gguf"));
        byte[] sig = FixtureKit.sigFile(keys, manifest);
        sig[10] ^= 0x01;
        server.asset(FixtureKit.manifestPath(modelId, version), manifest);
        server.asset(FixtureKit.manifestSigPath(modelId, version), sig);

        try {
            client().fetchManifest(modelId, version);
            fail("expected CatalogException");
        } catch (CatalogException e) {
            assertEquals(CatalogException.Code.BAD_SIGNATURE, e.code());
        }
    }

    @Test
    public void fetchManifest_schemaInvalid_rejected() throws Exception {
        String modelId = "m1";
        String version = "1.0";
        byte[] payload = FixtureKit.ggufPayload(modelId, "qwen2", 4096);
        // 缺少 sha256 的 manifest（结构非法）
        byte[] manifest = FixtureKit.manifestJson(modelId, version, "Model One", "qwen2", "pub",
                "Apache-2.0", "model.gguf", payload.length, "not-a-sha",
                FixtureKit.filePath(modelId, version, "model.gguf"));
        server.asset(FixtureKit.manifestPath(modelId, version), manifest);
        server.asset(FixtureKit.manifestSigPath(modelId, version), FixtureKit.sigFile(keys, manifest));

        try {
            client().fetchManifest(modelId, version);
            fail("expected CatalogException");
        } catch (CatalogException e) {
            assertEquals(CatalogException.Code.SCHEMA_INVALID, e.code());
        }
    }

    @Test
    public void resolveUrl_relativePath() {
        CatalogClient client = client();
        assertEquals(server.baseUrl() + "/v1/a", client.resolveUrl("/v1/a"));
        assertEquals("https://cdn.example.com/x", client.resolveUrl("https://cdn.example.com/x"));
    }

    @Test
    public void manifestBundle_carriesRawBytes() throws Exception {
        String modelId = "m1";
        String version = "1.0";
        byte[] payload = FixtureKit.ggufPayload(modelId, "qwen2", 4096);
        byte[] manifest = FixtureKit.manifestJson(modelId, version, "Model One", "qwen2", "pub",
                "Apache-2.0", "model.gguf", payload.length, FixtureKit.sha256Hex(payload),
                FixtureKit.filePath(modelId, version, "model.gguf"));
        server.asset(FixtureKit.manifestPath(modelId, version), manifest);
        server.asset(FixtureKit.manifestSigPath(modelId, version), FixtureKit.sigFile(keys, manifest));

        CatalogClient.ManifestBundle bundle = client().fetchManifestBundle(modelId, version);
        assertNotNull(bundle.manifest);
        assertTrue(bundle.json.length > 0);
        assertTrue(bundle.sig.length > 0);
    }
}
