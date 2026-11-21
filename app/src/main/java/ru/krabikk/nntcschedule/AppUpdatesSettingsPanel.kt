package ru.krabikk.nntcschedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.krabikk.nntcschedule.ui.theme.themedPanelColor
import java.io.File

/** APK updates; intentionally separate from the PDF schedule's 30-minute checker. */
@Composable
fun AppUpdatesSettingsPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf(AppUpdatePreferences.getChannel(context)) }
    var offer by remember { mutableStateOf<AppUpdateOffer?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("Нажмите «Проверить», чтобы найти новую версию.") }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = themedPanelColor())
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Обновление приложения", style = MaterialTheme.typography.titleLarge)
            Text(
                "Новые версии APK через GitHub. Это не проверка изменений расписания.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listOf(
                AppUpdateChannel.STABLE to "Stable · только стабильные версии",
                AppUpdateChannel.BETA to "Beta · также тестовые версии"
            ).forEach { (option, title) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !checking && !downloading) {
                        channel = option
                        AppUpdatePreferences.setChannel(context, option)
                        offer = null
                        downloaded = null
                        status = "Канал изменён. Нажмите «Проверить»."
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = channel == option,
                        onClick = {
                            if (!checking && !downloading) {
                                channel = option
                                AppUpdatePreferences.setChannel(context, option)
                                offer = null
                                downloaded = null
                                status = "Канал изменён. Нажмите «Проверить»."
                            }
                        },
                        enabled = !checking && !downloading
                    )
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                enabled = !checking && !downloading,
                onClick = {
                    scope.launch {
                        checking = true
                        offer = null
                        downloaded = null
                        status = "Проверяем выпуски GitHub…"
                        try {
                            val found = AppUpdateRepository.check(context, channel)
                            offer = found
                            status = if (found == null) "Новых версий в канале нет."
                            else "Найдена версия ${found.versionName} (${found.versionCode})."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            status = "Не удалось проверить: ${e.localizedMessage ?: "ошибка сети"}"
                        } finally {
                            checking = false
                        }
                    }
                }
            ) { Text(if (checking) "Проверка…" else "Проверить сейчас") }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            offer?.let { release ->
                Text("Что нового:", style = MaterialTheme.typography.titleMedium)
                Text(
                    release.description.ifBlank { "Сведения об изменениях размещены в GitHub Releases." },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 8
                )
                Text("Размер APK: ${release.apkSizeBytes / (1024 * 1024)} МБ", style = MaterialTheme.typography.bodySmall)
                if (downloading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        CircularProgressIndicator()
                        Text("Скачивание: $progress%")
                    }
                } else if (downloaded != null) {
                    Button(onClick = {
                        try {
                            val started = AppUpdateRepository.requestSystemInstall(
                                context, downloaded!!, release.versionCode, release.apkSha256
                            )
                            status = if (started) "Следуйте инструкциям установщика Android."
                            else "Разрешите установку из этого источника и вернитесь для повторного нажатия."
                        } catch (e: Exception) {
                            status = "Не удалось открыть установщик: ${e.localizedMessage}"
                        }
                    }) { Text("Установить через Android") }
                } else {
                    Button(onClick = {
                        scope.launch {
                            downloading = true
                            progress = 0
                            status = "Загружаем и проверяем подпись APK…"
                            try {
                                downloaded = AppUpdateRepository.downloadAndVerify(context, release) { value ->
                                    progress = value
                                }
                                status = "APK скачан. Контрольная сумма, версия и подпись проверены."
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                status = "Не удалось скачать APK: ${e.localizedMessage ?: "ошибка"}"
                            } finally {
                                downloading = false
                            }
                        }
                    }) { Text("Скачать обновление") }
                }
                TextButton(onClick = {
                    offer = null
                    downloaded = null
                    status = "Обновление отложено."
                }, enabled = !downloading) { Text("Позже") }
            }
            Text(
                "Установка требует подтверждения Android. Если APK подписан другим ключом, обновление будет отклонено.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
