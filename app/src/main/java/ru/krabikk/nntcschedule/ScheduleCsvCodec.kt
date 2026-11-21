package ru.krabikk.nntcschedule

/**
 * Канонический промежуточный формат расписания: одна пара = одна CSV-строка.
 * PDF и Room не знают о внутренних структурах друг друга.
 * UTF-8; RFC4180-кавычки, допускаются запятые, кавычки и переносы строк.
 */
object ScheduleCsvCodec {
    private val HEADER = listOf("date", "weekday", "group", "number", "time", "subject", "room", "teacher")

    fun encode(days: List<ParsedDay>, group: String): String = buildString {
        append(HEADER.joinToString(","))
        append("\r\n")
        for (day in days) for (lesson in day.lessons.sortedBy { it.number }) {
            val fields = listOf(
                day.date, day.weekday, group, lesson.number.toString(), lesson.time,
                lesson.subject, lesson.room, lesson.teacher
            )
            append(fields.joinToString(",", transform = ::escape))
            append("\r\n")
        }
    }

    /** Throws IllegalArgumentException on malformed or unrelated data. */
    fun decode(csv: String, expectedGroup: String): List<ParsedDay> {
        val records = splitRecords(csv.removePrefix("\uFEFF")).filterNot { row ->
            row.size == 1 && row[0].isBlank()
        }
        require(records.isNotEmpty() && records.first() == HEADER) { "Некорректный заголовок CSV" }
        val days = linkedMapOf<String, ParsedDay>()
        for ((index, record) in records.drop(1).withIndex()) {
            require(record.size == HEADER.size) { "CSV: строка ${index + 2} содержит ${record.size} полей вместо ${HEADER.size}" }
            val date = record[0]
            val weekday = record[1]
            val group = record[2]
            val number = record[3]
            val time = record[4]
            val subject = record[5]
            val room = record[6]
            val teacher = record[7]
            require(group == expectedGroup) { "CSV: обнаружена другая группа ($group)" }
            require(date.isNotBlank() && subject.isNotBlank() && time.isNotBlank()) { "CSV: неполная пара в строке ${index + 2}" }
            val n = number.toIntOrNull()
            require(n != null && n in 1..12) { "CSV: неверный номер пары в строке ${index + 2}" }
            val old = days[date]
            require(old == null || old.weekday == weekday) { "CSV: различные дни недели для даты $date" }
            val lesson = ParsedLesson(n, time, room, subject, teacher)
            require(old?.lessons?.none { it.number == n } != false) { "CSV: дублируется пара $n за $date" }
            days[date] = ParsedDay(date, weekday, old?.lessons.orEmpty() + lesson)
        }
        require(days.isNotEmpty()) { "CSV не содержит занятий" }
        return days.values.map { it.copy(lessons = it.lessons.sortedBy { lesson -> lesson.number }) }
    }

    private fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' })
        "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun splitRecords(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var afterQuote = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            if (quoted) {
                if (c == '"' && i + 1 < csv.length && csv[i + 1] == '"') {
                    field.append('"')
                    i++
                } else if (c == '"') {
                    quoted = false
                    afterQuote = true
                } else field.append(c)
            } else when (c) {
                '"' -> {
                    require(field.isEmpty() && !afterQuote) { "CSV: неожиданная кавычка" }
                    quoted = true
                }
                ',' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                    afterQuote = false
                }
                '\r', '\n' -> {
                    fields.add(field.toString())
                    rows.add(fields)
                    fields = mutableListOf()
                    field.setLength(0)
                    afterQuote = false
                    if (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
                }
                else -> {
                    require(!afterQuote) { "CSV: символ после закрывающей кавычки" }
                    field.append(c)
                }
            }
            i++
        }
        require(!quoted) { "CSV: незакрытая кавычка" }
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(field.toString())
            rows.add(fields)
        }
        return rows
    }
}
