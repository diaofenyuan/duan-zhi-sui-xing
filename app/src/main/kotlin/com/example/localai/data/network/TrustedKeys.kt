package com.example.localai.data.network

import java.util.HashMap
import java.util.HashSet

/**
 * 内置可信公钥（P2 dev 联调用）。
 * dev 公钥由 backend/fixtures/generate.ps1 生成并签名 backend/fixtures 下全部 fixture，
 * 生成流程与重新签发方法见 backend/signing/keys/README.md。
 * 生产发布（P5）前必须替换为生产密钥并移除 dev keyId。
 */
class TrustedKeys private constructor() : TrustStore {

    private val keys = HashMap<String, ByteArray>()
    private val revoked = HashSet<String>()

    init {
        keys[DEV_KEY_ID] = Hex.fromHex(DEV_PUB_HEX)
    }

    override fun publicKey(keyId: String?): ByteArray? {
        if (keyId == null) {
            return null
        }
        return keys[keyId]
    }

    override fun isRevoked(keyId: String?): Boolean {
        return if (keyId == null) false else revoked.contains(keyId)
    }

    companion object {
        private const val DEV_KEY_ID = "release-2026-01-dev"
        private const val DEV_PUB_HEX =
            "7310bdfedac907f23759d12ad1f278159d26181d26515bf60e94b93b70eecbf4"

        private val INSTANCE = TrustedKeys()

        @JvmStatic
        fun get(): TrustedKeys = INSTANCE
    }
}
