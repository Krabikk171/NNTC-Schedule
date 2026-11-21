package ru.krabikk.nntcschedule.v3

import ru.krabikk.nntcschedule.PdfTextItem
import ru.krabikk.nntcschedule.ParsedDay
import ru.krabikk.nntcschedule.ParsedLesson
import java.util.Locale
import kotlin.math.abs

/**
 * 3.1-alpha.2: БЕЗОПАСНАЯ диагностика ВСЕХ групп корпуса №2.
 *
 * Не меняет Room, не переключает рабочую группу и не записывает
 * сомнительные пары в расписание пользователя. Показать ошибки раньше,
 * чем включать массовую замену данных, — сознательное решение.
 */
object GroupCatalog {
    enum class Quality { STRUCTURAL_OK, NEEDS_REVIEW, UNSUPPORTED }

    data class LessonRow(
        val group: String,
        val date: String,
        val weekday: String,
        val lesson: ParsedLesson,
        val notes: List<String>
    )

    data class GroupResult(
        val name: String,
        val quality: Quality,
        val days: List<ParsedDay>,
        val notes: List<String>,
        val rows: List<LessonRow>
    )

    data class Report(val groups: List<GroupResult>, val sourceItems: Int) {
        val cleanCount get() = groups.count { it.quality == Quality.STRUCTURAL_OK }
        val needReviewCount get() = groups.count { it.quality == Quality.NEEDS_REVIEW }
        val unsupportedCount get() = groups.count { it.quality == Quality.UNSUPPORTED }
        val lessonCount get() = groups.sumOf { it.rows.size }

        fun summary(): String = buildString {
            appendLine("NNTC Schedule 3.1.0-alpha.8.2 — проверка групп")
            appendLine("Каталог: ${groups.size} групп; ${lessonCount} найденных пар")
            appendLine("Без структурных замечаний: $cleanCount")
            appendLine("Требуют ручной проверки: $needReviewCount")
            appendLine("Не поддерживаются текущим парсером: $unsupportedCount")
            appendLine("Внимание: 'без замечаний' НЕ гарантирует точность всех предметов!")
            appendLine("Этот диагностический запуск НЕ записывает данные в Room и НЕ отправляет уведомления.")
            appendLine()
            for (group in groups) {
                val tag = when (group.quality) {
                    Quality.STRUCTURAL_OK -> "OK?"
                    Quality.NEEDS_REVIEW -> "REVIEW"
                    Quality.UNSUPPORTED -> "UNSUPPORTED"
                }
                appendLine("[$tag] ${group.name}: ${group.rows.size} пар, ${group.days.count { it.lessons.isNotEmpty() }} дн.")
                group.notes.distinct().take(4).forEach { appendLine("   • $it") }
                val more = group.notes.distinct().size - 4
                if (more > 0) appendLine("   • ...и ещё $more замечаний")
            }
        }

        /** CSV с предупреждениями: в приложении нельзя использовать без проверки! */
        fun csv(): String = buildString {
            appendLine("date,weekday,group,number,time,subject,room,teacher,quality,notes")
            for (group in groups) for (row in group.rows) {
                val values = listOf(
                    row.date, row.weekday, row.group, row.lesson.number.toString(),
                    row.lesson.time, row.lesson.subject, row.lesson.room, row.lesson.teacher,
                    if (group.quality == Quality.STRUCTURAL_OK && row.notes.isEmpty()) "CHECKED_STRUCTURE" else "REVIEW",
                    row.notes.joinToString("; ")
                )
                appendLine(values.joinToString(",", transform = ::csvEscape))
            }
        }
    }

