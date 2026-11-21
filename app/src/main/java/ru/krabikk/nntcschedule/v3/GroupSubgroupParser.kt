package ru.krabikk.nntcschedule.v3

import ru.krabikk.nntcschedule.ParsedLesson

/**
 * Разбивает ОДНУ пару на два занятия подгрупп. Исходный текст всегда
 * сохраняется в GroupLessonEntity. Ничего не угадываем при неоднозначности.
 *
 * Например: «Информатика/1 подгруппа/Ивкова М.Н./Основы черчения
 * /2 подгруппа/Дубровина Л.И.», кабинет «41/21».
 */
data class GroupSubgroup(
    val number: Int,
    val subject: String,
    val room: String,
    val teacher: String
)

object GroupSubgroupParser {
    private val marker = Regex("""/\s*([12])\s*подгруппа(?:\s*/|(?=$))""", RegexOption.IGNORE_CASE)
    private val teacher = Regex("""^[\s/]*([А-ЯЁ][а-яё\-]+\s+[А-ЯЁ]\.\s*[А-ЯЁ]\.?)""")

    fun hasMultipleSubgroups(lesson: ParsedLesson): Boolean =
        marker.findAll(combined(lesson)).map { it.groupValues[1] }.distinct().count() > 1

    fun parse(lesson: ParsedLesson): List<GroupSubgroup> {
        val text = combined(lesson)
        val matches = marker.findAll(text).toList()
        if (matches.size != 2 || matches.map { it.groupValues[1] }.toSet() != setOf("1", "2")) return emptyList()
        val rooms = lesson.room.split('/').map { it.trim() }
        // При параллельных занятиях два кабинета должны быть указаны раздельно.
        if (rooms.size != 2 || rooms.any { it.isBlank() }) return emptyList()
        val output = mutableListOf<GroupSubgroup>()
        var subjectStart = 0
        for ((index, match) in matches.withIndex()) {
            val subject = text.substring(subjectStart, match.range.first).trim().trim('/').trim()
            val tail = text.substring(match.range.last + 1)
            val instructor = teacher.find(tail) ?: return emptyList()
            val teacherName = instructor.groupValues[1].replace(Regex("""\s+"""), " ").trim()
            val nextStart = match.range.last + 1 + instructor.range.last + 1
            if (subject.isBlank() || teacherName.isBlank()) return emptyList()
            output += GroupSubgroup(
                number = match.groupValues[1].toInt(),
                subject = subject,
                room = rooms[index],
                teacher = teacherName
            )
            subjectStart = nextStart
        }
        // Любые непонятные остатки текста лучше отметить проверкой, а не потерять.
        if (text.substring(subjectStart).trim().trim('/').isNotEmpty()) return emptyList()
        return output.sortedBy { it.number }
    }

    private fun combined(lesson: ParsedLesson): String = lesson.subject.trim() +
        if (lesson.teacher.isBlank()) "" else "/" + lesson.teacher.trim()
}
