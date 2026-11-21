package ru.krabikk.nntcschedule

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Only the presentation layer. Dates are obtained from already-saved lessons.
 * Does not fetch or interpret PDFs and does not modify Room.
 *
 * Keys are yyyyMMdd (not timestamps), so the selection survives timezone changes
 * and daylight-saving time shifts without moving to an adjacent date.
 */
object ScheduleDayStrip {
    private val russian = Locale.forLanguageTag("ru-RU")
    private val weekdays = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")

    data class Day(
        val key: Int,
        val weekday: String,
        val dayAndMonth: String,
        val isToday: Boolean
    )

    fun keyFromScheduleDate(raw: String): Int? {
        val input = raw.trim()
        val formatter = SimpleDateFormat("d MMMM yyyy 'г.'", russian).apply { isLenient = false }
        val pos = ParsePosition(0)
        val parsed = formatter.parse(input, pos) ?: return null
        if (pos.index != input.length) return null
        return keyFromCalendar(Calendar.getInstance().apply { time = parsed })
    }

    fun todayKey(nowMillis: Long = System.currentTimeMillis()): Int =
        keyFromCalendar(Calendar.getInstance().apply { timeInMillis = nowMillis })

    /** Prefer today when present, otherwise the next known day, otherwise the last known day. */
    fun initialSelection(storedDates: Collection<String>, today: Int): Int {
        val keys = storedDates.mapNotNull(::keyFromScheduleDate).distinct().sorted()
        return keys.firstOrNull { it >= today } ?: keys.lastOrNull() ?: today
    }

    fun nextSavedDate(savedKeys: Collection<Int>, selectedKey: Int): Int? =
        savedKeys.filter { it > selectedKey }.minOrNull()

    fun shift(key: Int, days: Int): Int = calendarForKey(key).apply {
        add(Calendar.DAY_OF_MONTH, days)
    }.let(::keyFromCalendar)

    fun monday(key: Int): Int = calendarForKey(key).apply {
        val offset = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        add(Calendar.DAY_OF_MONTH, -offset)
    }.let(::keyFromCalendar)

    fun week(selectedKey: Int, todayKey: Int): List<Day> {
        val monday = monday(selectedKey)
        return (0..6).map { index ->
            val calendar = calendarForKey(monday).apply { add(Calendar.DAY_OF_MONTH, index) }
            val key = keyFromCalendar(calendar)
            Day(
                key = key,
                weekday = weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1],
                dayAndMonth = SimpleDateFormat("d MMM", russian).format(calendar.time),
                isToday = key == todayKey
            )
        }
    }

    fun weekLabel(selectedKey: Int): String {
        val monday = calendarForKey(monday(selectedKey))
        val sunday = calendarForKey(shift(monday(selectedKey), 6))
        val short = SimpleDateFormat("d MMM", russian)
        val long = SimpleDateFormat("d MMMM", russian)
        return if (monday.get(Calendar.MONTH) == sunday.get(Calendar.MONTH) &&
            monday.get(Calendar.YEAR) == sunday.get(Calendar.YEAR)
        ) {
            "${monday.get(Calendar.DAY_OF_MONTH)}–${long.format(sunday.time)}"
        } else {
            "${short.format(monday.time)} – ${short.format(sunday.time)}"
        }
    }

    fun displayDate(key: Int): String =
        SimpleDateFormat("d MMMM yyyy 'г.'", russian).format(calendarForKey(key).time)

    private fun keyFromCalendar(c: Calendar): Int =
        c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

    private fun calendarForKey(key: Int): Calendar = Calendar.getInstance().apply {
        clear()
        isLenient = false
        set(key / 10_000, key / 100 % 100 - 1, key % 100, 12, 0, 0)
        // Force validation rather than silently accepting invalid yyyyMMdd keys.
        timeInMillis
    }
}
