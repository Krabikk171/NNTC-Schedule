package ru.krabikk.nntcschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar

/** One clock for both the day-strip and multi-day presentations, refreshed each half-minute. */
@Composable
internal fun routeRememberClockMillis(): Long {
    var current by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            current = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    return current
}

internal data class RouteArrival(val index: Int, val label: String, val isOngoing: Boolean)

/** The countdown is NEVER displayed for dates other than device-local today.
 * Missing/malformed time never produces a fabricated arrival estimate.
 * The closest future start is prioritized, like the next bus at a stop.
 */
internal fun routeArrivalFor(
    lessons: List<ParsedLesson>,
    selectedDateKey: Int?,
    clockMillis: Long
): RouteArrival? {
    if (selectedDateKey == null) return null
    val todayKey = ScheduleDayStrip.todayKey(clockMillis)
    if (selectedDateKey > todayKey) {
        // The viewed future day starts with its first station, not a fake live countdown.
        val timeRegex = Regex("""\b(?:[01]?\d|2[0-3]):[0-5]\d\b""")
        val first = lessons.mapIndexedNotNull { index, lesson ->
            val time = timeRegex.find(lesson.time)?.value ?: return@mapIndexedNotNull null
            val start = routeParseMinutes(time) ?: return@mapIndexedNotNull null
            Triple(index, start, time)
        }.minByOrNull { it.second }
        return first?.let { RouteArrival(it.first, "Первая пара · ${it.third}", false) }
    }
    if (selectedDateKey != todayKey) return null
    val calendar = Calendar.getInstance().apply { timeInMillis = clockMillis }
    val now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val re = Regex("""\b(?:[01]?\d|2[0-3]):[0-5]\d\b""")
    val times = lessons.mapIndexedNotNull { i, l ->
        val m = re.findAll(l.time).map { it.value }.toList()
        val from = m.getOrNull(0)?.let(::routeParseMinutes) ?: return@mapIndexedNotNull null
        val to = m.getOrNull(1)?.let(::routeParseMinutes)
        Triple(i, from, to)
    }
    val next = times.filter { it.second > now }.minByOrNull { it.second }
    if (next != null) {
        return RouteArrival(next.first, "Через ${routeCountdown(next.second - now)}", false)
    }
    val ongoing = times.firstOrNull { (_, start, end) -> end != null && start <= now && now < end }
    return ongoing?.let { RouteArrival(it.first, "Идёт сейчас · ещё ${routeCountdown(it.third!! - now)}", true) }
}

/** The timetable has no next arrival after its final confirmed end time.
 * Show an explicit completed state instead of suggesting that countdown is broken.
 * Never classify a different calendar date as today's completed route.
 */
internal fun routeFinishedToday(
    lessons: List<ParsedLesson>, selectedDateKey: Int?, clockMillis: Long
): Boolean {
    if (lessons.isEmpty() || selectedDateKey == null ||
        selectedDateKey != ScheduleDayStrip.todayKey(clockMillis)) return false
    val calendar = Calendar.getInstance().apply { timeInMillis = clockMillis }
    val now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val re = Regex("""\b(?:[01]?\d|2[0-3]):[0-5]\d\b""")
    val ends = lessons.mapNotNull { lesson ->
        val matches = re.findAll(lesson.time).map { it.value }.toList()
        matches.getOrNull(1)?.let(::routeParseMinutes)
    }
    // Do not claim the day has ended if some lessons have unknown times.
    return ends.size == lessons.size && ends.isNotEmpty() && now >= ends.maxOrNull()!!
}

private fun routeParseMinutes(raw: String): Int? {
    val p = raw.split(':')
    val h = p.getOrNull(0)?.toIntOrNull() ?: return null
    val m = p.getOrNull(1)?.toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

internal fun routeCountdown(minutes: Int): String = when {
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0 -> "${minutes / 60} ч"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}

@Composable
internal fun RouteArrivalPill(label: String?, accent: Color, ink: Color) {
    if (label == null) return
    Text(
        text = "● $label",
        color = ink,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(accent.copy(alpha = .19f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
