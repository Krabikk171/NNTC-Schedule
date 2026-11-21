package ru.krabikk.nntcschedule.v3

import ru.krabikk.nntcschedule.ParsedDay
import ru.krabikk.nntcschedule.ParsedLesson
import ru.krabikk.nntcschedule.PdfTextItem
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Рабочий парсер для таблицы корпуса №2.
 *
 * В отличие от пробных версий он никогда не собирает текст разных колонок
 * в одну строку: сначала определяются страницы, строка нужной группы и
 * горизонтальные границы пар, и только потом восстанавливается текст ячейки.
 *
 * Код не зависит от Android — координатные тесты можно выполнять на JVM.
 */
object ExperimentalGroupTableParser {
    const val TARGET_GROUP = "2РПУ-25-1"
    // Экспериментальная ветка: не используется рабочим обновлением 3.0.0.

    data class ParseResult(
        val days: List<ParsedDay>,
        val warnings: List<String>,
        val error: String? = null
    ) {
        val isValid: Boolean get() = error == null && days.any { it.lessons.isNotEmpty() }
    }

    private data class TextLine(val page: Int, val y: Float, val items: List<PdfTextItem>)
    private data class MappedText(val text: String, val xPositions: List<Float>) {
        fun xAt(index: Int): Float = xPositions.getOrElse(index) { 0f }
    }
    private data class DateHeader(val date: String, val weekday: String)
    private data class GroupAnchor(val name: String, val y: Float, val x: Float)
    private data class Column(val number: Int, val startX: Float)
    private data class TimeAnchor(val x: Float, val time: String)
    private data class CellText(val x: Float, val y: Float, val value: String)

    private const val Y_TOLERANCE = 2.5f
    private val timeRegex = Regex("""(?<!\d)(\d{1,2})\s*[:.]\s*(\d{2})\s*[-–—−]\s*(\d{1,2})\s*[:.\-]\s*(\d{2})(?!\d)""")
    private val clockRegex = Regex("""(?<!\d)(\d{1,2})\s*[:.]\s*(\d{2})(?!\d)""")
    private val headerRegex = Regex("""([1-6])\s*пара""", RegexOption.IGNORE_CASE)
    private val timeHeaderRegex = Regex("""(?:время|вре)(?![а-яё])""", RegexOption.IGNORE_CASE)
    private val groupRegex = Regex("""\d{1,2}[A-ZА-ЯЁа-яё]{2,15}-\d{2,4}-\d+[A-ZА-ЯЁа-яё]?""", RegexOption.IGNORE_CASE)
    private val groupStartRegex = Regex("""\d{1,2}[A-ZА-ЯЁа-яё]{2,15}-\d{2,4}-""", RegexOption.IGNORE_CASE)
    private val dateRegex = Regex(
        """(?:расписание\s+занятий\s+на\s+)(\d{1,2})\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\s+(\d{4})(?:\s*г\.)?(?:\s*\(([^)]*)\))?""",
        RegexOption.IGNORE_CASE
    )
    private val bareDateRegex = Regex(
        """(\d{1,2})\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\s+(\d{4})(?:\s*г\.)?(?:\s*\(([^)]*)\))?""",
        RegexOption.IGNORE_CASE
    )
    private val roomRegex = Regex("""^(?:каб\.?\s*|ауд\.?\s*)?(?:\d{1,4}\s*[а-я]?(?:\s*/\s*\d{1,4})?|[сч]\s*/\s*з|спортзал|мастерская)$""", RegexOption.IGNORE_CASE)
    private val teacherRegex = Regex("""(?u)[А-ЯЁ][а-яё-]+\s+[А-ЯЁ]\.[А-ЯЁ]\.?$""")
    private val monthNames = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")

