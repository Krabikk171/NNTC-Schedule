package ru.krabikk.nntcschedule

import kotlin.math.abs
import android.util.Log

/**
 * Новый координатный парсер расписания.
 *
 * Основная идея:
 *
 * 1. Находим нужную страницу по дате.
 * 2. Находим строку группы 2РПУ-25-1.
 * 3. Находим вертикальные границы именно этой строки
 *    через соседние строки групп.
 * 4. Находим реальные X-координаты колонок времени.
 * 5. Используем эти X как границы шести пар.
 * 6. Внутри каждой ячейки отдельно ищем:
 *      - кабинет
 *      - предмет
 *      - преподавателя
 *
 * Фиксированных roomRanges / subjectRanges / timeRanges здесь нет.
 */
object ScheduleParser2 {

    // =============================================================
    // Основные настройки
    // =============================================================

    private const val GROUP = "2РПУ-25-1"

    /*
     * Все группы находятся в самом левом столбце.
     */
    private const val GROUP_MAX_X = 35f

    /*
     * Допуск при объединении PDF-элементов
     * в одну визуальную строку.
     */
    private const val LINE_Y_TOLERANCE = 2f

    /*
     * Максимальный промежуток между соседними
     * кусками текста внутри одного блока.
     */
    private const val BLOCK_GAP_THRESHOLD = 4f

    /*
     * Если не удалось найти соседнюю группу,
     * используем этот запас сверху/снизу.
     */
    private const val GROUP_HALF_HEIGHT = 30f

    /*
     * Временная колонка занимает примерно первые
     * 30-35 пикселей после её X-координаты.
     *
     * Всё правее считается содержимым пары.
     */
    private const val TIME_COLUMN_WIDTH = 18f

    /*
     * Небольшой запас перед следующей парой.
     */
    private const val NEXT_COLUMN_PADDING = 5f

    /*
     * Допуск для объединения одинаковых X-координат
     * при поиске колонок времени.
     */
    private const val X_CLUSTER_TOLERANCE = 12f

    /*
     * Область, в которой ищем вторую часть времени,
     * если PDF разделил его на несколько элементов.
     */
    private const val TIME_RECONSTRUCT_X_RANGE = 34f
    private const val TIME_RECONSTRUCT_Y_RANGE = 18f


    // =============================================================
    // Регулярные выражения
    // =============================================================

    private val dayHeaderRegex =
        Regex(
            """Расписание\s+занятий\s+на\s+(\d{1,2}\s+[А-Яа-яЁё]+\s+\d{4}\s+г\.)\s*\(([^)]+)\)""",
            RegexOption.IGNORE_CASE
        )

    /*
     * Именно наша группа.
     */
    private val groupRegex =
        Regex(
            """2\s*РПУ\s*-\s*25\s*-\s*1""",
            RegexOption.IGNORE_CASE
        )

    /*
     * Более общий шаблон группы.
     *
     * Нужен для поиска соседних строк:
     *
     * 1РПУ-26-1
     * 1ТМ-26-1
     * 1ИКСиС-26-1
     * 2РПУ-25-1
     * и т.д.
     */
    private val anyGroupRegex =
        Regex(
            """^[0-9A-Za-zА-Яа-яЁё]+-\d{2,4}-\d+$"""
        )

    /*
     * Полное время.
     *
     * Поддерживаем варианты:
     *
     * 8.10-9.40
     * 10:00-11:30
     * 10.50-12-20
     */
    private val timeRegex =
        Regex(
            """(\d{1,2})\s*[:.]\s*(\d{2})\s*[-–—]\s*(\d{1,2})\s*[:.\-]\s*(\d{2})"""
        )

    /*
     * Время, у которого пока есть только начало:
     *
     * 8.10-
     * 10:00-
     */
    private val timeStartRegex =
        Regex(
            """^\d{1,2}\s*[:.]\s*\d{2}\s*[-–—]?$"""
        )

    /*
     * Время без дефиса.
     *
     * Например:
     * 9.40
     * 11:30
     */
    private val timeEndRegex =
        Regex(
            """^\d{1,2}\s*[:.]\s*\d{2}$"""
        )

