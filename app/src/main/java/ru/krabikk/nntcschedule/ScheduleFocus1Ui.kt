package ru.krabikk.nntcschedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.util.Calendar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Момент · ФОКУС 1» — независимый экспериментальный Compose-экран.
 * Только получает существующие UI-данные и делегирует действия наверх.
 * Не обращается к парсерам, сети, Room, DataStore или WorkManager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFocus1Ui(
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
    val c = focusColors()
    val todayKey = ScheduleDayStrip.todayKey()
    val saved = remember(days) {
        days.mapNotNull { ScheduleDayStrip.keyFromScheduleDate(it.date) }.distinct().sorted()
    }
    val ordered = remember(days) { days.sortedBy { ScheduleDayStrip.keyFromScheduleDate(it.date) ?: Int.MAX_VALUE } }
    val preferred = ScheduleDayStrip.initialSelection(days.map { it.date }, todayKey)
    var userKey by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selectedGroup) { userKey = 0 }
    val selectedKey = userKey.takeIf { it != 0 } ?: preferred
    var detail by remember { mutableStateOf<ParsedLesson?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(c.background)) {
        // Небольшие пятна света только на фоне, не на поверхности с текстом.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.glowA, Color.Transparent),
                    center = Offset(size.width * 0.98f, size.height * 0.12f),
                    radius = size.width * .95f
                ),
                radius = size.width * .95f,
                center = Offset(size.width * .98f, size.height * .12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.glowB, Color.Transparent),
                    center = Offset(size.width * .10f, size.height * .86f),
                    radius = size.width * 1.2f
                ),
                radius = size.width * 1.2f,
                center = Offset(size.width * .10f, size.height * .86f)
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 15.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "focus_header") { FocusHeader(c) }
                item(key = "focus_group") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(onClick = onGroupClick, color = c.chip,
                            shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.Groups, null, tint = c.aqua, modifier = Modifier.size(22.dp))
                                Text("Группа $selectedGroup", modifier = Modifier.weight(1f),
                                    color = c.text, style = MaterialTheme.typography.titleSmall)
                                Icon(Icons.Outlined.ChevronRight, "Сменить группу", tint = c.muted)
                            }
                        }
                        if (!groupWarning.isNullOrBlank()) {
                            Text(groupWarning, color = c.warning, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item(key = "focus_primary_refresh") {
                    ExperimentalRefreshControl(
                        status = status, isUpdating = isUpdating,
                        lastCheckTime = lastCheckTime, onRefresh = onRefresh,
                        accent = c.aqua,
                        buttonInk = if (LocalAppDarkTheme.current) Color(0xFF241608) else Color.White,
                        surface = c.chip.copy(alpha = .70f),
                        text = c.text, muted = c.muted, edge = c.edge
                    )
                }
                if (showDayStrip) {
                    item(key = "focus_date") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FocusDateBar(selectedKey, c,
                                onPrev = { userKey = ScheduleDayStrip.shift(selectedKey, -7) },
                                onNext = { userKey = ScheduleDayStrip.shift(selectedKey, 7) })
                            FocusWeek(selectedKey, todayKey, saved.toSet(), c) { userKey = it }
                        }
                    }
                    item(key = "focus_selected_day") {
                        AnimatedContent(
                            targetState = selectedKey,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "focusDayTransition"
                        ) { key ->
                            val chosen = days.firstOrNull { ScheduleDayStrip.keyFromScheduleDate(it.date) == key }
                            if (chosen?.lessons.isNullOrEmpty()) {
                                FocusEmpty(c, hasSaved = days.isNotEmpty()) { userKey = preferred }
                            } else {
                                FocusLessonList(chosen!!.lessons, c, isToday = key == todayKey) { detail = it }
                            }
                        }
                    }
                } else {
                    if (ordered.isEmpty()) {
                        item(key = "focus_nodata") { FocusEmpty(c, hasSaved = false, onClick = onRefresh) }
                    } else {
                        items(ordered, key = { "focus_${it.date}" }) { day ->
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                FocusDayTitle(day, c)
                                if (day.lessons.isEmpty()) FocusEmpty(c, hasSaved = true, onClick = onRefresh)
                                else FocusLessonList(day.lessons, c, isToday = ScheduleDayStrip.keyFromScheduleDate(day.date) == todayKey) { detail = it }
                            }
                        }
                    }
                }
            }
            FocusBottomNav(c,
                onHome = { scope.launch { listState.animateScrollToItem(0) } },
                onGroups = onGroupClick,
                onSettings = onSettings
            )
        }
    }
    detail?.let { lesson ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = c.sheet) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Пара ${lesson.number} · ${lesson.time}", color = c.aqua,
                    style = MaterialTheme.typography.titleMedium)
                if (lesson.subgroups.isNotEmpty()) {
                    Text("Занятия по подгруппам", style = MaterialTheme.typography.headlineSmall, color = c.text)
                    lesson.subgroups.sortedBy { it.number }.forEach { sg ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${sg.number}-я подгруппа · ${sg.subject}", color = c.text,
                                style = MaterialTheme.typography.titleMedium)
                            FocusDetailRow(Icons.Outlined.LocationOn, "Кабинет: ${sg.room}", c)
                            FocusDetailRow(Icons.Outlined.PersonOutline, "Преподаватель: ${sg.teacher}", c)
                        }
                    }
                } else {
                    Text(lesson.subject.ifBlank { "Название уточняется" },
                        style = MaterialTheme.typography.headlineSmall, color = c.text)
                    if (lesson.room.isNotBlank()) FocusDetailRow(Icons.Outlined.LocationOn, "Кабинет: ${lesson.room}", c)
                    if (lesson.teacher.isNotBlank()) FocusDetailRow(Icons.Outlined.PersonOutline, "Преподаватель: ${lesson.teacher}", c)
                }
                Text("Группа: $selectedGroup", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { detail = null }) { Text("Закрыть", color = c.aqua) }
            }
        }
    }
}

