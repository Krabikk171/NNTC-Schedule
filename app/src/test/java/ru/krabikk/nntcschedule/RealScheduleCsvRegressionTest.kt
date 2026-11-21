package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ground truth copied from the actual November 17–18, 2026 campus-2 PDF. */
class RealScheduleCsvRegressionTest {
    @Test fun november1726ThreePracticeLessonsRoundTrip() {
        val days = listOf(17, 18).map { day ->
            ParsedDay("$day ноября 2026 г.", if (day == 17) "ВТОРНИК" else "СРЕДА", listOf(
                ParsedLesson(1, "08:10–09:40", "54", "УП.04", "Федурин В.И."),
                ParsedLesson(2, "10:00–11:30", "54", "УП.04", "Федурин В.И."),
                ParsedLesson(3, "11:50–13:20", "54", "УП.04", "Федурин В.И.")
            ))
        }
        val csv = ScheduleCsvCodec.encode(days, "2РПУ-25-1")
        assertEquals(days, ScheduleCsvCodec.decode(csv, "2РПУ-25-1"))
        assertEquals(6, ScheduleCsvCodec.decode(csv, "2РПУ-25-1").sumOf { it.lessons.size })
    }
}