    /*
     * Кабинет.
     *
     * Поддерживаем:
     * 51
     * 9
     * 29
     * 32/33
     * с/з
     */
    private val roomRegex =
        Regex(
            """^\d{1,4}(?:\s*/\s*\d{1,4})?$|^с\s*/\s*з$|^с\s*з$""",
            RegexOption.IGNORE_CASE
        )


    // =============================================================
    // Поиск дней
    // =============================================================

    fun findDays(
        text: String
    ): List<DayBlock> {

        val matches =
            dayHeaderRegex
                .findAll(text)
                .toList()

        if (matches.isEmpty()) {
            return emptyList()
        }

        val result =
            mutableListOf<DayBlock>()

        for (index in matches.indices) {

            val current =
                matches[index]

            val start =
                current.range.first

            val end =
                if (index + 1 < matches.size) {
                    matches[index + 1].range.first
                } else {
                    text.length
                }

            result.add(
                DayBlock(
                    date =
                        current
                            .groupValues[1]
                            .trim(),

                    weekday =
                        current
                            .groupValues[2]
                            .trim(),

                    text =
                        text.substring(
                            start,
                            end
                        )
                )
            )
        }

        return result
    }


    // =============================================================
    // Основной разбор документа
    // =============================================================

    fun parseDocument(
        items: List<PdfTextItem>,
        days: List<DayBlock>
    ): List<ParsedDay> {

        if (
            items.isEmpty() ||
            days.isEmpty()
        ) {
            return days.map {
                ParsedDay(
                    date = it.date,
                    weekday = it.weekday,
                    lessons = emptyList()
                )
            }
        }

        /*
         * Группируем PDF-элементы по страницам.
         */
        val pageGroups =
            items.groupBy {
                it.page
            }

        /*
         * Текст каждой страницы нужен только
         * для определения соответствующей даты.
         */
        val pageTexts =
            pageGroups.mapValues { (_, pageItems) ->
                buildPageTextForDateSearch(
                    pageItems
                )
            }

        val result =
            mutableListOf<ParsedDay>()

        for (day in days) {

            val matchingPage =
                pageGroups.entries.firstOrNull { entry ->

                    val pageText =
                        pageTexts[entry.key]
                            ?: return@firstOrNull false

                    containsDate(
                        pageText = pageText,
                        date = day.date
                    )
                }

            if (matchingPage == null) {

                result.add(
                    ParsedDay(
                        date = day.date,
                        weekday = day.weekday,
                        lessons = emptyList()
                    )
                )

                continue
            }

            result.add(
                parsePage(
                    items = matchingPage.value,
                    date = day.date,
                    weekday = day.weekday
                )
            )
        }

        return result
    }


    // =============================================================
    // Разбор одной страницы
    // =============================================================

