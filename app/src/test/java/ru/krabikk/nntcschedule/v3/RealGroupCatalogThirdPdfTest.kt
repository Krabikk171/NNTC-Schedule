package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krabikk.nntcschedule.PdfTextItem

/** The actual UK2 timetable published for 19–21 November 2026 (three days, three PDF pages). */
class RealGroupCatalogThirdPdfTest {
    private fun report(file: String = "nntc_2026_11_19_20_21_glyphs.tsv"): GroupCatalog.Report {
        val stream = requireNotNull(javaClass.getResourceAsStream("/$file"))
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

    private fun GroupCatalog.Report.rows(group: String, date: String) =
        groups.single { it.name == group }.rows.filter { it.date.startsWith(date) }.map { it.lesson }

    @Test fun allGroupsAndAllThreeDays() {
        val r = report()
        assertEquals(32, r.groups.size)
        assertEquals(248, r.lessonCount)
        assertEquals(32, r.cleanCount)
        assertEquals(0, r.needReviewCount)
        assertEquals(0, r.unsupportedCount)
        assertEquals(setOf("19 ноября", "20 ноября", "21 ноября"),
            r.groups.flatMap { it.rows }.map { it.date.substringBefore(" 2026") }.toSet())
    }

    @Test fun theReferenceGroupHasExactlyThePrintedLessons() {
        val r = report()
        for ((date, expected) in listOf(
            "19 ноября" to listOf(3, 4, 5),
            "20 ноября" to listOf(1, 2, 3),
            "21 ноября" to listOf(1, 2, 3)
        )) {
            val lessons = r.rows("2РПУ-25-1", date)
            assertEquals("date: $date", expected, lessons.map { it.number })
            assertTrue(lessons.all { it.subject == "УП.04" && it.room == "54" && it.teacher == "Федурин В.И." })
        }
    }

    @Test fun completelyEmptyTimetableRowsAreNotFailures() {
        val r = report()
        assertEquals(emptyList<Any>(), r.rows("1РПУ-26-1", "21 ноября"))
        assertEquals(GroupCatalog.Quality.STRUCTURAL_OK,
            r.groups.single { it.name == "1РПУ-26-1" }.quality)
        assertEquals(emptyList<Any>(), r.rows("1ИКСИС-26-1", "20 ноября"))
    }

    @Test fun neighboringLongSubjectsDoNotLeakIntoTargetGroup() {
        val r = report()
        val chemistry = r.rows("2ОНС-25-1", "20 ноября")[0]
        assertEquals("Химия", chemistry.subject)
        assertEquals("Чублукова Л.Б.", chemistry.teacher)
        assertFalse(chemistry.subject.contains("МДК."))
        assertFalse(chemistry.subject.contains("Смирнова"))

        val economics = r.rows("4ИКСИС-23-1", "21 ноября").single { it.number == 4 }
        assertEquals("Основы экономики отрасли", economics.subject)
        assertEquals("Чуб К.М.", economics.teacher)

        val fourth = r.rows("1ТМ-26-1КЗ", "20 ноября").single { it.number == 4 }
        assertEquals("Допуски и технические измерения", fourth.subject)
        assertEquals("Халидов Т.Р.", fourth.teacher)
    }

    @Test fun preservesLetteredRoomAndSeparateSubgroups() {
        val r = report()
        val workshop = r.rows("2ОНС-25-2", "20 ноября").first()
        assertEquals("105а", workshop.room)
        assertFalse(workshop.subject.contains("на а фрезерных"))

        val lesson = r.rows("1МРП-26-2", "19 ноября").first()
        val parts = GroupSubgroupParser.parse(lesson)
        assertEquals(2, parts.size)
        assertEquals(setOf("41", "21"), parts.map { it.room }.toSet())
    }

    @Test fun november19RevisionIsNotMistakenForParserCorruption() {
        val before = report("nntc_2026_11_18_19_glyphs.tsv")
        val after = report()
        assertEquals(before.rows("2РПУ-25-1", "19 ноября"), after.rows("2РПУ-25-1", "19 ноября"))
        val oldNumbers = before.rows("4ТЭО-23-1", "19 ноября").map { it.number }
        val newNumbers = after.rows("4ТЭО-23-1", "19 ноября").map { it.number }
        assertEquals(listOf(4, 5, 6), oldNumbers)
        assertEquals(listOf(3, 4), newNumbers)
    }
}
