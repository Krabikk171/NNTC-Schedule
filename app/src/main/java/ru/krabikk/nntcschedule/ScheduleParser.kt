package ru.krabikk.nntcschedule

import android.util.Log
import kotlin.math.abs


data class DayBlock(
    val date: String,
    val weekday: String,
    val text: String
)


data class ParsedSubgroup(
    val number: Int,
    val subject: String,
    val room: String,
    val teacher: String
)

data class ParsedLesson(
    val number: Int,
    val time: String,
    val room: String,
    val subject: String,
    val teacher: String,
    val subgroups: List<ParsedSubgroup> = emptyList()
)


data class ParsedDay(
    val date: String,
    val weekday: String,
    val lessons: List<ParsedLesson>
)


object ScheduleParser {

    private const val GROUP =
        "2РПУ-25-1"

    private const val GROUP_MAX_X =
        35f

    private const val GROUP_Y_TOLERANCE =
        2f

    private const val CELL_Y_TOLERANCE =
        5f

    private const val WORD_GAP_THRESHOLD =
        4f


    // -------------------------------------------------------------
    // Заголовок дня
    // -------------------------------------------------------------

    private val dayHeaderRegex =
        Regex(
            """Расписание\s+занятий\s+на\s+(\d{1,2}\s+[А-Яа-яЁё]+\s+\d{4}\s+г\.)\s*\(([^)]+)\)""",
            RegexOption.IGNORE_CASE
        )


    // -------------------------------------------------------------
    // Поиск группы в старом текстовом представлении
    // -------------------------------------------------------------

    private val groupRegex =
        Regex(
            """2\s*РПУ\s*-\s*25\s*-\s*1""",
            RegexOption.IGNORE_CASE
        )


    // -------------------------------------------------------------
    // Координаты шести временных колонок
    //
    // Получены из реального PDF расписания.
    // -------------------------------------------------------------

    private val timeRanges =
        listOf(

            35f..55f,

            156f..176f,

            273f..295f,

            412f..432f,

            537f..557f,

            665f..685f
        )


    // -------------------------------------------------------------
    // Области кабинетов
    // -------------------------------------------------------------

    private val roomRanges =
        listOf(

            55f..78f,

            178f..198f,

            297f..315f,

            430f..447f,

            555f..572f,

            683f..700f
        )


    // -------------------------------------------------------------
    // Области предметов
    // -------------------------------------------------------------

    private val subjectRanges =
        listOf(

            80f..158f,

            198f..275f,

            318f..412f,

            450f..540f,

            575f..665f,

            705f..800f
        )


    // -------------------------------------------------------------
    // Время
    // -------------------------------------------------------------

    /*
     * В PDF начало времени иногда записано через точку:
     *
     * 8.10-9.40
     *
     * а иногда через двоеточие:
     *
     * 10:00-11:30
     *
     * Поэтому принимаем оба варианта.
     */
    private val timeRegex =
        Regex(
            """(\d{1,2})\s*[:.]\s*(\d{2})\s*-\s*(\d{1,2})\s*[:.-]\s*(\d{2})"""
        )


    // -------------------------------------------------------------
    // Поиск дней
    // -------------------------------------------------------------

