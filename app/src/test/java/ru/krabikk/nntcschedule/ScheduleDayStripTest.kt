package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDayStripTest {
    @Test fun parsesActualScheduleDates() {
        assertEquals(20261117, ScheduleDayStrip.keyFromScheduleDate("17 ноября 2026 г."))
        assertEquals(20261118, ScheduleDayStrip.keyFromScheduleDate("18 ноября 2026 г."))
        assertNull(ScheduleDayStrip.keyFromScheduleDate("31 ноября 2026 г."))
        assertNull(ScheduleDayStrip.keyFromScheduleDate("среда"))
    }

    @Test fun startsWeekOnMonday() {
        val week = ScheduleDayStrip.week(20261118, 20261118)
        assertEquals(listOf(20261116, 20261117, 20261118, 20261119, 20261120, 20261121, 20261122), week.map { it.key })
        assertEquals("Пн", week.first().weekday)
        assertEquals("Ср", week[2].weekday)
        assertTrue(week[2].isToday)
        assertEquals("16–22 ноября", ScheduleDayStrip.weekLabel(20261118))
    }

    @Test fun handlesYearBoundary() {
        assertEquals(20261228, ScheduleDayStrip.monday(20270101))
        assertEquals(20270101, ScheduleDayStrip.shift(20261231, 1))
        assertEquals(20260228, ScheduleDayStrip.shift(20260301, -1))
        assertEquals(20240229, ScheduleDayStrip.shift(20240301, -1))
    }

    @Test fun findsReasonableInitiallyVisibleDate() {
        val dates = listOf("17 ноября 2026 г.", "18 ноября 2026 г.")
        assertEquals(20261117, ScheduleDayStrip.initialSelection(dates, 20261117))
        assertEquals(20261118, ScheduleDayStrip.initialSelection(dates, 20261118))
        assertEquals(20261117, ScheduleDayStrip.initialSelection(dates, 20261116))
        assertEquals(20261118, ScheduleDayStrip.initialSelection(dates, 20261119))
        assertEquals(20261119, ScheduleDayStrip.initialSelection(emptyList(), 20261119))
        assertEquals(20261118, ScheduleDayStrip.nextSavedDate(listOf(20261118, 20261117), 20261117))
        assertNull(ScheduleDayStrip.nextSavedDate(listOf(20261118), 20261118))
    }
}
