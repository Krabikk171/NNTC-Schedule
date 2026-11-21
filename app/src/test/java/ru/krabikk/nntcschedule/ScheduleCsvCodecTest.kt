package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCsvCodecTest {
    @Test
    fun csvRoundTripKeepsNamesCommaQuotesAndNewlines() {
        val days = listOf(
            ParsedDay("18 ноября 2026 г.", "СРЕДА", listOf(
                ParsedLesson(2, "10:00–11:30", "54", "Сборка, настройка и \"контроль\"", "Федурин В.И."),
                ParsedLesson(4, "13:30–15:00", "", "Строка 1\nСтрока 2", "")
            ))
        )
        val csv = ScheduleCsvCodec.encode(days, "2РПУ-25-1")
        assertEquals(days, ScheduleCsvCodec.decode(csv, "2РПУ-25-1"))
    }

    @Test
    fun refusesCsvForOtherGroup() {
        val data = ScheduleCsvCodec.encode(listOf(
            ParsedDay("17 ноября 2026 г.", "ВТОРНИК", listOf(
                ParsedLesson(1, "08:10–09:40", "54", "УП.04", "Федурин В.И.")
            ))
        ), "2ТМ-25-1")
        var rejected = false
        try { ScheduleCsvCodec.decode(data, "2РПУ-25-1") }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
}
