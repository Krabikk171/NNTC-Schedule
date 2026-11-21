package ru.krabikk.nntcschedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

/**
 * Изолированный UI-эксперимент «Маршрут». Данные передаются из существующего экрана.
 * Не обращается к Room, PDF, CSV, WorkManager, сети или настройкам напрямую.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleRoute2Ui(
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
    val colors = route2Colors()
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
            item(key = "route2_brand") { Route2Brand(colors) }
            item(key = "route2_group") {
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
                            Icon(Icons.Outlined.Groups, null, tint = colors.pink, modifier = Modifier.size(21.dp))
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
            item(key = "route2_primary_refresh") {
                ExperimentalRefreshControl(
                    status = status, isUpdating = isUpdating,
                    lastCheckTime = lastCheckTime, onRefresh = onRefresh,
                    accent = colors.pink,
                    buttonInk = if (LocalAppDarkTheme.current) Color(0xFF11271D) else Color.White,
                    surface = colors.chip.copy(alpha = .78f),
                    text = colors.text, muted = colors.muted, edge = colors.line
                )
            }
            if (showDayStrip) {
                item(key = "route2_date_title") {
                    Route2DateTitle(selectedKey, colors)
                }
                item(key = "route2_selected_day") {
                    Column {
                        Route2DayPicker(
                            selectedKey = selectedKey,
                            todayKey = todayKey,
                            savedKeys = savedKeys.toSet(),
                            colors = colors,
                            onSelect = { manualKey = it }
                        )
                        AnimatedContent(
                            targetState = selectedKey,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "route2SelectedDay"
                        ) { key ->
                            val chosen = days.firstOrNull { ScheduleDayStrip.keyFromScheduleDate(it.date) == key }
                            if (chosen != null && chosen.lessons.isNotEmpty()) {
                                Route2Timeline(chosen.lessons, colors, dayKey = key, clockMillis = clockMillis, onLessonClick = { selectedLesson = it })
                            } else {
                                Route2EmptyDay(
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
                    item(key = "route2_no_data") {
                        Route2EmptyDay(false, colors, onGoToSaved = onRefresh)
                    }
                } else {
                    items(sortedDays, key = { "route2_${it.date}" }) { day ->
                        // No day strip: every day becomes the starting station of its own route.
                        // Keep the header and its first lesson in one zero-gap column.
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Route2DaySectionHeader(day, colors)
                            if (day.lessons.isNotEmpty()) {
                                Route2Timeline(day.lessons, colors, dayKey = ScheduleDayStrip.keyFromScheduleDate(day.date), clockMillis = clockMillis, onLessonClick = { selectedLesson = it })
                            } else {
                                Route2EmptyDay(true, colors, onGoToSaved = onRefresh)
                            }
                        }
                    }
                }
            }
        }
        Route2BottomNav(colors, onGroupClick, onSettings)
    }
    selectedLesson?.let { lesson ->
        ModalBottomSheet(onDismissRequest = { selectedLesson = null }, containerColor = colors.card) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("${lesson.number}-я пара", color = colors.pink, style = MaterialTheme.typography.labelLarge)
                if (lesson.subgroups.isNotEmpty()) {
                    Text("Занятия по подгруппам", color = colors.text,
                        style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.time.ifBlank { "Время уточняется" }, color = colors.text,
                        style = MaterialTheme.typography.titleLarge)
                    lesson.subgroups.sortedBy { it.number }.forEach { subgroup ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${subgroup.number}-я подгруппа · ${subgroup.subject}",
                                color = colors.text, style = MaterialTheme.typography.titleMedium)
                            Route2DetailRow(Icons.Outlined.LocationOn,
                                "Кабинет: ${subgroup.room}", colors)
                            Route2DetailRow(Icons.Outlined.PersonOutline,
                                "Преподаватель: ${subgroup.teacher}", colors)
                        }
                    }
                } else {
                    Text(lesson.subject, color = colors.text, style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.time.ifBlank { "Время уточняется" }, color = colors.text,
                        style = MaterialTheme.typography.titleLarge)
                    if (lesson.room.isNotBlank()) {
                        Route2DetailRow(Icons.Outlined.LocationOn, "Кабинет: ${lesson.room}", colors)
                    }
                    if (lesson.teacher.isNotBlank()) {
                        Route2DetailRow(Icons.Outlined.PersonOutline,
                            "Преподаватель: ${lesson.teacher}", colors)
                    }
                }
                Text("Группа: $selectedGroup", color = colors.muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { selectedLesson = null }) { Text("Закрыть") }
            }
        }
    }
}

private data class Route2Colors(
    val pink: Color,
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
private fun route2Colors(): Route2Colors {
    return if (LocalAppDarkTheme.current) Route2Colors(
        pink = Color(0xFF69E1A1),
        background = Color(0xFF0B1B19),
        card = Color(0xFF17332D),
        cardEnd = Color(0xFF102922),
        chip = Color(0xFF173C31),
        line = Color(0xFF69E1A1),
        text = Color(0xFFF0FFF6),
        muted = Color(0xFFB6D5C7),
        warning = Color(0xFFFFC37B)
    ) else Route2Colors(
        pink = Color(0xFF16784A),
        background = Color(0xFFF6FAF7),
        card = Color(0xFFFFFFFF),
        cardEnd = Color(0xFFEAF5EE),
        chip = Color(0xFFE8F4EB),
        line = Color(0xFF21965D),
        text = Color(0xFF172D24),
        muted = Color(0xFF587264),
        warning = Color(0xFF9D5100)
    )
}

@Composable
private fun Route2Brand(colors: Route2Colors) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // An actual metro line with two nodes — also a distinctive app symbol.
        Canvas(Modifier.size(34.dp)) {
            val unit = size.width / 34f
            drawLine(colors.pink, Offset(7f * unit, 6f * unit),
                Offset(25f * unit, 28f * unit), strokeWidth = 3.3f * unit, cap = StrokeCap.Round)
            drawCircle(colors.background, radius = 6.5f * unit, center = Offset(7f * unit, 6f * unit))
            drawCircle(colors.background, radius = 6.5f * unit, center = Offset(25f * unit, 28f * unit))
            drawCircle(colors.pink, radius = 5f * unit, center = Offset(7f * unit, 6f * unit), style = Stroke(2.8f * unit))
            drawCircle(colors.pink, radius = 5f * unit, center = Offset(25f * unit, 28f * unit), style = Stroke(2.8f * unit))
        }
        Column {
            Text("NNTC", color = colors.text, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text("Schedule · Зелёная ветка", color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Route2DateTitle(selectedKey: Int, colors: Route2Colors) {
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
private fun Route2DayPicker(
    selectedKey: Int,
    todayKey: Int,
    savedKeys: Set<Int>,
    colors: Route2Colors,
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
                    color = if (selected) colors.pink else colors.chip
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(day.weekday, fontSize = 13.sp,
                            color = if (selected) colors.background else colors.text,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        Text(day.dayAndMonth.substringBefore(' '), fontSize = 16.sp,
                            color = if (selected) colors.background else colors.muted,
                            fontWeight = FontWeight.Bold)
                        if (!selected && savedKeys.contains(day.key)) {
                            Box(Modifier.size(3.dp).background(colors.pink, CircleShape))
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
            drawPath(path, colors.pink.copy(alpha = .15f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, colors.line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/**
 * Day-strip-free view: the day header is also the start of the metro line.
 * The first station follows immediately underneath, with the same 24.dp track axis.
 */
