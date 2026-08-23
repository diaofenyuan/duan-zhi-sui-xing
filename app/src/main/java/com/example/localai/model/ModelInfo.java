package com.example.localai.model;

import java.util.Arrays;
import java.util.List;

/** 模拟市场中的模型条目（P1 仅前端演示，不接入真实 Manifest）。 */
public class ModelInfo {

    public static final String COMPAT_RECOMMENDED = "RECOMMENDED";
    public static final String COMPAT_RUNNABLE = "RUNNABLE";
    public static final String COMPAT_HIGH_LOAD = "HIGH_LOAD";
    public static final String COMPAT_UNSUPPORTED = "UNSUPPORTED";

    public static final String TASK_TEXT = "TEXT";
    public static final String TASK_CODE = "CODE";

    public final String id;
    public final String name;
    public final String publisher;
    public final String paramsLabel;
    public final double paramsB;
    public final String quant;
    public final String sizeLabel;
    public final long sizeBytes;
    public final String contextLabel;
    public final String task;
    public final List<String> langs;
    public final String license;
    public final String desc;
    public final String compat;
    public final String compatReason;
    public final int ttftMs;
    public final double tps;
    public final int memMb;
    public final String updated;
    public final int gradIndex;
    public boolean installed;

    public ModelInfo(String id, String name, String publisher, String paramsLabel, double paramsB,
                     String quant, String sizeLabel, long sizeBytes, String contextLabel,
                     String task, List<String> langs, String license, String desc,
                     String compat, String compatReason, int ttftMs, double tps, int memMb,
                     String updated, int gradIndex, boolean installed) {
        this.id = id;
        this.name = name;
        this.publisher = publisher;
        this.paramsLabel = paramsLabel;
        this.paramsB = paramsB;
        this.quant = quant;
        this.sizeLabel = sizeLabel;
        this.sizeBytes = sizeBytes;
        this.contextLabel = contextLabel;
        this.task = task;
        this.langs = langs;
        this.license = license;
        this.desc = desc;
        this.compat = compat;
        this.compatReason = compatReason;
        this.ttftMs = ttftMs;
        this.tps = tps;
        this.memMb = memMb;
        this.updated = updated;
        this.gradIndex = gradIndex;
        this.installed = installed;
    }

    public char letter() {
        return Character.toUpperCase(name.charAt(0));
    }

    public static List<String> langs(String... values) {
        return Arrays.asList(values);
    }
}
