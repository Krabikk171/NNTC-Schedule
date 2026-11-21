package ru.krabikk.nntcschedule

import org.junit.Assert.*
import org.junit.Test

class SyncReportTest {
    @Test fun failureDoesNotCountAsSuccessfulCheck() {
        val failed = SyncReport.fromStorage("FAILED", 1234L, "PDF недоступен")!!
        assertFalse(failed.successful)
        assertEquals("Последняя проверка завершилась ошибкой", failed.title)
        assertEquals("PDF недоступен", failed.details)
    }

    @Test fun twoSuccessfulKindsAreDistinct() {
        val updated = SyncReport.fromStorage("UPDATED", 2000L, "")!!
        val unchanged = SyncReport.fromStorage("UNCHANGED", 2100L, null)!!
        assertTrue(updated.successful)
        assertTrue(unchanged.successful)
        assertEquals("Расписание обновлено", updated.title)
        assertEquals("Изменений не обнаружено", unchanged.title)
    }

    @Test fun ignoresStaleOrInvalidPreferences() {
        assertNull(SyncReport.fromStorage(null, 1234L, ""))
        assertNull(SyncReport.fromStorage("UNKNOWN", 1234L, ""))
        assertNull(SyncReport.fromStorage("FAILED", null, ""))
        assertNull(SyncReport.fromStorage("FAILED", -1L, ""))
    }

    @Test fun limitsErrorTextShownOnTheScreen() {
        val report = SyncReport.fromStorage("FAILED", 1L, "x".repeat(1000))!!
        assertEquals(240, report.details.length)
    }
}
