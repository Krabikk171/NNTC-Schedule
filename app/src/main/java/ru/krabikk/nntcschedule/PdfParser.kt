package ru.krabikk.nntcschedule

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayInputStream
import kotlin.math.abs


// ============================================================================
// Один текстовый элемент PDF
// ============================================================================
//
// Храним не только символ, но и его положение на странице.
// Координаты нужны ScheduleParser для определения:
// - группы
// - времени
// - кабинета
// - предмета
//
data class PdfTextItem(
    val page: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)


// ============================================================================
// Визуальная строка PDF
// ============================================================================

data class VisualLine(
    val page: Int,
    val y: Float,
    val text: String
)


// ============================================================================
// Результат разбора PDF
// ============================================================================
//
// Раньше PDF открывался два раза:
//
// 1. extractText()
// 2. extractItems()
//
// Теперь мы открываем PDF ОДИН раз и сразу получаем оба результата.
//
// Это и есть одна из главных оптимизаций 2.2.1.
//
data class PdfParseResult(
    val text: String,
    val items: List<PdfTextItem>
)


// ============================================================================
// Основной парсер PDF
// ============================================================================

object PdfParser {

    private const val LINE_Y_TOLERANCE =
        4f

    private const val WORD_GAP_THRESHOLD =
        4f

    private val WHITESPACE_REGEX =
        Regex("\\s+")


    // ========================================================================
    // ОСНОВНОЙ МЕТОД
    // ========================================================================
    //
    // PDF открывается только ОДИН раз.
    //
    // За один проход получаем:
    // - обычный текст PDF
    // - координаты всех символов
    //
    fun parse(
        pdfBytes: ByteArray
    ): PdfParseResult {

        val totalStartTime =
            System.nanoTime()


        ByteArrayInputStream(
            pdfBytes
        ).use { inputStream ->

            val loadStartTime =
                System.nanoTime()


            PDDocument.load(
                inputStream
            ).use { document ->

                val loadTimeMs =
                    (
                            System.nanoTime() -
                                    loadStartTime
                            ) / 1_000_000


                val stripper =
                    PositionTextStripper().apply {
                        sortByPosition = true

                        // Дубликаты символов могут создавать ложные названия
                        // и кабинеты. Возвращаем стандартную фильтрацию PDFBox.
                        suppressDuplicateOverlappingText = true
                    }


                val textStartTime =
                    System.nanoTime()


                // Запускаем обработку PDF.
// PositionTextStripper одновременно собирает:
// 1. координатные элементы;
// 2. готовый текст для поиска дат.
                stripper.getText(
                    document
                )

                val text =
                    stripper.collectedText.toString()


                val textTimeMs =
                    (
                            System.nanoTime() -
                                    textStartTime
                            ) / 1_000_000


                val itemsStartTime =
                    System.nanoTime()


                val items =
                    stripper.items.toList()

                val writeStringTimeMs =
                    stripper.writeStringTimeNs /
                            1_000_000


                Log.d(
                    "SCHEDULE_PDF",
                    "writeString: ${writeStringTimeMs} мс, " +
                            "вызовов: ${stripper.writeStringCalls}"
                )

                val itemsCopyTimeMs =
                    (
                            System.nanoTime() -
                                    itemsStartTime
                            ) / 1_000_000


                val totalTimeMs =
                    (
                            System.nanoTime() -
                                    totalStartTime
                            ) / 1_000_000


                Log.d(
                    "SCHEDULE_PDF",
                    "PDDocument.load: ${loadTimeMs} мс"
                )

                Log.d(
                    "SCHEDULE_PDF",
                    "stripper.getText: ${textTimeMs} мс"
                )

                Log.d(
                    "SCHEDULE_PDF",
                    "items.toList: ${itemsCopyTimeMs} мс"
                )

                Log.d(
                    "SCHEDULE_PDF",
                    "items: ${items.size}"
                )

                Log.d(
                    "SCHEDULE_PDF",
                    "ИТОГО PdfParser.parse: ${totalTimeMs} мс"
                )


                return PdfParseResult(
                    text =
                        text,

                    items =
                        items
                )
            }
        }
    }


    // ========================================================================
    // Совместимость со старым кодом
    // ========================================================================
    //
    // Эти методы оставляем, чтобы старые места приложения не сломались.
    //
    // Но ScheduleRepository теперь должен использовать parse(),
    // потому что это быстрее.
    //

    fun extractText(
        pdfBytes: ByteArray
    ): String {

        return parse(
            pdfBytes
        ).text
    }


    fun extractItems(
        pdfBytes: ByteArray
    ): List<PdfTextItem> {

        return parse(
            pdfBytes
        ).items
    }


    // ========================================================================
    // Получение визуальных строк
    // ========================================================================
    //
    // Этот метод пока оставляем для совместимости и диагностики.
    // ScheduleRepository напрямую его не использует.
    //

    fun extractVisualLines(
        pdfBytes: ByteArray
    ): List<VisualLine> {

        val items =
            extractItems(
                pdfBytes
            )

        return buildVisualLines(
            items
        )
    }


    // ========================================================================
    // Сборка визуальных строк
    // ========================================================================

