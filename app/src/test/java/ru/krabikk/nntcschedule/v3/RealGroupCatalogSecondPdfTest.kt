package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krabikk.nntcschedule.PdfTextItem

/** Regression snapshots from the actual PDFs for 17–18 and 18–19 November 2026. */
class RealGroupCatalogSecondPdfTest {
    private fun loadFixture(resource: String): List<PdfTextItem> =
        requireNotNull(javaClass.getResourceAsStream("/$resource")).bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                lines.map { line ->
                    val p = line.split('\t')
                    require(p.size == 6)
                    PdfTextItem(p[0].toInt(), p[1], p[2].toFloat(), p[3].toFloat(),
                        p[4].toFloat(), p[5].toFloat())
                }.toList()
            }

    @Test fun parsesBothDaysAndAllGroups() {
        val report = GroupCatalog.analyze(loadFixture("nntc_2026_11_18_19_glyphs.tsv"))
        assertEquals(32, report.groups.size)
        assertEquals(187, report.lessonCount)
        assertEquals(0, report.unsupportedCount)
        assertEquals(32, report.cleanCount)
        assertEquals(0, report.needReviewCount)
    }

    @Test fun referenceGroupStartsOnThirdLessonOnNovember19() {
        val report = GroupCatalog.analyze(loadFixture("nntc_2026_11_18_19_glyphs.tsv"))
        val lessons = report.groups.single { it.name == "2РПУ-25-1" }
        val on18 = lessons.rows.filter { it.date.startsWith("18 ноября") }
        val on19 = lessons.rows.filter { it.date.startsWith("19 ноября") }
        assertEquals(listOf(1, 2, 3), on18.map { it.lesson.number })
        assertEquals(listOf(3, 4, 5), on19.map { it.lesson.number })
        assertEquals(listOf("11:50–13:20", "13:30–15:00", "15:10–16:40"),
            on19.map { it.lesson.time })
        assertTrue(on19.all { it.lesson.subject == "УП.04" && it.lesson.room == "54" && it.lesson.teacher == "Федурин В.И." })
    }

    @Test fun sharedNovember18DataIsIdenticalBetweenPdfVersions() {
        val previous = GroupCatalog.analyze(loadFixture("nntc_2026_11_17_18_glyphs.tsv"))
        val newer = GroupCatalog.analyze(loadFixture("nntc_2026_11_18_19_glyphs.tsv"))
        val previousGroups = previous.groups.associateBy { it.name }
        assertEquals(previousGroups.keys, newer.groups.map { it.name }.toSet())
        for (group in newer.groups) {
            val a = previousGroups.getValue(group.name).rows.filter { it.date.startsWith("18 ноября") }
                .map { it.lesson }
            val b = group.rows.filter { it.date.startsWith("18 ноября") }.map { it.lesson }
            assertEquals("Изменения в группе ${group.name}", a, b)
        }
    }

    @Test fun mergedExamDoesNotProduceThreeFalseLessons() {
        val report = GroupCatalog.analyze(loadFixture("nntc_2026_11_18_19_glyphs.tsv"))
        val examDay = report.groups.single { it.name == "2ОНС-25-2" }
            .rows.filter { it.date.startsWith("19 ноября") }
        assertEquals(1, examDay.size)
        assertEquals(1, examDay.single().lesson.number)
        assertTrue(examDay.single().lesson.subject.startsWith("Экзамен/ПМ.02"))
    }
}
