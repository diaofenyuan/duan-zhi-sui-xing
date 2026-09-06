package com.example.localai.data.network

import java.util.HashMap
import java.util.HashSet

/**
 * 随应用发布的模型目录公钥；开发 Fixture 密钥不进入产品信任集合。
 */
class TrustedKeys private constructor() : TrustStore {

    private val keys = HashMap<String, ByteArray>()
    private val revoked = HashSet<String>()

    init {
        keys[CATALOG_KEY_ID] = Hex.fromHex(CATALOG_PUB_HEX)
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
        private const val CATALOG_KEY_ID = "bundled-catalog-2026-09"
        private const val CATALOG_PUB_HEX =
            "35e79f339191d095bfb3734dbaecf617e149097c6082cafd69494b95b37b149d"

        private val INSTANCE = TrustedKeys()

        @JvmStatic
        fun get(): TrustedKeys = INSTANCE
    }
}
