package com.example.localai.common

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
