package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krabikk.nntcschedule.PdfTextItem
import ru.krabikk.nntcschedule.ParsedLesson

class GroupCatalogTest {
    private fun row(page: Int, y: Float, text: String, startX: Float = 10f): List<PdfTextItem> =
        text.mapIndexed { i, ch ->
            PdfTextItem(page, ch.toString(), startX + i * 2.7f, y, 2.7f, 6f)
        }

    @Test
    fun discoverAllGroupNameFormatsAndDeduplicate() {
        val items = row(1, 100f, "2РПУ-25-1") +
            row(1, 120f, "1ТМ-26-1кз") +
            row(2, 100f, "26-4 МРП") +
            row(2, 140f, "2РПУ-25-1")
        val found = GroupCatalog.discoverGroups(items)
        assertEquals(3, found.size)
        assertTrue("2РПУ-25-1" in found)
        assertTrue("1ТМ-26-1КЗ" in found)
        assertTrue("26-4 МРП" in found)
    }

    @Test
    fun suspiciousSubjectsAndBlankTimeAreFlagged() {
        val bad = GroupCatalog.inspectLesson(
            ParsedLesson(3, "", "", "11:50 - 1 корпус, Математика", "")
        )
        assertTrue(bad.any { it.contains("время", ignoreCase = true) })
        assertTrue(bad.any { it.contains("часы", ignoreCase = true) })
    }

    @Test
    fun goodLessonHasNoStructuralErrors() {
        val good = GroupCatalog.inspectLesson(
            ParsedLesson(1, "08:10–09:40", "54", "УП.04", "Федурин В.И.")
        )
        assertEquals(emptyList<String>(), good)
    }

    @Test
    fun csvEscapesDelimiters() {
        val row = GroupCatalog.LessonRow("2РПУ-25-1", "17 ноября 2026 г.", "Вторник",
            ParsedLesson(1, "08:10–09:40", "54", "Тема, \"двойная\"", "Федурин В.И."), emptyList())
        val group = GroupCatalog.GroupResult("2РПУ-25-1", GroupCatalog.Quality.STRUCTURAL_OK,
            emptyList(), emptyList(), listOf(row))
        val csv = GroupCatalog.Report(listOf(group), 1).csv()
        assertTrue(csv.contains("\"Тема, \"\"двойная\"\"\""))
    }
}
