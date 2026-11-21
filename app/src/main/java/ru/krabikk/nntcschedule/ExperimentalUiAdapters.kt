package ru.krabikk.nntcschedule

import java.time.LocalTime

internal data class ExperimentalDayRow(
    val stableId: String,
    val original: ParsedLesson,
    val subgroupNumber: Int?,
    val subject: String,
    val room: String,
    val teacher: String
)

internal fun experimentalDayRows(dateKey: Int, lessons: List<ParsedLesson>): List<ExperimentalDayRow> =
    lessons.flatMapIndexed { index, lesson ->
        if (lesson.subgroups.isEmpty()) {
            listOf(ExperimentalDayRow("$dateKey:$index:0", lesson, null,
                lesson.subject, lesson.room, lesson.teacher))
        } else lesson.subgroups.mapIndexed { subgroupIndex, subgroup ->
            ExperimentalDayRow("$dateKey:$index:${subgroupIndex + 1}", lesson, subgroup.number,
                subgroup.subject, subgroup.room, subgroup.teacher)
        }
    }

private val timePattern = Regex("""(\d{1,2})[:.](\d{2})\s*[-–—]\s*(\d{1,2})[:.](\d{2})""")
internal fun experimentalTimeRange(time: String): Pair<LocalTime?, LocalTime?> {
    val m = timePattern.find(time) ?: return null to null
    val from = runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
    val to = runCatching { LocalTime.of(m.groupValues[3].toInt(), m.groupValues[4].toInt()) }.getOrNull()
    return from to to
}
