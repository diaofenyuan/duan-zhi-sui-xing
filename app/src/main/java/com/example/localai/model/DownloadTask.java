package com.example.localai.model;

/** 下载任务：状态机为纯 Java 实现，便于单元测试；引擎只负责驱动时间。 */
public class DownloadTask {

    public enum State { DOWNLOADING, PAUSED, VERIFYING, READY, FAILED }

    public enum Action { PAUSE, RESUME, VERIFY, FAIL, COMPLETE }

    public final String taskId;
    public final String modelId;
    public final String modelName;
    public final long totalBytes;
    public long downloadedBytes;
    public double speedBps = 6.0 * 1024 * 1024;
    public State state = State.DOWNLOADING;
    public String failReason;

    public DownloadTask(String taskId, String modelId, String modelName,
                        long totalBytes, long downloadedBytes, State state) {
        this.taskId = taskId;
        this.modelId = modelId;
        this.modelName = modelName;
        this.totalBytes = totalBytes;
        this.downloadedBytes = downloadedBytes;
        this.state = state;
    }

    /**
     * 纯状态迁移：合法迁移返回 true 并更新状态；非法迁移保持原状态并返回 false。
     */
    public boolean transition(Action action) {
        switch (state) {
            case DOWNLOADING:
                if (action == Action.PAUSE) { state = State.PAUSED; return true; }
                if (action == Action.VERIFY) { state = State.VERIFYING; return true; }
                if (action == Action.FAIL) { state = State.FAILED; return true; }
                return false;
            case PAUSED:
                if (action == Action.RESUME) { state = State.DOWNLOADING; return true; }
                return false;
            case VERIFYING:
                if (action == Action.COMPLETE) { state = State.READY; return true; }
                if (action == Action.FAIL) { state = State.FAILED; return true; }
                return false;
            default:
                return false;
        }
    }

    public int percent() {
        if (totalBytes <= 0) {
            return 0;
        }
        long p = downloadedBytes * 100 / totalBytes;
        return (int) Math.min(100L, Math.max(0L, p));
    }
}
