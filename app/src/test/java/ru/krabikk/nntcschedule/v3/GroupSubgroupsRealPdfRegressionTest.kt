package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krabikk.nntcschedule.PdfTextItem

class GroupSubgroupsRealPdfRegressionTest {
    private fun report(file: String): GroupCatalog.Report {
        val items = requireNotNull(javaClass.getResourceAsStream("/$file"))
            .bufferedReader(Charsets.UTF_8).useLines { rows ->
                rows.map { row ->
                    val p = row.split('\t')
                    PdfTextItem(p[0].toInt(), p[1], p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat())
                }.toList()
            }
        return GroupCatalog.analyze(items)
    }

    @Test fun secondPdfAllThreeComplexGroupsHaveSeparateSubgroups() {
        val result = report("nntc_2026_11_18_19_glyphs.tsv")
        val expected = listOf("1ИКСиС-26-1", "1МРП-26-2", "2ОНС-25-2")
        for (name in expected) {
            val group = result.groups.single { it.name.equals(name, ignoreCase = true) }
            val multi = group.rows.filter { GroupSubgroupParser.hasMultipleSubgroups(it.lesson) }
            assertTrue("В тестовом PDF не найдены параллельные занятия $name", multi.isNotEmpty())
            assertTrue("Потеряна подгруппа: $name", multi.all { row ->
                val parts = GroupSubgroupParser.parse(row.lesson)
                parts.size == 2 && parts.all { it.subject.isNotBlank() && it.teacher.isNotBlank() && it.room.isNotBlank() }
            })
        }
    }

    @Test fun existingReferenceGroupStillHasSixUnsplitLessons() {
        val result = report("nntc_2026_11_17_18_glyphs.tsv")
        val rows = result.groups.single { it.name == "2РПУ-25-1" }.rows
        assertEquals(6, rows.size)
        assertTrue(rows.all { it.lesson.room == "54" && it.lesson.subject == "УП.04" })
        assertTrue(rows.none { GroupSubgroupParser.hasMultipleSubgroups(it.lesson) })
    }
}
