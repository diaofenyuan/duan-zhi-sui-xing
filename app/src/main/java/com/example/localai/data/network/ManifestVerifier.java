package com.example.localai.data.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Base64;

/**
 * Manifest/Catalog 签名验证（Ed25519，RFC 8032）。
 * 签名协议：签名覆盖数据文件（manifest.json / catalog.json）的原始字节流；
 * sig 侧车文件为 JSON {"algorithm":"Ed25519","keyId":"...","value":"<base64 R||S>"}。
 * 校验顺序：sig 文件可解析 -> 算法受支持 -> keyId 已知 -> 未撤销 -> 签名通过。
 */
public final class ManifestVerifier {

    public static final String ALGORITHM_ED25519 = "Ed25519";

    public enum Code {
        OK,
        MALFORMED_SIG,
        UNSUPPORTED_ALGORITHM,
        UNKNOWN_KEY_ID,
        REVOKED_KEY,
        BAD_SIGNATURE
    }

    private static final Gson GSON = new Gson();

    private ManifestVerifier() {
    }

    /**
     * 验证签名。返回 Code.OK 或具体失败码（失败码可直接展示给 UI）。
     */
    public static Code verify(byte[] payload, byte[] sigFileBytes, TrustStore store) {
        SigFile sig;
        try {
            JsonObject json = JsonParser.parseString(new String(sigFileBytes, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            sig = GSON.fromJson(json, SigFile.class);
        } catch (RuntimeException e) {
            return Code.MALFORMED_SIG;
        }
        if (sig == null || sig.algorithm == null || sig.keyId == null || sig.value == null) {
            return Code.MALFORMED_SIG;
        }
        if (!ALGORITHM_ED25519.equals(sig.algorithm)) {
            return Code.UNSUPPORTED_ALGORITHM;
        }
        byte[] pubKey = store.publicKey(sig.keyId);
        if (pubKey == null) {
            return Code.UNKNOWN_KEY_ID;
        }
        if (store.isRevoked(sig.keyId)) {
            return Code.REVOKED_KEY;
        }
        final byte[] sigBytes;
        try {
            sigBytes = Base64.getDecoder().decode(sig.value);
        } catch (IllegalArgumentException e) {
            return Code.MALFORMED_SIG;
        }
        if (sigBytes.length != Ed25519.SIGNATURE_LENGTH) {
            return Code.MALFORMED_SIG;
        }
        return Ed25519.verify(pubKey, payload, sigBytes) ? Code.OK : Code.BAD_SIGNATURE;
    }

    static final class SigFile {
        String algorithm;
        String keyId;
        String value;
    }
}
