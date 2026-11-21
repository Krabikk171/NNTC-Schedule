package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krabikk.nntcschedule.ParsedLesson
import java.nio.file.Files

class GroupPreviewCacheTest {
    @Test fun persistsOfflineReportWithoutRoom() {
        val folder = Files.createTempDirectory("nntc-group-cache-test").toFile()
        try {
            val row = GroupCatalog.LessonRow("2РПУ-25-1", "19 ноября 2026 г.", "ЧЕТВЕРГ",
                ParsedLesson(3, "11:50–13:20", "54", "УП.04", "Федурин В.И."), emptyList())
            val report = GroupCatalog.Report(listOf(GroupCatalog.GroupResult(
                "2РПУ-25-1", GroupCatalog.Quality.STRUCTURAL_OK,
                emptyList(), emptyList(), listOf(row))), 1)
            val cache = GroupPreviewCache(folder)
            cache.save(report, "локальный PDF")
            assertEquals(report.csv(), GroupPreviewCache(folder).readCsv())
            assertTrue(GroupPreviewCache(folder).readSummary()!!.contains("локальный PDF"))
        } finally { folder.deleteRecursively() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToOverwriteWithEmptyReport() {
        val folder = Files.createTempDirectory("nntc-empty-cache-test").toFile()
        try {
            GroupPreviewCache(folder).save(GroupCatalog.Report(emptyList(), 0), "empty")
        } finally { folder.deleteRecursively() }
    }
}
