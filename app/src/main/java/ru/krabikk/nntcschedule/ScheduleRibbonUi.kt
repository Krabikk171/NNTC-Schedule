package ru.krabikk.nntcschedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme

/**
 * «Лента»: самостоятельный экспериментальный UI. Только получает ParsedDay/ParsedLesson
 * и передаёт действия в уже существующие обработчики. Ни PDF/CSV, ни Room, ни
 * WorkManager, ни сохранённые настройки здесь не читаются и не меняются.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleRibbonUi(
    days: List<ParsedDay>,
    status: String,
    isUpdating: Boolean,
    lastCheckTime: Long?,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    selectedGroup: String,
    onGroupClick: () -> Unit,
    groupWarning: String?,
    showDayStrip: Boolean
) {
    val colors = ribbonColors()
    val todayKey = ScheduleDayStrip.todayKey()
    val keys = remember(days) {
        days.mapNotNull { ScheduleDayStrip.keyFromScheduleDate(it.date) }.distinct().sorted()
    }
    val sortedDays = remember(days) {
        days.sortedBy { ScheduleDayStrip.keyFromScheduleDate(it.date) ?: Int.MAX_VALUE }
    }
    val preferred = ScheduleDayStrip.initialSelection(days.map { it.date }, todayKey)
    var manualKey by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selectedGroup) { manualKey = 0 }
    val selectedKey = manualKey.takeIf { it != 0 } ?: preferred
    var selectedLesson by remember { mutableStateOf<ParsedLesson?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "ribbon_header") { RibbonHeader(colors) }
            item(key = "ribbon_group") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onGroupClick,
                        shape = RoundedCornerShape(16.dp),
                        color = colors.chip
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.Groups, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                            Text(selectedGroup, color = colors.text,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.ChevronRight, "Сменить группу", tint = colors.muted)
                        }
                    }
                    if (!groupWarning.isNullOrBlank()) {
                        Text(groupWarning, color = colors.warning, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (showDayStrip) {
                item(key = "ribbon_calendar") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RibbonWeekHeader(selectedKey, colors,
                            onPrev = { manualKey = ScheduleDayStrip.shift(selectedKey, -7) },
                            onNext = { manualKey = ScheduleDayStrip.shift(selectedKey, 7) })
                        RibbonDayStrip(selectedKey, todayKey, keys.toSet(), colors) { manualKey = it }
                    }
                }
                item(key = "ribbon_selected_lessons") {
                    AnimatedContent(
                        targetState = selectedKey,
                        transitionSpec = { fadeIn(tween(190)) togetherWith fadeOut(tween(130)) },
                        label = "ribbonDayChange"
                    ) { key ->
                        val selectedDay = days.firstOrNull { ScheduleDayStrip.keyFromScheduleDate(it.date) == key }
                        if (selectedDay?.lessons.isNullOrEmpty()) {
                            RibbonEmpty(colors, hasAnyDays = days.isNotEmpty()) {
                                manualKey = preferred
                            }
                        } else {
                            RibbonLessons(selectedDay!!.lessons, colors) { selectedLesson = it }
                        }
                    }
                }
            } else {
                // Существующая настройка: без ленты показаны ВСЕ сохранённые дни списком.
                if (sortedDays.isEmpty()) {
                    item(key = "ribbon_no_saved_days") { RibbonEmpty(colors, false, onRefresh) }
                } else {
                    items(sortedDays, key = { "ribbon_${it.date}" }) { day ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RibbonSectionDate(day, colors)
                            if (day.lessons.isEmpty()) RibbonEmpty(colors, true, onRefresh)
                            else RibbonLessons(day.lessons, colors) { selectedLesson = it }
                        }
                    }
                }
            }
            item(key = "ribbon_status") {
                RibbonSync(colors, status, isUpdating, lastCheckTime, onRefresh)
            }
        }
        RibbonBottomNavigation(colors,
            onHome = { scope.launch { listState.animateScrollToItem(0) } },
            onGroups = onGroupClick,
            onSettings = onSettings)
    }

    selectedLesson?.let { lesson ->
        ModalBottomSheet(
            onDismissRequest = { selectedLesson = null },
            containerColor = colors.card
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${lesson.number}-я пара · ${lesson.time}",
                    style = MaterialTheme.typography.titleMedium, color = colors.primary)
                if (lesson.subgroups.isNotEmpty()) {
                    Text("Занятия по подгруппам", color = colors.text,
                        style = MaterialTheme.typography.headlineSmall)
                    lesson.subgroups.sortedBy { it.number }.forEach { subgroup ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${subgroup.number}-я подгруппа · ${subgroup.subject}",
                                color = colors.text, style = MaterialTheme.typography.titleMedium)
                            RibbonDetailRow(Icons.Outlined.LocationOn, "Кабинет: ${subgroup.room}", colors)
                            RibbonDetailRow(Icons.Outlined.PersonOutline, "Преподаватель: ${subgroup.teacher}", colors)
                        }
                    }
                } else {
                    Text(lesson.subject, color = colors.text,
                        style = MaterialTheme.typography.headlineSmall)
                    if (lesson.room.isNotBlank()) RibbonDetailRow(Icons.Outlined.LocationOn, "Кабинет: ${lesson.room}", colors)
                    if (lesson.teacher.isNotBlank()) RibbonDetailRow(Icons.Outlined.PersonOutline, "Преподаватель: ${lesson.teacher}", colors)
                }
                Text("Группа: $selectedGroup", color = colors.muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { selectedLesson = null }) { Text("Закрыть", color = colors.primary) }
            }
        }
    }
}

private data class RibbonColors(
    val background: Color,
    val card: Color,
    val chip: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val outline: Color,
    val navBackground: Color,
    val warning: Color,
    val gradients: List<List<Color>>
)

@Composable
private fun ribbonColors(): RibbonColors = if (LocalAppDarkTheme.current) RibbonColors(
    background = Color(0xFF141829), card = Color(0xFF22273D), chip = Color(0xFF292941),
    text = Color(0xFFF8F6FF), muted = Color(0xFFCED0E4), primary = Color(0xFFCCB5FF),
    outline = Color(0xFF383B59), navBackground = Color(0xFF24243B),
    warning = Color(0xFFFFCB80),
    gradients = listOf(
        listOf(Color(0xFF536BED), Color(0xFF7874E9)),
        listOf(Color(0xFF8A51D7), Color(0xFFBB75DB)),
        listOf(Color(0xFFB044AB), Color(0xFFE077B0)),
        listOf(Color(0xFF187EAC), Color(0xFF4EBCCA))
    )
) else RibbonColors(
    background = Color(0xFFFAF8FF), card = Color.White, chip = Color(0xFFF0EAFC),
    text = Color(0xFF251B37), muted = Color(0xFF554969), primary = Color(0xFF6948B5),
    outline = Color(0xFFE4DCEF), navBackground = Color(0xFFF2EEFC),
    warning = Color(0xFF8D5300),
    gradients = listOf(
        listOf(Color(0xFF5175E8), Color(0xFF9686EC)),
        listOf(Color(0xFF8759D3), Color(0xFFB18BE6)),
        listOf(Color(0xFFB04AA8), Color(0xFFDE83C1)),
        listOf(Color(0xFF2B90BD), Color(0xFF75BEE5))
    )
)

@Composable
private fun RibbonHeader(colors: RibbonColors) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = colors.primary, modifier = Modifier.size(26.dp))
        Column {
            Text("NNTC Schedule", color = colors.text, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("Лента · экспериментальный стиль", color = colors.muted,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RibbonWeekHeader(
    selectedKey: Int, colors: RibbonColors,
    onPrev: () -> Unit, onNext: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.ChevronLeft, "Предыдущая неделя", tint = colors.primary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ScheduleDayStrip.weekLabel(selectedKey), color = colors.text,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(ScheduleDayStrip.displayDate(selectedKey), color = colors.muted,
                style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.ChevronRight, "Следующая неделя", tint = colors.primary)
        }
    }
}

@Composable
private fun RibbonDayStrip(
    selectedKey: Int, todayKey: Int,
    savedKeys: Set<Int>, colors: RibbonColors,
    onSelect: (Int) -> Unit
) {
    val week = remember(selectedKey, todayKey) { ScheduleDayStrip.week(selectedKey, todayKey) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.forEach { day ->
            val selected = day.key == selectedKey
            Column(
                modifier = Modifier.weight(1f)
                    .defaultMinSize(minHeight = 63.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) colors.primary else colors.chip)
                    .clickable { onSelect(day.key) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(day.weekday, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.card else colors.muted)
                Text(day.dayAndMonth.substringBefore(' '), fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = if (selected) colors.card else colors.text)
                Box(Modifier.size(3.dp).background(
                    if (day.key in savedKeys && !selected) colors.primary.copy(alpha = .7f) else Color.Transparent,
                    RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun RibbonSectionDate(day: ParsedDay, colors: RibbonColors) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("${day.weekday} · ${day.date}", color = colors.text,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().height(3.dp)
            .background(Brush.horizontalGradient(listOf(colors.gradients[0][0], colors.gradients[2][0], Color.Transparent)),
                RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun RibbonLessons(
    lessons: List<ParsedLesson>, colors: RibbonColors,
    onLessonClick: (ParsedLesson) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        lessons.sortedBy { it.number }.forEachIndexed { index, lesson ->
            RibbonLessonRow(lesson, colors, index, onClick = { onLessonClick(lesson) })
        }
    }
}

private val slantedRibbonShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width * .86f, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun RibbonLessonRow(lesson: ParsedLesson, colors: RibbonColors, index: Int, onClick: () -> Unit) {
    val gradient = colors.gradients[index % colors.gradients.size]
    val multiple = lesson.subgroups.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(BorderStroke(1.dp, colors.outline), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(92.dp).fillMaxHeight().clip(slantedRibbonShape)
            .background(Brush.linearGradient(gradient)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(start = 6.dp, end = 13.dp, top = 14.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(lesson.number.toString(), color = Color.White, fontSize = 30.sp,
                    fontWeight = FontWeight.Bold, lineHeight = 33.sp)
                Text("пара", color = Color.White, fontSize = 12.sp)
            }
        }
        Column(Modifier.weight(1f).defaultMinSize(minHeight = 93.dp)
            .padding(start = 9.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.Center) {
            Text(lesson.time.ifBlank { "Время уточняется" }, color = colors.text,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(if (multiple && lesson.subgroups.map { it.subject }.distinct().size > 1)
                    "Занятия по подгруппам" else lesson.subject,
                color = colors.text, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            if (multiple) {
                lesson.subgroups.sortedBy { it.number }.forEach { subgroup ->
                    Text("${subgroup.number} п/г · ${subgroup.subject} · каб. ${subgroup.room}",
                        color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            } else {
                if (lesson.room.isNotBlank()) Text("Каб. ${lesson.room}", color = colors.muted,
                    fontSize = 12.sp)
                if (lesson.teacher.isNotBlank()) Text(lesson.teacher, color = colors.muted,
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Outlined.ChevronRight, "Подробнее о паре", tint = colors.primary,
            modifier = Modifier.padding(end = 5.dp).size(18.dp))
    }
}

@Composable
private fun RibbonEmpty(colors: RibbonColors, hasAnyDays: Boolean, onGoToSaved: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = colors.card,
        border = BorderStroke(1.dp, colors.outline)) {
        Column(Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CalendarMonth, null, tint = colors.primary,
                modifier = Modifier.size(32.dp))
            Text(if (hasAnyDays) "На этот день занятий нет" else "Расписание пока не загружено",
                color = colors.text, style = MaterialTheme.typography.titleMedium)
            Text(if (hasAnyDays) "Можно выбрать другую дату." else "Попробуйте проверить обновления.",
                color = colors.muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onGoToSaved) {
                Text(if (hasAnyDays) "К ближайшему дню" else "Проверить обновления", color = colors.primary)
            }
        }
    }
}

@Composable
private fun RibbonSync(
    colors: RibbonColors, status: String,
    isUpdating: Boolean, lastCheckTime: Long?, onRefresh: () -> Unit
) {
    Surface(shape = RoundedCornerShape(15.dp), color = colors.chip) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 5.dp, bottom = 5.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isUpdating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.primary)
            else Icon(Icons.Outlined.CheckCircleOutline, null, tint = colors.primary, modifier = Modifier.size(19.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isUpdating) "Проверка обновлений…" else status.ifBlank { "Статус обновлений неизвестен" },
                    color = colors.text, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (lastCheckTime != null) {
                    Text("Последняя попытка: ${java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.forLanguageTag("ru-RU")).format(java.util.Date(lastCheckTime))}",
                        color = colors.muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onRefresh, enabled = !isUpdating, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Refresh, "Проверить обновления", tint = colors.primary)
            }
        }
    }
}

@Composable
private fun RibbonBottomNavigation(
    colors: RibbonColors, onHome: () -> Unit,
    onGroups: () -> Unit, onSettings: () -> Unit
) {
    Row(Modifier.fillMaxWidth().background(colors.navBackground)
        .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RibbonTab(Modifier.weight(1f), Icons.Outlined.CalendarMonth, "Расписание", true, colors, onHome)
        RibbonTab(Modifier.weight(1f), Icons.Outlined.Groups, "Группы", false, colors, onGroups)
        RibbonTab(Modifier.weight(1f), Icons.Outlined.Settings, "Настройки", false, colors, onSettings)
    }
}

@Composable
private fun RibbonTab(
    modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, selected: Boolean, colors: RibbonColors, onClick: () -> Unit
) {
    Column(
        modifier = modifier.defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary.copy(alpha = .15f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, label, tint = if (selected) colors.primary else colors.muted,
            modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.muted)
    }
}

@Composable
private fun RibbonDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, colors: RibbonColors
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(title, color = colors.text, style = MaterialTheme.typography.bodyMedium)
    }
}
