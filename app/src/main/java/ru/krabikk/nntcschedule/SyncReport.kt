package ru.krabikk.nntcschedule

/** A single completed sync attempt. It never substitutes for the saved schedule. */
enum class SyncOutcome {
    UPDATED, UNCHANGED, FAILED;

    companion object {
        fun fromStorage(raw: String?): SyncOutcome? = entries.firstOrNull { it.name == raw }
    }
}

data class SyncReport(
    val outcome: SyncOutcome,
    val timestampMillis: Long,
    val details: String = ""
) {
    init {
        require(timestampMillis >= 0L)
    }

    val successful: Boolean get() = outcome != SyncOutcome.FAILED

    val title: String get() = when (outcome) {
        SyncOutcome.UPDATED -> "Расписание обновлено"
        SyncOutcome.UNCHANGED -> "Изменений не обнаружено"
        SyncOutcome.FAILED -> "Последняя проверка завершилась ошибкой"
    }

    companion object {
        /** A failed download must not be displayed as a successful check. */
        fun fromStorage(outcome: String?, timestamp: Long?, details: String?): SyncReport? {
            val value = SyncOutcome.fromStorage(outcome) ?: return null
            val time = timestamp?.takeIf { it >= 0L } ?: return null
            return SyncReport(value, time, details.orEmpty().take(240))
        }
    }
}