    private fun buildVisualLines(
        items: List<PdfTextItem>
    ): List<VisualLine> {

        if (
            items.isEmpty()
        ) {
            return emptyList()
        }


        val result =
            mutableListOf<VisualLine>()


        val itemsByPage =
            items.groupBy {
                it.page
            }


        for (
        (page, pageItems)
        in itemsByPage
        ) {

            // Сначала сортируем:
            // сверху вниз -> слева направо.
            val sortedItems =
                pageItems.sortedWith(
                    compareBy<PdfTextItem> {
                        it.y
                    }.thenBy {
                        it.x
                    }
                )


            val lines =
                buildLines(
                    items =
                        sortedItems,

                    tolerance =
                        LINE_Y_TOLERANCE,

                    alreadySorted =
                        true
                )


            for (
            line
            in lines
            ) {

                if (
                    line.text.isBlank()
                ) {
                    continue
                }


                result +=
                    VisualLine(
                        page =
                            page,

                        y =
                            line.y,

                        text =
                            line.text
                    )
            }
        }


        return result.sortedWith(
            compareBy<VisualLine> {
                it.page
            }.thenBy {
                it.y
            }
        )
    }


    // ========================================================================
    // Группировка символов в строки
    // ========================================================================

    private fun buildLines(
        items: List<PdfTextItem>,
        tolerance: Float,
        alreadySorted: Boolean
    ): List<VisualLineInternal> {

        if (
            items.isEmpty()
        ) {
            return emptyList()
        }


        val sortedItems =
            if (
                alreadySorted
            ) {
                items
            } else {
                items.sortedWith(
                    compareBy<PdfTextItem> {
                        it.y
                    }.thenBy {
                        it.x
                    }
                )
            }


        val lines =
            mutableListOf<
                    MutableList<PdfTextItem>
                    >()


        for (
        item
        in sortedItems
        ) {

            // В PDF строки идут последовательно.
            // Поэтому достаточно проверять последнюю созданную строку.
            val lastLine =
                lines.lastOrNull()


            if (
                lastLine == null
            ) {

                lines +=
                    mutableListOf(
                        item
                    )

                continue
            }


            val averageY =
                lastLine
                    .map {
                        it.y
                    }
                    .average()
                    .toFloat()


            if (
                abs(
                    averageY -
                            item.y
                ) < tolerance
            ) {

                lastLine +=
                    item

            } else {

                lines +=
                    mutableListOf(
                        item
                    )
            }
        }


        return lines.map { line ->

            val sortedLine =
                line.sortedBy {
                    it.x
                }


            VisualLineInternal(
                y =
                    sortedLine
                        .map {
                            it.y
                        }
                        .average()
                        .toFloat(),

                text =
                    buildLineText(
                        sortedLine
                    )
            )
        }
    }


    // ========================================================================
    // Сборка текста одной строки
    // ========================================================================

    private fun buildLineText(
        items: List<PdfTextItem>
    ): String {

        if (
            items.isEmpty()
        ) {
            return ""
        }


        return buildString {

            var previous:
                    PdfTextItem? =
                null


            for (
            item
            in items
            ) {

                if (
                    previous != null
                ) {

                    // Если между двумя символами достаточно большой
                    // горизонтальный промежуток, считаем это пробелом.
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

                        append(' ')
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
                WHITESPACE_REGEX,
                " "
            )
            .trim()
    }


    // ========================================================================
    // PDFTextStripper с координатами
    // ========================================================================
    //
    // PDFBox вызывает writeString() для найденного текста.
    // TextPosition содержит координаты каждого символа.
    //
    // ============================================================================
// PDFTextStripper с координатами
// ============================================================================
//
// PDFBox вызывает writeString() для найденного текста.
//
// Здесь одновременно собираем:
// 1. координаты каждого символа;
// 2. обычный текст PDF.
//
// Важно: collectedText должен существовать только ОДИН раз.
//

    private class PositionTextStripper :
        PDFTextStripper() {

        val items =
            mutableListOf<PdfTextItem>()


        // Собственный буфер обычного текста PDF.
        val collectedText =
            StringBuilder()


        // Диагностика времени writeString().
        var writeStringTimeNs =
            0L

        var writeStringCalls =
            0


        private var currentPage =
            0


        override fun startPage(
            page: PDPage?
        ) {

            super.startPage(
                page
            )


            currentPage++
        }


        override fun writeString(
            text: String,
            textPositions:
            MutableList<TextPosition>
        ) {

            val startTime =
                System.nanoTime()


            collectedText
                .append(
                    text
                )
                .append(
                    '\n'
                )


            for (
            position
            in textPositions
            ) {

                items +=
                    PdfTextItem(

                        page =
                            currentPage,

                        text =
                            position.unicode,

                        x =
                            position.xDirAdj,

                        y =
                            position.yDirAdj,

                        width =
                            position.widthDirAdj,

                        height =
                            position.heightDir
                    )
            }


            writeStringCalls++


            writeStringTimeNs +=
                System.nanoTime() -
                        startTime
        }
    }


    // Внутреннее представление строки.
    private data class VisualLineInternal(
        val y: Float,
        val text: String
    )
}