private data class FocusColors(
    val background: Color, val sheet: Color, val front: Color, val chip: Color,
    val text: Color, val muted: Color, val aqua: Color, val violet: Color,
    val pink: Color, val warning: Color, val edge: Color,
    val backA: Color, val backB: Color, val glowA: Color, val glowB: Color
)

@Composable
private fun focusColors(): FocusColors = if (LocalAppDarkTheme.current) FocusColors(
    background = Color(0xFF0C1019), sheet = Color(0xFF181E29),
    front = Color(0xFF1D2430), chip = Color(0xFF252C37),
    text = Color(0xFFF7FAFF), muted = Color(0xFFC5D1E6),
    aqua = Color(0xFFFFBA60), violet = Color(0xFFFF8C32), pink = Color(0xFFFFCE86),
    warning = Color(0xFFFFCD85), edge = Color(0xFF618FB4),
    backA = Color(0xFFAA6325), backB = Color(0xFF5B3B23),
    glowA = Color(0x667D451A), glowB = Color(0x334D2B14)
) else FocusColors(
    background = Color(0xFFF8F6F2), sheet = Color(0xFFFFFFFF),
    front = Color(0xFFFFFFFF), chip = Color(0xFFF3EADF),
    text = Color(0xFF1B2944), muted = Color(0xFF4B6176),
    aqua = Color(0xFF995009), violet = Color(0xFFB96817), pink = Color(0xFF8F5A23),
    warning = Color(0xFF975300), edge = Color(0xFFA3B9DC),
    backA = Color(0xFFFFDEB5), backB = Color(0xFFF3CC9E),
    glowA = Color(0x66FFD7AA), glowB = Color(0x44F8E3C7)
)

@Composable
private fun FocusHeader(c: FocusColors) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val stroke = 2.4.dp.toPx()
                for (i in 0..2) {
                    val left = w * (.16f + i * .11f)
                    val top = h * (.10f + i * .17f)
                    drawRoundRect(
                        color = if (i == 2) c.aqua else c.violet.copy(alpha = .8f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(w * .55f, h * .48f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                        style = Stroke(width = stroke)
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("NNTC Schedule", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = c.text)
            Text("МОМЕНТ · ФОКУС 1", fontSize = 11.sp, color = c.muted)
        }
    }
}

@Composable
private fun FocusDateBar(key: Int, c: FocusColors, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text("Расписание", color = c.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(ScheduleDayStrip.displayDate(key), color = c.muted, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.ChevronLeft, "Предыдущая неделя", tint = c.text)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.ChevronRight, "Следующая неделя", tint = c.text)
        }
    }
}

