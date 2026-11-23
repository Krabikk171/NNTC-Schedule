package ru.krabikk.nntcschedule.v3

import org.junit.Assert.*
import org.junit.Test
import ru.krabikk.nntcschedule.PdfTextItem

class CampusRoomRegressionTest {
    private val items = requireNotNull(javaClass.getResourceAsStream("/nntc_2026_11_23_24_glyphs.tsv"))
        .bufferedReader().useLines { lines -> lines.map { row ->
            val p = row.split('\t')
            PdfTextItem(p[0].toInt(), p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat())
        }.toList() }

    @Test fun missingRoomNumberRejectsTheDamagedSchedule() {
        // Remove only the printed 409 below the first campus label on page 2.
        val damaged = items.filterNot { it.page == 2 && it.x in 58f..69f && it.y in 274f..275f }
        assertEquals(3, items.size - damaged.size)
        val result = ScheduleTableParser.parse(damaged)
        assertFalse(result.isValid)
        assertTrue(result.days.isEmpty())
    }

    @Test fun campusAndRoomRemainTogetherAcrossTwoPeriods() {
        val result = ScheduleTableParser.parse(items)
        assertTrue(result.error, result.isValid)
        val tuesday = result.days.single { it.date.startsWith("24 ноября") }.lessons
        assertEquals(listOf(1, 2), tuesday.map { it.number })
        assertEquals(listOf("08:10–09:40", "10:00–11:30"), tuesday.map { it.time })
        for (lesson in tuesday) {
            assertEquals("1 корпус, 409", lesson.room)
            assertEquals("Информатика и вычислительная техника", lesson.subject)
            assertEquals("Булатова Т.М.", lesson.teacher)
        }
        val monday = result.days.single { it.date.startsWith("23 ноября") }.lessons
        assertEquals(listOf("46", "46", "34"), monday.map { it.room })
    }
}
