package com.example.localai.data.network

/** 目录/Manifest 拉取失败（结构化错误码，可直接展示给 UI）。 */
class CatalogException @JvmOverloads constructor(
    private val errorCode: Code,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class Code {
        NETWORK,
        HTTP,
        NOT_FOUND,
        BAD_SIGNATURE,
        SCHEMA_INVALID
    }

    fun code(): Code = errorCode
}
