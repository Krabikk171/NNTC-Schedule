package ru.krabikk.nntcschedule.v3

        import android.util.Log
                import ru.krabikk.nntcschedule.PdfTextItem
                import kotlin.math.abs


                /**
                 * Новый экспериментальный распознаватель расписания — версия 3.0.0.
                 *
                 * Главная идея:
                 *
                 * PDF
                 *  ↓
                 * отдельные элементы
                 *  ↓
                 * строки
                 *  ↓
                 * текстовые блоки
                 *  ↓
                 * группы
                 *  ↓
                 * колонки пар
                 *  ↓
                 * время / кабинет / предмет / преподаватель
                 *
                 * Пока используется в основном для диагностики.
                 */
                object ScheduleParser3 {

                    // =========================================================================
                    // НАСТРОЙКИ
                    // =========================================================================

                    private const val LINE_Y_TOLERANCE = 2.5f
                    private const val BLOCK_X_GAP = 6f
                    private const val GROUP_LEFT_LIMIT = 35f

                    // =========================================================================
                    // ВНУТРЕННИЕ СТРУКТУРЫ
                    // =========================================================================

                    data class PdfLine(
                        val y: Float,
                        val items: List<PdfTextItem>
                    )

                    data class TextBlock(
                        val x: Float,
                        val y: Float,
                        val text: String,
                        val items: List<PdfTextItem>
                    )

                    data class GroupBounds(
                        val page: Int,
                        val group: String,
                        val topY: Float,
                        val bottomY: Float
                    )

                    data class PairItem(
                        val pairNumber: Int,
                        val items: List<PdfTextItem>
                    )

                    data class PairColumn(
                        val pairNumber: Int,
                        val headerX: Float
                    )

                    // =========================================================================
                    // ПОСТРОЕНИЕ СТРОК
                    // =========================================================================

                    fun buildLines(
                        items: List<PdfTextItem>
                    ): List<PdfLine> {

                        if (items.isEmpty()) {
                            return emptyList()
                        }

                        val sortedItems =
                            items
                                .filter {
                                    it.text.isNotBlank()
                                }
                                .sortedWith(
                                    compareBy<PdfTextItem> { it.page }
                                        .thenBy { it.y }
                                        .thenBy { it.x }
                                )

                        val lines =
                            mutableListOf<MutableList<PdfTextItem>>()

                        for (item in sortedItems) {

                            val currentLine =
                                lines.lastOrNull()

                            if (
                                currentLine == null ||
                                currentLine.first().page != item.page ||
                                abs(
                                    currentLine.averageY() - item.y
                                ) > LINE_Y_TOLERANCE
                            ) {

                                lines.add(
                                    mutableListOf(item)
                                )

                            } else {

                                currentLine.add(item)
                            }
                        }

                        return lines.map { line ->

                            PdfLine(
                                y = line.averageY(),
                                items = line.sortedBy {
                                    it.x
                                }
                            )
                        }
                    }

                    // =========================================================================
                    // ПОСТРОЕНИЕ ТЕКСТОВЫХ БЛОКОВ
                    // =========================================================================

                    fun buildTextBlocks(
                        line: PdfLine
                    ): List<TextBlock> {

                        if (line.items.isEmpty()) {
                            return emptyList()
                        }

                        val blocks =
                            mutableListOf<MutableList<PdfTextItem>>()

                        for (item in line.items) {

                            val currentBlock =
                                blocks.lastOrNull()

                            if (currentBlock == null) {

                                blocks.add(
                                    mutableListOf(item)
                                )

                                continue
                            }

                            val previousItem =
                                currentBlock.last()

                            val previousRight =
                                previousItem.x +
                                        previousItem.width

                            val gap =
                                item.x -
                                        previousRight

                            if (gap > BLOCK_X_GAP) {

                                blocks.add(
                                    mutableListOf(item)
                                )

                            } else {

                                currentBlock.add(item)
                            }
                        }

                        return blocks.map { block ->

                            val sorted =
                                block.sortedBy {
                                    it.x
                                }

                            TextBlock(
                                x = sorted.first().x,
                                y = line.y,
                                text = sorted
                                    .joinToString("") {
                                        it.text
                                    }
                                    .trim(),
                                items = sorted
                            )
                        }
                    }

                    // =========================================================================
                    // ОБЩИЙ АНАЛИЗ
                    // =========================================================================

                    fun analyze(
                        items: List<PdfTextItem>
                    ): List<List<TextBlock>> {

                        return buildLines(items)
                            .map {
                                buildTextBlocks(it)
                            }
                    }

                    private fun List<PdfTextItem>.averageY(): Float {

                        if (isEmpty()) {
                            return 0f
                        }

                        return sumOf {
                            it.y.toDouble()
                        }
                            .div(size)
                            .toFloat()
                    }

                    // =========================================================================
                    // КОЛОНКИ ПАР
                    // =========================================================================

                    fun findPairForX(
                        x: Float,
                        pairColumns: List<PairColumn>
                    ): Int? {

                        if (pairColumns.isEmpty()) {
                            return null
                        }

                        val sorted =
                            pairColumns.sortedBy {
                                it.headerX
                            }

                        for (i in sorted.indices) {

                            val current =
                                sorted[i]

                            val left =
                                current.headerX

                            val right =
                                if (i < sorted.lastIndex) {
                                    sorted[i + 1].headerX
                                } else {
                                    Float.MAX_VALUE
                                }

                            if (
                                x >= left &&
                                x < right
                            ) {
                                return current.pairNumber
                            }
                        }

                        return null
                    }

                    fun findPairColumns(
                        lines: List<PdfLine>
                    ): List<PairColumn> {

                        val result =
                            mutableListOf<PairColumn>()

                        val pairRegex =
                            Regex(
                                """([1-6])пара"""
                            )

                        for (line in lines) {

                            val blocks =
                                buildTextBlocks(line)

                            for (block in blocks) {

                                val normalized =
                                    block.text
                                        .lowercase()
                                        .replace('ё', 'е')
                                        .replace(" ", "")
                                        .trim()

                                val match =
                                    pairRegex.find(
                                        normalized
                                    )
                                        ?: continue

                                val pairNumber =
                                    match.groupValues[1]
                                        .toInt()

                                result +=
                                    PairColumn(
                                        pairNumber = pairNumber,
                                        headerX = block.x
                                    )
                            }
                        }

                        return result
                            .distinctBy {
                                it.pairNumber
                            }
                            .sortedBy {
                                it.pairNumber
                            }
                    }

                    fun buildPairRanges(
                        pairColumns: List<PairColumn>
                    ): Map<Int, ClosedFloatingPointRange<Float>> {

                        val sorted =
                            pairColumns
                                .sortedBy {
                                    it.headerX
                                }

                        val result =
                            mutableMapOf<
                                    Int,
                                    ClosedFloatingPointRange<Float>
                                    >()

                        for (i in sorted.indices) {

                            val current =
                                sorted[i]

                            val leftBoundary =
                                if (i == 0) {
                                    current.headerX - 60f
                                } else {
                                    (
                                            sorted[i - 1].headerX +
                                                    current.headerX
                                            ) / 2f
                                }

                            val rightBoundary =
                                if (i == sorted.lastIndex) {
                                    current.headerX + 70f
                                } else {
                                    (
                                            current.headerX +
                                                    sorted[i + 1].headerX
                                            ) / 2f
                                }

                            result[
                                current.pairNumber
                            ] =
                                leftBoundary..rightBoundary
                        }

                        return result
                    }

                    // =========================================================================
                    // ПОИСК ГРУППЫ
                    // =========================================================================

                    private fun normalizeGroupText(
                        text: String
                    ): String {

                        return text
                            .lowercase()
                            .replace('ё', 'е')
                            .replace(" ", "")
                            .replace("\u00A0", "")
                            .trim()
                    }

                    private fun looksLikeGroupName(
                        text: String
                    ): Boolean {

                        val normalized =
                            normalizeGroupText(
                                text
                            )

                        return Regex(
                            """\d+[А-ЯA-ZЁ]+-\d{2}-\d+"""
                        )
                            .matches(
                                normalized
                            )
                    }

                    fun findGroupBounds(
                        items: List<PdfTextItem>,
                        targetGroup: String
                    ): GroupBounds? {

                        if (items.isEmpty()) {
                            return null
                        }

                        val tag =
                            "ScheduleParser3"

                        val targetNormalized =
                            normalizeGroupText(
                                targetGroup
                            )

                        val groupAreaItems =
                            items.filter {
                                it.x < GROUP_LEFT_LIMIT
                            }

                        val lines =
                            buildLines(
                                groupAreaItems
                            )

                        val groupRegex =
                            Regex(
                                """\d+[А-ЯA-ZЁ]+-\d{2}-\d+"""
                            )

                        data class GroupLine(
                            val page: Int,
                            val y: Float,
                            val text: String,
                            val groupName: String
                        )

                        val groupLines =
                            lines
                                .mapNotNull { line ->

                                    if (line.items.isEmpty()) {
                                        return@mapNotNull null
                                    }

                                    val text =
                                        line.items
                                            .sortedBy {
                                                it.x
                                            }
                                            .joinToString("") {
                                                it.text
                                            }
                                            .trim()

                                    val match =
                                        groupRegex.find(
                                            text
                                        )
                                            ?: return@mapNotNull null

                                    GroupLine(
                                        page =
                                            line.items.first().page,

                                        y =
                                            line.y,

                                        text =
                                            text,

                                        groupName =
                                            normalizeGroupText(
                                                match.value
                                            )
                                    )
                                }
                                .sortedWith(
                                    compareBy<GroupLine> {
                                        it.page
                                    }.thenBy {
                                        it.y
                                    }
                                )

                        Log.d(
                            tag,
                            "findGroupBounds: detected groups = ${groupLines.size}"
                        )

                        for (groupLine in groupLines) {

                            Log.d(
                                tag,
                                "GROUP LINE: " +
                                        "page=${groupLine.page} " +
                                        "y=${groupLine.y} " +
                                        "group=${groupLine.groupName} " +
                                        "text='${groupLine.text}'"
                            )
                        }

                        val targetIndex =
                            groupLines.indexOfFirst {
                                it.groupName ==
                                        targetNormalized
                            }

                        if (targetIndex == -1) {

                            Log.d(
                                tag,
                                "findGroupBounds: target group not found: $targetGroup"
                            )

                            return null
                        }

                        val target =
                            groupLines[targetIndex]

                        Log.d(
                            tag,
                            "findGroupBounds: TARGET MATCH " +
                                    "page=${target.page} " +
                                    "y=${target.y} " +
                                    "text='${target.text}'"
                        )

                        // Границы строки группы определяем по серединам между соседними
                        // названиями групп на той же странице. Само название группы не является
                        // верхней границей её расписания: часть ячеек может находиться выше него.
                        val previousGroup =
                            groupLines
                                .take(targetIndex)
                                .lastOrNull {
                                    it.page == target.page &&
                                            it.y < target.y
                                }

                        val nextGroup =
                            groupLines
                                .drop(targetIndex + 1)
                                .firstOrNull {
                                    it.page == target.page &&
                                            it.y > target.y
                                }

                        val pageItems =
                            items.filter {
                                it.page == target.page
                            }

                        val pageMinY =
                            pageItems.minOfOrNull { it.y } ?: target.y

                        val pageMaxY =
                            pageItems.maxOfOrNull { it.y } ?: target.y

                        val topY =
                            if (previousGroup != null) {
                                (previousGroup.y + target.y) / 2f
                            } else {
                                pageMinY
                            }

                        val bottomY =
                            if (nextGroup != null) {
                                (target.y + nextGroup.y) / 2f
                            } else {
                                pageMaxY + 0.01f
                            }

                        return GroupBounds(
                            page =
                                target.page,

                            group =
                                targetGroup,

                            topY =
                                topY,

                            bottomY =
                                bottomY
                        )
                            .also { result ->

                                Log.d(
                                    tag,
                                    "findGroupBounds: FOUND MIDPOINT BOUNDS " +
                                            "group=${result.group} " +
                                            "page=${result.page} " +
                                            "topY=${result.topY} " +
                                            "groupY=${target.y} " +
                                            "bottomY=${result.bottomY}"
                                )
                            }
                    }

                    fun getGroupItems(
                        items: List<PdfTextItem>,
                        bounds: GroupBounds
                    ): List<PdfTextItem> {

                        return items
                            .filter { item ->

                                item.page == bounds.page &&
                                        item.y >= bounds.topY &&
                                        item.y < bounds.bottomY
                            }
                            .sortedWith(
                                compareBy<PdfTextItem> {
                                    it.y
                                }.thenBy {
                                    it.x
                                }
                            )
                    }

                    // =========================================================================
                    // РАСПРЕДЕЛЕНИЕ ПО ПАРАМ
                    // =========================================================================

                    fun splitGroupIntoPairs(
                        groupItems: List<PdfTextItem>,
                        pairColumns: List<PairColumn>
                    ): List<PairItem> {

                        return (1..6).map { pairNumber ->

                            PairItem(
                                pairNumber =
                                    pairNumber,

                                items =
                                    groupItems.filter { item ->

                                        findPairForX(
                                            x =
                                                item.x,

                                            pairColumns =
                                                pairColumns
                                        ) == pairNumber
                                    }
                            )
                        }
                    }

                    // =========================================================================
                    // ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
                    // =========================================================================

                    private fun blockMinX(
                        block: TextBlock
                    ): Float {

                        return block.items
                            .minOfOrNull {
                                it.x
                            }
                            ?: block.x
                    }

                    private fun blockMaxX(
                        block: TextBlock
                    ): Float {

                        return block.items
                            .maxOfOrNull {
                                it.x + it.width
                            }
                            ?: block.x
                    }

                    private fun blockCenterX(
                        block: TextBlock
                    ): Float {

                        return (
                                blockMinX(block) +
                                        blockMaxX(block)
                                ) / 2f
                    }

                    private fun cleanText(
                        text: String
                    ): String {

                        return text
                            .replace("\u00A0", " ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                    }

                    private fun normalizedSearchText(
                        text: String
                    ): String {

                        return text
                            .lowercase()
                            .replace('ё', 'е')
                            .replace(" ", "")
                            .replace("\u00A0", "")
                    }

                    // =========================================================================
                    // ВРЕМЯ
                    // =========================================================================

                    private val timeRegex =
                        Regex(
                            """\d{1,2}:\d{2}|(?<![\d.])\d{1,2}\.\d{2}(?!\.)"""
                        )

                    private fun findTimes(
                        text: String
                    ): List<String> {

                        return timeRegex
                            .findAll(text)
                            .map {
                                it.value
                            }
                            .toList()
                    }

                    private fun timeToMinutes(
                        value: String
                    ): Int? {

                        val normalized =
                            value.replace(
                                '.',
                                ':'
                            )

                        val parts =
                            normalized.split(":")

                        if (parts.size != 2) {
                            return null
                        }

                        val hour =
                            parts[0].toIntOrNull()
                                ?: return null

                        val minute =
                            parts[1].toIntOrNull()
                                ?: return null

                        if (
                            hour !in 0..23 ||
                            minute !in 0..59
                        ) {
                            return null
                        }

                        return hour * 60 + minute
                    }

                    // =========================================================================
                    // КАБИНЕТЫ
                    // =========================================================================

                    private fun isLikelyRoom(
                        text: String
                    ): Boolean {

                        val normalized =
                            cleanText(
                                text
                            )
                                .lowercase()
                                .replace('ё', 'е')
                                .replace(" ", "")
                                .replace("\u00A0", "")

                        if (normalized.isBlank()) {
                            return false
                        }

                        if (
                            normalized.contains("пара") ||
                            normalized.contains("рпу")
                        ) {
                            return false
                        }

                        /*
                         * ВАЖНО:
                         *
                         * "с/з" — это нормальное обозначение кабинета
                         * (спортивный зал), поэтому "/" здесь НЕ является
                         * причиной отбрасывания.
                         */
                        if (
                            normalized == "с/з" ||
                            normalized == "с/з1" ||
                            normalized == "с/з2"
                        ) {
                            return true
                        }

                        /*
                         * Обычный номер кабинета:
                         *
                         * 34
                         * 12
                         * 101
                         * 12а
                         * а12
                         */
                        return normalized.matches(
                            Regex(
                                """(?:\d{1,4}[а-яa-z]?|[а-яa-z]\d{1,4})"""
                            )
                        )
                    }

                    // =========================================================================
                    // ПРЕПОДАВАТЕЛИ
                    // =========================================================================

                    private fun looksLikeTeacher(
                        text: String
                    ): Boolean {

                        val normalized =
                            cleanText(
                                text
                            )
                                .replace(" ", "")
                                .replace("\u00A0", "")

                        if (normalized.isBlank()) {
                            return false
                        }

                        /*
                         * Примеры:
                         *
                         * ИпатоваЕ.Ю.
                         * ЛысенковА.В.
                         *
                         * Фамилия + две инициалы.
                         */
                        val teacherRegex =
                            Regex(
                                """[А-ЯЁA-Z][а-яёa-z]+[А-ЯЁA-Z]\.?[А-ЯЁA-Z]\.?"""
                            )

                        return teacherRegex.matches(
                            normalized
                        )
                    }

                    // =========================================================================
                    // ДИАГНОСТИКА ГРУППЫ
                    // =========================================================================

                    fun debugLeftSide(
                        items: List<PdfTextItem>
                    ) {

                        val tag =
                            "ScheduleParser3"

                        Log.d(
                            tag,
                            "========== TARGET GROUP DEBUG =========="
                        )

                        val leftItems =
                            items
                                .filter {
                                    it.x < 60f
                                }
                                .sortedWith(
                                    compareBy<PdfTextItem> {
                                        it.page
                                    }.thenBy {
                                        it.y
                                    }.thenBy {
                                        it.x
                                    }
                                )

                        Log.d(
                            tag,
                            "LEFT ITEMS: ${leftItems.size}"
                        )

                        if (leftItems.isEmpty()) {
                            return
                        }

                        var currentPage =
                            leftItems.first().page

                        var currentY =
                            leftItems.first().y

                        val currentLine =
                            StringBuilder()

                        val currentItems =
                            mutableListOf<PdfTextItem>()

                        fun flushLine() {

                            val text =
                                currentLine
                                    .toString()
                                    .trim()

                            if (
                                normalizedSearchText(text)
                                    .contains(
                                        normalizedSearchText(
                                            "2РПУ-25-1"
                                        )
                                    )
                            ) {

                                val minX =
                                    currentItems.minOfOrNull {
                                        it.x
                                    } ?: 0f

                                val maxX =
                                    currentItems.maxOfOrNull {
                                        it.x + it.width
                                    } ?: 0f

                                Log.d(
                                    tag,
                                    "TARGET FOUND: " +
                                            "page=$currentPage " +
                                            "y=$currentY " +
                                            "xStart=$minX " +
                                            "xEnd=$maxX " +
                                            "text='$text'"
                                )
                            }

                            currentLine.clear()
                            currentItems.clear()
                        }

                        for (item in leftItems) {

                            val samePage =
                                item.page == currentPage

                            val sameLine =
                                abs(
                                    item.y - currentY
                                ) < 1.5f

                            if (
                                !samePage ||
                                !sameLine
                            ) {

                                flushLine()

                                currentPage =
                                    item.page

                                currentY =
                                    item.y
                            }

                            currentLine.append(
                                item.text
                            )

                            currentItems.add(
                                item
                            )
                        }

                        flushLine()

        Log.d(
            tag,
            "========== END TARGET GROUP DEBUG =========="
        )
    }

    fun debugTextBlocks(
        items: List<PdfTextItem>,
        targetGroups: List<String>
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG MULTIPLE GROUPS =========="
        )

        for (targetGroup in targetGroups) {

            val bounds =
                findGroupBounds(
                    items =
                        items,

                    targetGroup =
                        targetGroup
                )

            if (bounds == null) {
                continue
            }

            val groupItems =
                getGroupItems(
                    items =
                        items,

                    bounds =
                        bounds
                )

            val lines =
                buildLines(
                    groupItems
                )

            Log.d(
                tag,
                "========== GROUP: $targetGroup =========="
            )

            for (line in lines) {

                val blocks =
                    buildTextBlocks(
                        line
                    )

                Log.d(
                    tag,
                    "Y=${line.y}:"
                )

                for (block in blocks) {

                    Log.d(
                        tag,
                        "    X=${blockMinX(block)} .. " +
                                "${blockMaxX(block)} " +
                                "TEXT='${block.text}'"
                    )
                }
            }
        }

        Log.d(
            tag,
            "========== END DEBUG MULTIPLE GROUPS =========="
        )
    }

    fun debugGroup(
        items: List<PdfTextItem>,
        targetGroup: String,
        pairColumns: List<PairColumn>
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== GROUP DEBUG =========="
        )

        Log.d(
            tag,
            "TARGET GROUP: $targetGroup"
        )

        Log.d(
            tag,
            "TOTAL ITEMS: ${items.size}"
        )

        Log.d(
            tag,
            "PAIR COLUMNS: ${pairColumns.size}"
        )

        val bounds =
            findGroupBounds(
                items =
                    items,

                targetGroup =
                    targetGroup
            )
                ?: run {
                    Log.d(
                        tag,
                        "GROUP NOT FOUND: $targetGroup"
                    )
                    return
                }

        Log.d(
            tag,
            "GROUP FOUND: ${bounds.group} " +
                    "page=${bounds.page} " +
                    "topY=${bounds.topY} " +
                    "bottomY=${bounds.bottomY}"
        )

        val groupItems =
            getGroupItems(
                items =
                    items,

                bounds =
                    bounds
            )

        Log.d(
            tag,
            "GROUP ITEMS: ${groupItems.size}"
        )

        val pairs =
            splitGroupIntoPairs(
                groupItems =
                    groupItems,

                pairColumns =
                    pairColumns
            )

        for (pair in pairs) {

            Log.d(
                tag,
                "----- PAIR ${pair.pairNumber} -----"
            )

            if (pair.items.isEmpty()) {

                Log.d(
                    tag,
                    "EMPTY"
                )

                continue
            }

            val lines =
                buildLines(
                    pair.items
                )

            for (line in lines) {

                val blocks =
                    buildTextBlocks(
                        line
                    )

                Log.d(
                    tag,
                    "Y=${line.y}: " +
                            blocks.joinToString(" | ") {
                                "'${it.text}'"
                            }
                )
            }
        }

        Log.d(
            tag,
            "================================"
        )
    }

    // =========================================================================
    // ДИАГНОСТИКА СТРУКТУРЫ
    // =========================================================================

    fun debugPairStructure(
        items: List<PdfTextItem>,
        targetGroup: String,
        pairColumns: List<PairColumn>? = null
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR STRUCTURE =========="
        )

        val bounds =
            findGroupBounds(
                items =
                    items,

                targetGroup =
                    targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items =
                    items,

                bounds =
                    bounds
            )

        val columns =
            pairColumns
                ?: findPairColumns(
                    buildLines(
                        items.filter {
                            it.page ==
                                    bounds.page
                        }
                    )
                )

        val pairs =
            splitGroupIntoPairs(
                groupItems =
                    groupItems,

                pairColumns =
                    columns
            )

        for (pair in pairs) {

            Log.d(
                tag,
                "PAIR ${pair.pairNumber}: " +
                        "${pair.items.size} ITEMS"
            )
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR STRUCTURE =========="
        )
    }

    // =========================================================================
    // ПЕРЕСТРОЕННЫЕ СТРОКИ
    // =========================================================================

    fun debugReconstructedLines(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG RECONSTRUCTED LINES =========="
        )

        val bounds =
            findGroupBounds(
                items =
                    items,

                targetGroup =
                    targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items =
                    items,

                bounds =
                    bounds
            )

        val lines =
            buildLines(
                groupItems
            )

        for (line in lines) {

            val text =
                buildTextBlocks(line)
                    .joinToString("") {
                        it.text
                    }

            Log.d(
                tag,
                "Y=${line.y} TEXT='$text'"
            )
        }

        Log.d(
            tag,
            "========================================"
        )
    }

    // =========================================================================
    // ГОРИЗОНТАЛЬНЫЕ ПРОМЕЖУТКИ
    // =========================================================================

    fun debugHorizontalGaps(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG HORIZONTAL GAPS =========="
        )

        val bounds =
            findGroupBounds(
                items =
                    items,

                targetGroup =
                    targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items =
                    items,

                bounds =
                    bounds
            )

        val lines =
            buildLines(
                groupItems
            )

        for (line in lines) {

            val blocks =
                buildTextBlocks(
                    line
                )

            if (blocks.isEmpty()) {
                continue
            }

            Log.d(
                tag,
                "Y=${line.y}:"
            )

            var previousRight =
                blocks.first().x

            for (block in blocks) {

                val minX =
                    blockMinX(
                        block
                    )

                val maxX =
                    blockMaxX(
                        block
                    )

                val gap =
                    minX - previousRight

                Log.d(
                    tag,
                    "    X=$minX .. $maxX " +
                            "GAP=$gap " +
                            "TEXT='${block.text}'"
                )

                previousRight =
                    maxX
            }
        }

        Log.d(
            tag,
            "========================================"
        )
    }

    // =========================================================================
    // ГРАНИЦЫ ПАР
    // =========================================================================

    fun debugPairRanges(
        items: List<PdfTextItem>,
        targetGroup: String,
        pairColumns: List<PairColumn>
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR RANGES =========="
        )

        val bounds =
            findGroupBounds(
                items =
                    items,

                targetGroup =
                    targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items =
                    items,

                bounds =
                    bounds
            )

        val ranges =
            buildPairRanges(
                pairColumns
            )

        for ((pair, range) in ranges) {

            Log.d(
                tag,
                "PAIR $pair RANGE: " +
                        "${range.start} .. ${range.endInclusive}"
            )
        }

        val lines =
            buildLines(
                groupItems
            )

        for (line in lines) {

            for (block in buildTextBlocks(line)) {

                val center =
                    blockCenterX(
                        block
                    )

                val pair =
                    ranges.entries
                        .firstOrNull {
                            center in it.value
                        }
                        ?.key

                Log.d(
                    tag,
                    "Y=${line.y} " +
                            "X=${blockMinX(block)}..${blockMaxX(block)} " +
                            "CENTER=$center " +
                            "PAIR=${pair ?: "NONE"} " +
                            "TEXT='${block.text}'"
                )
            }
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR RANGES =========="
        )
    }

    // =========================================================================
    // ДИАГНОСТИКА ВРЕМЕНИ
    // =========================================================================

    fun debugTimes(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG TIMES =========="
        )

        val bounds =
            findGroupBounds(
                items =
                    items,

                targetGroup =
                    targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items =
                    items,

                bounds =
                    bounds
            )

        for (line in buildLines(groupItems)) {

            for (block in buildTextBlocks(line)) {

                val times =
                    findTimes(
                        block.text
                    )

                if (times.isNotEmpty()) {

                    Log.d(
                        tag,
                        "Y=${line.y} " +
                                "X=${blockMinX(block)} " +
                                "TEXT='${block.text}' " +
                                "TIMES=$times"
                    )
                }
            }
        }

        Log.d(
            tag,
            "========== END DEBUG TIMES =========="
        )
    }

    fun debugPairTimes(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR TIMES =========="
        )

        val bounds =
            findGroupBounds(
                items = items,
                targetGroup = targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items = items,
                bounds = bounds
            )

        val anchors =
            collectTimeAnchors(
                groupItems
            )

        for (anchor in anchors) {

            Log.d(
                tag,
                "TIME=${anchor.value} " +
                        "X=${anchor.x} " +
                        "Y=${anchor.y}"
            )
        }

        Log.d(
            tag,
            "TIME ANCHORS FOUND: ${anchors.size}"
        )

        Log.d(
            tag,
            "========== END DEBUG PAIR TIMES =========="
        )
    }

    fun debugPairIntervals(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR INTERVALS =========="
        )

        val bounds =
            findGroupBounds(
                items = items,
                targetGroup = targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items = items,
                bounds = bounds
            )

        val sorted =
            collectTimeAnchors(groupItems)
                .sortedBy {
                    it.x
                }

        if (sorted.size < 2) {

            Log.d(
                tag,
                "NOT ENOUGH TIME ANCHORS"
            )

            return
        }

        for (i in 0 until sorted.size - 1 step 2) {

            val start =
                sorted[i]

            val end =
                sorted[i + 1]

            Log.d(
                tag,
                "PAIR ${i / 2 + 1}: " +
                        "${start.value} - ${end.value}"
            )
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR INTERVALS =========="
        )
    }

    // =========================================================================
    // ДИАГНОСТИКА КАБИНЕТОВ
    // =========================================================================

    private data class TimeAnchor(
        val value: String,
        val minutes: Int,
        val x: Float,
        val y: Float
    )

    private fun collectTimeAnchors(
        groupItems: List<PdfTextItem>
    ): List<TimeAnchor> {

        /*
         * В PDF время хранится не обязательно одной строкой:
         *
         * 8
         * .
         * 1
         * 0
         *
         * могут быть четырьмя отдельными PdfTextItem.
         *
         * Поэтому сначала восстанавливаем строки и текстовые блоки,
         * а уже потом ищем время внутри готового текста.
         */
        val anchors =
            mutableListOf<TimeAnchor>()

        val lines =
            buildLines(
                groupItems
            )

        for (line in lines) {

            val blocks =
                buildTextBlocks(
                    line
                )

            for (block in blocks) {

                val times =
                    findTimes(
                        block.text
                    )

                if (times.isEmpty()) {
                    continue
                }

                val x =
                    blockMinX(
                        block
                    )

                for (value in times) {

                    val minutes =
                        timeToMinutes(
                            value
                        )
                            ?: continue

                    anchors +=
                        TimeAnchor(
                            value = value,
                            minutes = minutes,
                            x = x,
                            y = line.y
                        )
                }
            }
        }

        /*
         * В одном PDF-блоке теоретически может встретиться
         * несколько времён. Оставляем каждое найденное время,
         * но убираем полные дубликаты одной и той же координаты.
         */
        return anchors
            .distinctBy {
                Triple(
                    it.value,
                    it.x,
                    it.y
                )
            }
            .sortedWith(
                compareBy<TimeAnchor> {
                    it.y
                }.thenBy {
                    it.x
                }
            )
    }

    private fun collectStartTimeAnchors(
        groupItems: List<PdfTextItem>
    ): List<TimeAnchor> {

        val startMinutes =
            setOf(
                8 * 60 + 10,
                10 * 60,
                11 * 60 + 50,
                13 * 60 + 30,
                15 * 60 + 10,
                16 * 60 + 50
            )

        return collectTimeAnchors(groupItems)
            .filter {
                it.minutes in startMinutes
            }
            .sortedBy {
                it.x
            }
            .distinctBy {
                it.minutes
            }
            .take(6)
    }

    fun debugPairRooms(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR ROOMS =========="
        )

        val bounds =
            findGroupBounds(
                items = items,
                targetGroup = targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items = items,
                bounds = bounds
            )

        val startTimes =
            collectStartTimeAnchors(
                groupItems
            )

        if (startTimes.isEmpty()) {

            Log.d(
                tag,
                "NO START TIME ANCHORS"
            )

            Log.d(
                tag,
                "========== END DEBUG PAIR ROOMS =========="
            )

            return
        }

        /*
         * Кабинеты находятся в верхней строке группы,
         * рядом с началом соответствующей пары.
         *
         * Не привязываемся к одному конкретному X-диапазону
         * и не предполагаем, что кабинет обязательно будет
         * отдельным PDF-блоком с одинаковой шириной.
         */
        val topLines =
            buildLines(groupItems)
                .filter {
                    abs(it.y - bounds.topY) <= 4f
                }

        val topBlocks =
            topLines.flatMap {
                buildTextBlocks(it)
            }

        for ((index, start) in startTimes.withIndex()) {

            val pairNumber =
                index + 1

            val expectedRoomX =
                start.x + 28f

            val roomBlock =
                topBlocks
                    .filter {
                        isLikelyRoom(it.text)
                    }
                    .filter {
                        abs(it.y - bounds.topY) <= 4f
                    }
                    .filter {
                        val center =
                            blockCenterX(it)

                        center >= start.x + 10f &&
                                center <= start.x + 60f
                    }
                    .minByOrNull {
                        abs(
                            blockCenterX(it) -
                                    expectedRoomX
                        )
                    }

            Log.d(
                tag,
                "PAIR $pairNumber ROOM = " +
                        "${roomBlock?.text ?: "NONE"} " +
                        "(START=${start.value} X=${start.x})"
            )
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR ROOMS =========="
        )
    }

    // =========================================================================
    // ДИАГНОСТИКА ЯЧЕЕК ПАР
    // =========================================================================

    fun debugGroupWindow(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG GROUP WINDOW =========="
        )

        val bounds =
            findGroupBounds(
                items = items,
                targetGroup = targetGroup
            )
                ?: return

        // Проверяем только вертикальную полосу, отведённую целевой группе.
        // Границы рассчитаны как середины между соседними строками групп.
        val windowTop = bounds.topY
        val windowBottom = bounds.bottomY

        Log.d(
            tag,
            "TARGET GROUP CELL WINDOW: page=${bounds.page} " +
                    "Y=$windowTop..$windowBottom"
        )

        val windowItems =
            items
                .filter { item ->
                    item.page == bounds.page &&
                            item.y >= windowTop &&
                            item.y <= windowBottom
                }
                .sortedWith(
                    compareBy<PdfTextItem> { it.y }
                        .thenBy { it.x }
                )

        val lines =
            buildLines(
                windowItems
            )

        Log.d(
            tag,
            "WINDOW LINES: ${lines.size}"
        )

        for (line in lines) {

            val blocks =
                buildTextBlocks(
                    line
                )

            for (block in blocks) {

                Log.d(
                    tag,
                    "WINDOW BLOCK: " +
                            "X=${blockMinX(block)}..${blockMaxX(block)} " +
                            "Y=${block.y} " +
                            "CENTER=${blockCenterX(block)} " +
                            "ROOM=${isLikelyRoom(block.text)} " +
                            "SLASH=${block.text.contains("/")} " +
                            "TEXT='${block.text}'"
                )
            }
        }

        Log.d(
            tag,
            "========== END DEBUG GROUP WINDOW =========="
        )
    }

    fun debugPairCells(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR CELLS =========="
        )

        val bounds =
            findGroupBounds(
                items = items,
                targetGroup = targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items = items,
                bounds = bounds
            )

        val allBlocks =
            buildLines(groupItems)
                .flatMap { line ->
                    buildTextBlocks(line)
                }

        val starts =
            collectStartTimeAnchors(groupItems)

        if (starts.isEmpty()) {
            Log.d(tag, "NO START TIME ANCHORS")
            Log.d(tag, "========== END DEBUG PAIR CELLS ==========")
            return
        }

        for ((index, start) in starts.withIndex()) {

            val pairNumber = index + 1

            val nextStartX =
                starts.getOrNull(index + 1)?.x
                    ?: (start.x + 125f)

            Log.d(
                tag,
                "PAIR $pairNumber CELL " +
                        "X=${start.x}..$nextStartX " +
                        "START=${start.value}"
            )

            allBlocks
                .filter { block ->
                    val center = blockCenterX(block)
                    center >= start.x - 5f &&
                            center < nextStartX - 5f &&
                            abs(block.y - start.y) <= 22f
                }
                .sortedWith(
                    compareBy<TextBlock> { it.y }
                        .thenBy { blockMinX(it) }
                )
                .forEach { block ->

                    Log.d(
                        tag,
                        "    X=${blockMinX(block)}..${blockMaxX(block)} " +
                                "Y=${block.y} " +
                                "CENTER=${blockCenterX(block)} " +
                                "ROOM=${isLikelyRoom(block.text)} " +
                                "SLASH=${block.text.contains("/")} " +
                                "TEXT='${block.text}'"
                    )
                }
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR CELLS =========="
        )
    }

    // =========================================================================
    // ПРЕДМЕТ / ПРЕПОДАВАТЕЛЬ
    // =========================================================================

    // =========================================================================
    // ПРЕДМЕТ / ПРЕПОДАВАТЕЛЬ
    // =========================================================================

    private fun splitSubjectTeacher(
        text: String
    ): Pair<String, String>? {

        val slash =
            text.indexOf('/')

        if (slash < 0) {
            return null
        }

        val subject =
            cleanText(
                text.substring(
                    0,
                    slash
                )
            )

        val teacher =
            cleanText(
                text.substring(
                    slash + 1
                )
            )

        if (
            subject.isBlank() ||
            teacher.isBlank()
        ) {
            return null
        }

        if (
            !looksLikeTeacher(
                teacher
            )
        ) {
            return null
        }

        return subject to teacher
    }

    /**
     * Собирает продолжение названия предмета перед slash-блоком.
     *
     * Важный момент:
     * диапазон строится не от общей границы колонки,
     * а от конкретного времени начала пары.
     *
     * Это не позволяет тексту соседней пары случайно
     * попасть в текущий предмет.
     */
    private fun collectSubjectParts(
        allBlocks: List<TextBlock>,
        slashBlock: TextBlock,
        startX: Float
    ): String {

        val slashX =
            blockMinX(
                slashBlock
            )

        val slashY =
            slashBlock.y

        val subjectLeft =
            startX + 20f

        val subjectRight =
            slashX + 3f

        val candidates =
            allBlocks
                .filter {
                    it != slashBlock
                }
                .filter {
                    abs(
                        it.y - slashY
                    ) <= 20f
                }
                .filter {
                    blockMaxX(it) >= subjectLeft &&
                            it.x <= subjectRight
                }
                .filter {
                    blockCenterX(it) <= subjectRight
                }
                .filterNot {
                    findTimes(it.text).isNotEmpty()
                }
                .filterNot {
                    isLikelyRoom(it.text)
                }
                .filterNot {
                    it.text.contains("/")
                }
                .filterNot {
                    looksLikeTeacher(it.text)
                }
                .filterNot {
                    normalizedSearchText(it.text)
                        .startsWith("мдк.")
                }
                .sortedWith(
                    compareBy<TextBlock> {
                        it.y
                    }.thenBy {
                        it.x
                    }
                )

        return candidates
            .joinToString(" ") {
                cleanText(it.text)
            }
            .trim()
    }

    fun debugPairSubjects(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR SUBJECTS =========="
        )

        val bounds =
            findGroupBounds(
                items = items,
                targetGroup = targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items = items,
                bounds = bounds
            )

        val allBlocks =
            buildLines(groupItems)
                .flatMap {
                    buildTextBlocks(it)
                }

        val startTimes =
            collectStartTimeAnchors(
                groupItems
            )

        if (startTimes.isEmpty()) {

            Log.d(
                tag,
                "NO START TIME ANCHORS"
            )

            Log.d(
                tag,
                "========== END DEBUG PAIR SUBJECTS =========="
            )

            return
        }

        /*
         * Не выбираем slash-блок просто как "первый по Y".
         *
         * Сначала находим время начала конкретной пары,
         * затем ищем предмет/преподавателя рядом с ним.
         *
         * Это особенно важно для PDF, где несколько
         * горизонтальных колонок имеют одинаковый Y.
         */
        for ((index, start) in startTimes.withIndex()) {

            val pairNumber =
                index + 1

            val slashBlocks =
                allBlocks
                    .filter {
                        it.text.contains("/")
                    }
                    .filter {
                        splitSubjectTeacher(it.text) != null
                    }
                    .filter {
                        val center =
                            blockCenterX(it)

                        center >= start.x + 30f &&
                                center <= start.x + 120f
                    }
                    .filter {
                        abs(
                            it.y - start.y
                        ) <= 20f
                    }

            val slashBlock =
                slashBlocks.minByOrNull {

                    val center =
                        blockCenterX(it)

                    abs(
                        center -
                                (start.x + 75f)
                    ) +
                            abs(
                                it.y -
                                        start.y
                            ) * 1.5f
                }

            if (slashBlock == null) {

                Log.d(
                    tag,
                    "PAIR $pairNumber SUBJECT = NONE " +
                            "(START=${start.value} X=${start.x})"
                )

                continue
            }

            val split =
                splitSubjectTeacher(
                    slashBlock.text
                )
                    ?: run {

                        Log.d(
                            tag,
                            "PAIR $pairNumber SUBJECT = NONE"
                        )

                        continue
                    }

            val previousParts =
                collectSubjectParts(
                    allBlocks = allBlocks,
                    slashBlock = slashBlock,
                    startX = start.x
                )

            val fullSubject =
                listOf(
                    previousParts,
                    split.first
                )
                    .filter {
                        it.isNotBlank()
                    }
                    .joinToString(" ")

            Log.d(
                tag,
                "PAIR $pairNumber " +
                        "SUBJECT='" +
                        cleanText(fullSubject) +
                        "' " +
                        "TEACHER='" +
                        split.second +
                        "' " +
                        "START=${start.value}"
            )
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR SUBJECTS =========="
        )
    }

    // =========================================================================
    // ДИАГНОСТИКА ЯКОРЕЙ
    // =========================================================================

    fun debugPairAnchors(
        items: List<PdfTextItem>,
        targetGroup: String,
        pairColumns: List<PairColumn> = emptyList()
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR ANCHORS =========="
        )

        val bounds =
            findGroupBounds(
                items = items,
                targetGroup = targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items = items,
                bounds = bounds
            )

        val sorted =
            collectTimeAnchors(groupItems)
                .sortedWith(
                    compareBy<TimeAnchor> {
                        it.x
                    }.thenBy {
                        it.y
                    }
                )

        Log.d(
            tag,
            "TIME ANCHORS FOUND: ${sorted.size}"
        )

        for (anchor in sorted) {

            Log.d(
                tag,
                "TIME=${anchor.value} " +
                        "X=${anchor.x} " +
                        "Y=${anchor.y}"
            )
        }

        if (pairColumns.isNotEmpty()) {

            for (anchor in sorted) {

                val pair =
                    findPairForX(
                        x = anchor.x,
                        pairColumns = pairColumns
                    )

                Log.d(
                    tag,
                    "ANCHOR ${anchor.value} " +
                            "-> PAIR=${pair ?: "NONE"}"
                )
            }
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR ANCHORS =========="
        )
    }

    // =========================================================================
    // ДОПОЛНИТЕЛЬНАЯ ДИАГНОСТИКА
    // =========================================================================

    fun debugPairAssignment(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== DEBUG PAIR ASSIGNMENT =========="
        )

        val bounds =
            findGroupBounds(
                items =
                    items,

                targetGroup =
                    targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items =
                    items,

                bounds =
                    bounds
            )

        val pairColumns =
            findPairColumns(
                buildLines(
                    items.filter {
                        it.page ==
                                bounds.page
                    }
                )
            )

        for (line in buildLines(groupItems)) {

            for (block in buildTextBlocks(line)) {

                val center =
                    blockCenterX(
                        block
                    )

                val pair =
                    findPairForX(
                        x =
                            center,

                        pairColumns =
                            pairColumns
                    )

                Log.d(
                    tag,
                    "Y=${line.y} " +
                            "CENTER_X=$center " +
                            "PAIR=${if (pair != null) "FOUND" else "NONE"} " +
                            "TEXT='${block.text}'"
                )
            }
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR ASSIGNMENT =========="
        )
    }

    // =========================================================================
    // ДИАГНОСТИКА ОТДЕЛЬНОЙ ПАРЫ
    // =========================================================================

    fun debugPairBlocks(
        items: List<PdfTextItem>,
        pairNumber: Int,
        pairColumns: List<PairColumn>
    ) {

        val tag =
            "ScheduleParser3"

        val pair =
            pairColumns.firstOrNull {
                it.pairNumber ==
                        pairNumber
            }
                ?: run {

                    Log.d(
                        tag,
                        "PAIR $pairNumber: COLUMN NOT FOUND"
                    )

                    return
                }

        val sortedColumns =
            pairColumns.sortedBy {
                it.headerX
            }

        val index =
            sortedColumns.indexOf(
                pair
            )

        val left =
            pair.headerX

        val right =
            if (
                index + 1 <
                sortedColumns.size
            ) {

                sortedColumns[
                    index + 1
                ].headerX

            } else {

                Float.MAX_VALUE
            }

        Log.d(
            tag,
            "========== DEBUG PAIR $pairNumber =========="
        )

        Log.d(
            tag,
            "X RANGE: $left .. $right"
        )

        val pairItems =
            items
                .filter {

                    it.x >= left &&
                            it.x < right
                }
                .sortedWith(
                    compareBy<PdfTextItem> {
                        it.page
                    }.thenBy {
                        it.y
                    }.thenBy {
                        it.x
                    }
                )

        if (pairItems.isEmpty()) {

            Log.d(
                tag,
                "PAIR $pairNumber: EMPTY"
            )

            return
        }

        for (line in buildLines(pairItems)) {

            Log.d(
                tag,
                "Y=${line.y}: " +
                        buildTextBlocks(line)
                            .joinToString(" | ") {
                                "'${it.text}'"
                            }
            )
        }

        Log.d(
            tag,
            "========== END DEBUG PAIR $pairNumber =========="
        )
    }

    // =========================================================================
    // РАСПРЕДЕЛЕНИЕ БЛОКОВ ПО ПАРАМ
    // =========================================================================

    fun debugDistribution(
        items: List<PdfTextItem>,
        targetGroup: String,
        pairColumns: List<PairColumn>
    ) {

        val tag =
            "ScheduleParser3"

        val bounds =
            findGroupBounds(
                items =
                    items,

                targetGroup =
                    targetGroup
            )
                ?: return

        val groupItems =
            getGroupItems(
                items =
                    items,

                bounds =
                    bounds
            )

        Log.d(
            tag,
            "========== РАСПРЕДЕЛЕНИЕ ПО ПАРАМ =========="
        )

        for (line in buildLines(groupItems)) {

            val pairTexts =
                mutableMapOf<
                        Int,
                        MutableList<String>
                        >()

            for (block in buildTextBlocks(line)) {

                val pair =
                    findPairForX(
                        x =
                            block.x,

                        pairColumns =
                            pairColumns
                    )
                        ?: continue

                pairTexts
                    .getOrPut(pair) {
                        mutableListOf()
                    }
                    .add(
                        block.text
                    )
            }

            if (pairTexts.isNotEmpty()) {

                val result =
                    pairTexts
                        .toSortedMap()
                        .entries
                        .joinToString(" || ") {

                            "ПАРА ${it.key}: " +
                                    it.value.joinToString(" + ")
                        }

                Log.d(
                    tag,
                    "Y=${line.y}: $result"
                )
            }
        }

        Log.d(
            tag,
            "========== КОНЕЦ РАСПРЕДЕЛЕНИЯ =========="
        )
    }

    // =========================================================================
    // СТАРАЯ ДИАГНОСТИКА ВРЕМЕННЫХ ИНТЕРВАЛОВ
    // =========================================================================

    fun debugPairIntervalsLegacy(
        items: List<PdfTextItem>,
        targetGroup: String
    ) {

        debugPairIntervals(
            items =
                items,

            targetGroup =
                targetGroup
        )
    }

    // =========================================================================
    // ДИАГНОСТИКА ПАРНЫХ КОЛОНОК
    // =========================================================================

    fun debugPairColumns(
        items: List<PdfTextItem>
    ) {

        val tag =
            "ScheduleParser3"

        val lines =
            buildLines(
                items
            )

        val columns =
            findPairColumns(
                lines
            )

        Log.d(
            tag,
            "PAIR COLUMNS: ${
                columns.joinToString(" | ") {
                    "пара ${it.pairNumber}: x=${it.headerX}"
                }
            }"
        )
    }

    // =========================================================================
    // ДИАГНОСТИКА ПОЛНОГО БЛОКА ГРУППЫ
    // =========================================================================

    fun debugFullGroup(
        items: List<PdfTextItem>,
        targetGroup: String,
        pairColumns: List<PairColumn>
    ) {

        val tag =
            "ScheduleParser3"

        Log.d(
            tag,
            "========== FULL GROUP DEBUG =========="
        )

        debugGroup(
            items =
                items,

            targetGroup =
                targetGroup,

            pairColumns =
                pairColumns
        )

        debugPairRooms(
            items =
                items,

            targetGroup =
                targetGroup
        )

        debugPairCells(
            items =
                items,

            targetGroup =
                targetGroup
        )

        debugPairSubjects(
            items =
                items,

            targetGroup =
                targetGroup
        )

        debugPairAnchors(
            items =
                items,

            targetGroup =
                targetGroup,

            pairColumns =
                pairColumns
        )

        Log.d(
            tag,
            "========== END FULL GROUP DEBUG =========="
        )
    }
}
