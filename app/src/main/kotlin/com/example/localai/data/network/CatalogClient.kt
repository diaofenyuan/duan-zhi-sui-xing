package com.example.localai.data.network

import com.google.gson.Gson
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 目录/Manifest 拉取客户端。
 * 协议：
 *   GET {base}/v1/catalog.json (+catalog.sig)
 *   GET {base}/v1/models/{modelId}/{version}/manifest.json (+manifest.sig)
 * 目录与 Manifest 均需签名验证通过；Manifest 还需通过结构校验。
 */
class CatalogClient(
    baseUrl: String,
    private val client: OkHttpClient,
    private val trustStore: TrustStore,
    private val bundledReader: ((String) -> ByteArray)? = null
) {

    private val base: String = stripTrailingSlash(baseUrl)

    constructor(baseUrl: String, trustStore: TrustStore) : this(
        baseUrl,
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build(),
        trustStore)

    /** 拉取并验证目录；签名失败抛 CatalogException(BAD_SIGNATURE)。 */
    @Throws(CatalogException::class)
    fun fetchCatalog(): Catalog {
        val json = fetch("/v1/catalog.json")
        val sig = fetch("/v1/catalog.sig")
        val code = ManifestVerifier.verify(json, sig, trustStore)
        if (code != ManifestVerifier.Code.OK) {
            throw CatalogException(CatalogException.Code.BAD_SIGNATURE, "目录签名验证失败：$code")
        }
        return try {
            val catalog = GSON.fromJson(String(json, StandardCharsets.UTF_8), Catalog::class.java)
            if (catalog == null || catalog.schemaVersion != 1L || catalog.models == null) {
                throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "目录结构非法")
            }
            val ids = HashSet<String>()
            for (entry in catalog.models!!) {
                if (entry == null || !validIdentifier(entry.modelId) || !validVersion(entry.version) ||
                    !ids.add(entry.modelId!!)) {
                    throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "目录模型标识非法或重复")
                }
            }
            catalog
        } catch (e: CatalogException) {
            throw e
        } catch (e: RuntimeException) {
            throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "目录解析失败", e)
        }
    }

    /** 拉取并验证 Manifest；返回已验证的 ModelManifest。 */
    @Throws(CatalogException::class)
    fun fetchManifest(modelId: String, version: String): ModelManifest {
        return fetchManifestBundle(modelId, version).manifest
    }

    /** 拉取 Manifest 及其签名原始字节（用于安装时随模型落盘）。 */
    @Throws(CatalogException::class)
    fun fetchManifestBundle(modelId: String, version: String): ManifestBundle {
        if (!validIdentifier(modelId) || !validVersion(version)) {
            throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "模型标识或版本非法")
        }
        val basePath = "/v1/models/$modelId/$version"
        val json = fetch("$basePath/manifest.json")
        val sig = fetch("$basePath/manifest.sig")
        return verifyManifestBundle(modelId, version, json, sig)
    }

    /** 恢复安装时重新验证本地字节，不依赖网络或曾经验证过的内存状态。 */
    fun verifyManifestBundle(modelId: String, version: String, json: ByteArray, sig: ByteArray): ManifestBundle {
        if (json.isEmpty() || json.size > MAX_METADATA_BYTES || sig.size > MAX_METADATA_BYTES) {
            throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "Manifest 文件大小非法")
        }
        val code = ManifestVerifier.verify(json, sig, trustStore)
        if (code != ManifestVerifier.Code.OK) {
            throw CatalogException(
                CatalogException.Code.BAD_SIGNATURE,
                "模型 $modelId Manifest 签名验证失败：$code")
        }
        val manifest = try {
            GSON.fromJson(String(json, StandardCharsets.UTF_8), ModelManifest::class.java)
        } catch (e: RuntimeException) {
            throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "Manifest 解析失败", e)
        }
        if (manifest == null) {
            throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "Manifest 为空")
        }
        val problem = manifest.validate()
        if (problem != null) {
            throw CatalogException(CatalogException.Code.SCHEMA_INVALID, problem)
        }
        if (manifest.modelId != modelId || manifest.version != version) {
            throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "Manifest 与请求的模型或版本不一致")
        }
        return ManifestBundle(manifest, json, sig)
    }

    /** 已验证 Manifest 及其原始字节。 */
    class ManifestBundle(
        @JvmField val manifest: ModelManifest,
        @JvmField val json: ByteArray,
        @JvmField val sig: ByteArray
    )

    /** Manifest 中相对路径 URL 解析为绝对地址。 */
    fun resolveUrl(url: String?): String? {
        if (url == null || url.isEmpty()) {
            return null
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        return base + if (url.startsWith("/")) url else "/$url"
    }

    fun baseUrl(): String = base

    private fun fetch(path: String): ByteArray {
        bundledReader?.let { reader ->
            return try {
                reader(path).also {
                    if (it.isEmpty() || it.size > MAX_METADATA_BYTES) {
                        throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "内置目录文件大小非法")
                    }
                }
            } catch (e: IOException) {
                throw CatalogException(CatalogException.Code.NOT_FOUND, "内置模型目录文件缺失", e)
            }
        }
        val request = Request.Builder().url(base + path).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    throw CatalogException(CatalogException.Code.NOT_FOUND, "资源不存在：$path")
                }
                if (!response.isSuccessful) {
                    throw CatalogException(CatalogException.Code.HTTP, "HTTP ${response.code}：$path")
                }
                val body = response.body?.byteStream()?.use { it.readBytesLimited() } ?: ByteArray(0)
                if (body.isEmpty()) {
                    throw CatalogException(CatalogException.Code.HTTP, "空响应：$path")
                }
                body
            }
        } catch (e: IOException) {
            throw CatalogException(CatalogException.Code.NETWORK, "网络错误：$path", e)
        }
    }

    companion object {
        private val GSON = Gson()
        private const val MAX_METADATA_BYTES = 1024 * 1024
        private fun validIdentifier(value: String?) = value?.matches(Regex("^[a-z0-9][a-z0-9._-]{0,63}$")) == true
        private fun validVersion(value: String?) = value?.matches(Regex("^[0-9A-Za-z_-][0-9A-Za-z._-]{0,31}$")) == true

        private fun java.io.InputStream.readBytesLimited(): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = read(buffer)
                if (n < 0) break
                if (out.size() + n > MAX_METADATA_BYTES) {
                    throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "目录文件过大")
                }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        private fun stripTrailingSlash(url: String): String {
            var s = url
            while (s.endsWith("/")) {
                s = s.substring(0, s.length - 1)
            }
            return s
        }
    }
}