@Composable
private fun Route2DaySectionHeader(day: ParsedDay, colors: Route2Colors) {
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
            drawCircle(colors.pink, radius = 2.dp.toPx(), center = Offset(trackX, markerY))
        }
        val headerShape = RoundedCornerShape(18.dp)
        Surface(
            modifier = Modifier.weight(1f)
                .padding(top = 8.dp, bottom = 8.dp)
                .border(1.5.dp, colors.pink, headerShape),
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
private fun Route2Timeline(
    lessons: List<ParsedLesson>,
    colors: Route2Colors,
    dayKey: Int?,
    clockMillis: Long,
    onLessonClick: (ParsedLesson) -> Unit
) {
    val sorted = remember(lessons) {
        lessons.sortedWith(compareBy<ParsedLesson> { it.number }.thenBy { it.time })
    }
    val arrival = remember(sorted, dayKey, clockMillis) { routeArrivalFor(sorted, dayKey, clockMillis) }
    val finishedToday = remember(sorted, dayKey, clockMillis) { routeFinishedToday(sorted, dayKey, clockMillis) }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (finishedToday) {
            RouteArrivalPill("Сегодня занятия закончились", colors.pink, colors.text)
        }
        sorted.forEachIndexed { index, lesson ->
            Route2Station(lesson, colors, active = index == arrival?.index, arrivalLabel = arrival?.takeIf { it.index == index }?.label, onClick = { onLessonClick(lesson) })
            if (index < sorted.lastIndex) {
                val gap = route2BreakMinutes(lesson.time, sorted[index + 1].time)
                if (gap != null) Route2BreakStop(gap, colors)
            }
        }
        if (sorted.isNotEmpty()) {
            Route2End(colors, sorted.last().time.substringAfter('–', "").trim())
        }
    }
}

