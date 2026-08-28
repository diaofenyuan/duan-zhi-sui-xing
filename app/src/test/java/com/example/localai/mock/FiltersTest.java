package com.example.localai.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.localai.model.ModelInfo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 市场筛选逻辑测试（独立 fixture，不依赖 MockStore）。 */
public class FiltersTest {

    private static List<ModelInfo> source() {
        List<ModelInfo> models = new ArrayList<>();
        models.add(new ModelInfo("qwen3-4b", "Qwen3-4B-Instruct", "Qwen",
                "4.0B", 4.0, "Q4_K_M", "2.4 GB", 2_400_000_000L, "32K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("中文", "多语言"), "Apache-2.0",
                "", ModelInfo.COMPAT_RECOMMENDED, "", 0, 0, 0, "", 0, true));
        models.add(new ModelInfo("llama3.2-3b", "Llama-3.2-3B-Instruct", "Meta",
                "3.0B", 3.0, "Q4_K_M", "1.9 GB", 1_900_000_000L, "128K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("英文"), "Llama 3.2 Community",
                "", ModelInfo.COMPAT_RUNNABLE, "", 0, 0, 0, "", 1, true));
        models.add(new ModelInfo("qwen-coder-1.5b", "Qwen2.5-Coder-1.5B-Instruct", "Qwen",
                "1.5B", 1.5, "Q4_K_M", "1.0 GB", 1_000_000_000L, "16K",
                ModelInfo.TASK_CODE, ModelInfo.langs("多语言"), "Apache-2.0",
                "", ModelInfo.COMPAT_RECOMMENDED, "", 0, 0, 0, "", 2, false));
        models.add(new ModelInfo("yi-1.5-9b", "Yi-1.5-9B-Chat", "01.AI",
                "9.0B", 9.0, "Q4_K_M", "5.3 GB", 5_300_000_000L, "32K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("中文", "英文"), "Apache-2.0",
                "", ModelInfo.COMPAT_HIGH_LOAD, "", 0, 0, 0, "", 0, false));
        return models;
    }

    @Test
    public void noFilterReturnsAll() {
        assertEquals(source().size(),
                Filters.apply(source(), "", Filters.TASK_ALL, Filters.LANG_ALL,
                        Filters.SIZE_ALL).size());
    }

    @Test
    public void taskFilterCodeOnly() {
        List<ModelInfo> result = Filters.apply(source(), "", "CODE",
                Filters.LANG_ALL, Filters.SIZE_ALL);
        for (ModelInfo m : result) {
            assertEquals("CODE", m.task);
        }
        assertEquals(1, result.size());
    }

    @Test
    public void langChineseExcludesEnglishOnly() {
        List<ModelInfo> result = Filters.apply(source(), "", Filters.TASK_ALL,
                "中文", Filters.SIZE_ALL);
        for (ModelInfo m : result) {
            assertTrue(m.langs.contains("中文"));
        }
        assertEquals(2, result.size());
    }

    @Test
    public void sizeBucketsPartition() {
        int le2 = Filters.apply(source(), "", Filters.TASK_ALL, Filters.LANG_ALL,
                Filters.SIZE_LE2).size();
        int mid = Filters.apply(source(), "", Filters.TASK_ALL, Filters.LANG_ALL,
                Filters.SIZE_MID).size();
        int gt8 = Filters.apply(source(), "", Filters.TASK_ALL, Filters.LANG_ALL,
                Filters.SIZE_GT8).size();
        assertEquals(source().size(), le2 + mid + gt8);
    }

    @Test
    public void queryMatchesNameOrPublisherIgnoreCase() {
        List<ModelInfo> byName = Filters.apply(source(), "qwen", Filters.TASK_ALL,
                Filters.LANG_ALL, Filters.SIZE_ALL);
        assertEquals(2, byName.size());

        List<ModelInfo> byPublisher = Filters.apply(source(), "meta", Filters.TASK_ALL,
                Filters.LANG_ALL, Filters.SIZE_ALL);
        assertEquals(1, byPublisher.size());
        assertEquals("llama3.2-3b", byPublisher.get(0).id);
    }

    @Test
    public void combinedFiltersAreAnded() {
        List<ModelInfo> all = Filters.apply(source(), "", "TEXT", "中文", Filters.SIZE_LE2);
        List<ModelInfo> expected = new ArrayList<>();
        for (ModelInfo m : source()) {
            if ("TEXT".equals(m.task) && m.langs.contains("中文") && m.paramsB <= 2.0) {
                expected.add(m);
            }
        }
        assertEquals(expected.size(), all.size());
    }
}
