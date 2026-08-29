package com.example.localai.model

import java.util.Arrays

/**
 * 市场/演示模型条目（P1 前端演示 + P4 起由真实目录映射填充）。
 * 含可变字段（installed 与 7 个 P4 扩展字段），故使用普通类 + @JvmField 保留与 Java 版相同的
 * 字段访问与对象同一性语义；license / quant 来自未做空值兜底的 manifest 字段，故保持可空。
 */
class ModelInfo(
    @JvmField val id: String,
    @JvmField val name: String,
    @JvmField val publisher: String,
    @JvmField val paramsLabel: String,
    @JvmField val paramsB: Double,
    @JvmField val quant: String?,
    @JvmField val sizeLabel: String,
    @JvmField val sizeBytes: Long,
    @JvmField val contextLabel: String,
    @JvmField val task: String,
    @JvmField val langs: List<String>,
    @JvmField val license: String?,
    @JvmField val desc: String,
    @JvmField val compat: String,
    @JvmField val compatReason: String,
    @JvmField val ttftMs: Int,
    @JvmField val tps: Double,
    @JvmField val memMb: Int,
    @JvmField val updated: String,
    @JvmField val gradIndex: Int,
    @JvmField var installed: Boolean
) {
    @JvmField var weightStatus: String? = null   // "approved" | "demo"
    @JvmField var isDemo: Boolean = false
    @JvmField var sourceUrl: String? = null
    @JvmField var licenseUrl: String? = null
    @JvmField var chatTemplate: String? = null
    @JvmField var minAndroidApi: Int = 0
    @JvmField var estimatedPeakMb: Int = 0

    fun letter(): Char = Character.toUpperCase(name[0])

    companion object {
        @JvmField val COMPAT_RECOMMENDED = "RECOMMENDED"
        @JvmField val COMPAT_RUNNABLE = "RUNNABLE"
        @JvmField val COMPAT_HIGH_LOAD = "HIGH_LOAD"
        @JvmField val COMPAT_UNSUPPORTED = "UNSUPPORTED"

        @JvmField val TASK_TEXT = "TEXT"
        @JvmField val TASK_CODE = "CODE"

        @JvmStatic
        fun langs(vararg values: String): List<String> = Arrays.asList(*values)
    }
}
