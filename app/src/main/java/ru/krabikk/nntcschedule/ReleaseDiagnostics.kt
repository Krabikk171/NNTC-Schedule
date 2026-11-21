package ru.krabikk.nntcschedule

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only release diagnostic: it does not start downloads, mutate Room, or
 * rewrite the saved successful-check timestamp. A scheduled worker is NOT
 * proof that Android ran it at an exact time; on-device observation is needed.
 */
private data class DiagnosticCheck(
    val title: String,
    val description: String,
    val passed: Boolean? // null means not conclusive / not applicable
)

private data class DiagnosticSnapshot(
    val checkedAt: Long,
    val checks: List<DiagnosticCheck>,
    val recentSync: String,
    val recentSuccess: String,
    val lessonCount: Int,
    val dateCount: Int,
    val registeredWorkCount: Int,
    val intervalMinutes: Long
) {
    fun toShareableText(): String = buildString {
        appendLine("NNTC Schedule — диагностика перед релизом")
        appendLine("Проверено: ${diagnosticTime(checkedAt)}")
        appendLine("Занятий: $lessonCount; дней: $dateCount")
        appendLine("Интервал настройки: $intervalMinutes мин; активных работ: $registeredWorkCount")
        appendLine("Последняя успешная проверка: $recentSuccess")
        appendLine("Последняя попытка: $recentSync")
        checks.forEach { check ->
            val marker = when (check.passed) {
                true -> "OK"
                false -> "ВНИМАНИЕ"
                null -> "ИНФО"
            }
            appendLine("[$marker] ${check.title}: ${check.description}")
        }
        appendLine("Примечание: отчёт не доказывает своевременность запуска фоновой задачи, " +
            "корректность новых PDF или фактическое получение уведомлений.")
    }
}

private fun diagnosticTime(time: Long?): String =
    time?.let {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("ru-RU"))
            .format(Date(it))
    } ?: "нет данных"

private fun notificationPermissionGranted(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

private suspend fun captureReleaseDiagnostics(context: Context): DiagnosticSnapshot =
    withContext(Dispatchers.IO) {
        val lessons = ScheduleDatabase.getInstance(context).scheduleDao().getAll()
        val interval = SettingsRepository.getCheckInterval(context).first()
        val lastSuccessful = SettingsRepository.getLastCheckTime(context).first()
        val sync = SettingsRepository.getLastSyncReport(context).first()
        val notificationAllowed = notificationPermissionGranted(context)

        val workLookup = runCatching {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("schedule_auto_update")
                .get()
        }
        val workInfos = workLookup.getOrNull().orEmpty()
        val activePeriodic = workInfos.count { info ->
            info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.RUNNING ||
                info.state == WorkInfo.State.BLOCKED
        }

        // Identical stored rows are suspicious. Two DIFFERENT subgroup lessons
        // sharing the same period are not treated as duplicates.
        val distinctCount = lessons.map { lesson ->
            listOf(lesson.date, lesson.weekday, lesson.number.toString(),
                lesson.time, lesson.room, lesson.subject, lesson.teacher)
        }.toSet().size
        val duplicateCount = lessons.size - distinctCount
        val incompleteCount = lessons.count { item ->
            item.date.isBlank() || item.number <= 0 || item.subject.isBlank()
        }
        val dateCount = lessons.map { it.date }.toSet().size

        val checks = listOf(
            DiagnosticCheck(
                "Сохранённое расписание",
                if (lessons.isEmpty()) "Занятий пока нет в базе" else
                    "$dateCount дн., ${lessons.size} пар — доступны для чтения",
                lessons.isNotEmpty()
            ),
            DiagnosticCheck(
                "Полнота записей",
                if (incompleteCount == 0) "Пустых дат, номеров и предметов нет" else
                    "Обнаружено $incompleteCount неполных записей",
                incompleteCount == 0
            ),
            DiagnosticCheck(
                "Одинаковые записи",
                if (duplicateCount == 0) "Полных дублей не обнаружено" else
                    "Полных дублей: $duplicateCount",
                duplicateCount == 0
            ),
            DiagnosticCheck(
                "Автопроверка WorkManager",
                when {
                    interval < 15L -> "Отключена в настройках ($interval мин)"
                    workLookup.isFailure -> "Не удалось запросить сведения WorkManager"
                    activePeriodic > 0 -> "Задача зарегистрирована (это не подтверждает время запуска)"
                    else -> "Активная периодическая задача не найдена"
                },
                when {
                    interval < 15L -> null
                    workLookup.isFailure -> false
                    else -> activePeriodic > 0
                }
            ),
            DiagnosticCheck(
                "Последняя успешная проверка",
                diagnosticTime(lastSuccessful),
                if (lastSuccessful == null) null else true
            ),
            DiagnosticCheck(
                "Разрешение уведомлений",
                if (notificationAllowed) "Разрешены на уровне приложения" else
                    "Не разрешены: тестовое уведомление не отобразится",
                notificationAllowed
            )
        )
        DiagnosticSnapshot(
            checkedAt = System.currentTimeMillis(),
            checks = checks,
            recentSync = sync?.let {
                "${it.title}, ${diagnosticTime(it.timestampMillis)}" +
                    if (it.details.isBlank()) "" else ": ${it.details}"
            } ?: "ещё нет записей",
            recentSuccess = diagnosticTime(lastSuccessful),
            lessonCount = lessons.size,
            dateCount = dateCount,
            registeredWorkCount = activePeriodic,
            intervalMinutes = interval
        )
    }

/** Insert this in the already existing DeveloperScreen LazyColumn. */
@Composable
fun ReleaseDiagnosticsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<DiagnosticSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Проверки перед релизом", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Безопасная диагностика: читает Room, DataStore и состояние WorkManager. " +
                    "Ничего не удаляет, не загружает и не отправляет уведомлений.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    if (loading) return@Button
                    loading = true
                    error = null
                    copied = false
                    scope.launch {
                        try {
                            snapshot = captureReleaseDiagnostics(context)
                        } catch (e: Exception) {
                            error = "Ошибка диагностики: ${e.message ?: "неизвестная ошибка"}"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Проверяю..." else "Проверить состояние приложения")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            snapshot?.let { report ->
                Text("Проверено ${diagnosticTime(report.checkedAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium)
                report.checks.forEach { check ->
                    val marker = when (check.passed) {
                        true -> "✓"
                        false -> "!"
                        null -> "·"
                    }
                    Text("$marker ${check.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(check.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Последняя попытка: ${report.recentSync}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(
                            "NNTC diagnostics", report.toShareableText()
                        ))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (copied) "Отчёт скопирован" else "Скопировать отчёт") }
            }
            Text(
                "Далее ниже: «Запустить Worker сейчас» → повторить запуск без " +
                    "изменения PDF → «Тест уведомления». Ошибку сети проверяй с " +
                    "главного экрана в авиарежиме. Для автозапуска нужна " +
                    "проверка на устройстве в течение 1–2 дней.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Внимание: раздел «Тестовые изменения БД» ниже действительно меняет " +
                    "расписание. Не используй его без резервной копии.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