    private fun parsePage(
        items: List<PdfTextItem>,
        date: String,
        weekday: String
    ): ParsedDay {

        /*
         * Сортируем страницу для предсказуемой работы
         * с координатами.
         */
        val pageItems =
            items.sortedWith(
                compareBy<PdfTextItem> {
                    it.y
                }.thenBy {
                    it.x
                }
            )

        // ---------------------------------------------------------
        // 1. Ищем нашу группу
        // ---------------------------------------------------------

        val groupY =
            findGroupY(
                pageItems
            )

        if (groupY == null) {

            return ParsedDay(
                date = date,
                weekday = weekday,
                lessons = emptyList()
            )
        }

        // ---------------------------------------------------------
        // 2. Определяем вертикальную область группы
        // ---------------------------------------------------------

        val groupBand =
            findGroupBand(
                items = pageItems,
                groupY = groupY
            )

        // ---------------------------------------------------------
        // 3. Оставляем только элементы нашей строки
        // ---------------------------------------------------------

        val groupItems =
            pageItems.filter { item ->

                item.y >= groupBand.first &&
                        item.y <= groupBand.second
            }

        if (groupItems.isEmpty()) {

            return ParsedDay(
                date = date,
                weekday = weekday,
                lessons = emptyList()
            )
        }

        // ---------------------------------------------------------
        // 4. Пытаемся найти времена прямо в строке группы
        // ---------------------------------------------------------

        var timeColumns =
            findTimeColumnsInGroupRow(
                groupItems = groupItems
            )

        /*
         * Для некоторых страниц время вынесено
         * в отдельный блок над таблицей.
         *
         * Поэтому если в самой строке группы
         * времени недостаточно, ищем их по всей странице.
         */
        if (timeColumns.size < 3) {

            timeColumns =
                findTimeColumnsOnPage(
                    pageItems = pageItems
                )
        }

        /*
         * Без координат колонок времени мы не можем
         * надёжно разделить шесть ячеек.
         */
        if (timeColumns.isEmpty()) {

            return ParsedDay(
                date = date,
                weekday = weekday,
                lessons = emptyList()
            )
        }

        Log.d(
            "ScheduleParser2Debug",
            "$date | groupY=$groupY | band=${groupBand.first}..${groupBand.second}"
        )

        Log.d(
            "ScheduleParser2Debug",
            "$date | TIME COLUMNS: ${
                timeColumns.mapIndexed { index, column ->
                    "${index + 1}: x=${column.x}, time=${column.time}"
                }.joinToString(" | ")
            }"
        )

        // ---------------------------------------------------------
        // 5. Разбираем пары
        // ---------------------------------------------------------

        val lessons =
            mutableListOf<ParsedLesson>()

        val count =
            minOf(
                6,
                timeColumns.size
            )

        for (index in 0 until count) {

            val timeColumn =
                timeColumns[index]

            /*
             * Извлекаем только содержимое
             * соответствующей пары.
             */
            val cellItems =
                extractPairCell(
                    groupItems = groupItems,
                    timeColumns = timeColumns,
                    index = index
                )

            Log.d(
                "ScheduleParser2Debug",
                "$date | PAIR ${index + 1} | BLOCKS: ${
                    buildTextBlocks(cellItems)
                        .sortedWith(
                            compareBy<TextBlock> {
                                it.y
                            }.thenBy {
                                it.x
                            }
                        )
                        .joinToString(" || ") {
                            "[x=${it.x}, y=${it.y}, text='${it.text}']"
                        }
                }"
            )

            if (cellItems.isEmpty()) {
                continue
            }

            /*
             * Превращаем PDF-символы в более крупные
             * текстовые блоки.
             */
            val blocks =
                buildTextBlocks(
                    cellItems
                )
                    .sortedWith(
                        compareBy<TextBlock> {
                            it.y
                        }.thenBy {
                            it.x
                        }
                    )

            /*
             * Отбрасываем случайно попавшие куски времени.
             */
            val contentBlocks =
                blocks.filterNot {
                    isTimeLike(
                        it.text
                    )
                }

            if (contentBlocks.isEmpty()) {
                continue
            }

            // -----------------------------------------------------
            // Кабинет
            // -----------------------------------------------------

            val roomIndex =
                contentBlocks.indexOfFirst {
                    isRoomText(
                        it.text
                    )
                }

            val room =
                if (roomIndex >= 0) {

                    normalizeRoom(
                        contentBlocks[
                            roomIndex
                        ].text
                    )

                } else {

                    "—"
                }

            // -----------------------------------------------------
            // Предмет + преподаватель
            // -----------------------------------------------------

            val subjectBlocks =
                contentBlocks.filterIndexed { blockIndex, _ ->

                    blockIndex != roomIndex
                }

            val subjectText =
                subjectBlocks
                    .sortedWith(
                        compareBy<TextBlock> {
                            it.y
                        }.thenBy {
                            it.x
                        }
                    )
                    .joinToString(" ") {
                        it.text
                    }
                    .replace(
                        Regex("""\s+"""),
                        " "
                    )
                    .trim()

            /*
             * Если кроме кабинета ничего нет,
             * такую ячейку считаем пустой.
             */
            if (subjectText.isBlank()) {
                continue
            }

