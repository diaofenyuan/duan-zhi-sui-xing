package com.example.localai.fixtures

import com.example.localai.data.network.Hex
import com.example.localai.data.network.TrustStore
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

/**
 * 测试夹具工具：JDK 原生 Ed25519 动态签名（与 App 内纯 Java 验证器互操作）、
 * 确定性 GGUF 演示载荷、Manifest/Catalog JSON 构造。
 */
object FixtureKit {

    @JvmField val GSON: Gson = Gson()

    /** 测试密钥对 + 对应 TrustStore（验证器经此信任测试公钥）。 */
    class TestKeys(
        @JvmField val keyId: String,
        @JvmField val publicKeyBytes: ByteArray,
        @JvmField val trustStore: TrustStore,
        private val keyPair: KeyPair
    ) {
        fun sign(data: ByteArray): ByteArray {
            val sig = Signature.getInstance("Ed25519")
            sig.initSign(keyPair.private)
            sig.update(data)
            return sig.sign()
        }
    }

    @JvmStatic
    fun newTestKeys(keyId: String): TestKeys {
        val gen = KeyPairGenerator.getInstance("Ed25519")
        val pair = gen.generateKeyPair()
        val spki = pair.public.encoded
        val rawPub = ByteArray(32)
        System.arraycopy(spki, spki.size - 32, rawPub, 0, 32)
        val expectedKeyId = keyId
        val store = object : TrustStore {
            override fun publicKey(keyId: String?): ByteArray? =
                if (keyId == expectedKeyId) rawPub else null

            override fun isRevoked(keyId: String?): Boolean = false
        }
        return TestKeys(keyId, rawPub, store, pair)
    }

    /** 用 JDK Ed25519 签名数据并生成 sig 侧车文件字节。 */
    @JvmStatic
    fun sigFile(keys: TestKeys, data: ByteArray): ByteArray {
        val sig = keys.sign(data)
        val json = JsonObject()
        json.addProperty("algorithm", "Ed25519")
        json.addProperty("keyId", keys.keyId)
        json.addProperty("value", Base64.getEncoder().encodeToString(sig))
        return json.toString().toByteArray(StandardCharsets.UTF_8)
    }

    /** 生成确定性 GGUF 演示载荷（合法 v3 头部 + general.architecture + 模式填充）。 */
    @JvmStatic
    fun ggufPayload(modelId: String, architecture: String, sizeBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        writeIntLE(out, 0x46554747) // "GGUF"
        writeIntLE(out, 3) // version
        writeLongLE(out, 0L) // tensor count
        writeLongLE(out, 1L) // kv count
        writeKvString(out, "general.architecture", architecture)
        val header = out.toByteArray()
        if (sizeBytes < header.size + 32) {
            throw IllegalArgumentException("payload too small")
        }
        val result = ByteArray(sizeBytes)
        System.arraycopy(header, 0, result, 0, header.size)
        var state = sha256(("payload:" + modelId).toByteArray(StandardCharsets.UTF_8))
        var pos = header.size
        while (pos < result.size) {
            state = sha256(state)
            val n = minOf(state.size, result.size - pos)
            System.arraycopy(state, 0, result, pos, n)
            pos += n
        }
        return result
    }

    @JvmStatic
    fun sha256(data: ByteArray): ByteArray {
        return try {
            MessageDigest.getInstance("SHA-256").digest(data)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    @JvmStatic
    fun sha256Hex(data: ByteArray): String = Hex.toHex(sha256(data))

    @JvmStatic
    fun catalogJson(models: Array<Array<String>>): ByteArray {
        val json = JsonObject()
        json.addProperty("schemaVersion", 1)
        json.addProperty("generatedAt", "2026-08-23T00:00:00Z")
        val array = JsonArray()
        for (m in models) {
            val entry = JsonObject()
            entry.addProperty("modelId", m[0])
            entry.addProperty("version", m[1])
            entry.addProperty("displayName", m[2])
            entry.addProperty("description", "fixture model")
            array.add(entry)
        }
        json.add("models", array)
        return json.toString().toByteArray(StandardCharsets.UTF_8)
    }

    /** 生成 manifest.json 字节（未签名；签名由 sigFile 侧车承载）。 */
    @JvmStatic
    fun manifestJson(modelId: String, version: String, displayName: String,
                     architecture: String, publisher: String, licenseSpdx: String,
                     fileName: String, sizeBytes: Int, sha256: String, urlPath: String): ByteArray {
        val json = JsonObject()
        json.addProperty("modelId", modelId)
        json.addProperty("version", version)
        json.addProperty("displayName", displayName)
        val source = JsonObject()
        source.addProperty("publisher", publisher)
        source.addProperty("url", "https://example.com/$publisher")
        json.add("source", source)
        val license = JsonObject()
        license.addProperty("spdx", licenseSpdx)
        license.addProperty("url", "https://example.com/license")
        json.add("license", license)
        json.addProperty("format", "gguf")
        json.addProperty("architecture", architecture)
        json.addProperty("parameterCount", 1500000000L)
        json.addProperty("quantization", "Q4_K_M")
        json.addProperty("contextLength", 32768)
        val files = JsonArray()
        val file = JsonObject()
        file.addProperty("name", fileName)
        file.addProperty("sizeBytes", sizeBytes)
        file.addProperty("sha256", sha256)
        val urls = JsonArray()
        urls.add(urlPath)
        file.add("urls", urls)
        files.add(file)
        json.add("files", files)
        val runtime = JsonObject()
        runtime.addProperty("minAndroidApi", 26)
        val abis = JsonArray()
        abis.add("arm64-v8a")
        runtime.add("abis", abis)
        val backends = JsonArray()
        backends.add("cpu")
        runtime.add("backends", backends)
        json.add("runtime", runtime)
        return json.toString().toByteArray(StandardCharsets.UTF_8)
    }

    @JvmStatic
    fun manifestPath(modelId: String, version: String): String =
        "/v1/models/$modelId/$version/manifest.json"

    @JvmStatic
    fun manifestSigPath(modelId: String, version: String): String =
        "/v1/models/$modelId/$version/manifest.sig"

    @JvmStatic
    fun filePath(modelId: String, version: String, fileName: String): String =
        "/v1/models/$modelId/$version/$fileName"

    private fun writeKvString(out: ByteArrayOutputStream, key: String, value: String) {
        val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
        val valueBytes = value.toByteArray(StandardCharsets.UTF_8)
        writeLongLE(out, keyBytes.size.toLong())
        out.write(keyBytes)
        writeIntLE(out, 8) // STRING
        writeLongLE(out, valueBytes.size.toLong())
        out.write(valueBytes)
    }

    private fun writeIntLE(out: ByteArrayOutputStream, v: Int) {
        for (i in 0 until 4) {
            out.write((v shr (8 * i)).toByte().toInt())
        }
    }

    private fun writeLongLE(out: ByteArrayOutputStream, v: Long) {
        for (i in 0 until 8) {
            out.write((v shr (8 * i)).toByte().toInt())
        }
    }
}
