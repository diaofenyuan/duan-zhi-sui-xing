package com.example.localai.data.network

import java.util.Locale

/**
 * 模型 Manifest（后端 model-manifest.schema.json 的客户端镜像）。
 * 字段名与 schema 严格一致；签名由 manifest.sig 侧车文件承载，不在本对象内。
 * 因 Gson 反射写入 + Java 侧按字段访问，使用普通类 + @JvmField 保持与 Java 版一致的字节码语义。
 */
class ModelManifest {

    @JvmField var modelId: String? = null
    @JvmField var version: String? = null
    @JvmField var displayName: String? = null
    @JvmField var source: Source? = null
    @JvmField var license: License? = null
    @JvmField var format: String? = null
    @JvmField var architecture: String? = null
    @JvmField var parameterCount: Long = 0
    @JvmField var quantization: String? = null
    @JvmField var contextLength: Long = 0
    @JvmField var description: String? = null
    @JvmField var tasks: List<String>? = null
    @JvmField var languages: List<String>? = null
    /** approved=真实正式权重；demo=开发演示载荷（不可推理）。缺省视为 demo。 */
    @JvmField var weightStatus: String? = null
    @JvmField var chatTemplate: String? = null
    @JvmField var updatedAt: String? = null
    @JvmField var files: List<FileEntry>? = null
    @JvmField var runtime: Runtime? = null

    class Source {
        @JvmField var publisher: String? = null
        @JvmField var url: String? = null
    }

    class License {
        @JvmField var spdx: String? = null
        @JvmField var url: String? = null
    }

    class FileEntry {
        @JvmField var name: String? = null
        @JvmField var sizeBytes: Long = 0
        @JvmField var sha256: String? = null
        @JvmField var etag: String? = null
        @JvmField var urls: List<String>? = null
    }

    class Runtime {
        @JvmField var minAndroidApi: Int = 0
        @JvmField var abis: List<String>? = null
        @JvmField var backends: List<String>? = null
    }

    /**
     * 客户端侧结构校验（防缺失字段被误用，服务器 schema 之外的防御性检查）。
     * 返回 null 表示通过，否则返回可展示给 UI 的失败原因。
     */
    fun validate(): String? {
        val id = modelId
        val v = version
        val src = source
        val lic = license
        val filesList = files
        val rt = runtime

        if (id == null || !ID_PATTERN.matches(id)) {
            return "manifest 缺少合法 modelId"
        }
        if (v == null || !VERSION_PATTERN.matches(v)) {
            return "manifest 缺少合法 version"
        }
        if (isBlank(displayName)) {
            return "manifest 缺少 displayName"
        }
        if (src == null || isBlank(src.publisher) || isBlank(src.url)) {
            return "manifest 缺少来源（publisher/url）"
        }
        if (lic == null || isBlank(lic.spdx) || isBlank(lic.url)) {
            return "manifest 缺少许可证（spdx/url）"
        }
        if ("gguf" != format) {
            return "不支持的格式：$format"
        }
        if (isBlank(architecture)) {
            return "manifest 缺少 architecture"
        }
        if (parameterCount < 1 || contextLength < 1) {
            return "manifest 参数数量或上下文长度非法"
        }
        if (filesList == null || filesList.isEmpty()) {
            return "manifest 缺少文件列表"
        }
        for (f in filesList) {
            if (isBlank(f.name) || f.sizeBytes < 1) {
                return "文件条目缺少 name/sizeBytes"
            }
            val sha = f.sha256
            if (sha == null || !SHA256_PATTERN.matches(sha.lowercase(Locale.ROOT))) {
                return "文件 ${f.name} 缺少合法 sha256"
            }
            val urls = f.urls
            if (urls == null || urls.isEmpty()) {
                return "文件 ${f.name} 缺少下载地址"
            }
        }
        if (rt == null || rt.minAndroidApi < 1) {
            return "manifest 缺少运行约束（minAndroidApi/abis/backends）"
        }
        val rtAbis = rt.abis
        val rtBackends = rt.backends
        if (rtAbis == null || rtAbis.isEmpty() || rtBackends == null || rtBackends.isEmpty()) {
            return "manifest 缺少运行约束（minAndroidApi/abis/backends）"
        }
        return null
    }

    fun primaryFile(): FileEntry? {
        val filesList = files
        return if (filesList == null || filesList.isEmpty()) null else filesList[0]
    }

    fun isApproved(): Boolean = "approved" == weightStatus

    fun isDemo(): Boolean = !isApproved()

    companion object {
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
        private val VERSION_PATTERN = Regex("^[0-9A-Za-z._-]{1,32}$")
        private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")

        private fun isBlank(s: String?): Boolean = s == null || s.trim().isEmpty()
    }
}
