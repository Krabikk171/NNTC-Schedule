package ru.krabikk.nntcschedule

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ru.krabikk.nntcschedule.ui.theme.themedPanelColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

fun formatThemeMode(mode: String): String = when (mode) {
    SettingsRepository.ThemeMode.LIGHT -> "Светлая"
    SettingsRepository.ThemeMode.DARK -> "Тёмная"
    else -> "Системная"
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onEditProfile: () -> Unit = {}) {
    val context = LocalContext.current
    val selectedGroup by SettingsRepository.getSelectedGroup(context)
        .collectAsState(initial = "2РПУ-25-1")
    val scope = rememberCoroutineScope()
    val checkInterval by SettingsRepository.getCheckInterval(context).collectAsState(initial = 30L)
    val themeMode by SettingsRepository.getThemeMode(context)
        .collectAsState(initial = SettingsRepository.ThemeMode.SYSTEM)
    val interfaceStyle by SettingsRepository.getInterfaceStyle(context)
        .collectAsState(initial = InterfaceStyle.MINIMAL)
    val dayStripEnabled by SettingsRepository.getDayStripEnabled(context)
        .collectAsState(initial = true)
    var showIntervalDialog by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableStateOf(0) }
    var showDeveloperScreen by remember { mutableStateOf(false) }
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }

    if (showDeveloperScreen) {
        DeveloperScreen(onBack = { showDeveloperScreen = false })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(49.dp)) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("Настройки", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        item {
            SettingsSection(
                title = "Профиль и расписание",
                subtitle = "Можно изменить выбор, сделанный при первом запуске"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(onClick = onEditProfile)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Студент · $selectedGroup", style = MaterialTheme.typography.titleMedium)
                        Text("Изменить роль или группу",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "Изменить профиль",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            SettingsSection(title = "Оформление", subtitle = "Выбери, как будет выглядеть приложение") {
                ThemeChoice(
                    title = "Как в системе",
                    subtitle = "Переключать тему вместе с Android",
                    icon = Icons.Outlined.BrightnessAuto,
                    selected = themeMode == SettingsRepository.ThemeMode.SYSTEM,
                    onClick = { scope.launch { SettingsRepository.setThemeMode(context, SettingsRepository.ThemeMode.SYSTEM) } }
                )
                ThemeChoice(
                    title = "Светлая",
                    subtitle = "Светлые карточки и фиолетовые акценты",
                    icon = Icons.Outlined.LightMode,
                    selected = themeMode == SettingsRepository.ThemeMode.LIGHT,
                    onClick = { scope.launch { SettingsRepository.setThemeMode(context, SettingsRepository.ThemeMode.LIGHT) } }
                )
                ThemeChoice(
                    title = "Тёмная",
                    subtitle = "Тёмный фон и мягкий фиолетовый",
                    icon = Icons.Outlined.DarkMode,
                    selected = themeMode == SettingsRepository.ThemeMode.DARK,
                    onClick = { scope.launch { SettingsRepository.setThemeMode(context, SettingsRepository.ThemeMode.DARK) } }
                )
            }
        }

        item {
            SettingsSection(
                title = "Стиль интерфейса",
                subtitle = "Выбор оформления не влияет на поиск и распознавание занятий"
            ) {
                VisualStyleChoice(
                    title = "Minimal",
                    subtitle = "Спокойный и аккуратный. Текущий дизайн",
                    style = InterfaceStyle.MINIMAL,
                    selected = interfaceStyle == InterfaceStyle.MINIMAL,
                    onClick = { scope.launch { SettingsRepository.setInterfaceStyle(context, InterfaceStyle.MINIMAL) } }
                )
                VisualStyleChoice(
                    title = "Aurora",
                    subtitle = "Мягкий фон и спокойные цветовые переливы",
                    style = InterfaceStyle.AURORA,
                    selected = interfaceStyle == InterfaceStyle.AURORA,
                    onClick = { scope.launch { SettingsRepository.setInterfaceStyle(context, InterfaceStyle.AURORA) } }
                )
                VisualStyleChoice(
                    title = "Neon",
                    subtitle = "Тёмная палитра и деликатное неоновое свечение",
                    style = InterfaceStyle.NEON,
                    selected = interfaceStyle == InterfaceStyle.NEON,
                    onClick = { scope.launch { SettingsRepository.setInterfaceStyle(context, InterfaceStyle.NEON) } }
                )
                VisualStyleChoice(
                    title = "Lavender",
                    subtitle = "Лавандовые и сливовые оттенки, мягкая эстетика",
                    style = InterfaceStyle.LAVENDER,
                    selected = interfaceStyle == InterfaceStyle.LAVENDER,
                    onClick = { scope.launch { SettingsRepository.setInterfaceStyle(context, InterfaceStyle.LAVENDER) } }
                )
                VisualStyleChoice(
                    title = "Ocean",
                    subtitle = "Свежий сине-бирюзовый стиль",
                    style = InterfaceStyle.OCEAN,
                    selected = interfaceStyle == InterfaceStyle.OCEAN,
                    onClick = { scope.launch { SettingsRepository.setInterfaceStyle(context, InterfaceStyle.OCEAN) } }
                )
                // Экспериментальные варианты живут только в скрытом меню «Разработка».
                // Здесь оставляем пять стабильных пользовательских стилей.
                if (ExperimentalDesigns.isExperimental(interfaceStyle)) {
                    Text(
                        "Сейчас активен экспериментальный дизайн. Его можно сменить в меню «Разработка» " +
                            "или выбрать здесь любой обычный стиль.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Каждый стиль работает со светлой, тёмной и системной темой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsSection(
                title = "Отображение расписания",
                subtitle = "Настройки только внешнего вида, без изменения данных"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            scope.launch {
                                SettingsRepository.setDayStripEnabled(context, !dayStripEnabled)
                            }
                        }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(modifier = Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Лента дней", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Выключи, чтобы видеть все дни расписания одним списком",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dayStripEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                SettingsRepository.setDayStripEnabled(context, enabled)
                            }
                        }
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Обновления", subtitle = "Проверка нового расписания в фоне") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showIntervalDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(13.dp)) {
                        Box(modifier = Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Автопроверка", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Интервал: ${formatCheckInterval(checkInterval)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Android может немного сдвигать фоновую проверку для экономии заряда.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }


        item {
            AppUpdatesSettingsPanel()
        }

        item {
            SettingsSection(
                title = "О приложении",
                subtitle = "NNTC Schedule — расписание занятий второго корпуса"
            ) {
                Text("Разработчик: Krabikk171", style = MaterialTheme.typography.titleMedium)
                Text("Вы можете найти меня в Telegram: Krabikk171",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Версия $versionName", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Независимое приложение. Не является официальным приложением колледжа.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Text(
                text = "NNTC Schedule • Версия $versionName",
                modifier = Modifier.fillMaxWidth().clickable {
                    versionTapCount++
                    if (versionTapCount >= 7) {
                        versionTapCount = 0
                        showDeveloperScreen = true
                    }
                }.padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showIntervalDialog) {
        val options = listOf(0L, 15L, 20L, 30L, 60L, 120L, 300L)
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Как часто проверять?") },
            text = {
                Column {
                    options.forEach { interval ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch { SettingsRepository.setCheckInterval(context, interval) }
                                showIntervalDialog = false
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = checkInterval == interval,
                                onClick = {
                                    scope.launch { SettingsRepository.setCheckInterval(context, interval) }
                                    showIntervalDialog = false
                                }
                            )
                            Text(formatCheckInterval(interval), modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIntervalDialog = false }) { Text("Закрыть") } }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = themedPanelColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ThemeChoice(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val choiceColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(260), label = "themeChoiceColor"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = choiceColor,
        border = null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

/** Выбор варианта оформления с маленьким образцом цветов — без тяжёлых изображений. */
@Composable
private fun VisualStyleChoice(
    title: String,
    subtitle: String,
    style: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val swatches = when (style) {
        InterfaceStyle.AURORA -> listOf(Color(0xFF7D8CF0), Color(0xFFC27BDF), Color(0xFF83D8F0))
        InterfaceStyle.NEON -> listOf(Color(0xFF261239), Color(0xFF9A32D8), Color(0xFFEE44BE))
        InterfaceStyle.LAVENDER -> listOf(Color(0xFFF5E8F1), Color(0xFFCC9ED1), Color(0xFF714B99))
        InterfaceStyle.OCEAN -> listOf(Color(0xFFE0F7F7), Color(0xFF60CBD4), Color(0xFF135D89))
        InterfaceStyle.ROUTE -> listOf(Color(0xFF0C1630), Color(0xFFFF4CA8), Color(0xFF25314E))
        else -> listOf(Color(0xFFF7F4FA), Color(0xFFDDCFF2), Color(0xFF7256B2))
    }
    val shape = RoundedCornerShape(16.dp)
    val choiceColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(260), label = "visualStyleChoiceColor"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = choiceColor,
        shape = shape,
        border = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(width = 68.dp, height = 48.dp)
                    .background(Brush.linearGradient(swatches), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier.size(width = 13.dp, height = (13 + i * 6).dp)
                                .background(Color.White.copy(alpha = .75f), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
