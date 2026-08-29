package com.example.localai.data.network

import com.example.localai.fixtures.FixtureKit
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Manifest 签名验证（S013 四组 + 扩展）：
 * 有效签名、篡改字段、错误 keyId、撤销 key、未知 keyId、不支持算法、sig 文件损坏。
 */
class ManifestVerifierTest {

    @Test
    fun validSignature_passes() {
        val keys = FixtureKit.newTestKeys("k1")
        val data = FixtureKit.manifestJson("m1", "1.0", "Model One", "qwen2", "pub",
            "Apache-2.0", "f.gguf", 1024, FixtureKit.sha256Hex(byteArrayOf(1)), "/v1/f.gguf")
        val sig = FixtureKit.sigFile(keys, data)
        assertEquals(ManifestVerifier.Code.OK, ManifestVerifier.verify(data, sig, keys.trustStore))
    }

    @Test
    fun tamperedPayload_rejected() {
        val keys = FixtureKit.newTestKeys("k2")
        val data = FixtureKit.manifestJson("m1", "1.0", "Model One", "qwen2", "pub",
            "Apache-2.0", "f.gguf", 1024, FixtureKit.sha256Hex(byteArrayOf(1)), "/v1/f.gguf")
        val sig = FixtureKit.sigFile(keys, data)
        val tampered = data.clone()
        tampered[50] = (tampered[50].toInt() xor 0x01).toByte()
        assertEquals(ManifestVerifier.Code.BAD_SIGNATURE,
            ManifestVerifier.verify(tampered, sig, keys.trustStore))
    }

    @Test
    fun wrongKeyId_rejected() {
        val keys = FixtureKit.newTestKeys("k3")
        val data = "data".toByteArray(StandardCharsets.UTF_8)
        val sig = FixtureKit.sigFile(keys, data)
        // sig 声明 k3，但 TrustStore 只有 k-other
        val other = FixtureKit.newTestKeys("k-other")
        assertEquals(ManifestVerifier.Code.UNKNOWN_KEY_ID,
            ManifestVerifier.verify(data, sig, other.trustStore))
    }

    @Test
    fun revokedKey_rejected() {
        val keys = FixtureKit.newTestKeys("k4")
        val data = "data".toByteArray(StandardCharsets.UTF_8)
        val sig = FixtureKit.sigFile(keys, data)
        val revokedStore = object : TrustStore {
            override fun publicKey(keyId: String?): ByteArray? = keys.publicKeyBytes

            override fun isRevoked(keyId: String?): Boolean = true
        }
        assertEquals(ManifestVerifier.Code.REVOKED_KEY,
            ManifestVerifier.verify(data, sig, revokedStore))
    }

    @Test
    fun unsupportedAlgorithm_rejected() {
        val keys = FixtureKit.newTestKeys("k5")
        val data = "data".toByteArray(StandardCharsets.UTF_8)
        val sig = ("{\"algorithm\":\"RSA\",\"keyId\":\"k5\",\"value\":\"AAAA\"}")
            .toByteArray(StandardCharsets.UTF_8)
        assertEquals(ManifestVerifier.Code.UNSUPPORTED_ALGORITHM,
            ManifestVerifier.verify(data, sig, keys.trustStore))
    }

    @Test
    fun malformedSigFile_rejected() {
        val keys = FixtureKit.newTestKeys("k6")
        val data = "data".toByteArray(StandardCharsets.UTF_8)
        assertEquals(ManifestVerifier.Code.MALFORMED_SIG,
            ManifestVerifier.verify(data, "{not json".toByteArray(StandardCharsets.UTF_8), keys.trustStore))
        assertEquals(ManifestVerifier.Code.MALFORMED_SIG,
            ManifestVerifier.verify(data, "{\"algorithm\":\"Ed25519\"}".toByteArray(StandardCharsets.UTF_8),
                keys.trustStore))
    }

    @Test
    fun okCodeIsUnique() {
        assertNotEquals(ManifestVerifier.Code.OK, ManifestVerifier.Code.BAD_SIGNATURE)
    }
}
