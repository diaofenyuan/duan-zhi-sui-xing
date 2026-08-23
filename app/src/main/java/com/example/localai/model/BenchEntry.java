package com.example.localai.model;

/** 单条模型短测结果（演示数据）。 */
public class BenchEntry {

    public final String modelName;
    public final int ttftMs;
    public final double tps;
    public final double memGb;

    public BenchEntry(String modelName, int ttftMs, double tps, double memGb) {
        this.modelName = modelName;
        this.ttftMs = ttftMs;
        this.tps = tps;
        this.memGb = memGb;
    }
}