/** Delay between stations calculated solely from the visible lesson time strings. */
internal fun route2BreakMinutes(first: String, next: String): Int? {
    val re = Regex("""\d{1,2}:\d{2}""")
    val end = re.findAll(first).lastOrNull()?.value ?: return null
    val start = re.find(next)?.value ?: return null
    fun minutes(value: String): Int? {
        val parts = value.split(':')
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
    val diff = (minutes(start) ?: return null) - (minutes(end) ?: return null)
    return diff.takeIf { it in 1..180 }
}

@Composable
private fun Route2BreakStop(minutes: Int, colors: Route2Colors) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(48.dp).fillMaxHeight()) {
            val x = 24.dp.toPx()
            drawLine(colors.line.copy(alpha = .72f), Offset(x, 0f), Offset(x, size.height),
                strokeWidth = 3.dp.toPx())
            drawCircle(colors.background, radius = 5.dp.toPx(), center = Offset(x, size.height / 2f))
            drawCircle(colors.line, radius = 3.dp.toPx(), center = Offset(x, size.height / 2f))
        }
        Text("Перерыв · $minutes мин", color = colors.muted,
            modifier = Modifier.padding(start = 14.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Route2Station(lesson: ParsedLesson, colors: Route2Colors, active: Boolean, arrivalLabel: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(56.dp).fillMaxHeight()) {
            val x = 24.dp.toPx()
            val y = size.height / 2f
            drawLine(colors.line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            if (active) drawCircle(colors.pink.copy(alpha = .17f), radius = 33.dp.toPx(), center = Offset(x, y))
            drawCircle(colors.pink, radius = (if (active) 27.dp else 20.dp).toPx(), center = Offset(x, y))
            drawCircle(colors.pink, radius = 20.dp.toPx(), center = Offset(x, y),
                style = Stroke(width = 3.dp.toPx()))
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(8, 43, 29)
                    textSize = (if (active) 21.sp else 16.sp).toPx()
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(lesson.number.toString(), x, y - (paint.ascent() + paint.descent()) / 2f, paint)
            }
        }
        Surface(
            onClick = onClick,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp).height(IntrinsicSize.Min),
            shape = RoundedCornerShape(17.dp),
            color = colors.cardEnd,
            border = BorderStroke(1.dp, colors.line.copy(alpha = .24f))
        ) {
            Column(Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(colors.card, colors.cardEnd)))
                .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (lesson.subgroups.isNotEmpty())
                        lesson.subgroups.sortedBy { it.number }
                            .joinToString(" / ") { "${it.number}: ${it.subject}" }
                    else lesson.subject.ifBlank { "Предмет уточняется" },
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                RouteArrivalPill(arrivalLabel, colors.pink, colors.text)
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
private fun Route2End(colors: Route2Colors, time: String) {
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
                .border(1.5.dp, colors.pink, finishShape)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Outlined.Flag, null, tint = colors.pink,
                    modifier = Modifier.size(20.dp))
                Text("Конец маршрута", color = colors.text,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                if (time.isNotBlank()) {
                    Text(time, color = colors.pink, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Route2EmptyDay(hasSavedDays: Boolean, colors: Route2Colors, onGoToSaved: () -> Unit) {
    Surface(color = colors.card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.pink,
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
private fun Route2Sync(
    colors: Route2Colors,
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
                contentDescription = null, tint = if (isError) colors.warning else colors.pink,
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
                CircularProgressIndicator(Modifier.size(23.dp), color = colors.pink, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Проверить обновление",
                        tint = colors.pink)
                }
            }
        }
    }
}

@Composable
private fun Route2BottomNav(colors: Route2Colors, onGroupClick: () -> Unit, onSettings: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Surface(color = colors.chip, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Route2NavItem(Modifier.weight(1f), Icons.Outlined.CalendarMonth, "Расписание", colors,
                selected = true, onClick = {})
            Route2NavItem(Modifier.weight(1f), Icons.Outlined.Groups, "Группы", colors,
                selected = false, onClick = onGroupClick)
            Route2NavItem(Modifier.weight(1f), Icons.Outlined.Settings, "Настройки", colors,
                selected = false, onClick = onSettings)
        }
    }
}

@Composable
private fun Route2NavItem(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    colors: Route2Colors,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(if (selected) colors.pink.copy(alpha = .15f) else Color.Transparent,
                RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .height(55.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (selected) colors.pink else colors.muted, modifier = Modifier.size(23.dp))
        Text(text, color = if (selected) colors.pink else colors.muted,
            fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Route2DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    colors: Route2Colors
) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.pink)
        Text(text, color = colors.text, style = MaterialTheme.typography.bodyLarge)
    }
}
