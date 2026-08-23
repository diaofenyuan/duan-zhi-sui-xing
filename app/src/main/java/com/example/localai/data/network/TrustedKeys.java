package com.example.localai.data.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 内置可信公钥（P2 dev 联调用）。
 * dev 公钥由 backend/fixtures/generate.ps1 生成并签名 backend/fixtures 下全部 fixture，
 * 生成流程与重新签发方法见 backend/signing/keys/README.md。
 * 生产发布（P5）前必须替换为生产密钥并移除 dev keyId。
 */
public final class TrustedKeys implements TrustStore {

    private static final String DEV_KEY_ID = "release-2026-01-dev";
    private static final String DEV_PUB_HEX =
            "7310bdfedac907f23759d12ad1f278159d26181d26515bf60e94b93b70eecbf4";

    private static final TrustedKeys INSTANCE = new TrustedKeys();

    private final Map<String, byte[]> keys = new HashMap<>();
    private final Set<String> revoked = new HashSet<>();

    private TrustedKeys() {
        keys.put(DEV_KEY_ID, Hex.fromHex(DEV_PUB_HEX));
    }

    public static TrustedKeys get() {
        return INSTANCE;
    }

    @Override
    public byte[] publicKey(String keyId) {
        if (keyId == null) {
            return null;
        }
        return keys.get(keyId);
    }

    @Override
    public boolean isRevoked(String keyId) {
        return revoked.contains(keyId);
    }
}
