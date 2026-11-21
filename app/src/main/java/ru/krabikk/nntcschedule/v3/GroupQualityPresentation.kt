package ru.krabikk.nntcschedule.v3

/**
 * Short descriptions for the group list. Room stores diagnostics from the most
 * recent successful PDF import, not a verdict about the group for all time.
 * This utility has no Compose/Android dependencies and can be regression-tested.
 */
object GroupQualityPresentation {
    data class Summary(val detail: String, val needsAttention: Boolean)

    fun forEntry(quality: String, lessons: Int, warnings: Int): Summary {
        val count = lessons.coerceAtLeast(0)
        val issues = warnings.coerceAtLeast(0)
        return when (quality) {
            "STRUCTURAL_OK" -> Summary("$count ${declension(count, "пара", "пары", "пар")} в сохранённом PDF", false)
            "NEEDS_REVIEW" -> Summary(
                "$count ${declension(count, "пара", "пары", "пар")} · ${issues.coerceAtLeast(1)} ${declension(issues.coerceAtLeast(1), "замечание", "замечания", "замечаний")}",
                true
            )
            "UNSUPPORTED" -> Summary("Не удалось надёжно распознать расписание", true)
            "LEGACY" -> Summary("Контрольная группа", false)
            else -> Summary("$count ${declension(count, "пара", "пары", "пар")} в сохранённом PDF", false)
        }
    }

    fun flaggedGroupCount(qualities: List<String>): Int =
        qualities.count { it == "NEEDS_REVIEW" || it == "UNSUPPORTED" }

    private fun declension(count: Int, one: String, few: String, many: String): String {
        val mod100 = count % 100
        if (mod100 in 11..14) return many
        return when (count % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }
}
