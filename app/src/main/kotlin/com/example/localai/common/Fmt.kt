package com.example.localai.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 人类可读的容量/速度/时间格式化。静态工具类，以 object + @JvmStatic 保留 Java 静态调用。 */
object Fmt {

    @JvmStatic
    fun humanBytes(bytes: Long): String {
        if (bytes < 0) {
            return "?"
        }
        val gb = bytes / 1_000_000_000.0
        if (gb >= 1.0) {
            return String.format(Locale.US, "%.1f GB", gb)
        }
        val mb = bytes / 1_000_000.0
        return String.format(Locale.US, "%.0f MB", mb)
    }

    /** 将剩余秒数格式化为 m:ss（超过 1 小时显示 h:mm:ss），用于下载 ETA 展示。 */
    @JvmStatic
    fun humanEta(seconds: Long): String {
        var s = seconds
        if (s < 0) {
            s = 0
        }
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.US, "%d:%02d", m, sec)
        }
    }

    /** 会话时间标签：刚刚 / N 分钟前 / N 小时前 / 昨天 / 日期。 */
    @JvmStatic
    fun timeLabel(epochMillis: Long, nowMillis: Long): String {
        if (epochMillis <= 0 || nowMillis <= 0) {
            return ""
        }
        val diff = nowMillis - epochMillis
        val minute = 60_000L
        val hour = 60 * minute
        if (diff < minute) {
            return "刚刚"
        }
        if (diff < hour) {
            return "${diff / minute} 分钟前"
        }
        if (diff < 24 * hour) {
            return "${diff / hour} 小时前"
        }
        if (diff < 48 * hour) {
            return "昨天"
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        return sdf.format(Date(epochMillis))
    }
}
