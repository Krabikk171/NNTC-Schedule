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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

/**
 * Изолированный UI-эксперимент «МАРШРУТ 1»: янтарная линия. Данные передаются из существующего экрана.
 * Не обращается к Room, PDF, CSV, WorkManager, сети или настройкам напрямую.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleRoute1Ui(
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
    val colors = amberRoute1Colors()
    val clockMillis = routeRememberClockMillis()
    val todayKey = ScheduleDayStrip.todayKey(clockMillis)
    val savedKeys = remember(days) { days.mapNotNull { ScheduleDayStrip.keyFromScheduleDate(it.date) }.distinct().sorted() }
    val sortedDays = remember(days) {
        days.sortedBy { ScheduleDayStrip.keyFromScheduleDate(it.date) ?: Int.MAX_VALUE }
    }
    val initialKey = ScheduleDayStrip.initialSelection(days.map { it.date }, todayKey)
    var manualKey by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selectedGroup) { manualKey = 0 }
    val selectedKey = manualKey.takeIf { it != 0 } ?: initialKey
    var selectedLesson by remember { mutableStateOf<ParsedLesson?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "route_brand") { AmberRoute1Brand(colors) }
            item(key = "route_group") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = onGroupClick,
                        shape = RoundedCornerShape(18.dp),
                        color = colors.chip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Icon(Icons.Outlined.Groups, null, tint = colors.amber, modifier = Modifier.size(21.dp))
                            Text("Группа $selectedGroup", style = MaterialTheme.typography.titleSmall,
                                color = colors.text, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.ChevronRight, "Сменить группу", tint = colors.muted)
                        }
                    }
                    if (!groupWarning.isNullOrBlank()) {
                        Text(groupWarning, color = colors.warning, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (showDayStrip) {
                item(key = "route_date_title") {
                    AmberRoute1DateTitle(selectedKey, colors)
                }
                item(key = "route_selected_day") {
                    Column {
                        AmberRoute1DayPicker(
                            selectedKey = selectedKey,
                            todayKey = todayKey,
                            savedKeys = savedKeys.toSet(),
                            colors = colors,
                            onSelect = { manualKey = it }
                        )
                        AnimatedContent(
                            targetState = selectedKey,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "routeSelectedDay"
                        ) { key ->
                            val chosen = days.firstOrNull { ScheduleDayStrip.keyFromScheduleDate(it.date) == key }
                            if (chosen != null && chosen.lessons.isNotEmpty()) {
                                AmberRoute1Timeline(chosen.lessons, colors, dayKey = key, clockMillis = clockMillis, onLessonClick = { selectedLesson = it })
                            } else {
                                AmberRoute1EmptyDay(
                                    hasSavedDays = days.isNotEmpty(),
                                    colors = colors,
                                    onGoToSaved = { manualKey = initialKey }
                                )
                            }
                        }
                    }
                }
            } else {
                // Existing setting «Лента дней» off: show every saved day in sequence.
                if (sortedDays.isEmpty()) {
                    item(key = "route_no_data") {
                        AmberRoute1EmptyDay(false, colors, onGoToSaved = onRefresh)
                    }
                } else {
                    items(sortedDays, key = { "route_${it.date}" }) { day ->
                        // No day strip: every day becomes the starting station of its own route.
                        // Keep the header and its first lesson in one zero-gap column.
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            AmberRoute1DaySectionHeader(day, colors)
                            if (day.lessons.isNotEmpty()) {
                                AmberRoute1Timeline(day.lessons, colors, dayKey = ScheduleDayStrip.keyFromScheduleDate(day.date), clockMillis = clockMillis, onLessonClick = { selectedLesson = it })
                            } else {
                                AmberRoute1EmptyDay(true, colors, onGoToSaved = onRefresh)
                            }
                        }
                    }
                }
            }
            item(key = "route_sync") {
                AmberRoute1Sync(colors, status, isUpdating, lastCheckTime, onRefresh)
            }
        }
        AmberRoute1BottomNav(colors, onGroupClick, onSettings)
    }
    selectedLesson?.let { lesson ->
        ModalBottomSheet(onDismissRequest = { selectedLesson = null }, containerColor = colors.card) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("${lesson.number}-я пара", color = colors.amber, style = MaterialTheme.typography.labelLarge)
                if (lesson.subgroups.isNotEmpty()) {
                    Text("Занятия по подгруппам", color = colors.text,
                        style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.time.ifBlank { "Время уточняется" }, color = colors.text,
                        style = MaterialTheme.typography.titleLarge)
                    lesson.subgroups.sortedBy { it.number }.forEach { subgroup ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${subgroup.number}-я подгруппа · ${subgroup.subject}",
                                color = colors.text, style = MaterialTheme.typography.titleMedium)
                            AmberRoute1DetailRow(Icons.Outlined.LocationOn,
                                "Кабинет: ${subgroup.room}", colors)
                            AmberRoute1DetailRow(Icons.Outlined.PersonOutline,
                                "Преподаватель: ${subgroup.teacher}", colors)
                        }
                    }
                } else {
                    Text(lesson.subject, color = colors.text, style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.time.ifBlank { "Время уточняется" }, color = colors.text,
                        style = MaterialTheme.typography.titleLarge)
                    if (lesson.room.isNotBlank()) {
                        AmberRoute1DetailRow(Icons.Outlined.LocationOn, "Кабинет: ${lesson.room}", colors)
                    }
                    if (lesson.teacher.isNotBlank()) {
                        AmberRoute1DetailRow(Icons.Outlined.PersonOutline,
                            "Преподаватель: ${lesson.teacher}", colors)
                    }
                }
                Text("Группа: $selectedGroup", color = colors.muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { selectedLesson = null }) { Text("Закрыть") }
            }
        }
    }
}

private data class AmberRoute1Colors(
    val amber: Color,
    val background: Color,
    val card: Color,
    val cardEnd: Color,
    val chip: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val warning: Color
)

@Composable
private fun amberRoute1Colors(): AmberRoute1Colors {
    return if (LocalAppDarkTheme.current) AmberRoute1Colors(
        amber = Color(0xFFFFC14B),
        background = Color(0xFF0C1119),
        card = Color(0xFF222831),
        cardEnd = Color(0xFF181E28),
        chip = Color(0xFF242933),
        line = Color(0xFFFFC14B),
        text = Color(0xFFF9F6F0),
        muted = Color(0xFFBCC0C7),
        warning = Color(0xFFFFD98B)
    ) else AmberRoute1Colors(
        amber = Color(0xFF986008),
        background = Color(0xFFFFFCF6),
        card = Color(0xFFFFFFFF),
        cardEnd = Color(0xFFF4EEE3),
        chip = Color(0xFFF5F0E7),
        line = Color(0xFF986008),
        text = Color(0xFF27231C),
        muted = Color(0xFF6C6252),
        warning = Color(0xFF89530A)
    )
}

@Composable
private fun AmberRoute1Brand(colors: AmberRoute1Colors) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // An actual metro line with two nodes — also a distinctive app symbol.
        Canvas(Modifier.size(34.dp)) {
            val unit = size.width / 34f
            drawLine(colors.amber, Offset(7f * unit, 6f * unit),
                Offset(25f * unit, 28f * unit), strokeWidth = 3.3f * unit, cap = StrokeCap.Round)
            drawCircle(colors.background, radius = 6.5f * unit, center = Offset(7f * unit, 6f * unit))
            drawCircle(colors.background, radius = 6.5f * unit, center = Offset(25f * unit, 28f * unit))
            drawCircle(colors.amber, radius = 5f * unit, center = Offset(7f * unit, 6f * unit), style = Stroke(2.8f * unit))
            drawCircle(colors.amber, radius = 5f * unit, center = Offset(25f * unit, 28f * unit), style = Stroke(2.8f * unit))
        }
        Column {
            Text("NNTC", color = colors.text, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text("Schedule · Маршрут", color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AmberRoute1DateTitle(selectedKey: Int, colors: AmberRoute1Colors) {
    val date = remember(selectedKey) {
        val cal = Calendar.getInstance().apply {
            clear()
            set(selectedKey / 10000, (selectedKey / 100) % 100 - 1, selectedKey % 100, 12, 0)
        }
        SimpleDateFormat("EEEE,\nd MMMM yyyy", Locale.forLanguageTag("ru-RU"))
            .format(cal.time).replaceFirstChar { it.uppercaseChar() }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(date, color = colors.text, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, lineHeight = 34.sp)
        Text("Выбери день", color = colors.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AmberRoute1DayPicker(
    selectedKey: Int,
    todayKey: Int,
    savedKeys: Set<Int>,
    colors: AmberRoute1Colors,
    onSelect: (Int) -> Unit
) {
    val centeredDays = remember(selectedKey, todayKey) {
        (-2..2).map { shift ->
            val key = ScheduleDayStrip.shift(selectedKey, shift)
            ScheduleDayStrip.week(key, todayKey).first { it.key == key }
        }
    }
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            centeredDays.forEach { day ->
                val selected = day.key == selectedKey
                val shape = RoundedCornerShape(14.dp)
                Surface(
                    onClick = { onSelect(day.key) },
                    modifier = Modifier.weight(1f).height(66.dp),
                    shape = shape,
                    color = if (selected) colors.amber else colors.chip
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(day.weekday, fontSize = 13.sp,
                            color = if (selected) Color(0xFF231A0B) else colors.text,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        Text(day.dayAndMonth.substringBefore(' '), fontSize = 16.sp,
                            color = if (selected) Color(0xFF231A0B) else colors.muted,
                            fontWeight = FontWeight.Bold)
                        if (!selected && savedKeys.contains(day.key)) {
                            Box(Modifier.size(3.dp).background(colors.amber, CircleShape))
                        }
                    }
                }
            }
        }
        // Fundamental feature of the chosen reference: route leaves the selected day.
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val end = 24.dp.toPx()
            val path = Path().apply {
                moveTo(size.width * .5f, 0f)
                cubicTo(size.width * .5f, size.height * .56f, end, size.height * .18f,
                    end, size.height)
            }
            drawPath(path, colors.amber.copy(alpha = .15f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, colors.line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/**
 * Day-strip-free view: the day header is also the start of the metro line.
 * The first station follows immediately underneath, with the same 24.dp track axis.
 */