            val teacherSplit =
                splitTeacher(
                    subjectText
                )

            lessons.add(
                ParsedLesson(
                    number =
                        index + 1,

                    time =
                        timeColumn.time,

                    room =
                        room,

                    subject =
                        teacherSplit.first,

                    teacher =
                        teacherSplit.second
                )
            )
        }

        return ParsedDay(
            date = date,
            weekday = weekday,
            lessons = lessons
        )
    }


    // =============================================================
    // Поиск строки группы
    // =============================================================

    private fun findGroupY(
        items: List<PdfTextItem>
    ): Float? {

        val lines =
            buildLines(
                items
            )

        val target =
            lines.firstOrNull { line ->

                val leftItems =
                    line.items.filter {
                        it.x < GROUP_MAX_X
                    }

                if (leftItems.isEmpty()) {
                    return@firstOrNull false
                }

                val text =
                    buildLineText(
                        leftItems
                    )

                groupRegex.matches(
                    text.replace(
                        Regex("""\s+"""),
                        ""
                    )
                )
            }

        return target?.y
    }


    // =============================================================
    // Вертикальная область строки группы
    // =============================================================

    private fun findGroupBand(
        items: List<PdfTextItem>,
        groupY: Float
    ): Pair<Float, Float> {

        /*
         * Находим Y всех строк групп.
         */
        val groupYs =
            findAllGroupYs(
                items
            ).sorted()

        /*
         * Находим положение нашей группы
         * среди остальных групп.
         */
        val currentIndex =
            groupYs.indexOfFirst {
                abs(
                    it - groupY
                ) <= LINE_Y_TOLERANCE
            }

        /*
         * Базовый размер строки.
         *
         * По реальным координатам PDF наша строка
         * занимает примерно ±25–30 единиц относительно
         * центра группы.
         *
         * Поэтому больше не используем огромные ±40.
         */
        var topY =
            groupY -
                    GROUP_HALF_HEIGHT

        var bottomY =
            groupY +
                    GROUP_HALF_HEIGHT

        /*
         * Если рядом есть предыдущая группа,
         * не позволяем нашей области пересечься
         * с её строкой.
         */
        if (currentIndex > 0) {

            val previousY =
                groupYs[
                    currentIndex - 1
                ]

            val border =
                (
                        previousY +
                                groupY
                        ) / 2f

            topY =
                maxOf(
                    topY,
                    border
                )
        }

        /*
         * То же самое для следующей группы.
         */
        if (
            currentIndex >= 0 &&
            currentIndex + 1 <
            groupYs.size
        ) {

            val nextY =
                groupYs[
                    currentIndex + 1
                ]

            val border =
                (
                        groupY +
                                nextY
                        ) / 2f

            bottomY =
                minOf(
                    bottomY,
                    border
                )
        }

        return topY to bottomY
    }

    private fun findAllGroupYs(
        items: List<PdfTextItem>
    ): List<Float> {

        return buildLines(
            items
        )
            .mapNotNull { line ->

                val leftItems =
                    line.items.filter {
                        it.x < GROUP_MAX_X
                    }

                if (leftItems.isEmpty()) {
                    return@mapNotNull null
                }

                val text =
                    buildLineText(
                        leftItems
                    )
                        .replace(
                            Regex("""\s+"""),
                            ""
                        )

                if (
                    anyGroupRegex.matches(
                        text
                    )
                ) {
                    line.y
                } else {
                    null
                }
            }
            .distinctBy {
                (it / 2f).toInt()
            }
    }


    // =============================================================
    // Временные колонки в строке группы
    // =============================================================

    private fun findTimeColumnsInGroupRow(
        groupItems: List<PdfTextItem>
    ): List<TimeColumn> {

        val blocks =
            buildTextBlocks(
                groupItems
            )

        val candidates =
            blocks
                .filter {
                    isTimeLike(
                        it.text
                    )
                }
                .sortedBy {
                    it.x
                }

        return buildTimeColumns(
            candidates = candidates,
            sourceItems = groupItems
        )
    }


    // =============================================================
    // Временные колонки по всей странице
    // =============================================================

    private fun findTimeColumnsOnPage(
        pageItems: List<PdfTextItem>
    ): List<TimeColumn> {

        val blocks =
            buildTextBlocks(
                pageItems
            )

        val candidates =
            blocks
                .filter {
                    isTimeLike(
                        it.text
                    )
                }
                .sortedBy {
                    it.x
                }

        return buildTimeColumns(
            candidates = candidates,
            sourceItems = pageItems
        )
    }


    // =============================================================
    // Построение шести колонок времени
    // =============================================================

    private fun buildTimeColumns(
        candidates: List<TextBlock>,
        sourceItems: List<PdfTextItem>
    ): List<TimeColumn> {

        if (candidates.isEmpty()) {
            return emptyList()
        }

        /*
         * Сначала объединяем близкие X-координаты
         * в одну колонку.
         *
         * Например:
         *
         * 39.1
         * 39.8
         * 40.0
         *
         * относятся к одной колонке времени.
         */
        val clusters =
            mutableListOf<
                    MutableList<TextBlock>
                    >()

        for (
        candidate
        in candidates.sortedBy {
            it.x
        }
        ) {

            val lastCluster =
                clusters.lastOrNull()

            if (lastCluster == null) {

                clusters.add(
                    mutableListOf(
                        candidate
                    )
                )

                continue
            }

            val averageX =
                lastCluster
                    .map {
                        it.x
                    }
                    .average()
                    .toFloat()

            if (
                abs(
                    candidate.x -
                            averageX
                ) <= X_CLUSTER_TOLERANCE
            ) {

                lastCluster.add(
                    candidate
                )

            } else {

                clusters.add(
                    mutableListOf(
                        candidate
                    )
                )
            }
        }

        /*
         * Для каждого X-кластера пытаемся определить,
         * какое время встречается там чаще всего.
         *
         * Это важно для среды:
         *
         * 08:00–08:50 из блока "ЗВОНКИ"
         *
         * может встретиться один раз,
         *
         * а настоящее время пары:
         *
         * 08:10–09:40
         *
         * встречается во множестве строк расписания.
         *
         * Поэтому берём самое частое значение.
         */
        data class ColumnCandidate(
            val x: Float,
            val time: String,
            val score: Int
        )

        val columnCandidates =
            mutableListOf<ColumnCandidate>()

        for (
        cluster
        in clusters
        ) {

            val resolvedTimes =
                cluster.mapNotNull { block ->

                    resolveTime(
                        sourceItems = sourceItems,
                        anchor = block
                    )
                }

            if (
                resolvedTimes.isEmpty()
            ) {
                continue
            }

            val frequencies =
                resolvedTimes
                    .groupingBy {
                        it
                    }
                    .eachCount()

            val best =
                frequencies
                    .maxByOrNull {
                        it.value
                    }
                    ?: continue

            val averageX =
                cluster
                    .map {
                        it.x
                    }
                    .average()
                    .toFloat()

            columnCandidates.add(
                ColumnCandidate(
                    x = averageX,
                    time = best.key,
                    score = best.value
                )
            )
        }

        /*
         * Берём наиболее часто встречающиеся
         * временные колонки.
         *
         * Так мы отбрасываем редкие времена
         * из дополнительных блоков страницы.
         */
        return columnCandidates
            .sortedWith(
                compareByDescending<ColumnCandidate> {
                    it.score
                }.thenBy {
                    it.x
                }
            )
            .take(6)
            .sortedBy {
                it.x
            }
            .map {
                TimeColumn(
                    x = it.x,
                    time = it.time
                )
            }
    }


    // =============================================================
    // Восстановление полного времени
    // =============================================================

    private fun resolveTime(
        sourceItems: List<PdfTextItem>,
        anchor: TextBlock
    ): String? {

        /*
         * Сначала самый простой вариант:
         * сам блок уже содержит полное время.
         */
        val direct =
            normalizeTimeText(
                anchor.text
            )

        val directMatch =
            timeRegex.find(
                direct
            )

        if (directMatch != null) {

            return formatTime(
                directMatch
            )
        }

        /*
         * Иначе ищем вторую часть времени
         * в небольшом прямоугольнике вокруг X/Y.
         *
         * Важно:
         * мы НЕ берём всю страницу.
         *
         * Поэтому номер кабинета и предмет
         * обычно уже не попадают сюда.
         */
        val nearby =
            sourceItems.filter { item ->

                item.x >=
                        anchor.x -
                        2f &&

                        item.x <=
                        anchor.x +
                        TIME_RECONSTRUCT_X_RANGE &&

                        item.y >=
                        anchor.y -
                        TIME_RECONSTRUCT_Y_RANGE &&

                        item.y <=
                        anchor.y +
                        TIME_RECONSTRUCT_Y_RANGE
            }

        if (nearby.isEmpty()) {
            return null
        }

        /*
         * Пытаемся собрать всё содержимое
         * маленькой временной области.
         */
        val compact =
            nearby
                .sortedWith(
                    compareBy<PdfTextItem> {
                        it.y
                    }.thenBy {
                        it.x
                    }
                )
                .joinToString("") {
                    it.text
                }
                .replace(
                    Regex("""\s+"""),
                    ""
                )

        val match =
            timeRegex.find(
                compact
            )

        if (match != null) {

            return formatTime(
                match
            )
        }

        /*
         * Отдельно проверяем ситуацию,
         * когда начало и конец пришли двумя блоками.
         */
        val blocks =
            buildTextBlocks(
                nearby
            )
                .sortedWith(
                    compareBy<TextBlock> {
                        it.x
                    }.thenBy {
                        it.y
                    }
                )

        val startBlock =
            blocks.firstOrNull {
                timeStartRegex.matches(
                    normalizeTimeText(
                        it.text
                    )
                )
            }

        val endBlock =
            blocks.firstOrNull {
                timeEndRegex.matches(
                    normalizeTimeText(
                        it.text
                    )
                ) &&
                        it.x >=
                        (
                                startBlock?.x
                                    ?: anchor.x
                                )
            }

        if (
            startBlock != null &&
            endBlock != null
        ) {

            val start =
                Regex(
                    """(\d{1,2})\s*[:.]\s*(\d{2})"""
                )
                    .find(
                        normalizeTimeText(
                            startBlock.text
                        )
                    )
                    ?: return null

            val end =
                Regex(
                    """(\d{1,2})\s*[:.]\s*(\d{2})"""
                )
                    .find(
                        normalizeTimeText(
                            endBlock.text
                        )
                    )
                    ?: return null

            return buildString {

                append(
                    start
                        .groupValues[1]
                        .padStart(
                            2,
                            '0'
                        )
                )

                append(":")

                append(
                    start.groupValues[2]
                )

                append("–")

                append(
                    end
                        .groupValues[1]
                        .padStart(
                            2,
                            '0'
                        )
                )

                append(":")

                append(
                    end.groupValues[2]
                )
            }
        }

        return null
    }


    // =============================================================
    // Выделение одной ячейки пары
    // =============================================================

    private fun extractPairCell(
        groupItems: List<PdfTextItem>,
        timeColumns: List<TimeColumn>,
        index: Int
    ): List<PdfTextItem> {

        val current =
            timeColumns[
                index
            ]

        /*
         * Отрезаем саму колонку времени.
         *
         * Например:
         *
         * время      кабинет     предмет
         *  ^            ^           ^
         * 35px       дальше уже содержимое
         */
        val leftX =
            current.x +
                    TIME_COLUMN_WIDTH

        /*
         * Следующая колонка времени
         * является естественной правой границей.
         */
        val rightX =
            if (
                index + 1 <
                timeColumns.size
            ) {

                timeColumns[
                    index + 1
                ].x -
                        NEXT_COLUMN_PADDING

            } else {

                /*
                 * Для последней пары просто берём
                 * максимальную X-координату строки.
                 */
                (
                        groupItems
                            .maxOfOrNull {
                                it.x +
                                        it.width
                            }
                            ?: leftX
                        ) + 5f
            }

        if (
            rightX <= leftX
        ) {
            return emptyList()
        }

        /*
         * Проверяем пересечение элемента
         * с областью ячейки, а не только его левую границу.
         *
         * Это полезно для длинных слов.
         */
        return groupItems.filter { item ->

            item.x +
                    item.width >
                    leftX &&

                    item.x <
                    rightX
        }
    }


    // =============================================================
    // Построение строк
    // =============================================================

    private fun buildLines(
        items: List<PdfTextItem>
    ): List<PdfLine> {

        if (items.isEmpty()) {
            return emptyList()
        }

        val sorted =
            items.sortedWith(
                compareBy<PdfTextItem> {
                    it.y
                }.thenBy {
                    it.x
                }
            )

        val lines =
            mutableListOf<
                    MutableList<PdfTextItem>
                    >()

        for (item in sorted) {

            val currentLine =
                lines.lastOrNull()

            if (currentLine == null) {

                lines.add(
                    mutableListOf(
                        item
                    )
                )

                continue
            }

            val averageY =
                currentLine
                    .map {
                        it.y
                    }
                    .average()
                    .toFloat()

            if (
                abs(
                    item.y -
                            averageY
                ) <= LINE_Y_TOLERANCE
            ) {

                currentLine.add(
                    item
                )

            } else {

                lines.add(
                    mutableListOf(
                        item
                    )
                )
            }
        }

        return lines.map { line ->

            val sortedLine =
                line.sortedBy {
                    it.x
                }

            PdfLine(

                y =
                    sortedLine
                        .map {
                            it.y
                        }
                        .average()
                        .toFloat(),

                items =
                    sortedLine
            )
        }
    }


    // =============================================================
    // Построение текстовых блоков
    // =============================================================

    /**
     * Очень важный момент:
     *
     * Сначала делим элементы на строки,
     * а уже потом соединяем соседние элементы
     * внутри каждой строки.
     *
     * Старый вариант делал это сразу по всему списку,
     * из-за чего элементы разных Y могли случайно
     * попасть в один блок.
     */
    private fun buildTextBlocks(
        items: List<PdfTextItem>
    ): List<TextBlock> {

        if (items.isEmpty()) {
            return emptyList()
        }

        val result =
            mutableListOf<TextBlock>()

        val lines =
            buildLines(
                items
            )

        for (line in lines) {

            val sortedLine =
                line.items.sortedBy {
                    it.x
                }

            var current =
                mutableListOf<PdfTextItem>()

            fun flushCurrent() {

                if (current.isEmpty()) {
                    return
                }

                val sortedCurrent =
                    current.sortedBy {
                        it.x
                    }

                val text =
                    buildLineText(
                        sortedCurrent
                    )

                if (text.isNotBlank()) {

                    val first =
                        sortedCurrent.first()

                    val last =
                        sortedCurrent.last()

                    result.add(
                        TextBlock(

                            x =
                                first.x,

                            y =
                                sortedCurrent
                                    .map {
                                        it.y
                                    }
                                    .average()
                                    .toFloat(),

                            width =
                                (
                                        last.x +
                                                last.width
                                        ) -
                                        first.x,

                            text =
                                text
                        )
                    )
                }

                current =
                    mutableListOf()
            }

            var previous:
                    PdfTextItem? =
                null

            for (item in sortedLine) {

                if (previous != null) {

                    val gap =
                        item.x -
                                (
                                        previous.x +
                                                previous.width
                                        )

                    if (
                        gap >
                        BLOCK_GAP_THRESHOLD
                    ) {
                        flushCurrent()
                    }
                }

                current.add(
                    item
                )

                previous =
                    item
            }

            flushCurrent()
        }

        return result
    }


    // =============================================================
    // Определение времени
    // =============================================================

    private fun isTimeLike(
        text: String
    ): Boolean {

        val compact =
            normalizeTimeText(
                text
            )

        return timeStartRegex.matches(
            compact
        ) ||
                timeEndRegex.matches(
                    compact
                ) ||
                timeRegex.containsMatchIn(
                    compact
                )
    }


    private fun normalizeTimeText(
        text: String
    ): String {

        return text
            .replace(
                Regex("""\s+"""),
                ""
            )
            .replace(
                "–",
                "-"
            )
            .replace(
                "—",
                "-"
            )
    }


    // =============================================================
    // Определение кабинета
    // =============================================================

    private fun isRoomText(
        text: String
    ): Boolean {

        val normalized =
            text
                .replace(
                    Regex("""\s+"""),
                    ""
                )
                .lowercase()

        return roomRegex.matches(
            normalized
        )
    }


    private fun normalizeRoom(
        text: String
    ): String {

        val normalized =
            text
                .replace(
                    Regex("""\s+"""),
                    ""
                )
                .lowercase()

        return when {

            normalized == "с/з" ->
                "с/з"

            normalized == "сз" ->
                "с/з"

            normalized.matches(
                Regex(
                    """\d{1,4}/\d{1,4}"""
                )
            ) ->
                normalized

            else ->
                normalized.filter {
                    it.isDigit()
                }
        }
            .ifBlank {
                "—"
            }
    }


    // =============================================================
    // Разделение предмета и преподавателя
    // =============================================================

    private fun splitTeacher(
        text: String
    ): Pair<String, String> {

        val parts =
            text.split(
                "/",
                limit = 2
            )

        val subject =
            parts
                .getOrNull(0)
                ?.trim()
                ?: text.trim()

        val teacher =
            parts
                .getOrNull(1)
                ?.trim()
                ?: ""

        return subject to
                teacher
    }


    // =============================================================
    // Построение текста строки
    // =============================================================

    private fun buildLineText(
        items: List<PdfTextItem>
    ): String {

        if (items.isEmpty()) {
            return ""
        }

        return buildString {

            var previous:
                    PdfTextItem? =
                null

            for (
            item
            in items.sortedBy {
                it.x
            }
            ) {

                if (previous != null) {

                    val gap =
                        item.x -
                                (
                                        previous.x +
                                                previous.width
                                        )

                    if (
                        gap >
                        BLOCK_GAP_THRESHOLD
                    ) {
                        append(
                            " "
                        )
                    }
                }

                append(
                    item.text
                )

                previous =
                    item
            }

        }
            .replace(
                Regex("""\s+"""),
                " "
            )
            .trim()
    }


    // =============================================================
    // Поиск даты страницы
    // =============================================================

    private fun buildPageTextForDateSearch(
        items: List<PdfTextItem>
    ): String {

        return items
            .sortedWith(
                compareBy<PdfTextItem> {
                    it.y
                }.thenBy {
                    it.x
                }
            )
            .joinToString("") {
                it.text
            }
    }


    private fun containsDate(
        pageText: String,
        date: String
    ): Boolean {

        return normalize(
            pageText
        )
            .contains(
                normalize(
                    date
                )
            )
    }


    // =============================================================
    // Нормализация
    // =============================================================

    private fun normalize(
        text: String
    ): String {

        return text
            .replace(
                "\\s".toRegex(),
                ""
            )
            .replace(
                "–",
                "-"
            )
            .replace(
                "—",
                "-"
            )
            .lowercase()
            .trim()
    }


    // =============================================================
    // Форматирование времени
    // =============================================================

    private fun formatTime(
        match: MatchResult
    ): String {

        val h1 =
            match
                .groupValues[1]
                .padStart(
                    2,
                    '0'
                )

        val m1 =
            match.groupValues[2]

        val h2 =
            match
                .groupValues[3]
                .padStart(
                    2,
                    '0'
                )

        val m2 =
            match.groupValues[4]

        return "$h1:$m1–$h2:$m2"
    }


    // =============================================================
    // Внутренние структуры
    // =============================================================

    private data class PdfLine(
        val y: Float,
        val items: List<PdfTextItem>
    )

    private data class TextBlock(
        val x: Float,
        val y: Float,
        val width: Float,
        val text: String
    )

    private data class TimeColumn(
        val x: Float,
        val time: String
    )
}