@Composable
private fun FocusWeek(selected: Int, today: Int, saved: Set<Int>, c: FocusColors, onSelect: (Int) -> Unit) {
    val week = ScheduleDayStrip.week(selected, today)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.forEach { d ->
            val active = d.key == selected
            val color = if (active) c.aqua else c.muted
            Surface(onClick = { onSelect(d.key) }, modifier = Modifier.weight(1f).height(70.dp),
                shape = RoundedCornerShape(17.dp),
                color = if (active) c.chip else c.front.copy(alpha = .72f),
                border = androidx.compose.foundation.BorderStroke(
                    if (active) 1.5.dp else .5.dp,
                    if (active) c.aqua else c.edge.copy(alpha = .32f)
                )
            ) {
                Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween) {
                    Text(d.weekday, fontSize = 12.sp, color = color,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    Text(d.key.rem(100).toString(), fontSize = 16.sp, color = if (active) c.text else c.muted,
                        fontWeight = FontWeight.Bold)
                    Box(Modifier.size(4.dp).background(
                        if (d.key in saved) (if (active) c.aqua else c.violet) else Color.Transparent,
                        CircleShape
                    ))
                }
            }
        }
    }
}

@Composable
private fun FocusDayTitle(day: ParsedDay, c: FocusColors) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(day.weekday.uppercase(Locale.forLanguageTag("ru-RU")), fontWeight = FontWeight.Bold,
            fontSize = 21.sp, color = c.text)
        Text(day.date, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

/** Computes the focus without relying on the number of a lesson (which can have gaps). */
private data class FocusSelection(val index: Int, val label: String, val afterDay: Boolean)

private fun focusMinutes(value: String): Pair<Int, Int>? {
    val times = Regex("(\\d{1,2}):(\\d{2})").findAll(value).take(2).map { match ->
        match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }.toList()
    return if (times.size == 2 && times[1] > times[0]) times[0] to times[1] else null
}

private fun chooseFocus(lessons: List<ParsedLesson>, isToday: Boolean, nowMinutes: Int): FocusSelection {
    if (lessons.isEmpty()) return FocusSelection(-1, "Нет занятий", false)
    if (!isToday) return FocusSelection(0, "Первая пара · выбранный день", false)
    val intervals = lessons.map { focusMinutes(it.time) }
    // Unknown/malformed times must never be represented as current.
    if (intervals.any { it == null }) return FocusSelection(0, "Первая пара · время уточняется", false)
    val current = intervals.indexOfFirst { it != null && nowMinutes >= it.first && nowMinutes < it.second }
    if (current >= 0) return FocusSelection(current, "Текущая пара", false)
    val upcoming = intervals.indexOfFirst { it != null && it.first > nowMinutes }
    if (upcoming >= 0) return FocusSelection(upcoming,
        if (upcoming == 0) "Следующая пара" else "Перерыв · следующая пара", false)
    return FocusSelection(-1, "Занятия завершены", true)
}

@Composable
private fun FocusLessonList(lessons: List<ParsedLesson>, c: FocusColors, isToday: Boolean,
    onClick: (ParsedLesson) -> Unit) {
    val clock by produceState(initialValue = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }, isToday) {
        while (true) {
            val now = Calendar.getInstance()
            value = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            delay(30_000L)
        }
    }
    val focus = chooseFocus(lessons, isToday, clock)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (focus.afterDay) {
            Text("Занятия завершены", color = c.aqua, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
        } else {
            Text(focus.label, color = c.aqua, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            FocusHeroCard(lessons[focus.index], c) { onClick(lessons[focus.index]) }
        }
        val remaining = lessons.withIndex().filter { it.index != focus.index }
        if (remaining.isNotEmpty()) {
            Text(if (focus.afterDay) "Сегодня были" else "Остальные занятия",
                color = c.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            remaining.forEach { (_, lesson) -> FocusCompactCard(lesson, c) { onClick(lesson) } }
        }
    }
}

@Composable
private fun FocusHeroCard(lesson: ParsedLesson, c: FocusColors, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxWidth().background(
        Brush.linearGradient(listOf(c.front, c.chip, c.backA.copy(alpha = .38f))), shape)
        .border(1.5.dp, c.aqua.copy(alpha = .72f), shape)
        .clickable(onClick = onClick).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("ПАРА ${lesson.number}", color = c.aqua, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Outlined.ChevronRight, "Подробности пары", tint = c.aqua)
            }
            Text(lesson.time.ifBlank { "Время уточняется" }, color = c.text,
                fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text(if (lesson.subgroups.isNotEmpty()) "Занятия по подгруппам" else
                lesson.subject.ifBlank { "Название уточняется" }, color = c.text,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            FocusLessonMeta(lesson, c)
        }
    }
}

@Composable
private fun FocusCompactCard(lesson: ParsedLesson, c: FocusColors, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().background(c.front, shape)
        .border(.7.dp, c.edge.copy(alpha = .45f), shape)
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).background(c.chip, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center) {
            Text(lesson.number.toString(), color = c.aqua, fontWeight = FontWeight.Bold, fontSize = 21.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lesson.time.ifBlank { "Время уточняется" }, color = c.aqua,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(if (lesson.subgroups.isNotEmpty()) "Занятия по подгруппам" else
                lesson.subject.ifBlank { "Название уточняется" }, color = c.text,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FocusLessonMeta(lesson, c)
        }
        Icon(Icons.Outlined.ChevronRight, "Подробнее", tint = c.muted)
    }
}

@Composable
private fun FocusLessonMeta(lesson: ParsedLesson, c: FocusColors) {
    if (lesson.subgroups.isNotEmpty()) {
        lesson.subgroups.sortedBy { it.number }.forEach { sg ->
            Text("${sg.number}-я: ${sg.subject} · Каб. ${sg.room.ifBlank { "—" }} · ${sg.teacher.ifBlank { "—" }}",
                color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        Text("Каб. ${lesson.room.ifBlank { "—" }} · ${lesson.teacher.ifBlank { "Преподаватель уточняется" }}",
            color = c.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FocusEmpty(c: FocusColors, hasSaved: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(c.front, RoundedCornerShape(22.dp))
        .border(1.dp, c.edge.copy(alpha = .45f), RoundedCornerShape(22.dp))
        .padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = c.aqua, modifier = Modifier.size(36.dp))
        Text(if (hasSaved) "В этот день занятий нет" else "Расписание ещё не загружено",
            color = c.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(if (hasSaved) "Можно посмотреть другой день." else "Проверь наличие расписания.",
            color = c.muted, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick) {
            Text(if (hasSaved) "К ближайшему дню" else "Проверить обновления", color = c.aqua)
        }
    }
}

@Composable
private fun FocusSync(c: FocusColors, status: String, isUpdating: Boolean, lastCheckTime: Long?, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(c.chip.copy(alpha = .9f), RoundedCornerShape(17.dp))
        .padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (isUpdating) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = c.aqua)
        else Icon(
            if (status.contains("ошиб", ignoreCase = true) ||
                status.contains("не удалось", ignoreCase = true) ||
                status.contains("нет сети", ignoreCase = true)) Icons.Outlined.ErrorOutline
            else Icons.Outlined.Info,
            null, tint = if (status.contains("ошиб", ignoreCase = true)) c.warning else c.aqua,
            modifier = Modifier.size(19.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (isUpdating) "Проверка расписания…" else status.ifBlank { "Данные из сохранённого расписания" },
                color = c.text, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (lastCheckTime != null) {
                Text("Последняя проверка: " + SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("ru-RU"))
                    .format(Date(lastCheckTime)), color = c.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(onClick = onRefresh, enabled = !isUpdating, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Refresh, "Проверить обновления", tint = c.aqua)
        }
    }
}

@Composable
private fun FocusBottomNav(c: FocusColors, onHome: () -> Unit, onGroups: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(c.sheet.copy(alpha = .98f), RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        FocusNavItem("Расписание", Icons.Outlined.CalendarMonth, true, c, onHome, Modifier.weight(1f))
        FocusNavItem("Группы", Icons.Outlined.Groups, false, c, onGroups, Modifier.weight(1f))
        FocusNavItem("Настройки", Icons.Outlined.Settings, false, c, onSettings, Modifier.weight(1f))
    }
}

@Composable
private fun FocusNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean, c: FocusColors, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(17.dp), color = if (active) c.chip else Color.Transparent) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 6.dp)) {
            Icon(icon, null, tint = if (active) c.aqua else c.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, color = if (active) c.aqua else c.muted,
                fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun FocusDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, c: FocusColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.aqua, modifier = Modifier.size(20.dp))
        Text(text, color = c.muted, style = MaterialTheme.typography.bodyMedium)
    }
}
