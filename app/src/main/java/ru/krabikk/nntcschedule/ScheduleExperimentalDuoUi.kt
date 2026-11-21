package ru.krabikk.nntcschedule

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme
import java.util.Locale

/** Shared UI-only adapter for two separate experimental renderers. No data access. */
internal data class DuoPalette(
    val bg: Color, val surface: Color, val text: Color, val muted: Color,
    val accent: Color, val secondary: Color, val edge: Color, val glow: Color
)

internal data class DuoEntry(val lesson: ParsedLesson, val index: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExperimentalDuoShell(
    days: List<ParsedDay>, status: String, isUpdating: Boolean, lastCheckTime: Long?,
    onRefresh: () -> Unit, onSettings: () -> Unit, selectedGroup: String,
    onGroupClick: () -> Unit, groupWarning: String?, showDayStrip: Boolean,
    palette: DuoPalette, label: String,
    dateDecoration: @Composable (String) -> Unit,
    lessonRenderer: @Composable (DuoEntry, () -> Unit) -> Unit,
    footerDecoration: @Composable () -> Unit = {},
    backgroundBrush: Brush? = null
) {
    val today = ScheduleDayStrip.todayKey()
    val preferred = ScheduleDayStrip.initialSelection(days.map { it.date }, today)
    var overrideKey by rememberSaveable(selectedGroup) { mutableIntStateOf(0) }
    val selectedKey = overrideKey.takeIf { it != 0 } ?: preferred
    val sorted = remember(days) { days.sortedBy { ScheduleDayStrip.keyFromScheduleDate(it.date) ?: Int.MAX_VALUE } }
    val savedKeys = remember(days) { days.mapNotNull { ScheduleDayStrip.keyFromScheduleDate(it.date) }.toSet() }
    var detail by remember { mutableStateOf<ParsedLesson?>(null) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dark = LocalAppDarkTheme.current

    Column(Modifier.fillMaxSize().background(backgroundBrush ?: Brush.verticalGradient(listOf(palette.bg, palette.bg)))
        .statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(
            state = state, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "duo_brand") {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("NNTC Schedule", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.text)
                    Text(label, fontSize = 12.sp, color = palette.accent)
                }
            }
            item(key = "duo_group") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(onClick = onGroupClick, shape = RoundedCornerShape(18.dp), color = palette.surface,
                        border = BorderStroke(1.dp, palette.edge), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Группа $selectedGroup", modifier = Modifier.weight(1f), color = palette.text,
                                style = MaterialTheme.typography.titleMedium)
                            Text("Сменить ›", color = palette.accent, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (!groupWarning.isNullOrBlank()) Text(groupWarning, color = palette.secondary)
                }
            }
            item(key = "duo_primary_refresh") {
                ExperimentalRefreshControl(
                    status = status, isUpdating = isUpdating,
                    lastCheckTime = lastCheckTime, onRefresh = onRefresh,
                    accent = palette.accent, buttonInk = palette.bg,
                    surface = palette.surface.copy(alpha = .68f),
                    text = palette.text, muted = palette.muted, edge = palette.edge
                )
            }
            if (showDayStrip) {
                item(key = "duo_date") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        dateDecoration(ScheduleDayStrip.displayDate(selectedKey))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { overrideKey = ScheduleDayStrip.shift(selectedKey, -7) },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("‹", color = palette.text, fontSize = 25.sp) }
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                ScheduleDayStrip.week(selectedKey, today).forEach { day ->
                                    val active = day.key == selectedKey
                                    Surface(onClick = { overrideKey = day.key }, modifier = Modifier.weight(1f).heightIn(min = 68.dp),
                                        color = if (active) palette.accent else palette.surface,
                                        shape = RoundedCornerShape(13.dp),
                                        border = BorderStroke(1.dp, if (active) palette.accent else palette.edge)) {
                                        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(day.weekday, color = if (active) palette.bg else palette.muted, fontSize = 11.sp)
                                            Text((day.key % 100).toString(), color = if (active) palette.bg else palette.text,
                                                fontWeight = FontWeight.Bold)
                                            Box(Modifier.padding(top = 5.dp).size(4.dp).background(
                                                if (day.key in savedKeys) palette.secondary else Color.Transparent, CircleShape))
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { overrideKey = ScheduleDayStrip.shift(selectedKey, 7) },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("›", color = palette.text, fontSize = 25.sp) }
                        }
                    }
                }
                item(key = "duo_selected") {
                    val chosen = sorted.firstOrNull { ScheduleDayStrip.keyFromScheduleDate(it.date) == selectedKey }
                    if (chosen == null || chosen.lessons.isEmpty()) {
                        DuoEmpty(palette, if (days.isEmpty()) "Расписание пока не загружено" else "В этот день занятий нет")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                            chosen.lessons.forEachIndexed { index, lesson ->
                                lessonRenderer(DuoEntry(lesson, index)) { detail = lesson }
                            }
                        }
                    }
                }
            } else {
                if (sorted.isEmpty()) item(key = "duo_empty") { DuoEmpty(palette, "Расписание пока не загружено") }
                items(sorted, key = { "duo_${it.date}" }) { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        dateDecoration("${day.weekday.replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru-RU")) }}, ${day.date}")
                        if (day.lessons.isEmpty()) DuoEmpty(palette, "В этот день занятий нет")
                        day.lessons.forEachIndexed { index, lesson ->
                            lessonRenderer(DuoEntry(lesson, index)) { detail = lesson }
                        }
                    }
                }
            }
            item(key = "duo_footer") {
                footerDecoration()
            }
        }
        ExperimentalFloatingNavigation(
            onHome = { scope.launch { state.animateScrollToItem(0) } },
            onGroups = onGroupClick, onSettings = onSettings,
            surface = palette.surface.copy(alpha = .89f),
            accent = palette.accent, text = palette.text, edge = palette.edge
        )
    }
    detail?.let { lesson ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = palette.surface) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("Пара ${lesson.number} · ${lesson.time}", color = palette.accent,
                    style = MaterialTheme.typography.titleLarge)
                if (lesson.subgroups.isNotEmpty()) {
                    lesson.subgroups.sortedBy { it.number }.forEach { sg ->
                        Text("${sg.number}-я подгруппа · ${sg.subject}", color = palette.text,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Каб. ${sg.room.ifBlank { "—" }} · ${sg.teacher.ifBlank { "Преподаватель уточняется" }}",
                            color = palette.muted)
                    }
                } else {
                    Text(lesson.subject.ifBlank { "Название уточняется" }, color = palette.text,
                        style = MaterialTheme.typography.titleLarge)
                    Text("Каб. ${lesson.room.ifBlank { "—" }}", color = palette.muted)
                    Text(lesson.teacher.ifBlank { "Преподаватель уточняется" }, color = palette.muted)
                }
                Text("Группа: $selectedGroup", color = palette.muted)
                TextButton(onClick = { detail = null }) { Text("Закрыть", color = palette.accent) }
            }
        }
    }
}

@Composable
internal fun DuoEmpty(c: DuoPalette, text: String) {
    Box(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(22.dp))
        .border(1.dp, c.edge, RoundedCornerShape(22.dp)).padding(26.dp), contentAlignment = Alignment.Center) {
        Text(text, color = c.text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun DuoLessonText(lesson: ParsedLesson, c: DuoPalette, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("${lesson.number} пара · ${lesson.time.ifBlank { "Время уточняется" }}", color = c.accent,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        if (lesson.subgroups.isNotEmpty()) {
            lesson.subgroups.sortedBy { it.number }.forEach { sg ->
                Text("${sg.number}-я подгруппа · ${sg.subject}", color = c.text,
                    style = MaterialTheme.typography.titleMedium)
                Text("Каб. ${sg.room.ifBlank { "—" }} · ${sg.teacher.ifBlank { "Преподаватель уточняется" }}",
                    color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text(lesson.subject.ifBlank { "Название уточняется" }, color = c.text,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 4 else 6, overflow = TextOverflow.Ellipsis)
            Text("Каб. ${lesson.room.ifBlank { "—" }} · ${lesson.teacher.ifBlank { "Преподаватель уточняется" }}",
                color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