@Composable
private fun AmberRoute1DaySectionHeader(day: ParsedDay, colors: AmberRoute1Colors) {
    // Когда лента выключена, заголовок — настоящая стартовая станция,
    // а не обычная строка текста. Рельсы непрерывно продолжаются к первой паре.
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.width(48.dp).fillMaxHeight()) {
            val trackX = 24.dp.toPx()
            val markerY = size.height / 2f
            if (day.lessons.isNotEmpty()) {
                drawLine(
                    colors.line,
                    Offset(trackX, markerY),
                    Offset(trackX, size.height),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(colors.background, radius = 9.dp.toPx(), center = Offset(trackX, markerY))
            drawCircle(
                colors.line,
                radius = 8.dp.toPx(),
                center = Offset(trackX, markerY),
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(colors.amber, radius = 2.dp.toPx(), center = Offset(trackX, markerY))
        }
        val headerShape = RoundedCornerShape(18.dp)
        Surface(
            modifier = Modifier.weight(1f)
                .padding(top = 8.dp, bottom = 8.dp)
                .border(1.5.dp, colors.amber, headerShape),
            color = colors.cardEnd,
            shape = headerShape
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${day.weekday} · ${day.date}",
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${day.lessons.size} занятий",
                    color = colors.muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun AmberRoute1Timeline(
    lessons: List<ParsedLesson>,
    colors: AmberRoute1Colors,
    dayKey: Int?,
    clockMillis: Long,
    onLessonClick: (ParsedLesson) -> Unit
) {
    val sorted = remember(lessons) { lessons.sortedWith(compareBy<ParsedLesson> { it.number }.thenBy { it.time }) }
    val arrival = remember(sorted, dayKey, clockMillis) { routeArrivalFor(sorted, dayKey, clockMillis) }
    val finishedToday = remember(sorted, dayKey, clockMillis) { routeFinishedToday(sorted, dayKey, clockMillis) }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (finishedToday) {
            RouteArrivalPill("Сегодня занятия закончились", colors.amber, colors.text)
        }
        sorted.forEachIndexed { index, lesson ->
            AmberRoute1Station(lesson, colors, active = index == arrival?.index, arrivalLabel = arrival?.takeIf { it.index == index }?.label, onClick = { onLessonClick(lesson) })
        }
        if (sorted.isNotEmpty()) {
            AmberRoute1End(colors, sorted.last().time.substringAfter('–', "").trim())
        }
    }
}

@Composable
private fun AmberRoute1Station(lesson: ParsedLesson, colors: AmberRoute1Colors, active: Boolean, arrivalLabel: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Canvas(Modifier.fillMaxSize()) {
                val x = 24.dp.toPx()
                val y = size.height / 2f
                drawLine(colors.line.copy(alpha = .2f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 10.dp.toPx())
                drawLine(colors.line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                if (active) drawCircle(colors.amber.copy(alpha = .25f), radius = 32.dp.toPx(), center = Offset(x, y))
                drawCircle(colors.amber, radius = (if (active) 27.dp else 19.dp).toPx(), center = Offset(x, y))
                drawCircle(Color(0xFFFFE3A0), radius = (if (active) 27.dp else 19.dp).toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
            }
            Text(lesson.number.toString(), color = Color(0xFF251A0C),
                fontWeight = FontWeight.ExtraBold, fontSize = if (active) 22.sp else 19.sp,
                modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        }
        Surface(
            onClick = onClick,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp).height(IntrinsicSize.Min),
            shape = RoundedCornerShape(15.dp),
            color = colors.card
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (lesson.subgroups.isNotEmpty())
                        lesson.subgroups.sortedBy { it.number }
                            .joinToString(" / ") { "${it.number}: ${it.subject}" }
                    else lesson.subject.ifBlank { "Предмет уточняется" },
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                RouteArrivalPill(arrivalLabel, colors.amber, colors.text)
                if (lesson.time.isNotBlank()) {
                    Text(lesson.time, color = colors.text, style = MaterialTheme.typography.bodyMedium)
                }
                if (lesson.subgroups.isNotEmpty()) {
                    Text("Подгруппы: " + lesson.subgroups.sortedBy { it.number }
                        .joinToString(" · ") { "${it.number}: каб. ${it.room}" },
                        color = colors.muted, style = MaterialTheme.typography.bodySmall)
                    Text("Нажми для просмотра преподавателей",
                        color = colors.muted, style = MaterialTheme.typography.labelSmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(16.dp), tint = colors.muted)
                        Text(lesson.room.ifBlank { "Кабинет уточняется" },
                            color = colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (lesson.teacher.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.PersonOutline, null, modifier = Modifier.size(16.dp), tint = colors.muted)
                            Text(lesson.teacher, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmberRoute1End(colors: AmberRoute1Colors, time: String) {
    // Важно: линия и конечная плашка рисуются в ОДНОЙ системе координат.
    // В прежней версии Canvas был отдельним ребёнком Row, и на некоторых
    // устройствах визуально обрывался около левой кромки плашки.
    val badgeStart = 62.dp
    val trackCenter = 24.dp
    val finishShape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier.fillMaxWidth().drawWithContent {
            drawContent()
            val x = trackCenter.toPx()
            val destinationX = badgeStart.toPx()
            val y = size.height / 2f - 1.dp.toPx()
            val radius = minOf(20.dp.toPx(), y.coerceAtLeast(0f))
            val path = Path().apply {
                moveTo(x, 0f)
                lineTo(x, y - radius)
                quadraticBezierTo(x, y, x + radius, y)
                lineTo(destinationX, y)
            }
            // Мягкая подсветка + сплошная линия; конечная точка НА границе плашки.
            drawPath(path, colors.line.copy(alpha = .15f),
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, colors.line,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(colors.line, radius = 4.dp.toPx(), center = Offset(destinationX, y))
        }
    ) {
        Surface(
            color = colors.cardEnd,
            shape = finishShape,
            modifier = Modifier.fillMaxWidth()
                .padding(start = badgeStart, top = 6.dp, bottom = 8.dp)
                .border(1.5.dp, colors.amber, finishShape)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Outlined.Flag, null, tint = colors.amber,
                    modifier = Modifier.size(20.dp))
                Text("Конец занятий", color = colors.text,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                if (time.isNotBlank()) {
                    Text(time, color = colors.amber, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AmberRoute1EmptyDay(hasSavedDays: Boolean, colors: AmberRoute1Colors, onGoToSaved: () -> Unit) {
    Surface(color = colors.card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.amber,
                modifier = Modifier.size(28.dp))
            Text(if (hasSavedDays) "На эту дату записей нет" else "Расписание ещё не загружено",
                color = colors.text, style = MaterialTheme.typography.titleLarge)
            Text(if (hasSavedDays) "Данные могли ещё не опубликовать. Это не обязательно выходной."
                 else "Проверь обновления, чтобы получить занятия.",
                color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onGoToSaved) {
                Text(if (hasSavedDays) "К ближайшему дню с занятиями" else "Проверить обновления")
            }
        }
    }
}

@Composable
private fun AmberRoute1Sync(
    colors: AmberRoute1Colors,
    status: String,
    isUpdating: Boolean,
    lastCheckTime: Long?,
    onRefresh: () -> Unit
) {
    val isError = listOf("ошиб", "не удалось", "не найд", "нет сети").any { status.contains(it, ignoreCase = true) }
    val isSuccess = !isError && listOf("актуальн", "обновлен", "обновлён").any {
        status.contains(it, ignoreCase = true)
    }
    Surface(color = colors.chip.copy(alpha = .92f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (isUpdating) Icons.Outlined.Refresh
                else if (isError) Icons.Outlined.ErrorOutline
                else if (isSuccess) Icons.Outlined.CheckCircle
                else Icons.Outlined.Info,
                contentDescription = null, tint = if (isError) colors.warning else colors.amber,
                modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (isUpdating) "Проверяю расписание…" else status,
                    color = colors.text, style = MaterialTheme.typography.bodySmall)
                if (lastCheckTime != null) {
                    Text("Успешная проверка: " + SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru-RU"))
                        .format(Date(lastCheckTime)), color = colors.muted,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            if (isUpdating) {
                CircularProgressIndicator(Modifier.size(23.dp), color = colors.amber, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Проверить обновление",
                        tint = colors.amber)
                }
            }
        }
    }
}

@Composable
private fun AmberRoute1BottomNav(colors: AmberRoute1Colors, onGroupClick: () -> Unit, onSettings: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Surface(color = colors.chip, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AmberRoute1NavItem(Modifier.weight(1f), Icons.Outlined.CalendarMonth, "Расписание", colors,
                selected = true, onClick = {})
            AmberRoute1NavItem(Modifier.weight(1f), Icons.Outlined.Groups, "Группы", colors,
                selected = false, onClick = onGroupClick)
            AmberRoute1NavItem(Modifier.weight(1f), Icons.Outlined.Settings, "Настройки", colors,
                selected = false, onClick = onSettings)
        }
    }
}

@Composable
private fun AmberRoute1NavItem(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    colors: AmberRoute1Colors,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(if (selected) colors.amber.copy(alpha = .15f) else Color.Transparent,
                RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .height(55.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (selected) colors.amber else colors.muted, modifier = Modifier.size(23.dp))
        Text(text, color = if (selected) colors.amber else colors.muted,
            fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AmberRoute1DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    colors: AmberRoute1Colors
) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.amber)
        Text(text, color = colors.text, style = MaterialTheme.typography.bodyLarge)
    }
}
