package ru.krabikk.nntcschedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.krabikk.nntcschedule.v3.GroupCatalog
import ru.krabikk.nntcschedule.v3.GroupPreviewCache

/**
 * Экспериментальный каталог групп 3.1.0.
 *
 * Ни скачанный, ни выбранный вручную PDF НИКОГДА не записывается в Room,
 * не изменяет выбранную группу, WorkManager или время синхронизации.
 * Все новые занятия доступны только для безопасного просмотра/экспорта.
 */
@Composable
fun GroupCatalogDiagnosticsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember(context) { GroupPreviewCache(context.filesDir) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<GroupCatalog.Report?>(null) }
    var sourceLabel by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Можно проверить PDF сервера или выбрать локальный файл. Расписание не изменяется.") }
    var groupQuery by remember { mutableStateOf("") }
    var selectedGroupName by remember { mutableStateOf("2РПУ-25-1") }
    var cachedInfo by remember { mutableStateOf(cache.readSummary()?.lineSequence()?.firstOrNull().orEmpty()) }
    var cachedAvailable by remember { mutableStateOf(cache.readCsv() != null) }

    fun analyzePdf(source: String, readBytes: () -> ByteArray) {
        if (running) return
        running = true
        message = "Читаю PDF ($source), проверяю все группы…"
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val bytes = readBytes()
                    require(bytes.size in 8..12_000_000) {
                        "Размер PDF должен быть от 8 байт до 12 МБ"
                    }
                    require(String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "%PDF") {
                        "Выбранный файл не является PDF"
                    }
                    PDFBoxResourceLoader.init(context.applicationContext)
                    val parsed = PdfParser.parse(bytes)
                    GroupCatalog.analyze(parsed.items)
                }
                // Пустой/ошибочный результат нельзя выдавать за успешный каталог.
                if (data.groups.isEmpty() || data.lessonCount == 0) {
                    error("В PDF не удалось извлечь занятия. Предыдущая диагностика сохранена.")
                }
                withContext(Dispatchers.IO) { cache.save(data, source) }
                result = data
                sourceLabel = source
                selectedGroupName = if (data.groups.any { it.name == "2РПУ-25-1" })
                    "2РПУ-25-1" else data.groups.first().name
                cachedInfo = cache.readSummary()?.lineSequence()?.firstOrNull().orEmpty()
                cachedAvailable = true
                message = "${data.groups.size} групп · ${data.lessonCount} записей · " +
                    "без замечаний ${data.cleanCount} · проверить ${data.needReviewCount} · " +
                    "не поддерживаются ${data.unsupportedCount}."
            } catch (e: Exception) {
                message = "Проверка не завершена: ${e.localizedMessage ?: e.javaClass.simpleName}. " +
                    "Рабочее расписание не изменено."
            } finally {
                running = false
            }
        }
    }

    val importPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            analyzePdf("локальный PDF") {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Не удалось открыть выбранный файл")
            }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val content = result?.csv() ?: cache.readCsv()
                    ?: error("Нет сохранённого отчёта")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                } ?: error("Файл не удалось открыть для записи")
            }.onSuccess { message = "CSV каталога сохранён." }
                .onFailure { message = "Не удалось сохранить CSV: ${it.localizedMessage}" }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("3.1 • Проверка групп и PDF", style = MaterialTheme.typography.titleMedium)
            Text(
                "Тестовый режим: PDF можно выбрать из файлов эмулятора. " +
                    "Результаты не попадают в основное расписание, базу Room и уведомления.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Button(enabled = !running, onClick = {
                analyzePdf("сервер колледжа") {
                    val download = PdfDownloader.downloadPdf()
                    download.bytes ?: error(download.error ?: "Не удалось скачать PDF")
                }
            }) { Text(if (running) "Проверяю…" else "Проверить PDF с сервера") }
            OutlinedButton(enabled = !running, onClick = {
                importPdf.launch(arrayOf("application/pdf"))
            }) { Text("Выбрать PDF из файлов устройства") }
            Text(message, style = MaterialTheme.typography.bodySmall)
            if (cachedInfo.isNotEmpty() && result == null) {
                Text("Есть сохранённый диагностический CSV. Его можно экспортировать без сети.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }

            val data = result
            if (data != null) {
                Text("Источник: $sourceLabel", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("NNTC Group Report", data.summary()))
                    message = "Отчёт скопирован."
                }) { Text("Скопировать отчёт") }
            }
            if (data != null || cachedAvailable) {
                OutlinedButton(onClick = { exportCsv.launch("nntc_groups_preview.csv") }) {
                    Text("Сохранить CSV (включая предупреждения)")
                }
            }
            if (data != null) {
                OutlinedTextField(
                    value = groupQuery,
                    onValueChange = { groupQuery = it },
                    label = { Text("Поиск группы") },
                    placeholder = { Text("Например, 2РПУ или 26-1 ЭМ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val visible = data.groups.filter {
                    it.name.contains(groupQuery.trim(), ignoreCase = true)
                }
                if (visible.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visible, key = { it.name }) { entry ->
                            FilterChip(
                                selected = selectedGroupName == entry.name,
                                onClick = { selectedGroupName = entry.name },
                                label = {
                                    val tag = if (entry.quality == GroupCatalog.Quality.STRUCTURAL_OK) "" else " ⚠"
                                    Text(entry.name + tag)
                                }
                            )
                        }
                    }
                    val selected = visible.firstOrNull { it.name == selectedGroupName } ?: visible.first()
                    Text("${selected.name} · ${selected.rows.size} пар · ${qualityLabel(selected.quality)}",
                        style = MaterialTheme.typography.titleSmall)
                    selected.rows.forEach { row ->
                        val lesson = row.lesson
                        Text("${row.date} · ${lesson.number} пара · ${lesson.time.ifBlank { "время не определено" }}\n" +
                            "${lesson.subject} · кабинет ${lesson.room.ifBlank { "—" }} · ${lesson.teacher.ifBlank { "преподаватель не определён" }}",
                            style = MaterialTheme.typography.bodySmall)
                        if (row.notes.isNotEmpty()) {
                            Text("⚠ ${row.notes.joinToString("; ")}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    selected.notes.distinct().take(4).forEach { note ->
                        Text("⚠ $note", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("Группы по такому запросу не найдены.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Без структурных замечаний — не значит проверено по оригиналу PDF. " +
                        "Ненадёжные занятия нельзя переносить в рабочую базу без дополнительной сверки.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun qualityLabel(quality: GroupCatalog.Quality): String = when (quality) {
    GroupCatalog.Quality.STRUCTURAL_OK -> "Без обнаруженных замечаний"
    GroupCatalog.Quality.NEEDS_REVIEW -> "Нужна проверка"
    GroupCatalog.Quality.UNSUPPORTED -> "Не поддерживается"
}
