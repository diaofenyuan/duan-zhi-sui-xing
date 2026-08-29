package com.example.localai.data.network

/** 模型目录（backend catalog.schema.json 的客户端镜像）；签名由 catalog.sig 承载。 */
class Catalog {
    @JvmField var schemaVersion: Long = 0
    @JvmField var generatedAt: String? = null
    @JvmField var models: List<Entry>? = null

    class Entry {
        @JvmField var modelId: String? = null
        @JvmField var version: String? = null
        @JvmField var displayName: String? = null
        @JvmField var description: String? = null
    }
}
