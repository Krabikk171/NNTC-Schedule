package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.krabikk.nntcschedule.PdfTextItem

/** Проверка на символах из PDF корпуса №2 за 17–18 ноября 2026.
 * Фикстура подготовлена из исходного PDF, позиции слегка отличаются
 * от Android PDFBox и НЕ заменяют испытание на устройстве. */
class RealGroupCatalogRegressionTest {
    private fun sample(): List<PdfTextItem> {
        val stream = requireNotNull(javaClass.getResourceAsStream("/nntc_2026_11_17_18_glyphs.tsv"))
        return stream.bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.map { line ->
                val parts = line.split('\t')
                require(parts.size == 6)
                PdfTextItem(parts[0].toInt(), parts[1], parts[2].toFloat(),
                    parts[3].toFloat(), parts[4].toFloat(), parts[5].toFloat())
            }.toList()
        }
    }

    @Test fun allGroupsAndReferenceSchedule() {
        val report = GroupCatalog.analyze(sample())
        assertEquals(32, report.groups.size)
        assertEquals(194, report.lessonCount)
        assertEquals(0, report.unsupportedCount)
        val main = report.groups.single { it.name == "2РПУ-25-1" }
        assertEquals(GroupCatalog.Quality.STRUCTURAL_OK, main.quality)
        assertEquals(6, main.rows.size)
        assertEquals(listOf(1,2,3,1,2,3), main.rows.map { it.lesson.number })
        assertTrue(main.rows.all { it.lesson.subject == "УП.04" && it.lesson.room == "54" && it.lesson.teacher == "Федурин В.И." })
        assertEquals("08:10–09:40", main.rows.first().lesson.time)
    }

    @Test fun mergedExamAndExceptionalGroupNames() {
        val report = GroupCatalog.analyze(sample())
        val examGroup = report.groups.single { it.name == "2ОНС-25-2" }
        val exam = examGroup.rows.single { it.date.startsWith("17 ") }
        assertEquals(4, exam.lesson.number)
        assertEquals("50", exam.lesson.room)
        assertTrue(exam.lesson.subject.startsWith("Экзамен/ПМ.01"))
        assertTrue(exam.lesson.teacher.contains("Груздев А.Л."))
        val unusual = report.groups.filter { it.name in setOf("26-1 ЭМ", "26-2 ЭМ", "26-4 МРП") }
        assertEquals(3, unusual.size)
        assertTrue(unusual.all { it.rows.isNotEmpty() })
        val em = unusual.single { it.name == "26-1 ЭМ" }
        assertEquals("10:50–12:20", em.rows.first().lesson.time)
        val mrp = unusual.single { it.name == "26-4 МРП" }
        assertEquals(2, mrp.rows.size)
        assertTrue(mrp.rows.all { it.lesson.teacher == "Федурин В.И." && it.lesson.subject.contains("вычислительной техники") })
    }

    @Test fun roomAndLongSubjectAreNotSwappedWithAdjacentRows() {
        val report = GroupCatalog.analyze(sample())
        val mrp = report.groups.single { it.name == "1МРП-26-2" }
        val russian = mrp.rows.filter { it.date.startsWith("17 ") }
        assertEquals(4, russian.size)
        assertTrue(russian.all { it.lesson.room == "1 корпус, 219" && it.lesson.subject == "Русский язык" })
        val ons = report.groups.single { it.name == "2ОНС-25-1" }
        assertTrue(ons.rows.filter { it.lesson.subject.startsWith("МДК.03.01") }
            .all { it.lesson.room == "105а" && it.lesson.teacher == "Попов А.Ю." })
        val pp = report.groups.single { it.name == "2ИКСИС-25-1" }
        assertTrue(pp.rows.all { it.lesson.subject == "ПП.06" })
        assertFalse(ons.rows.any { it.lesson.subject.contains("Иностранный язык") })
    }
}
