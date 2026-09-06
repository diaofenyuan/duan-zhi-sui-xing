package com.example.localai.data.network

import android.content.Context
import okhttp3.OkHttpClient

/** 签名目录随应用发布，浏览和重启无需控制服务器；仅模型权重通过 HTTPS 获取。 */
object CatalogConfig {
    fun create(context: Context, client: OkHttpClient): CatalogClient =
        CatalogClient("https://huggingface.co", client, TrustedKeys.get()) { path ->
            context.assets.open("catalog$path").use { it.readBytes() }
        }
}
