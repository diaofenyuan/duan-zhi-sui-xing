package com.example.localai.mock;

import com.example.localai.model.ModelInfo;

import java.util.ArrayList;
import java.util.List;

/** 市场筛选的纯函数实现（可单元测试）。 */
public final class Filters {

    public static final String TASK_ALL = "ALL";
    public static final String LANG_ALL = "ALL";
    public static final String SIZE_ALL = "ALL";
    public static final String SIZE_LE2 = "LE2";
    public static final String SIZE_MID = "MID";
    public static final String SIZE_GT8 = "GT8";

    private Filters() {
    }

    /**
     * @param task ALL | TEXT | CODE
     * @param lang ALL | 中文 | 英文 | 多语言
     * @param size ALL | LE2 | MID | GT8
     */
    public static List<ModelInfo> apply(List<ModelInfo> source, String query,
                                        String task, String lang, String size) {
        List<ModelInfo> result = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (ModelInfo m : source) {
            if (!task.equals(TASK_ALL) && !m.task.equals(task)) {
                continue;
            }
            if (!lang.equals(LANG_ALL) && !m.langs.contains(lang)) {
                continue;
            }
            if (!matchesSize(m.paramsB, size)) {
                continue;
            }
            if (!q.isEmpty()
                    && !m.name.toLowerCase().contains(q)
                    && !m.publisher.toLowerCase().contains(q)) {
                continue;
            }
            result.add(m);
        }
        return result;
    }

    public static boolean matchesSize(double paramsB, String size) {
        switch (size) {
            case SIZE_LE2:
                return paramsB <= 2.0;
            case SIZE_MID:
                return paramsB > 2.0 && paramsB <= 8.0;
            case SIZE_GT8:
                return paramsB > 8.0;
            default:
                return true;
        }
    }
}
