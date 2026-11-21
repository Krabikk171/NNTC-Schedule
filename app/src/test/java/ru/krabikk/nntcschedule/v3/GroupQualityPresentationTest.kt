package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupQualityPresentationTest {
    @Test fun cleanGroupsDoNotProduceAlarmingMessages() {
        val good = GroupQualityPresentation.forEntry("STRUCTURAL_OK", 9, 0)
        assertFalse(good.needsAttention)
        assertEquals("9 пар в сохранённом PDF", good.detail)
        assertEquals(0, GroupQualityPresentation.flaggedGroupCount(listOf("STRUCTURAL_OK", "LEGACY")))
    }

    @Test fun legacyCacheWarningsAreHonestButNotRepeatedInEachRow() {
        val bad = GroupQualityPresentation.forEntry("NEEDS_REVIEW", 5, 2)
        assertTrue(bad.needsAttention)
        assertEquals("5 пар · 2 замечания", bad.detail)
        assertFalse(bad.detail.contains("сверяй с PDF"))
        assertEquals(2, GroupQualityPresentation.flaggedGroupCount(
            listOf("NEEDS_REVIEW", "STRUCTURAL_OK", "UNSUPPORTED")))
    }

    @Test fun singularAndPluralRussianWarningCounts() {
        assertEquals("1 пара · 1 замечание", GroupQualityPresentation.forEntry("NEEDS_REVIEW", 1, 1).detail)
        assertEquals("4 пары · 4 замечания", GroupQualityPresentation.forEntry("NEEDS_REVIEW", 4, 4).detail)
        assertEquals("12 пар · 12 замечаний", GroupQualityPresentation.forEntry("NEEDS_REVIEW", 12, 12).detail)
        assertEquals("5 пар · 21 замечание", GroupQualityPresentation.forEntry("NEEDS_REVIEW", 5, 21).detail)
    }

    @Test fun unsupportedIsStillClearlyMarked() {
        val unsupported = GroupQualityPresentation.forEntry("UNSUPPORTED", 0, 0)
        assertTrue(unsupported.needsAttention)
        assertEquals("Не удалось надёжно распознать расписание", unsupported.detail)
    }

    @Test fun brokenPdfCannotOverwriteCleanCatalog() {
        assertFalse(GroupCatalogImportPolicy.mayReplace(32, 200, 1))
        assertFalse(GroupCatalogImportPolicy.mayReplace(32, 0, 32))
        assertFalse(GroupCatalogImportPolicy.mayReplace(6, 120, 6))
        assertTrue(GroupCatalogImportPolicy.mayReplace(32, 248, 32))
        assertTrue(GroupCatalogImportPolicy.mayReplace(32, 180, 25))
    }
}
