package com.example.localai.mock

import com.example.localai.model.ModelInfo
import java.util.ArrayList

/** 市场筛选的纯函数实现（可单元测试）。 */
object Filters {

    const val TASK_ALL = "ALL"
    const val LANG_ALL = "ALL"
    const val SIZE_ALL = "ALL"
    const val SIZE_LE2 = "LE2"
    const val SIZE_MID = "MID"
    const val SIZE_GT8 = "GT8"

    /**
     * @param task ALL | TEXT | CODE
     * @param lang ALL | 中文 | 英文 | 多语言
     * @param size ALL | LE2 | MID | GT8
     */
    @JvmStatic
    fun apply(source: List<ModelInfo>, query: String?, task: String, lang: String, size: String): List<ModelInfo> {
        val result = ArrayList<ModelInfo>()
        val q = if (query == null) "" else query.trim().lowercase()
        for (m in source) {
            if (task != TASK_ALL && m.task != task) {
                continue
            }
            if (lang != LANG_ALL && !m.langs.contains(lang)) {
                continue
            }
            if (!matchesSize(m.paramsB, size)) {
                continue
            }
            if (q.isNotEmpty()
                && !m.name.lowercase().contains(q)
                && !m.publisher.lowercase().contains(q)) {
                continue
            }
            result.add(m)
        }
        return result
    }

    @JvmStatic
    fun matchesSize(paramsB: Double, size: String): Boolean {
        return when (size) {
            SIZE_LE2 -> paramsB <= 2.0
            SIZE_MID -> paramsB > 2.0 && paramsB <= 8.0
            SIZE_GT8 -> paramsB > 8.0
            else -> true
        }
    }
}
