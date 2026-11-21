package ru.krabikk.nntcschedule.v3

import java.io.File

/**
 * Отдельное приватное хранилище диагностических снимков.
 * Не взаимодействует с Room и настройками рабочего расписания.
 */
class GroupPreviewCache(private val directory: File) {
    private val csvFile get() = File(directory, "group_catalog_preview.csv")
    private val summaryFile get() = File(directory, "group_catalog_preview_summary.txt")

    fun save(report: GroupCatalog.Report, source: String) {
        require(report.groups.isNotEmpty() && report.lessonCount > 0) {
            "Нельзя сохранять пустую диагностику"
        }
        require(directory.isDirectory || directory.mkdirs()) { "Не удалось создать каталог" }
        // CSV обновляется последним: ошибка сохранения не затронет старый CSV.
        val summary = "Источник: $source\n${report.summary()}"
        val tmpSummary = File(directory, "group_catalog_preview_summary.tmp")
        val tmpCsv = File(directory, "group_catalog_preview.tmp")
        try {
            tmpSummary.writeText(summary, Charsets.UTF_8)
            tmpCsv.writeText(report.csv(), Charsets.UTF_8)
            replace(tmpSummary, summaryFile)
            replace(tmpCsv, csvFile)
        } finally {
            tmpSummary.delete()
            tmpCsv.delete()
        }
    }

    fun readCsv(): String? = csvFile.takeIf { it.isFile }?.readText(Charsets.UTF_8)
    fun readSummary(): String? = summaryFile.takeIf { it.isFile }?.readText(Charsets.UTF_8)

    private fun replace(temp: File, destination: File) {
        // Copy to destination only after fully writing the temporary file.
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            check(temp.delete()) { "Не удалось удалить временный файл" }
        }
    }
}
