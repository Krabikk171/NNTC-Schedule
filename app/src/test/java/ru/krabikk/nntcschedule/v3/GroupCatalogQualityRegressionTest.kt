package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krabikk.nntcschedule.PdfTextItem

/** Проверки против двух реальных таблиц. Не подставляем кабинет, если его нет в PDF. */
class GroupCatalogQualityRegressionTest {
    private fun report(fileName: String): GroupCatalog.Report {
        val stream = requireNotNull(javaClass.getResourceAsStream("/$fileName"))
        val items = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { row ->
                val p = row.split('\t')
                require(p.size == 6)
                PdfTextItem(p[0].toInt(), p[1], p[2].toFloat(), p[3].toFloat(),
                    p[4].toFloat(), p[5].toFloat())
            }.toList()
        }
        return GroupCatalog.analyze(items)
    }

    private fun GroupCatalog.Report.lesson(group: String, date: String, number: Int) =
        groups.single { it.name == group }.rows.single {
            it.date.startsWith(date) && it.lesson.number == number
        }.lesson

    @Test fun recoversLastColumnClassroomsAcrossSeveralGroups() {
        val pdf = report("nntc_2026_11_18_19_glyphs.tsv")
        assertEquals("41", pdf.lesson("1МРП-26-2", "18 ноября", 6).room)
        assertEquals("54", pdf.lesson("1МРП-26-1", "19 ноября", 6).room)
        assertEquals("21", pdf.lesson("1МРП-26-3С", "19 ноября", 6).room)
        assertEquals("50", pdf.lesson("1ТМ-26-1КЗ", "19 ноября", 6).room)
        assertEquals("44", pdf.lesson("4ТЭО-23-1", "19 ноября", 6).room)
    }

    @Test fun adjacentGroupTailDoesNotCorruptHistorySubject() {
        val pdf = report("nntc_2026_11_18_19_glyphs.tsv")
        for (number in listOf(3, 4)) {
            val subject = pdf.lesson("1МРП-26-3С", "19 ноября", number).subject
            assertTrue(subject.startsWith("История Росии")) // орфография сохранена из PDF
            assertTrue(!subject.contains("подгруппа/Дубровина"))
            assertEquals("Балашов А.В.", pdf.lesson("1МРП-26-3С", "19 ноября", number).teacher)
        }
    }

    @Test fun restoresTruncatedParallelSubgroupLessonsOnlyFromSameRow() {
        val pdf = report("nntc_2026_11_18_19_glyphs.tsv")
        val lessons = listOf(1, 2, 3).map { pdf.lesson("2ОНС-25-2", "18 ноября", it) }
        assertTrue(lessons.all { it.room == "32/33" })
        assertTrue(lessons.all { it.subject == lessons.first().subject })
        assertTrue(lessons.all { it.teacher == "Котлярова Н.Н." })
        assertTrue(lessons.all { it.subject.contains("Каримова А.Э.") })
        assertEquals(GroupCatalog.Quality.STRUCTURAL_OK,
            pdf.groups.single { it.name == "2ОНС-25-2" }.quality)
        assertEquals(2, GroupSubgroupParser.parse(lessons.first()).size)
    }

    @Test fun remoteSelfStudyDoesNotRequireClassroomButRealMissingRoomIsFlagged() {
        val latest = report("nntc_2026_11_18_19_glyphs.tsv")
        val study = latest.lesson("1МРП-26-3С", "18 ноября", 4)
        assertTrue(study.subject.contains("самостоятельная работа"))
        assertEquals("", study.room)
        assertEquals(GroupCatalog.Quality.STRUCTURAL_OK,
            latest.groups.single { it.name == "1МРП-26-3С" }.quality)

        val earlier = report("nntc_2026_11_17_18_glyphs.tsv")
        val missing = earlier.lesson("1МРП-26-1", "17 ноября", 3)
        assertEquals("", missing.room)
        assertEquals(GroupCatalog.Quality.NEEDS_REVIEW,
            earlier.groups.single { it.name == "1МРП-26-1" }.quality)
    }
}
