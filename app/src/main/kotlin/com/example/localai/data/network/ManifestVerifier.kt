package com.example.localai.data.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Manifest/Catalog 签名验证（Ed25519，RFC 8032）。
 * 签名协议：签名覆盖数据文件（manifest.json / catalog.json）的原始字节流；
 * sig 侧车文件为 JSON {"algorithm":"Ed25519","keyId":"...","value":"<base64 R||S>"}。
 * 校验顺序：sig 文件可解析 -> 算法受支持 -> keyId 已知 -> 未撤销 -> 签名通过。
 */
object ManifestVerifier {

    const val ALGORITHM_ED25519 = "Ed25519"

    enum class Code {
        OK,
        MALFORMED_SIG,
        UNSUPPORTED_ALGORITHM,
        UNKNOWN_KEY_ID,
        REVOKED_KEY,
        BAD_SIGNATURE
    }

    private val GSON = Gson()

    /**
     * 验证签名。返回 Code.OK 或具体失败码（失败码可直接展示给 UI）。
     */
    @JvmStatic
    fun verify(payload: ByteArray, sigFileBytes: ByteArray, store: TrustStore): Code {
        val sig = try {
            val json = JsonParser.parseString(String(sigFileBytes, StandardCharsets.UTF_8))
                .asJsonObject
            GSON.fromJson(json, SigFile::class.java)
        } catch (e: RuntimeException) {
            return Code.MALFORMED_SIG
        }
        if (sig == null || sig.algorithm == null || sig.keyId == null || sig.value == null) {
            return Code.MALFORMED_SIG
        }
        if (ALGORITHM_ED25519 != sig.algorithm) {
            return Code.UNSUPPORTED_ALGORITHM
        }
        val pubKey = store.publicKey(sig.keyId) ?: return Code.UNKNOWN_KEY_ID
        if (store.isRevoked(sig.keyId)) {
            return Code.REVOKED_KEY
        }
        val sigBytes: ByteArray
        try {
            sigBytes = Base64.getDecoder().decode(sig.value)
        } catch (e: IllegalArgumentException) {
            return Code.MALFORMED_SIG
        }
        if (sigBytes.size != Ed25519.SIGNATURE_LENGTH) {
            return Code.MALFORMED_SIG
        }
        return if (Ed25519.verify(pubKey, payload, sigBytes)) Code.OK else Code.BAD_SIGNATURE
    }

    internal class SigFile {
        @JvmField var algorithm: String? = null
        @JvmField var keyId: String? = null
        @JvmField var value: String? = null
    }
}
