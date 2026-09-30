package com.example.localai.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class FmtTest {

    @Test
    fun etaSecondsFormatsAsClock() {
        assertEquals("0:47", Fmt.humanEta(47))
        assertEquals("1:02", Fmt.humanEta(62))
        assertEquals("12:05", Fmt.humanEta(725))
    }

    @Test
    fun etaHoursIncludedWhenAtLeastOneHour() {
        assertEquals("1:00:00", Fmt.humanEta(3600))
        assertEquals("2:30:15", Fmt.humanEta(9015))
    }

    @Test
    fun etaClampsNegativesToZero() {
        assertEquals("0:00", Fmt.humanEta(-1))
        assertEquals("0:00", Fmt.humanEta(0))
    }

    @Test
    fun humanBytesFormatsGbAndMb() {
        assertEquals("1.5 GB", Fmt.humanBytes(1_500_000_000L))
        assertEquals("850 MB", Fmt.humanBytes(850_000_000L))
        assertEquals("0 MB", Fmt.humanBytes(1))
    }

    /** 用本地时区构造固定时刻，避免测试依赖运行机器的时区设置。 */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun timeLabelCoversJustNowMinutesHoursAndYesterday() {
        val now = at(2026, 9, 30, 12, 0)
        assertEquals("刚刚", Fmt.timeLabel(now - 30_000L, now))
        assertEquals("5 分钟前", Fmt.timeLabel(now - 5 * 60_000L, now))
        assertEquals("3 小时前", Fmt.timeLabel(now - 3 * 3_600_000L, now))
        assertEquals("昨天", Fmt.timeLabel(at(2026, 9, 29, 11, 0), now))
    }

    @Test
    fun timeLabelYesterdayUsesCalendarDayNotElapsedWindow() {
        // 今天 00:30 看前天 23:45：间隔 24 小时 45 分，仍属前天，不能标成「昨天」
        val now = at(2026, 9, 30, 0, 30)
        assertEquals("2026-09-28", Fmt.timeLabel(at(2026, 9, 28, 23, 45), now))
    }

    @Test
    fun timeLabelFallsBackToIsoDateAndRejectsInvalidInput() {
        val now = at(2026, 9, 30, 12, 0)
        assertEquals("2026-09-27", Fmt.timeLabel(at(2026, 9, 27, 9, 0), now))
        assertEquals("", Fmt.timeLabel(0L, now))
        assertEquals("", Fmt.timeLabel(now, 0L))
    }
}
