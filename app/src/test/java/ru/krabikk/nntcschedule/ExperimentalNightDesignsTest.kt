package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalNightDesignsTest {
    private val nightly = listOf(
        InterfaceStyle.LAYERS_1, InterfaceStyle.ROUTE_2,
        InterfaceStyle.FOCUS_1, InterfaceStyle.ORBIT_1,
        InterfaceStyle.LAYERS_2, InterfaceStyle.HOLOGRAM_1
    )

    @Test fun nightlyStylesAreSelectableOnlyFromDeveloperMenu() {
        nightly.forEach {
            assertEquals(it, InterfaceStyle.normalized(it))
            assertTrue(ExperimentalDesigns.isExperimental(it))
        }
        listOf(InterfaceStyle.MINIMAL, InterfaceStyle.AURORA,
            InterfaceStyle.NEON, InterfaceStyle.LAVENDER, InterfaceStyle.OCEAN).forEach {
            assertFalse(ExperimentalDesigns.isExperimental(it))
        }
    }

    @Test fun originalRoutesWereNotLostAndIdentifiersAreUnique() {
        assertTrue(ExperimentalDesigns.isExperimental(InterfaceStyle.ROUTE))
        assertTrue(ExperimentalDesigns.isExperimental(InterfaceStyle.ROUTE_1))
        assertEquals(18, ExperimentalDesigns.available.size)
        assertEquals(18, ExperimentalDesigns.available.map { it.style }.toSet().size)
        assertEquals(InterfaceStyle.MINIMAL, InterfaceStyle.normalized("unknown-theme"))
    }

    @Test fun subgroupRowsHaveDistinctStableKeysAndKeepRealData() {
        val original = ParsedLesson(2, "10:00–11:30", "41/21", "", "", listOf(
            ParsedSubgroup(1, "Информатика", "41", "Ивкова М.Н."),
            ParsedSubgroup(2, "Черчение", "21", "Дубровина Л.И.")
        ))
        val rows = experimentalDayRows(20261120, listOf(original))
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.stableId }.distinct().size)
        assertEquals("41", rows[0].room)
        assertEquals("21", rows[1].room)
        assertEquals("Дубровина Л.И.", rows[1].teacher)
    }

    @Test fun timeParserDoesNotInventAbsentTimes() {
        val (a, b) = experimentalTimeRange("11:50–13:20")
        assertEquals("11:50", a.toString())
        assertEquals("13:20", b.toString())
        assertEquals(null, experimentalTimeRange("—").first)
        assertEquals(null, experimentalTimeRange("25:90–29:10").first)
    }
}