    /** Имена групп берём непосредственно из левой колонки PDF, НЕ из заданного списка. */
    fun discoverGroups(items: List<PdfTextItem>): List<String> {
        if (items.isEmpty()) return emptyList()
        val usual = Regex("""(?<![\p{L}\d])\d{1,2}\s*[А-ЯЁA-Zа-яё]{2,15}\s*[-‐‑–—−]\s*\d{2,4}\s*[-‐‑–—−]\s*\d{1,2}[А-ЯЁA-Zа-яё]{0,3}(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
        // Например: «26-1 ЭМ» (не совпадает с обычным шаблоном).
        val special = Regex("""^\s*\d{2}\s*[-‐‑–—−]\s*\d{1,2}\s+[А-ЯЁа-яё]{2,5}(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
        val found = linkedSetOf<String>()
        for ((_, pageItems) in items.groupBy { it.page }.toSortedMap()) {
            // У разных листов может различаться ширина полей: используем
            // левую часть таблицы (не всю страницу) и строковую группировку.
            val startX = pageItems.minOfOrNull { it.x } ?: continue
            val grouped = mutableListOf<MutableList<PdfTextItem>>()
            for (item in pageItems.filter { it.x in (startX - 2f)..(startX + 53f) }
                .sortedWith(compareBy<PdfTextItem> { it.y }.thenBy { it.x })) {
                val last = grouped.lastOrNull()
                if (last == null || abs(last.first().y - item.y) > 2.5f) grouped += mutableListOf(item)
                else last.add(item)
            }
            for (line in grouped) {
                val sb = StringBuilder()
                var last: PdfTextItem? = null
                for (item in line.sortedBy { it.x }) {
                    if (last != null && item.x - (last.x + last.width) > 1.6f) sb.append(' ')
                    sb.append(item.text)
                    last = item
                }
                val text = sb.toString().replace('Ё', 'Е').replace('ё', 'е')
                usual.findAll(text).forEach { found += normalizeUsual(it.value) }
                special.findAll(text).forEach { found += normalizeSpecial(it.value) }
            }
        }
        return found.sortedWith(compareBy<String> { it.firstOrNull()?.digitToIntOrNull() ?: 99 }.thenBy { it })
    }

    fun analyze(items: List<PdfTextItem>): Report {
        val names = discoverGroups(items)
        val candidateResults = names.associateWith { group ->
            // Строгое поведение рабочего 3.0.0 НЕ меняется: используем
            // особый диагностический режим, позволяющий обнаружить дефекты.
            ExperimentalGroupTableParser.parse(items = items, group = group, strict = false)
        }
        val groups = names.map { name ->
            val parsed = candidateResults.getValue(name)
            val notes = parsed.warnings.toMutableList()
            val rows = parsed.days.flatMap { day ->
                day.lessons.map { lesson ->
                    val issues = inspectLesson(lesson)
                    if (issues.isNotEmpty()) notes += "${day.date}, пара ${lesson.number}: ${issues.joinToString("; ")}"
                    LessonRow(name, day.date, day.weekday, lesson, issues)
                }
            }
            if (parsed.error != null) notes += parsed.error
            // Отсутствие занятий в конкретный день допустимо, но полностью
            // пустой результат не выдаём за распознанное расписание.
            if (rows.isEmpty()) notes += "Нет извлечённых занятий: нужен отдельный разбор формата группы"
            val quality = when {
                rows.isEmpty() || parsed.error != null -> Quality.UNSUPPORTED
                notes.isNotEmpty() -> Quality.NEEDS_REVIEW
                else -> Quality.STRUCTURAL_OK
            }
            GroupResult(name, quality, parsed.days, notes, rows)
        }
        return Report(groups, items.size)
    }

    /** Обнаруживает наиболее опасные сбои, не делая вид, что проверена семантика. */
    internal fun inspectLesson(lesson: ParsedLesson): List<String> = buildList {
        if (lesson.time.isBlank()) add("Не определено время")
        if (lesson.subject.isBlank()) add("Нет названия занятия")
        if (lesson.room.isBlank() &&
            !lesson.subject.matches(Regex("""^(?:ПП|УП)\.\d{2}$""")) &&
            !lesson.subject.contains("самостоятельная работа", ignoreCase = true))
            add("Кабинет не указан или не извлечён — сверить с PDF")
        if (lesson.teacher.isBlank() && lesson.subject.contains('/') &&
            Regex("""[А-ЯЁ][а-яё-]+\s+[А-ЯЁ]\.[А-ЯЁ]\.""").containsMatchIn(lesson.subject))
            add("ФИО преподавателя не выделено из названия")
        if (GroupSubgroupParser.hasMultipleSubgroups(lesson) && GroupSubgroupParser.parse(lesson).size != 2)
            add("Параллельные подгруппы не удалось разделить без потери данных")
        if (lesson.subject.contains("УП.") && lesson.subject.contains("МДК."))
            add("Вероятно смешан текст другой группы")
        if (lesson.subject.matches(Regex(""".*[/]\d\s*$""")))
            add("Возможно обрезано окончание названия")
        if (lesson.time.isNotBlank() && !Regex("""\d{2}:\d{2}[–-]\d{2}:\d{2}""").matches(lesson.time)) add("Нестандартный формат времени")
        if (Regex("""(?<!\d)\d{1,2}\s*[:.]\s*\d{2}\s*[-–]""").containsMatchIn(lesson.subject)) add("В название попали часы соседней колонки")
        if (Regex("""(?i)ПП\.\d+\s+МДК\.""").containsMatchIn(lesson.subject)) add("Вероятно смешаны соседние группы")
        if (Regex("""(?i)\b\d{2,3}[а-я]?\s+(?:станк|программ|токарн)""").containsMatchIn(lesson.subject)) add("Кабинет попал в название предмета")
        if (Regex("""(?i)^\s*(?:н/|/|злова\b)""").containsMatchIn(lesson.subject)) add("Обрывок экзамена или перенесённой строки")
        if (Regex("""(?i)[А-ЯЁ]\.[А-ЯЁ]\.\s+ПП\.""").containsMatchIn(lesson.subject)) add("В предмет попал текст соседней группы")
    }

    private fun normalizeUsual(s: String): String = s.uppercase(Locale.ROOT)
        .replace(Regex("""\s+"""), "").replace(Regex("""[‐‑–—−]"""), "-")
    private fun normalizeSpecial(s: String): String = s.uppercase(Locale.ROOT)
        .replace(Regex("""[‐‑–—−]"""), "-").replace(Regex("""\s+"""), " ").trim()
    private fun csvEscape(s: String) = if (s.any { it in ",\"\r\n" }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