    /**
     * Старый текстовый разбор дат.
     *
     * Используется для получения списка дней,
     * после чего конкретные страницы уже определяются
     * по координатам PDF.
     */
    fun findDays(
        text: String
    ): List<DayBlock> {

        val matches =
            dayHeaderRegex
                .findAll(text)
                .toList()


        if (
            matches.isEmpty()
        ) {

            return emptyList()
        }


        val result =
            mutableListOf<DayBlock>()


        for (
        i in matches.indices
        ) {

            val current =
                matches[i]


            val start =
                current.range.first


            val end =
                if (
                    i + 1 <
                    matches.size
                ) {

                    matches[
                        i + 1
                    ]
                        .range.first

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


    // -------------------------------------------------------------
    // Основной разбор документа
    // -------------------------------------------------------------

    /**
     * Получает координатные элементы PDF
     * и список найденных дней.
     *
     * Для каждого дня ищется страница,
     * содержащая соответствующую дату.
     *
     * После этого разбирается только наша группа.
     */
    fun parseDocument(
        items: List<PdfTextItem>,
        days: List<DayBlock>
    ): List<ParsedDay> {

        val totalStartTime =
            System.nanoTime()

        if (
            items.isEmpty() ||
            days.isEmpty()
        ) {

            return days.map {
                ParsedDay(

                    date =
                        it.date,

                    weekday =
                        it.weekday,

                    lessons =
                        emptyList()
                )
            }
        }


        val pageGroupsStartTime =
            System.nanoTime()

        val pageGroups =
            items.groupBy {
                it.page
            }


        val pageTexts =
            pageGroups.mapValues { (_, pageItems) ->
                buildPageTextForDateSearch(
                    pageItems
                )
            }


        val pageTextsTimeMs =
            (
                    System.nanoTime() -
                            pageGroupsStartTime
                    ) / 1_000_000


        Log.d(
            "SCHEDULE_PARSER",
            "Подготовка страниц: ${pageTextsTimeMs} мс, страниц: ${pageGroups.size}"
        )

        val result =
            mutableListOf<ParsedDay>()


        for (
        day
        in days
        ) {

            val pageSearchStartTime =
                System.nanoTime()


            val matchingPage =
                pageGroups
                    .entries
                    .firstOrNull { entry ->

                        val pageText =
                            pageTexts[
                                entry.key
                            ]
                                ?: return@firstOrNull false


                        containsDate(
                            pageText =
                                pageText,

                            date =
                                day.date
                        )
                    }


            val pageSearchTimeMs =
                (
                        System.nanoTime() -
                                pageSearchStartTime
                        ) / 1_000_000


            Log.d(
                "SCHEDULE_PARSER",
                "Поиск страницы ${day.date}: ${pageSearchTimeMs} мс"
            )


            if (
                matchingPage == null
            ) {

                result.add(
                    ParsedDay(

                        date =
                            day.date,

                        weekday =
                            day.weekday,

                        lessons =
                            emptyList()
                    )
                )

                continue
            }


            val parsePageStartTime =
                System.nanoTime()


            val parsedDay =
                parsePage(

                    items =
                        matchingPage.value,

                    date =
                        day.date,

                    weekday =
                        day.weekday
                )


            val parsePageTimeMs =
                (
                        System.nanoTime() -
                                parsePageStartTime
                        ) / 1_000_000


            Log.d(
                "SCHEDULE_PARSER",
                "Разбор страницы ${day.date}: ${parsePageTimeMs} мс"
            )


            result.add(
                parsedDay
            )
        }


        val totalTimeMs =
            (
                    System.nanoTime() -
                            totalStartTime
                    ) / 1_000_000


        Log.d(
            "SCHEDULE_PARSER",
            "ИТОГО parseDocument: ${totalTimeMs} мс"
        )


        result.forEach { day ->

            Log.d(
                "SCHEDULE_PARSER",
                "${day.date}: ${day.lessons.size} пар"
            )
        }


        return result
    }


    // -------------------------------------------------------------
    // Разбор одной страницы
    // -------------------------------------------------------------

    private fun parsePage(
        items: List<PdfTextItem>,
        date: String,
        weekday: String
    ): ParsedDay {

        val totalStartTime =
            System.nanoTime()

        val sortStartTime =
            System.nanoTime()


        val pageItems =
            items.sortedWith(

                compareBy<PdfTextItem> {
                    it.y
                }.thenBy {
                    it.x
                }
            )


        val sortTimeMs =
            (
                    System.nanoTime() -
                            sortStartTime
                    ) / 1_000_000


        val groupSearchStartTime =
            System.nanoTime()


        val groupY =
            findGroupY(
                pageItems
            )



        val groupSearchTimeMs =
            (
                    System.nanoTime() -
                            groupSearchStartTime
                    ) / 1_000_000


        if (
            groupY == null
        ) {

            return ParsedDay(

                date =
                    date,

                weekday =
                    weekday,

                lessons =
                    emptyList()
            )
        }


        val lessons =
            mutableListOf<ParsedLesson>()

        val lessonsStartTime =
            System.nanoTime()

        val rowFilterStartTime =
            System.nanoTime()


        val groupRowItems =
            findGroupRowItems(
                items = pageItems,
                groupY = groupY
            )

        /*
 * Временная диагностика координат PDF.
 *
 * Показываем все элементы вокруг строки
 * нашей группы, чтобы точно увидеть,
 * где PDFBox считает границы кабинетов
 * и предметов.
 *
 * После исправления парсера этот блок удалим.
 */
        Log.d(
            "SCHEDULE_DEBUG",
            "========== $date | groupY=$groupY =========="
        )

        pageItems
            .filter { item ->
                abs(
                    item.y -
                            groupY
                ) <= 40f
            }
            .sortedWith(
                compareBy<PdfTextItem> {
                    it.y
                }.thenBy {
                    it.x
                }
            )
            .forEach { item ->

                Log.d(
                    "SCHEDULE_DEBUG",
                    "x=${item.x}, " +
                            "y=${item.y}, " +
                            "w=${item.width}, " +
                            "text='${item.text}'"
                )
            }

        Log.d(
            "SCHEDULE_DEBUG",
            "=========================================="
        )

        val rowFilterTimeMs =
            (
                    System.nanoTime() -
                            rowFilterStartTime
                    ) / 1_000_000

        /*
         * Проверяем все шесть возможных пар.
         */
        for (
        index
        in 0 until 6
        ) {

            val time =
                findTime(
                    pageItems = pageItems,
                    index = index
                )
                    ?: continue


            val room =
                findRoom(
                    pageItems = groupRowItems,
                    index = index
                )


            val subjectText =
                findSubjectText(
                    pageItems = groupRowItems,
                    index = index
                )


            /*
             * Если предмета именно в ячейке
             * нашей группы нет — пару не добавляем.
             *
             * Это не позволяет случайно подхватить
             * соседние группы.
             */
            if (
                subjectText.isBlank()
            ) {

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
                        time,

                    room =
                        room,

                    subject =
                        teacherSplit.first,

                    teacher =
                        teacherSplit.second
                )
            )
        }

        val lessonsTimeMs =
            (
                    System.nanoTime() -
                            lessonsStartTime
                    ) / 1_000_000


        val totalTimeMs =
            (
                    System.nanoTime() -
                            totalStartTime
                    ) / 1_000_000


        Log.d(
            "SCHEDULE_PARSER",
            "Страница $date: " +
                    "сортировка=${sortTimeMs}мс, " +
                    "группа=${groupSearchTimeMs}мс, " +
                    "строка=${rowFilterTimeMs}мс, " +
                    "6 пар=${lessonsTimeMs}мс, " +
                    "итого=${totalTimeMs}мс"
        )

        return ParsedDay(

            date =
                date,

            weekday =
                weekday,

            lessons =
                lessons
        )
    }


    // -------------------------------------------------------------
    // Координата строки группы
    // -------------------------------------------------------------

    /**
     * Ищем Y строки нашей группы.
     *
     * Группа расположена только в левом столбце,
     * поэтому берём элементы с X < GROUP_MAX_X.
     */
    private fun findGroupY(

        items: List<PdfTextItem>
    ): Float? {

        val groupItems =
            items.filter {
                it.x < GROUP_MAX_X
            }


        val lines =
            buildLines(
                groupItems
            )


        for (
        line
        in lines
        ) {

            if (
                normalize(
                    line.text
                ) ==
                normalize(
                    GROUP
                )
            ) {

                return line.y
            }
        }


        return null
    }

    private fun findGroupRowItems(
        items: List<PdfTextItem>,
        groupY: Float
    ): List<PdfTextItem> {

        /*
         * Берём левый столбец, где находятся группы,
         * и строим его визуальные строки.
         */
        val groupItems =
            items.filter {
                it.x < GROUP_MAX_X
            }

        val lines =
            buildLines(
                groupItems
            ).sortedBy {
                it.y
            }

        /*
         * Ищем строку, соответствующую нашей группе.
         */
        val groupIndex =
            lines.indexOfFirst {
                abs(
                    it.y -
                            groupY
                ) <= GROUP_Y_TOLERANCE
            }

        if (
            groupIndex == -1
        ) {
            /*
             * Запасной вариант:
             * если соседние строки определить не удалось,
             * сохраняем старое поведение.
             */
            return items.filter { item ->

                abs(
                    item.y -
                            groupY
                ) <= CELL_Y_TOLERANCE
            }
        }

        /*
         * Верхняя граница строки нашей группы.
         */
        val topY =
            if (
                groupIndex > 0
            ) {

                (
                        lines[groupIndex - 1].y +
                                groupY
                        ) / 2f

            } else {

                groupY - 30f
            }

        /*
         * Нижняя граница строки нашей группы.
         */
        val bottomY =
            if (
                groupIndex + 1 <
                lines.size
            ) {

                (
                        groupY +
                                lines[groupIndex + 1].y
                        ) / 2f

            } else {

                groupY + 30f
            }

        /*
         * Теперь берём ВСЕ элементы,
         * которые находятся внутри строки нашей группы.
         *
         * Это позволяет захватывать
         * многострочные названия предметов.
         */
        return items.filter { item ->

            item.y >= topY &&
                    item.y <= bottomY
        }
    }

    // -------------------------------------------------------------
    // Поиск времени
    // -------------------------------------------------------------

    /**
     * Временная строка находится
     * в соответствующей колонке.
     */
    private fun findTime(
        pageItems: List<PdfTextItem>,
        index: Int
    ): String? {

        /*
         * Время может находиться:
         *
         * 1. непосредственно в строках таблицы;
         * 2. в отдельном блоке "ЗВОНКИ" сверху страницы.
         *
         * Поэтому больше не привязываем поиск
         * времени к X-координате конкретной колонки.
         */

        val lines =
            buildLines(
                pageItems
            )

        /*
         * Собираем все найденные интервалы времени
         * сверху вниз по странице.
         *
         * distinct() нужен потому, что на странице
         * одно и то же время встречается в каждой
         * строке группы.
         */
        val times =
            lines
                .flatMap { line ->

                    timeRegex
                        .findAll(
                            line.text
                        )
                        .map {
                            formatTime(
                                it
                            )
                        }
                        .toList()
                }
                .distinct()

        /*
         * Нам нужен интервал времени именно
         * для номера пары.
         *
         * index = 0 → первая пара
         * index = 1 → вторая пара
         * и т.д.
         */
        return times.getOrNull(
            index
        )
    }


    // -------------------------------------------------------------
    // Поиск кабинета
    // -------------------------------------------------------------

    /**
     * Кабинет расположен между временем
     * и предметом соответствующей пары.
     */
    private fun findRoom(
        pageItems: List<PdfTextItem>,
        index: Int
    ): String {

        val range =
            roomRanges[
                index
            ]

        /*
         * pageItems здесь уже являются
         * элементами строки нашей группы.
         *
         * Поэтому повторно ограничивать
         * их по Y уже не нужно.
         */

        val candidates =
            pageItems.filter { item ->

                item.x in range &&

                        item.text.any {
                            it.isDigit()
                        }
            }

        if (
            candidates.isEmpty()
        ) {
            return "—"
        }

        return candidates

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

            .filter {
                it.isDigit()
            }

            .ifBlank {
                "—"
            }
    }


    // -------------------------------------------------------------
    // Поиск предмета и преподавателя
    // -------------------------------------------------------------

    /**
     * Получает текст предмета/преподавателя
     * из соответствующей области пары.
     *
     * Для текущего PDF этого достаточно,
     * так как предмет и преподаватель находятся
     * на одной визуальной строке.
     */
    private fun findSubjectText(
        pageItems: List<PdfTextItem>,
        index: Int
    ): String {

        val range =
            subjectRanges[
                index
            ]

        /*
         * pageItems уже ограничены
         * вертикальной областью нашей группы.
         *
         * Поэтому здесь оставляем только
         * горизонтальную границу конкретной пары.
         */

        val candidates =
            pageItems.filter { item ->

                item.x in range
            }

        if (
            candidates.isEmpty()
        ) {
            return ""
        }

        /*
         * Предмет может занимать несколько
         * визуальных строк.
         *
         * Поэтому сначала собираем строки,
         * а уже потом объединяем их через пробел.
         */
        return buildLines(
            candidates
        )
            .sortedBy {
                it.y
            }
            .joinToString(" ") {
                it.text
            }
            .replace(
                Regex("""\s+"""),
                " "
            )
            .trim()
    }


    // -------------------------------------------------------------
    // Разделение предмета и преподавателя
    // -------------------------------------------------------------

    /**
     * В PDF предмет и преподаватель разделены "/".
     */
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


    // -------------------------------------------------------------
    // Текст страницы
    // -------------------------------------------------------------

    /**
     * Собирает текст страницы
     * из координатных элементов.
     *
     * Используется для поиска страницы
     * с конкретной датой.
     */

    // -------------------------------------------------------------
// Быстрый текст страницы для поиска даты
// -------------------------------------------------------------
//
// Здесь нам НЕ нужны визуальные строки.
//
// Нам нужно только понять:
// содержит ли страница нужную дату.
//
// Поэтому не используем buildLines(),
// а просто сортируем элементы страницы
// сверху вниз и слева направо.
//
// -------------------------------------------------------------

    private fun buildPageTextForDateSearch(
        items: List<PdfTextItem>
    ): String {

        if (
            items.isEmpty()
        ) {
            return ""
        }


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

    private fun buildPageText(
        items: List<PdfTextItem>
    ): String {

        val lines =
            buildLines(
                items
            )


        return lines

            .sortedBy {
                it.y
            }

            .joinToString(
                separator = "\n"
            ) {
                it.text
            }
    }


    // -------------------------------------------------------------
    // Проверка даты на странице
    // -------------------------------------------------------------

    private fun containsDate(
        pageText: String,
        date: String
    ): Boolean {

        val pageNormalized =
            normalize(
                pageText
            )


        val dateNormalized =
            normalize(
                date
            )


        return pageNormalized.contains(
            dateNormalized
        )
    }


    // -------------------------------------------------------------
    // Сборка визуальных строк
    // -------------------------------------------------------------

    private fun buildLines(
        items: List<PdfTextItem>
    ): List<VisualLineInternal> {

        if (
            items.isEmpty()
        ) {

            return emptyList()
        }


        val result =
            mutableListOf<
                    MutableList<PdfTextItem>
                    >()


        val sortedItems =
            items.sortedWith(

                compareBy<PdfTextItem> {
                    it.y
                }.thenBy {
                    it.x
                }
            )


        for (
        item
        in sortedItems
        ) {

            val line =
                result.firstOrNull { currentLine ->

                    val averageY =
                        currentLine
                            .map {
                                it.y
                            }
                            .average()
                            .toFloat()


                    abs(
                        averageY -
                                item.y
                    ) < GROUP_Y_TOLERANCE
                }


            if (
                line != null
            ) {

                line.add(
                    item
                )

            } else {

                result.add(
                    mutableListOf(
                        item
                    )
                )
            }
        }


        return result.map { line ->

            val sortedLine =
                line.sortedBy {
                    it.x
                }


            val text =
                buildString {

                    var previous:
                            PdfTextItem? =
                        null


                    for (
                    item
                    in sortedLine
                    ) {

                        if (
                            previous != null
                        ) {

                            val gap =
                                item.x -
                                        (
                                                previous.x +
                                                        previous.width
                                                )


                            if (
                                gap >
                                WORD_GAP_THRESHOLD
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


            VisualLineInternal(

                y =
                    sortedLine
                        .map {
                            it.y
                        }
                        .average()
                        .toFloat(),

                text =
                    text
            )
        }
    }


    // -------------------------------------------------------------
    // Формат времени
    // -------------------------------------------------------------

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


    // -------------------------------------------------------------
    // Нормализация текста
    // -------------------------------------------------------------

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

            .trim()

            .lowercase()
    }


    private data class VisualLineInternal(
        val y: Float,
        val text: String
    )


    // -------------------------------------------------------------
    // Старый поиск группы
    // -------------------------------------------------------------

    /*
     * Оставляем функцию,
     * чтобы ничего лишнего не ломалось,
     * если она понадобится позже.
     */
    fun findGroupInDay(
        day: DayBlock
    ): String? {

        return groupRegex
            .find(
                day.text
            )
            ?.value
    }
}