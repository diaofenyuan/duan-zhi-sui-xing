package com.example.localai.data.network

/**
 * 目录服务配置。
 * P2 dev 联调地址指向本机后端 FixtureServer（模拟器经 10.0.2.2 访问宿主）。
 * P4/P5 迁移到生产控制面（HTTPS + 签名短期下载 URL）时替换此常量。
 */
object CatalogConfig {

    const val BASE_URL = "http://10.0.2.2:8090"
}
