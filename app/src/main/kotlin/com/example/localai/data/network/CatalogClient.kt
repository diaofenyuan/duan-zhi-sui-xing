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
    private val trustStore: TrustStore
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
            if (catalog == null || catalog.models == null) {
                throw CatalogException(CatalogException.Code.SCHEMA_INVALID, "目录结构非法")
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
        val basePath = "/v1/models/$modelId/$version"
        val json = fetch("$basePath/manifest.json")
        val sig = fetch("$basePath/manifest.sig")
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
        val request = Request.Builder().url(base + path).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    throw CatalogException(CatalogException.Code.NOT_FOUND, "资源不存在：$path")
                }
                if (!response.isSuccessful) {
                    throw CatalogException(CatalogException.Code.HTTP, "HTTP ${response.code}：$path")
                }
                val body = response.body?.bytes() ?: ByteArray(0)
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

        private fun stripTrailingSlash(url: String): String {
            var s = url
            while (s.endsWith("/")) {
                s = s.substring(0, s.length - 1)
            }
            return s
        }
    }
}
