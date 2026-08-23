package com.example.localai.data.room;

/** 下载任务状态常量与状态机辅助（S014 状态机：仅允许合法迁移）。 */
public final class DownloadState {

    public static final String QUEUED = "QUEUED";
    public static final String DOWNLOADING = "DOWNLOADING";
    public static final String PAUSED = "PAUSED";
    public static final String VERIFYING = "VERIFYING";
    public static final String INSTALLING = "INSTALLING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";
    public static final String CANCELED = "CANCELED";

    private DownloadState() {
    }

    public static boolean isTerminal(String state) {
        return READY.equals(state) || FAILED.equals(state) || CANCELED.equals(state);
    }

    /** 进程重启后需要恢复执行的状态（FAILED 需用户显式重试，不自动恢复）。 */
    public static boolean isRecoverable(String state) {
        return QUEUED.equals(state) || DOWNLOADING.equals(state)
                || PAUSED.equals(state) || VERIFYING.equals(state)
                || INSTALLING.equals(state);
    }

    public static boolean isActive(String state) {
        return QUEUED.equals(state) || DOWNLOADING.equals(state)
                || VERIFYING.equals(state) || INSTALLING.equals(state);
    }

    /** 合法状态迁移；非法迁移返回 false 且不改状态。 */
    public static boolean canTransition(String from, String to) {
        switch (from) {
            case QUEUED:
                return DOWNLOADING.equals(to) || FAILED.equals(to) || CANCELED.equals(to);
            case DOWNLOADING:
                return PAUSED.equals(to) || VERIFYING.equals(to) || FAILED.equals(to) || CANCELED.equals(to);
            case PAUSED:
                return DOWNLOADING.equals(to) || CANCELED.equals(to);
            case VERIFYING:
                return INSTALLING.equals(to) || FAILED.equals(to) || CANCELED.equals(to);
            case INSTALLING:
                return READY.equals(to) || FAILED.equals(to) || CANCELED.equals(to);
            case FAILED:
                return DOWNLOADING.equals(to) || CANCELED.equals(to);
            default:
                return false;
        }
    }
}
