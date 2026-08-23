package com.example.localai.data.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.example.localai.fixtures.FixtureKit;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * Manifest 签名验证（S013 四组 + 扩展）：
 * 有效签名、篡改字段、错误 keyId、撤销 key、未知 keyId、不支持算法、sig 文件损坏。
 */
public class ManifestVerifierTest {

    @Test
    public void validSignature_passes() throws Exception {
        FixtureKit.TestKeys keys = FixtureKit.newTestKeys("k1");
        byte[] data = FixtureKit.manifestJson("m1", "1.0", "Model One", "qwen2", "pub",
                "Apache-2.0", "f.gguf", 1024, FixtureKit.sha256Hex(new byte[]{1}), "/v1/f.gguf");
        byte[] sig = FixtureKit.sigFile(keys, data);
        assertEquals(ManifestVerifier.Code.OK,
                ManifestVerifier.verify(data, sig, keys.trustStore));
    }

    @Test
    public void tamperedPayload_rejected() throws Exception {
        FixtureKit.TestKeys keys = FixtureKit.newTestKeys("k2");
        byte[] data = FixtureKit.manifestJson("m1", "1.0", "Model One", "qwen2", "pub",
                "Apache-2.0", "f.gguf", 1024, FixtureKit.sha256Hex(new byte[]{1}), "/v1/f.gguf");
        byte[] sig = FixtureKit.sigFile(keys, data);
        byte[] tampered = data.clone();
        tampered[50] ^= 0x01;
        assertEquals(ManifestVerifier.Code.BAD_SIGNATURE,
                ManifestVerifier.verify(tampered, sig, keys.trustStore));
    }

    @Test
    public void wrongKeyId_rejected() throws Exception {
        FixtureKit.TestKeys keys = FixtureKit.newTestKeys("k3");
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        byte[] sig = FixtureKit.sigFile(keys, data);
        // sig 声明 k3，但 TrustStore 只有 k-other
        FixtureKit.TestKeys other = FixtureKit.newTestKeys("k-other");
        assertEquals(ManifestVerifier.Code.UNKNOWN_KEY_ID,
                ManifestVerifier.verify(data, sig, other.trustStore));
    }

    @Test
    public void revokedKey_rejected() throws Exception {
        FixtureKit.TestKeys keys = FixtureKit.newTestKeys("k4");
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        byte[] sig = FixtureKit.sigFile(keys, data);
        TrustStore revokedStore = new TrustStore() {
            @Override
            public byte[] publicKey(String keyId) {
                return keys.publicKeyBytes;
            }

            @Override
            public boolean isRevoked(String keyId) {
                return true;
            }
        };
        assertEquals(ManifestVerifier.Code.REVOKED_KEY,
                ManifestVerifier.verify(data, sig, revokedStore));
    }

    @Test
    public void unsupportedAlgorithm_rejected() throws Exception {
        FixtureKit.TestKeys keys = FixtureKit.newTestKeys("k5");
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        byte[] sig = ("{\"algorithm\":\"RSA\",\"keyId\":\"k5\",\"value\":\"AAAA\"}")
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(ManifestVerifier.Code.UNSUPPORTED_ALGORITHM,
                ManifestVerifier.verify(data, sig, keys.trustStore));
    }

    @Test
    public void malformedSigFile_rejected() throws Exception {
        FixtureKit.TestKeys keys = FixtureKit.newTestKeys("k6");
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        assertEquals(ManifestVerifier.Code.MALFORMED_SIG,
                ManifestVerifier.verify(data, "{not json".getBytes(StandardCharsets.UTF_8), keys.trustStore));
        assertEquals(ManifestVerifier.Code.MALFORMED_SIG,
                ManifestVerifier.verify(data, "{\"algorithm\":\"Ed25519\"}".getBytes(StandardCharsets.UTF_8),
                        keys.trustStore));
    }

    @Test
    public void okCodeIsUnique() {
        assertNotEquals(ManifestVerifier.Code.OK, ManifestVerifier.Code.BAD_SIGNATURE);
    }
}
