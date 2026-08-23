package com.example.localai.data.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.localai.fixtures.FixtureKit;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Random;

/**
 * Ed25519 验证器测试：
 * RFC 8032 官方测试向量 + 与 JDK 17 原生 Ed25519 交叉验证（含篡改负例）。
 */
public class Ed25519Test {

    @Test
    public void rfc8032_vector1_emptyMessage() {
        byte[] pub = FixtureKitHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] sig = FixtureKitHex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
                + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
        assertTrue(Ed25519.verify(pub, new byte[0], sig));
    }

    @Test
    public void jdkCrossValidation_randomCases() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        Signature jdk = Signature.getInstance("Ed25519");
        Random rnd = new Random(42);
        for (int i = 0; i < 15; i++) {
            KeyPair kp = gen.generateKeyPair();
            byte[] msg = new byte[rnd.nextInt(500)];
            rnd.nextBytes(msg);
            jdk.initSign(kp.getPrivate());
            jdk.update(msg);
            byte[] sig = jdk.sign();
            byte[] spki = kp.getPublic().getEncoded();
            byte[] rawPub = Arrays.copyOfRange(spki, spki.length - 32, spki.length);

            assertTrue("case " + i + " valid signature rejected",
                    Ed25519.verify(rawPub, msg, sig));

            byte[] tamperedSig = sig.clone();
            tamperedSig[rnd.nextInt(64)] ^= (byte) (1 << rnd.nextInt(8));
            assertFalse("case " + i + " tampered signature accepted",
                    Ed25519.verify(rawPub, msg, tamperedSig));

            if (msg.length > 0) {
                byte[] tamperedMsg = msg.clone();
                tamperedMsg[rnd.nextInt(msg.length)] ^= 0x5A;
                assertFalse("case " + i + " tampered message accepted",
                        Ed25519.verify(rawPub, tamperedMsg, sig));
            }
        }
    }

    @Test
    public void rejectsMalformedInputs() {
        byte[] pub = new byte[32];
        byte[] sig = new byte[64];
        assertFalse(Ed25519.verify(pub, new byte[0], sig)); // 全零公钥：解码失败
        assertFalse(Ed25519.verify(new byte[31], new byte[0], sig));
        assertFalse(Ed25519.verify(pub, new byte[0], new byte[63]));
        assertFalse(Ed25519.verify(null, new byte[0], sig));
        assertFalse(Ed25519.verify(pub, null, sig));
    }

    @Test
    public void rejectsNonCanonicalS() {
        // 通过 FixtureKit 拿一个有效签名后篡改 S 为 >= L 的值
        byte[] pub = new byte[32];
        byte[] sig = new byte[64];
        // S 全 0xFF（> L）：直接构造（公钥/消息任意，S 检查应先行拒绝）
        Arrays.fill(sig, (byte) 0xFF);
        Arrays.fill(pub, (byte) 1);
        assertFalse(Ed25519.verify(pub, new byte[0], sig));
    }

    @Test
    public void appVerifierAcceptsFixtureKitSignature() throws Exception {
        // 与测试夹具签名（JDK Ed25519）互操作：App 侧验证器必须接受
        FixtureKit.TestKeys keys = FixtureKit.newTestKeys("test-key");
        byte[] msg = "hello manifest".getBytes(StandardCharsets.UTF_8);
        byte[] sig = keys.sign(msg);
        assertTrue(Ed25519.verify(keys.publicKeyBytes, msg, sig));
    }

    private static byte[] FixtureKitHex(String hex) {
        return Hex.fromHex(hex);
    }
}
