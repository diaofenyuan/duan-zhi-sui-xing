package com.example.localai.common;

import java.util.Locale;

/** 人类可读的容量/速度格式化（纯 Java）。 */
public final class Fmt {

    private Fmt() {
    }

    public static String humanBytes(long bytes) {
        if (bytes < 0) {
            return "?";
        }
        double gb = bytes / 1_000_000_000.0;
        if (gb >= 1.0) {
            return String.format(Locale.US, "%.1f GB", gb);
        }
        double mb = bytes / 1_000_000.0;
        return String.format(Locale.US, "%.0f MB", mb);
    }

    /** 将剩余秒数格式化为 m:ss（超过 1 小时显示 h:mm:ss），用于下载 ETA 展示。 */
    public static String humanEta(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%d:%02d", m, s);
    }
}
