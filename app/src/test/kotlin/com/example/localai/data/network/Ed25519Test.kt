package com.example.localai.data.network

import com.example.localai.fixtures.FixtureKit
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Arrays
import java.util.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ed25519 验证器测试：
 * RFC 8032 官方测试向量 + 与 JDK 17 原生 Ed25519 交叉验证（含篡改负例）。
 */
class Ed25519Test {

    @Test
    fun rfc8032_vector1_emptyMessage() {
        val pub = Hex.fromHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val sig = Hex.fromHex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        assertTrue(Ed25519.verify(pub, ByteArray(0), sig))
    }

    @Test
    fun jdkCrossValidation_randomCases() {
        val gen = KeyPairGenerator.getInstance("Ed25519")
        val jdk = Signature.getInstance("Ed25519")
        val rnd = Random(42)
        for (i in 0 until 15) {
            val kp = gen.generateKeyPair()
            val msg = ByteArray(rnd.nextInt(500))
            rnd.nextBytes(msg)
            jdk.initSign(kp.private)
            jdk.update(msg)
            val sig = jdk.sign()
            val spki = kp.public.encoded
            val rawPub = Arrays.copyOfRange(spki, spki.size - 32, spki.size)

            assertTrue("case $i valid signature rejected", Ed25519.verify(rawPub, msg, sig))

            val tamperedSig = sig.clone()
            val sigIdx = rnd.nextInt(64)
            tamperedSig[sigIdx] = (tamperedSig[sigIdx].toInt() xor (1 shl rnd.nextInt(8))).toByte()
            assertFalse("case $i tampered signature accepted", Ed25519.verify(rawPub, msg, tamperedSig))

            if (msg.isNotEmpty()) {
                val tamperedMsg = msg.clone()
                val msgIdx = rnd.nextInt(msg.size)
                tamperedMsg[msgIdx] = (tamperedMsg[msgIdx].toInt() xor 0x5A).toByte()
                assertFalse("case $i tampered message accepted", Ed25519.verify(rawPub, tamperedMsg, sig))
            }
        }
    }

    @Test
    fun rejectsMalformedInputs() {
        val pub = ByteArray(32)
        val sig = ByteArray(64)
        assertFalse(Ed25519.verify(pub, ByteArray(0), sig)) // 全零公钥：解码失败
        assertFalse(Ed25519.verify(ByteArray(31), ByteArray(0), sig))
        assertFalse(Ed25519.verify(pub, ByteArray(0), ByteArray(63)))
        assertFalse(Ed25519.verify(null, ByteArray(0), sig))
        assertFalse(Ed25519.verify(pub, null, sig))
    }

    @Test
    fun rejectsNonCanonicalS() {
        // S 全 0xFF（> L）：直接构造（公钥/消息任意，S 检查应先行拒绝）
        val pub = ByteArray(32)
        val sig = ByteArray(64)
        Arrays.fill(sig, 0xFF.toByte())
        Arrays.fill(pub, 1.toByte())
        assertFalse(Ed25519.verify(pub, ByteArray(0), sig))
    }

    @Test
    fun appVerifierAcceptsFixtureKitSignature() {
        // 与测试夹具签名（JDK Ed25519）互操作：App 侧验证器必须接受
        val keys = FixtureKit.newTestKeys("test-key")
        val msg = "hello manifest".toByteArray(StandardCharsets.UTF_8)
        val sig = keys.sign(msg)
        assertTrue(Ed25519.verify(keys.publicKeyBytes, msg, sig))
    }
}
