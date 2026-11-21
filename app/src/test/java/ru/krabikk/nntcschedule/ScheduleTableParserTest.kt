package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krabikk.nntcschedule.v3.ScheduleTableParser

/** Искусственные страницы, повторяющие структуру PDF второго корпуса. */
class ScheduleTableParserTest {
    private val starts = listOf(70f, 270f, 470f, 670f, 870f, 1070f)

    private fun MutableList<PdfTextItem>.put(page: Int, value: String, x: Float, y: Float) {
        value.forEachIndexed { index, letter ->
            add(PdfTextItem(page, letter.toString(), x + index * 2.6f, y, 2.6f, 8f))
        }
    }

    private fun makeNormalPage(page: Int, day: Int): List<PdfTextItem> = buildList {
        put(page, "Расписание занятий на $day ноября 2026 г. (ВТОРНИК)", 10f, 15f)
        starts.forEachIndexed { i, x -> put(page, "${i + 1} пара", x + 63f, 45f) }
        listOf(100f, 145f, 190f, 235f).forEachIndexed { rowIndex, y ->
            if (rowIndex == 2) {
                put(page, "2РПУ-25-", 10f, y - 5f)
                put(page, "1", 10f, y + 3f)
            } else put(page, "2ТМ-25-$rowIndex", 10f, y)
            val halves = listOf("8.10-" to "9.40", "10:00-" to "11:30", "11:50 -" to "13:20", "13:30-" to "15:00", "15:10-" to "16:40", "16:50-" to "18:20")
            starts.forEachIndexed { i, x ->
                put(page, halves[i].first, x + 3f, y - 7f)
                put(page, halves[i].second, x + 3f, y + 1f)
                if (rowIndex == 2 && i < 3) {
                    put(page, "54", x + 37f, y - 1f)
                    put(page, "УП.04/Федурин В.И.", x + 62f, y + 5f)
                }
                if (rowIndex != 2 && i < 3) put(page, "Чужой предмет", x + 62f, y + 5f)
            }
        }
    }

    @Test
    fun parsesOnlyTargetGroupAndKeepsOriginalPairNumbers() {
        val input = makeNormalPage(1, 17) + makeNormalPage(2, 18)
        val result = ScheduleTableParser.parse(input)
        assertTrue(result.error.orEmpty(), result.isValid)
        assertEquals(2, result.days.size)
        result.days.forEach { day ->
            assertEquals(listOf(1, 2, 3), day.lessons.map { it.number })
            assertEquals(listOf("08:10–09:40", "10:00–11:30", "11:50–13:20"), day.lessons.map { it.time })
            assertTrue(day.lessons.all { it.subject == "УП.04" && it.room == "54" && it.teacher == "Федурин В.И." })
        }
    }

    @Test
    fun usesSpecialWednesdayBellTableWhenRowTimesAreEmpty() {
        val items = mutableListOf<PdfTextItem>()
        items.put(1, "ЗВОНКИ НА 23.09.2026 (СРЕДА)", 100f, 15f)
        listOf("08.00-08.50", "9.00-10.30", "10.50-12-20", "12.30-14.00", "14.10-15.40", "15.50-17.20")
            .forEachIndexed { index, time -> items.put(1, "${index + 1} ПАРА $time", 100f, 35f + index * 12f) }
        items.put(1, "Расписание занятий на 23 сентября 2026 г. (СРЕДА)", 10f, 115f)
        starts.forEachIndexed { index, x ->
            items.put(1, "Вре", x + 4f, 140f)
            items.put(1, "мя", x + 4f, 150f)
            items.put(1, "${index + 1} пара", x + 63f, 140f)
        }
        items.put(1, "2ТМ-25-1", 10f, 205f)
        items.put(1, "2РПУ-25-1", 10f, 250f)
        items.put(1, "2ОНС-25-1", 10f, 300f)
        for (i in 1..3) {
            items.put(1, "29", starts[i] + 35f, 250f)
            items.put(1, "Основы электротехники/Казанцева С.Б.", starts[i] + 62f, 258f)
        }
        val result = ScheduleTableParser.parse(items)
        assertTrue(result.error.orEmpty(), result.isValid)
        val lessons = result.days.single().lessons
        assertEquals(listOf(2, 3, 4), lessons.map { it.number })
        assertEquals(listOf("09:00–10:30", "10:50–12:20", "12:30–14:00"), lessons.map { it.time })
        assertEquals("Основы электротехники", lessons[0].subject)
    }

    @Test
    fun refusesIncompletePdfInsteadOfOverwritingDatabase() {
        val wrongGroup = makeNormalPage(1, 17).filterNot {
            it.y > 160f && it.y < 220f
        }
        val result = ScheduleTableParser.parse(wrongGroup)
        assertFalse(result.isValid)
        assertTrue(result.error.orEmpty().isNotBlank())
    }
    @Test
    fun findsGroupEvenWhenAnotherTimeHeaderIsToItsLeft() {
        // Регрессия: старая locateGroups() выбирала «левую границу» по самому
        // левому слову «Время». Если это слово левее группы, всё обрезалось.
        val items = makeNormalPage(1, 17).toMutableList()
        items.put(1, "Время", 3f, 36f)
        val result = ScheduleTableParser.parse(items)
        assertTrue(result.error.orEmpty(), result.isValid)
        assertEquals(3, result.days.single().lessons.size)
    }

    @Test
    fun doesNotMistakeDigitFromNearbyClockForGroupSuffix() {
        // «2РПУ-25-» на Y=185 и «8.10-» на той же линии (X=73) не должны
        // превращаться в несуществующую группу «2РПУ-25-8».
        val result = ScheduleTableParser.parse(makeNormalPage(1, 17))
        assertTrue(result.error.orEmpty(), result.isValid)
        assertEquals(listOf(1, 2, 3), result.days.single().lessons.map { it.number })
    }

    @Test
    fun errorsRetainDetailedDiagnostics() {
        val withoutGroups = makeNormalPage(1, 17).filter { it.y < 80f }
        val result = ScheduleTableParser.parse(withoutGroups)
        assertFalse(result.isValid)
        assertTrue(result.error.orEmpty().contains("Старое расписание сохранено"))
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun handlesTwoDatesOnPageOneAndContinuationWithoutHeaderOnPageTwo() {
        val firstPage = makeNormalPage(1, 17).toMutableList()
        firstPage.put(1, "Расписание занятий на 18 ноября 2026 г. (СРЕДА)", 10f, 350f)
        firstPage.put(1, "1ТМ-26-1", 10f, 400f)
        val continuingPage = makeNormalPage(2, 18).filter { it.y > 75f }
        val result = ScheduleTableParser.parse(firstPage + continuingPage)
        assertTrue(result.error.orEmpty(), result.isValid)
        assertEquals(listOf("17 ноября 2026 г.", "18 ноября 2026 г."), result.days.map { it.date })
        assertEquals(listOf(3, 3), result.days.map { it.lessons.size })
        assertTrue(result.days.all { day -> day.lessons.all { it.subject == "УП.04" } })
    }

    @Test
    fun ignoresTextSpillingIntoNearbyEmptyLessonColumn() {
        val input = makeNormalPage(1, 17).toMutableList()
        // Текст из строки выше визуально свисает в пустую 4-ю пару
        // целевой группы; это НЕ дополнительное занятие.
        input.put(1, "чужой фрагмент/Преподаватель А.А.", starts[3] + 62f, 178f)
        val result = ScheduleTableParser.parse(input)
        assertTrue(result.error.orEmpty(), result.isValid)
        assertEquals(listOf(1, 2, 3), result.days.single().lessons.map { it.number })
    }

}