    /**
     * PDF корпуса №2 — не «одна страница = один день».
     * На одной странице могут быть два расписания, а второе продолжается на
     * следующей странице без нового заголовка даты. Поэтому сначала режем
     * страницу на секции по ЗАГОЛОВКАМ ДАТ, а затем ищем группы внутри секций.
     */
    fun parse(items: List<PdfTextItem>, documentText: String = "", group: String = TARGET_GROUP, strict: Boolean = true): ParseResult {
        if (items.isEmpty()) return ParseResult(emptyList(), emptyList(), "PDF не содержит извлекаемого текста.")
        val warnings = mutableListOf<String>()
        val pages = items.filter { it.text.isNotEmpty() }.groupBy { it.page }.toSortedMap()
        if (pages.isEmpty()) return ParseResult(emptyList(), emptyList(), "В PDF не найден текст.")
        val collected = linkedMapOf<String, ParsedDay>()
        val groupDates = mutableSetOf<String>()
        var lastHeader: DateHeader? = null
        var failedRows = 0
        var pagesWithoutDate = 0
        val bellsByDate = mutableMapOf<String, Map<Int, String>>()
        val expected = compact(group)

        for ((pageNumber, pageItems) in pages) {
            val pageLines = toLines(pageItems)
            // Ищем КАЖДЫЙ заголовок внутри страницы, а не только первый.
            val headers = pageLines.mapNotNull { line ->
                val text = mapText(line.items).text.replace(Regex("""\s+"""), " ")
                val match = dateRegex.find(text) ?: return@mapNotNull null
                dateFromMatch(match)?.let { line.y to it }
            }.sortedBy { it.first }.distinctBy { it.first.toInt() }

            // Первая страница без даты — ошибка; любая следующая может быть
            // продолжением предыдущего дня без собственного заголовка.
            if (headers.isEmpty() && lastHeader == null) {
                pagesWithoutDate++
                warnings += "Страница $pageNumber: дата не найдена и продолжать нечего"
                continue
            }

            val sections = mutableListOf<Triple<DateHeader, Float, Float>>()
            if (headers.isEmpty()) {
                sections += Triple(lastHeader!!, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
            } else {
                // Содержимое ДО первого заголовка на странице (если таковое
                // встретится) относится к предыдущей дате.
                if (lastHeader != null && pageItems.any { it.y < headers.first().first - 2f }) {
                    sections += Triple(lastHeader, Float.NEGATIVE_INFINITY, headers.first().first)
                }
                headers.forEachIndexed { index, (top, header) ->
                    val bottom = headers.getOrNull(index + 1)?.first ?: Float.POSITIVE_INFINITY
                    sections += Triple(header, top, bottom)
                }
                lastHeader = headers.last().second
            }

            for ((header, top, bottom) in sections) {
                val sectionItems = pageItems.filter { it.y >= top && it.y < bottom }
                if (sectionItems.isEmpty()) continue
                collected.putIfAbsent(header.date, ParsedDay(header.date, header.weekday, emptyList()))
                val lines = toLines(sectionItems)
                val allGroups = locateGroups(lines, sectionItems)
                val targets = allGroups.filter { it.name == expected }
                // В начале или конце большого дня нужной группы может НЕ быть.
                // Это не повод считать всю дату ошибочной: она может быть далее.
                if (targets.isEmpty()) continue
                groupDates += header.date
                val columns = locateColumns(lines, sectionItems, allGroups)
                if (columns.size != 6) {
                    failedRows += targets.size
                    warnings += "${header.date}: не найдены 6 столбцов времени (обнаружено ${columns.size})"
                    continue
                }
                val discoveredBells = findSpecialBellTimes(pageLines, header.date)
                if (discoveredBells.isNotEmpty()) bellsByDate[header.date] = discoveredBells
                val bellTimes = bellsByDate[header.date].orEmpty()
                val rowGap = typicalRowGap(allGroups)
                val parsedLessons = mutableListOf<ParsedLesson>()
                for (anchor in targets) {
                    val rowItems = isolateRow(sectionItems, allGroups, anchor, rowGap)
                    val lessons = columns.mapNotNull { column ->
                        val nextX = columns.firstOrNull { it.startX > column.startX + 2f }?.startX ?: Float.MAX_VALUE
                        val left = column.startX - 3f
                        val right = nextX - 3f
                        val cellItems = rowItems.filter { item ->
                            val center = item.x + item.width * .5f
                            center >= left && center < right
                        }
                        parseCell(column, cellItems, right - left, anchor.y, rowGap)?.let { lesson ->
                            if (lesson.time.isBlank()) lesson.copy(time = bellTimes[column.number].orEmpty()) else lesson
                        }
                    }.toMutableList()
                    // Объединённая ячейка экзамена может пересекать сразу 3 пары:
                    // в PDF это ОДНО занятие, а не три фрагмента 4/5/6.
                    // Действуем только если текст «Экзамен/» виден целиком на
                    // ОДНОЙ физической строке и у более поздних пар нет времени.
                    val mergedExam = toLines(rowItems).firstNotNullOfOrNull { line ->
                        val mapped = mapText(line.items)
                        val match = Regex("""экзамен\s*/""", RegexOption.IGNORE_CASE).find(mapped.text)
                            ?: return@firstNotNullOfOrNull null
                        val x = mapped.xAt(match.range.first)
                        val from = columns.indexOfLast { it.startX <= x }
                        if (from < 0) return@firstNotNullOfOrNull null
                        val number = from + 1
                        if (lessons.any { it.number > number && it.time.isNotBlank() })
                            return@firstNotNullOfOrNull null
                        val text = mapped.text.substring(match.range.first)
                            .replace(Regex("""\s+"""), " ").trim()
                        if (text.length < 16) return@firstNotNullOfOrNull null
                        val slash = text.lastIndexOf('/')
                        val subject = if (slash > match.value.length) text.substring(0, slash).trim() else text
                        val teacher = if (slash > match.value.length) text.substring(slash + 1).trim() else ""
                        val rootLesson = lessons.firstOrNull { it.number == number }
                        ParsedLesson(number, rootLesson?.time.orEmpty(), rootLesson?.room.orEmpty(), subject, teacher)
                    }
                    if (mergedExam != null) {
                        // Если занятие начинается с 4-й пары и занимает
                        // объединённую область 4–6, 5-я и 6-я не отдельные пары.
                        lessons.removeAll { it.number >= mergedExam.number }
                        lessons += mergedExam
                    }
                    // В PDF одна и та же пара нескольких подгрупп иногда
                    // перенесена на четыре строки. У соседних пар конец
                    // может выйти за эвристическую границу строки. Разрешаем
                    // восстановление ТОЛЬКО из полной соседней пары той же
                    // группы, даты и кабинета, когда неполный текст —
                    // буквальный префикс полного текста.
                    val completePeers = lessons.filter { it.teacher.isNotBlank() }
                    for (index in lessons.indices) {
                        val partial = lessons[index]
                        if (partial.teacher.isNotBlank() ||
                            !Regex("""/\s*[12]\s*$""").containsMatchIn(partial.subject)) continue
                        val peer = completePeers.firstOrNull { candidate ->
                            candidate.number != partial.number &&
                                candidate.room == partial.room &&
                                candidate.subject.startsWith(partial.subject) &&
                                candidate.subject.length > partial.subject.length + 6
                        } ?: continue
                        lessons[index] = partial.copy(subject = peer.subject, teacher = peer.teacher)
                    }
                    if (lessons.isEmpty() && rowContainsUnparsedLesson(rowItems, columns, anchor.y, rowGap)) {
                        // В PDF встречаются реальные пустые строки групп (выходной).
                        // Их нельзя считать ошибкой, но и текст потерянной пары
                        // нельзя незаметно выдать за пустой день.
                        failedRows++
                        warnings += "${header.date}: найдена строка $group с текстом занятий, но пары не распознаны"
                    }
                    parsedLessons += lessons
                }
                val existing = collected.getValue(header.date)
                val merged = existing.lessons + parsedLessons
                val duplicates = merged.groupBy { it.number }.filterValues { it.distinct().size > 1 }
                for (number in duplicates.keys) warnings += "${header.date}: пара $number встречается несколько раз"
                collected[header.date] = existing.copy(lessons = merged.distinctBy { it.number }.sortedBy { it.number })
            }
        }
        if (collected.isEmpty()) return ParseResult(emptyList(), warnings,
            "Не удалось определить даты таблиц PDF. Старое расписание сохранено.")
        val absent = collected.keys.filterNot(groupDates::contains)
        if (strict && absent.isNotEmpty()) {
            warnings += "Группа $group не найдена за ${absent.joinToString()}"
            return ParseResult(emptyList(), warnings,
                "Группа $group отсутствует в таблицах за ${absent.joinToString()}. Старое расписание сохранено.")
        }
        if (strict && failedRows > 0) return ParseResult(emptyList(), warnings,
            "Не распознано $failedRows строк(и) группы $group. Старое расписание сохранено.")
        if (strict && pagesWithoutDate > 0) return ParseResult(emptyList(), warnings,
            "Не найдена дата на $pagesWithoutDate страницах. Старое расписание сохранено.")
        val days = collected.values.toList()
        if (strict && days.any { it.lessons.isEmpty() }) return ParseResult(emptyList(), warnings,
            "Не найдены пары группы $group для одного или нескольких дней. Старое расписание сохранено.")
        if (strict && days.flatMap { it.lessons }.any { it.subject.isBlank() || it.time.isBlank() })
            return ParseResult(emptyList(), warnings,
                "Часть занятий не содержит предмет или время. Старое расписание сохранено.")
        return ParseResult(days, warnings)
    }

    private fun dateFromMatch(match: MatchResult): DateHeader? {
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = monthNames.indexOf(match.groupValues[2].lowercase(Locale.ROOT)) + 1
        val year = match.groupValues[3].toIntOrNull() ?: return null
        if (day !in 1..31 || month == 0 || year !in 2020..2100) return null
        val calendar = GregorianCalendar(year, month - 1, day).apply { isLenient = false }
        try { calendar.time } catch (_: IllegalArgumentException) { return null }
        val weekday = match.groupValues.getOrNull(4)?.trim()?.takeIf { it.isNotBlank() }
            ?: SimpleDateFormat("EEEE", Locale.forLanguageTag("ru-RU")).format(calendar.time)
        return DateHeader("$day ${monthNames[month - 1]} $year г.", weekday)
    }

    private fun compact(text: String): String = text.uppercase(Locale.ROOT)
        .replace('Ё', 'Е').replace('–', '-').replace('—', '-').replace('\u0002', '-')
        .replace(Regex("""\s+"""), "")

    private fun toLines(items: List<PdfTextItem>): List<TextLine> {
        val result = mutableListOf<TextLine>()
        val current = mutableListOf<PdfTextItem>()
        var ySum = 0.0
        var currentPage = -1
        fun flush() {
            if (current.isNotEmpty()) {
                result += TextLine(currentPage, (ySum / current.size).toFloat(), current.sortedBy { it.x })
                current.clear()
                ySum = 0.0
            }
        }
        for (item in items.sortedWith(compareBy<PdfTextItem> { it.page }.thenBy { it.y }.thenBy { it.x })) {
            if (current.isNotEmpty() && (currentPage != item.page || abs(item.y - ySum / current.size) > Y_TOLERANCE)) flush()
            currentPage = item.page
            current += item
            ySum += item.y
        }
        flush()
        return result
    }

    private fun mapText(items: List<PdfTextItem>): MappedText {
        val text = StringBuilder()
        val x = mutableListOf<Float>()
        var prev: PdfTextItem? = null
        for (item in items.sortedBy { it.x }) {
            val previous = prev
            if (previous != null) {
                val gap = item.x - (previous.x + previous.width)
                if (gap > max(1.2f, min(3.0f, item.height * 0.18f)) && text.isNotEmpty() && !text.last().isWhitespace()) {
                    text.append(' ')
                    x += item.x - gap / 2f
                }
            }
            for ((i, char) in item.text.withIndex()) {
                text.append(char)
                x += item.x + item.width * i / item.text.length.coerceAtLeast(1)
            }
            prev = item
        }
        return MappedText(text.toString().replace('\u00a0', ' '), x)
    }

    private fun findTimeHeaderXs(lines: List<TextLine>): List<Float> {
        // Колонка «Время» или перенесённая «Вре/мя» имеется даже в тех
        // таблицах, где времена вынесены отдельно в раздел «ЗВОНКИ».
        val matches = lines.flatMap { line ->
            val mapped = mapText(line.items)
            timeHeaderRegex.findAll(mapped.text).map { mapped.xAt(it.range.first) }.toList()
        }.sorted()
        val unique = mutableListOf<Float>()
        for (x in matches) if (unique.isEmpty() || x - unique.last() > 12f) unique += x
        return unique
    }

    private fun findSpecialBellTimes(lines: List<TextLine>, date: String): Map<Int, String> {
        // Дополнительная таблица «ЗВОНКИ НА 23.09.2026 (СРЕДА)».
        // Если отдельные строки пар не содержат времени, берём его отсюда.
        val day = bareDateRegex.find(date)?.groupValues
        val dayMonth = day?.getOrNull(2)?.lowercase(Locale.ROOT)?.let { monthNames.indexOf(it) + 1 }
        val dayNumber = day?.getOrNull(1)?.toIntOrNull()
        val year = day?.getOrNull(3)?.toIntOrNull()
        val special = lines.any { line ->
            val text = mapText(line.items).text.lowercase(Locale.ROOT)
            val marker = Regex("""звонки\s+на\s+(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(text)
            marker != null && marker.groupValues[1].toIntOrNull() == dayNumber &&
                marker.groupValues[2].toIntOrNull() == dayMonth && marker.groupValues[3].toIntOrNull() == year
        }
        if (!special) return emptyMap()
        val result = mutableMapOf<Int, String>()
        for (line in lines) {
            val mapped = mapText(line.items)
            val number = headerRegex.find(mapped.text)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val match = timeRegex.find(mapped.text) ?: continue
            val time = normalizedTime(match) ?: continue
            result[number] = time
        }
        return if (result.size >= 3) result else emptyMap()
    }

    /**
     * Ищем группы по надписям на самих строках, независимо от колонки времени.
     *
     * В некоторых PDF заголовки «Время» находятся левее названия группы
     * (или отдельная таблица звонков начинается раньше основной). Раньше
     * вычисленная по времени левая граница полностью отрезала названия групп.
     *
     * Для исключения упоминаний групп внутри названий предметов используем
     * широкую левую область страницы — 18% ширины, но не меньше 68 единиц.
     */
    private fun locateGroups(lines: List<TextLine>, items: List<PdfTextItem>): List<GroupAnchor> {
        if (items.isEmpty()) return emptyList()
        val minX = items.minOf { it.x }
        val maxX = items.maxOf { it.x + it.width }
        val leftLimit = minX + max(68f, min(180f, (maxX - minX) * .18f))
        val fullGroupPattern = Regex(
            """(?<![0-9A-ZА-ЯЁа-яё])\d{1,2}\s*[A-ZА-ЯЁа-яё]{2,15}\s*-\s*\d{2,4}\s*-\s*\d+[A-ZА-ЯЁа-яё]{0,3}(?![0-9A-ZА-ЯЁа-яё])""",
            RegexOption.IGNORE_CASE
        )
        // Нестандартная подпись в PDF: «26-1 ЭМ», «26-2 ЭМ», «26-4 МРП».
        val specialGroupPattern = Regex(
            """(?<![0-9A-ZА-ЯЁа-яё])\d{2}\s*-\s*\d{1,2}\s+[А-ЯЁа-яё]{2,5}(?![0-9A-ZА-ЯЁа-яё])""",
            RegexOption.IGNORE_CASE
        )
        val partialGroupPattern = Regex(
            """(?<![0-9A-ZА-ЯЁа-яё])\d{1,2}\s*[A-ZА-ЯЁа-яё]{2,15}\s*-\s*\d{2,4}\s*-""",
            RegexOption.IGNORE_CASE
        )
        fun withAsciiHyphens(text: String): String = text
            .replace('–', '-').replace('—', '-').replace('−', '-')
            .replace('‐', '-').replace('‑', '-').replace('\u0002', '-')
            .replace('\u00AD', '-')

        val anchors = mutableListOf<GroupAnchor>()
        val partials = mutableListOf<GroupAnchor>()
        for (line in lines) {
            val mapped = mapText(line.items)
            // Не удаляем пробелы: иначе сместятся индексы координат xAt().
            val text = withAsciiHyphens(mapped.text)
            for (match in fullGroupPattern.findAll(text)) {
                val x = mapped.xAt(match.range.first)
                // Число из соседней колонки времени иногда следует сразу за
                // «2РПУ-25-» в одной визуальной строке: это НЕ суффикс группы.
                val dashIndex = match.value.lastIndexOf('-')
                val suffixIndex = (dashIndex + 1 until match.value.length)
                    .firstOrNull { match.value[it].isDigit() }
                val dashX = mapped.xAt(match.range.first + dashIndex)
                val suffixX = suffixIndex?.let { mapped.xAt(match.range.first + it) } ?: Float.MAX_VALUE
                if (x <= leftLimit && suffixX - dashX <= 14f)
                    anchors += GroupAnchor(compact(match.value), line.y, x)
            }
            for (match in specialGroupPattern.findAll(text)) {
                val x = mapped.xAt(match.range.first)
                if (x <= leftLimit) anchors += GroupAnchor(compact(match.value), line.y, x)
            }
            for (match in partialGroupPattern.findAll(text)) {
                val x = mapped.xAt(match.range.first)
                if (x <= leftLimit) partials += GroupAnchor(compact(match.value), line.y, x)
            }
        }

        // Узкая колонка часто переносит последнюю цифру:
        // «2РПУ-25-» (первая строка) + «1» (следующая строка).
        // Берём продолжение только на той же координате X, иначе можно случайно
        // присоединить номер пары или цифру времени из соседней колонки.
        for (partial in partials) {
            if (anchors.any { it.y in (partial.y - 3f)..(partial.y + 3f) && it.x in (partial.x - 5f)..(partial.x + 5f) }) continue
            val next = lines.asSequence()
                .filter { it.y > partial.y + 2.5f && it.y <= partial.y + 20f }
                .firstOrNull { line -> line.items.any { it.x in (partial.x - 4f)..(partial.x + 9f) } }
                ?: continue
            val continuation = compact(mapText(next.items.filter {
                it.x in (partial.x - 4f)..(partial.x + 9f)
            }).text)
            val suffix = Regex("""^\d{1,2}[A-ZА-ЯЁа-яё]?$""").matchEntire(continuation)
                ?.value ?: continue
            anchors += GroupAnchor(partial.name + suffix, (partial.y + next.y) / 2f, partial.x)
        }

        return anchors.distinctBy { it.name to (it.y / 3f).toInt() }.sortedBy { it.y }
    }

    private fun typicalRowGap(groups: List<GroupAnchor>): Float {
        val gaps = groups.zipWithNext().map { it.second.y - it.first.y }.filter { it > 7f }.sorted()
        return gaps.getOrNull(gaps.size / 2)?.coerceIn(14f, 100f) ?: 55f
    }

    private fun isolateRow(items: List<PdfTextItem>, groups: List<GroupAnchor>, target: GroupAnchor, rowGap: Float): List<PdfTextItem> {
        val before = groups.filter { it.y < target.y - Y_TOLERANCE }.maxByOrNull { it.y }
        val after = groups.filter { it.y > target.y + Y_TOLERANCE }.minByOrNull { it.y }
        val top = before?.let { (it.y + target.y) / 2f - 2.8f } ?: target.y - rowGap / 2f
        val bottom = after?.let { (it.y + target.y) / 2f + 0.7f } ?: target.y + max(18f, rowGap / 2f)
        return items.filter { it.y >= top && it.y < bottom }
    }

    private fun locateColumns(lines: List<TextLine>, items: List<PdfTextItem>, groups: List<GroupAnchor>): List<Column> {
        // В таблице колонки расположены так:
        // [ВРЕМЯ] [КАБИНЕТ] [ПРЕДМЕТ / ПРЕПОДАВАТЕЛЬ] x 6.
        // Положение надписи «N пара» соответствует третьей части колонки.
        // Поэтому искать начало пары по надписи НЕПРАВИЛЬНО.
        val headerNumbers = mutableSetOf<Int>()
        for (line in lines) {
            val mapped = mapText(line.items)
            headerRegex.findAll(mapped.text).forEach { headerNumbers += it.groupValues[1].toInt() }
        }
        val rowGap = typicalRowGap(groups)
        val rowItems = groups.flatMap { isolateRow(items, groups, it, rowGap) }.distinct()
        val groupArea = items.minOfOrNull { it.x } ?: 0f
        val timeAnchors = mutableListOf<TimeAnchor>()
        for (line in toLines(rowItems)) {
            val mapped = mapText(line.items)
            val fullMatches = timeRegex.findAll(mapped.text).toList()
            for (match in fullMatches) {
                val time = normalizedTime(match) ?: continue
                val x = mapped.xAt(match.range.first)
                if (x >= groupArea + 22f) timeAnchors += TimeAnchor(x, time)
            }
            // Первая и вторая половины времени нередко напечатаны на разных строках.
            for (match in clockRegex.findAll(mapped.text)) {
                if (fullMatches.any { match.range.first in it.range }) continue
                val hour = match.groupValues[1].toIntOrNull() ?: continue
                val minute = match.groupValues[2].toIntOrNull() ?: continue
                if (hour !in 7..20 || minute !in 0..59) continue
                val x = mapped.xAt(match.range.first)
                if (x >= groupArea + 22f) timeAnchors += TimeAnchor(x, "")
            }
        }
        val clusters = mutableListOf<MutableList<TimeAnchor>>()
        for (anchor in timeAnchors.sortedBy { it.x }) {
            val last = clusters.lastOrNull()
            if (last == null || abs(anchor.x - last.map { it.x }.average()) > 10f) clusters += mutableListOf(anchor)
            else last += anchor
        }
        val columnXs = clusters.sortedByDescending { cluster -> cluster.size }
            .take(6).map { cluster -> cluster.map { it.x }.average().toFloat() }.sorted()
        // Шесть временных колонок однозначно задают номера пар слева направо.
        // Заголовки используем для защиты от иных таблиц (например, «ЗВОНКИ»).
        if (columnXs.size == 6) return columnXs.mapIndexed { index, x -> Column(index + 1, x) }
        val headerXs = findTimeHeaderXs(lines)
        if (headerXs.size == 6) return headerXs.mapIndexed { index, x -> Column(index + 1, x) }
        if (columnXs.size >= 3 && headerNumbers.size >= 5) {
            // В неполной раскладке определение номера по порядку опасно:
            // 3 найденные колонки могут оказаться 1-й, 4-й и 6-й.
            // Вместо записи некорректных номеров оставляем странице статус ошибки.
            return emptyList()
        }
        return emptyList()
    }

    private fun normalizedTime(match: MatchResult): String? {
        val a = match.groupValues[1].toIntOrNull() ?: return null
        val b = match.groupValues[2].toIntOrNull() ?: return null
        val c = match.groupValues[3].toIntOrNull() ?: return null
        val d = match.groupValues[4].toIntOrNull() ?: return null
        if (a !in 0..23 || c !in 0..23 || b !in 0..59 || d !in 0..59) return null
        if (a * 60 + b >= c * 60 + d) return null
        return "%02d:%02d–%02d:%02d".format(Locale.ROOT, a, b, c, d)
    }

    private fun parseCell(column: Column, cellItems: List<PdfTextItem>, cellWidth: Float, groupY: Float, rowGap: Float): ParsedLesson? {
        if (cellItems.isEmpty()) return null
        val lines = toLines(cellItems)
        val blocks = mutableListOf<CellText>()
        val timeLane = column.startX + min(24f, cellWidth * .16f)
        // В шаблоне таблицы область кабинета идёт после времени и до предмета.
        // Эта граница определяется относительно каждого столбца, а НЕ от начала PDF.
        val roomLane = column.startX + min(38f, cellWidth * .32f)
        val candidateClocks = mutableListOf<Pair<Float, String>>()
        var time: String? = null
        val partialTime = Regex("""^\s*(\d{1,2}\s*[:.]\s*\d{2})\s*[-–—]\s*""")
        for (line in lines) {
            val sorted = line.items.sortedBy { it.x }
            val chunks = mutableListOf<MutableList<PdfTextItem>>()
            for (item in sorted) {
                val last = chunks.lastOrNull()
                val prev = last?.lastOrNull()
                if (prev == null || item.x - prev.x - prev.width > max(3.2f, min(6f, item.height * .45f))) chunks += mutableListOf(item)
                else last += item
            }
            for (chunk in chunks) {
                val mapped = mapText(chunk)
                val value = mapped.text.trim()
                if (value.isBlank()) continue
                val x = chunk.minOf { it.x }
                val found = timeRegex.find(value)
                if (found != null && x < timeLane) {
                    time = time ?: normalizedTime(found)
                    val rest = value.replace(timeRegex, "").trim(' ', '-', '–', '—')
                    if (rest.isNotBlank()) blocks += CellText(mapped.xAt(mapped.text.indexOf(rest).coerceAtLeast(0)), line.y, rest)
                    continue
                }
                // У третьей пары время «11:50 -» нередко прилипает к «1 корпус».
                // Удаляем только начальные часы и продолжаем обработку остатка
                // с НАСТОЯЩЕЙ координатой начала текста кабинета.
                val part = if (x < timeLane) partialTime.find(value) else null
                if (part != null) {
                    val clock = clockRegex.find(part.value)
                    if (clock != null) {
                        val hh = clock.groupValues[1].toIntOrNull()
                        val mm = clock.groupValues[2].toIntOrNull()
                        if (hh != null && mm != null && hh in 0..23 && mm in 0..59)
                            candidateClocks += line.y to "%02d:%02d".format(Locale.ROOT, hh, mm)
                    }
                    val rest = value.substring(part.range.last + 1).trim()
                    if (rest.isNotEmpty()) {
                        val pos = mapped.text.indexOf(rest)
                        blocks += CellText(mapped.xAt(pos.coerceAtLeast(0)), line.y, rest)
                    }
                    continue
                }
                // Кабинет «54»/«21» может начинаться на полпункта левее
                // эвристической границы timeLane. Число БЕЗ двоеточия
                // не является временем: оставляем его в области кабинета.
                if (x < timeLane && clockRegex.containsMatchIn(value) &&
                    value.matches(Regex("""[\d:.,\s–—-]+"""))) {
                    for (clock in clockRegex.findAll(value)) {
                        val hh = clock.groupValues[1].toIntOrNull() ?: continue
                        val mm = clock.groupValues[2].toIntOrNull() ?: continue
                        if (hh in 0..23 && mm in 0..59)
                            candidateClocks += line.y to "%02d:%02d".format(Locale.ROOT, hh, mm)
                    }
                    continue
                }
                blocks += CellText(x, line.y, value)
            }
        }
        if (time == null && candidateClocks.size >= 2) {
            val clocks = candidateClocks.sortedBy { it.first }.map { it.second }.distinct()
            if (clocks.size >= 2 && clocks[0] < clocks[1]) time = "${clocks[0]}–${clocks[1]}"
        }

        // Если в ячейке по центру строки стоит самостоятельная практика ПП.xx,
        // текст следующей группы НАД строкой практики не относится к этой ячейке.
        val centralPractice = blocks.firstOrNull { block ->
            abs(block.y - groupY) <= 3.8f &&
                Regex("""^(?:ПП|УП)\.\d{2}(?:/[А-ЯЁ][а-яё-]+\s+[А-ЯЁ]\.[А-ЯЁ]\.)?$""")
                    .matches(block.value.trim())
        }
        if (centralPractice != null && centralPractice.value.trim().startsWith("ПП.")) {
            val (practice, teacherName) = splitTeacher(centralPractice.value.trim())
            return ParsedLesson(column.number, time.orEmpty(), "", practice, teacherName)
        }

        var room = ""
        val content = mutableListOf<CellText>()
        val roomPieces = mutableListOf<CellText>()
        for (block in blocks) {
            val clean = block.value.trim()
            val isRoomFragment = block.x < roomLane &&
                (Regex("""(?i)^(?:\d{1,4}\s*[а-я]?|\d{1,4}/\d{1,4}|[сч]/з|корпус,?)$""").matches(clean))
            if (isRoomFragment) {
                roomPieces += block.copy(value = clean)
                continue
            }
            if (room.isEmpty() && roomRegex.matches(clean)) {
                room = clean.replace(Regex("""(?i)^(каб\.?|ауд\.?)\s*"""), "")
            } else {
                val roomPrefix = Regex("""(?i)^((?:\d{1,4}\s*[а-я]?(?:/\d{1,4})?|[сч]/з))\s+(.+)$""").find(clean)
                if (room.isEmpty() && roomPrefix != null && block.x < roomLane) {
                    roomPieces += block.copy(value = roomPrefix.groupValues[1])
                    content += block.copy(x = block.x + 8f, value = roomPrefix.groupValues[2])
                } else content += block
            }
        }
        if (roomPieces.isNotEmpty()) {
            val parts = roomPieces.sortedWith(compareBy<CellText> { it.y }.thenBy { it.x })
                .map { it.value }
            room = if (parts.any { it.equals("корпус,", true) || it.equals("корпус", true) }) {
                parts.joinToString(" ").replace(Regex("""\s+,"""), ",")
            } else roomPieces.minByOrNull { abs(it.y - groupY) }?.value.orEmpty()
        }
        // Часть встроенной строки «1 корпус, 219» может оказаться в content
        // вследствие плотного соседства символов; перенос комнаты в предмет не допускаем.
        if (room.matches(Regex("""(?i)^1\s+корпус,?$"""))) {
            val numeric = content.firstOrNull { it.x < roomLane + 4f && it.value.matches(Regex("""\d{2,3}""")) }
            if (numeric != null) {
                room += " " + numeric.value
                content.remove(numeric)
            }
        }
        val centerTolerance = max(3.5f, min(7f, rowGap * .38f))
        val centered = blocks.any { block -> abs(block.y - groupY) <= centerTolerance && block.value.isNotBlank() }
        if (!centered) return null
        val fullText = if (centralPractice != null && centralPractice.value.trim().startsWith("УП.")) {
            centralPractice.value.trim()
        } else selectContentOfOwnRow(content, groupY, rowGap)
            .joinToString(" ") { it.value }.replace(Regex("""\s+"""), " ").trim()
        if (fullText.isBlank() || fullText == "—" || fullText == "-") return null
        val cleanedText = fullText
            // Длинная строка предыдущей группы может немного перейти через
            // центр межстрочного промежутка. В таком случае встречается
            // «подгруппа/Дубровина Л.И. История России/Балашов А.В.».
            // Это хвост предыдущей ячейки, а не начало текущей дисциплины.
            .replace(Regex("""(?i)^подгруппа\s*/\s*[А-ЯЁ][а-яё-]+\s+[А-ЯЁ]\.[А-ЯЁ]\.?\s+(?=[А-ЯЁ])"""), "")
            .replace(Regex("""^[А-ЯЁ]\.[А-ЯЁ]\.\s+(?=(?:[сч]/з\s+)?[А-ЯЁ])"""), "")
        val (subject, teacher) = splitTeacher(cleanedText)
        if (subject.isBlank()) return null
        // На узких колонках «1 корпус, 219» может оказаться разбитым
        // между room и subject. Дочитываем 3-значный кабинет из ТОЛЬКО
        // области кабинета этой пары (а не из названия соседней группы).
        if (subject.startsWith("корпус,", ignoreCase = true) && room.matches(Regex("""\d{1,3}"""))) {
            val fromX = column.startX + 13f
            val toX = column.startX + min(37f, cellWidth * .33f)
            val roomFragments = toLines(cellItems.filter { item ->
                (item.x + item.width * .5f) in fromX..toX
            }).joinToString(" ") { mapText(it.items).text }
            val number = Regex("""(?<!\d)\d{3}(?!\d)""").find(roomFragments)?.value
            return ParsedLesson(column.number, time.orEmpty(),
                if (number != null) "1 корпус, $number" else "1 корпус",
                subject.removePrefix("корпус,").trim(), teacher)
        }
        return ParsedLesson(column.number, time.orEmpty(),
            room.replace(Regex("""(\d)\s+([а-я])$""", RegexOption.IGNORE_CASE), "$1$2"), subject, teacher)
    }

    /**
     * In tightly packed PDF rows a long title from the PREVIOUS/NEXT group
     * may overlap the target row. Prefer the nearest complete teacher-bearing
     * subject around the group anchor, keeping all lines of that subject.
     * Parallel subgroups are excluded: they legitimately contain two teachers.
     */
    private fun selectContentOfOwnRow(content: List<CellText>, groupY: Float, rowGap: Float): List<CellText> {
        val sorted = content.sortedWith(compareBy<CellText> { it.y }.thenBy { it.x })
        if (sorted.size < 2 || sorted.any { Regex("""/\s*[12]\s*подгруппа""", RegexOption.IGNORE_CASE).containsMatchIn(it.value) })
            return sorted
        val completedTeacher = Regex("""[А-ЯЁ][а-яё\-]+\s+[А-ЯЁ]\.\s*[А-ЯЁ]\.?$""")
        val candidates = sorted.indices.filter { index ->
            // A surname and initials are sometimes split into separate PDF lines.
            // "Допуски .../Халидов" + "Т.Р." must be detected as one ending.
            val tail = sorted.subList(max(0, index - 2), index + 1).joinToString(" ") { it.value }
            completedTeacher.containsMatchIn(tail.trim())
        }
        if (candidates.isEmpty()) return sorted
        val closest = candidates.minByOrNull { kotlin.math.abs(sorted[it].y - groupY) } ?: return sorted
        // For a 4-line subject its teacher may be >8 pt from the center;
        // without a nearby reliable end marker we do NOT cut the text.
        if (abs(sorted[closest].y - groupY) > max(6f, min(8f, rowGap * .42f))) return sorted
        val previousTeacher = candidates.lastOrNull { it < closest }
        val first = if (previousTeacher == null) 0 else previousTeacher + 1
        return sorted.subList(first, closest + 1)
    }

    /** Distinguish a blank timetable row from a parser failure. */
    private fun rowContainsUnparsedLesson(rowItems: List<PdfTextItem>, columns: List<Column>, groupY: Float, rowGap: Float): Boolean {
        val radius = min(5f, max(3f, rowGap * .28f))
        return columns.any { column ->
            val next = columns.firstOrNull { it.startX > column.startX + 2f }?.startX ?: Float.MAX_VALUE
            rowItems.any { item ->
                val centerX = item.x + item.width * .5f
                // A title starts after the time/room lane. Header and bell
                // digits are deliberately ignored.
                centerX >= column.startX + 38f && centerX < next - 3f &&
                    abs(item.y - groupY) <= radius && item.text.any { it.isLetter() }
            }
        }
    }

    private fun splitTeacher(raw: String): Pair<String, String> {
        // Не делим на каждой косой черте: «МДК 01.01/01.02» — название предмета.
        val slash = Regex("""\s+/\s+(?=[А-ЯЁ])""").find(raw)
        if (slash != null) {
            val left = raw.substring(0, slash.range.first).trim()
            val right = raw.substring(slash.range.last + 1).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) return left to right
        }
        val teacher = teacherRegex.find(raw)
        if (teacher != null && teacher.range.first > 0) {
            val name = raw.substring(0, teacher.range.first).trim().trimEnd('/', ',', ';')
            if (name.isNotEmpty()) return name to teacher.value
        }
        return raw to ""
    }
}
