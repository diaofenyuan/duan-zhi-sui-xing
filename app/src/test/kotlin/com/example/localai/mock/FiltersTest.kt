package com.example.localai.mock

import com.example.localai.model.ModelInfo
import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 市场筛选逻辑测试（独立 fixture，不依赖 MockStore）。 */
class FiltersTest {

    private fun source(): List<ModelInfo> {
        val models = ArrayList<ModelInfo>()
        models.add(ModelInfo("qwen3-4b", "Qwen3-4B-Instruct", "Qwen",
            "4.0B", 4.0, "Q4_K_M", "2.4 GB", 2_400_000_000L, "32K",
            ModelInfo.TASK_TEXT, ModelInfo.langs("中文", "多语言"), "Apache-2.0",
            "", ModelInfo.COMPAT_RECOMMENDED, "", 0, 0.0, 0, "", 0, true))
        models.add(ModelInfo("llama3.2-3b", "Llama-3.2-3B-Instruct", "Meta",
            "3.0B", 3.0, "Q4_K_M", "1.9 GB", 1_900_000_000L, "128K",
            ModelInfo.TASK_TEXT, ModelInfo.langs("英文"), "Llama 3.2 Community",
            "", ModelInfo.COMPAT_RUNNABLE, "", 0, 0.0, 0, "", 1, true))
        models.add(ModelInfo("qwen-coder-1.5b", "Qwen2.5-Coder-1.5B-Instruct", "Qwen",
            "1.5B", 1.5, "Q4_K_M", "1.0 GB", 1_000_000_000L, "16K",
            ModelInfo.TASK_CODE, ModelInfo.langs("多语言"), "Apache-2.0",
            "", ModelInfo.COMPAT_RECOMMENDED, "", 0, 0.0, 0, "", 2, false))
        models.add(ModelInfo("yi-1.5-9b", "Yi-1.5-9B-Chat", "01.AI",
            "9.0B", 9.0, "Q4_K_M", "5.3 GB", 5_300_000_000L, "32K",
            ModelInfo.TASK_TEXT, ModelInfo.langs("中文", "英文"), "Apache-2.0",
            "", ModelInfo.COMPAT_HIGH_LOAD, "", 0, 0.0, 0, "", 0, false))
        return models
    }

    @Test
    fun noFilterReturnsAll() {
        assertEquals(source().size,
            Filters.apply(source(), "", Filters.TASK_ALL, Filters.LANG_ALL,
                Filters.SIZE_ALL).size)
    }

    @Test
    fun taskFilterCodeOnly() {
        val result = Filters.apply(source(), "", "CODE",
            Filters.LANG_ALL, Filters.SIZE_ALL)
        for (m in result) {
            assertEquals("CODE", m.task)
        }
        assertEquals(1, result.size)
    }

    @Test
    fun langChineseExcludesEnglishOnly() {
        val result = Filters.apply(source(), "", Filters.TASK_ALL,
            "中文", Filters.SIZE_ALL)
        for (m in result) {
            assertTrue(m.langs.contains("中文"))
        }
        assertEquals(2, result.size)
    }

    @Test
    fun sizeBucketsPartition() {
        val le2 = Filters.apply(source(), "", Filters.TASK_ALL, Filters.LANG_ALL,
            Filters.SIZE_LE2).size
        val mid = Filters.apply(source(), "", Filters.TASK_ALL, Filters.LANG_ALL,
            Filters.SIZE_MID).size
        val gt8 = Filters.apply(source(), "", Filters.TASK_ALL, Filters.LANG_ALL,
            Filters.SIZE_GT8).size
        assertEquals(source().size, le2 + mid + gt8)
    }

    @Test
    fun queryMatchesNameOrPublisherIgnoreCase() {
        val byName = Filters.apply(source(), "qwen", Filters.TASK_ALL,
            Filters.LANG_ALL, Filters.SIZE_ALL)
        assertEquals(2, byName.size)

        val byPublisher = Filters.apply(source(), "meta", Filters.TASK_ALL,
            Filters.LANG_ALL, Filters.SIZE_ALL)
        assertEquals(1, byPublisher.size)
        assertEquals("llama3.2-3b", byPublisher[0].id)
    }

    @Test
    fun combinedFiltersAreAnded() {
        val all = Filters.apply(source(), "", "TEXT", "中文", Filters.SIZE_LE2)
        val expected = ArrayList<ModelInfo>()
        for (m in source()) {
            if ("TEXT" == m.task && m.langs.contains("中文") && m.paramsB <= 2.0) {
                expected.add(m)
            }
        }
        assertEquals(expected.size, all.size)
    }
}
