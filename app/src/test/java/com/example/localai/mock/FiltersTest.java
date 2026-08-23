package com.example.localai.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.localai.model.ModelInfo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 市场筛选逻辑测试（P1 验收命令 :app:testDebugUnitTest 的一部分）。 */
public class FiltersTest {

    private List<ModelInfo> source() {
        return MockStore.MODELS;
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
        assertTrue(result.size() >= 1);
    }

    @Test
    public void langChineseExcludesEnglishOnly() {
        List<ModelInfo> result = Filters.apply(source(), "", Filters.TASK_ALL,
                "中文", Filters.SIZE_ALL);
        for (ModelInfo m : result) {
            assertTrue(m.langs.contains("中文"));
        }
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
        assertTrue(byName.size() >= 2);

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
