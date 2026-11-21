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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme
import java.time.LocalTime
import java.util.Locale

/** Additional distinct 4.0 visual explorations. UI only: schedule data is read-only. */
internal object RemainingConcepts {
    val styles = setOf(
        InterfaceStyle.ORBIT_COSMOS, InterfaceStyle.STREAM_1, InterfaceStyle.COMPASS_1,
        InterfaceStyle.FOCUS_CORAL, InterfaceStyle.ORBIT_LIGHT, InterfaceStyle.LAYERS_PRISM,
        InterfaceStyle.ROUTE_CORAL, InterfaceStyle.ORBIT_ARC, InterfaceStyle.LAYERS_SPECTRUM,
        InterfaceStyle.ROUTE_CYAN
    )
    fun isSupported(style: String) = style in styles
}

private data class ConceptColors(
    val bg: Color, val surface: Color, val card: Color, val text: Color,
    val muted: Color, val accent: Color, val accentTwo: Color, val border: Color,
    val onAccent: Color
)

private fun paletteFor(style: String, dark: Boolean): ConceptColors {
    if (style == InterfaceStyle.STREAM_1 || style == InterfaceStyle.ROUTE_CORAL) {
        return if (dark) ConceptColors(
            Color(0xFF171628), Color(0xFF282637), Color(0xFF332E46), Color(0xFFF8F4FF),
            Color(0xFFBDB2D0), Color(0xFFFF8A78), Color(0xFFCDA7FF), Color(0xFF72647D), Color(0xFF1A1420)
        ) else ConceptColors(
            Color(0xFFFFFAFB), Color(0xFFFFFFFF), Color(0xFFFFF3F5), Color(0xFF25243C),
            Color(0xFF686777), Color(0xFFFF726A), Color(0xFFA87AEF), Color(0xFFF0DCE5), Color(0xFF291726)
        )
    }
    if (style == InterfaceStyle.ORBIT_COSMOS || style == InterfaceStyle.ROUTE_CYAN) {
        return if (dark) ConceptColors(
            Color(0xFF070F26), Color(0xFF172344), Color(0xFF1E2B50), Color(0xFFF6F9FF),
            Color(0xFFBBC7E3), Color(0xFF5FD7FA), Color(0xFFBA72FF), Color(0xFF5074BD), Color(0xFF08243B)
        ) else ConceptColors(
            Color(0xFFF0F8FF), Color.White, Color(0xFFE3F2FF), Color(0xFF18273C),
            Color(0xFF54718B), Color(0xFF0B8FB9), Color(0xFF7961D3), Color(0xFFBDD5E8), Color.White
        )
    }
    if (style == InterfaceStyle.FOCUS_CORAL) {
        return if (dark) ConceptColors(
            Color(0xFF0E121E), Color(0xFF1E2330), Color(0xFF262C3B), Color.White,
            Color(0xFFB8BDCC), Color(0xFFFF786D), Color(0xFFFFBA86), Color(0xFF526078), Color(0xFF261D22)
        ) else ConceptColors(
            Color(0xFFFFF9F7), Color.White, Color(0xFFFFF2EE), Color(0xFF222535),
            Color(0xFF656979), Color(0xFFE35850), Color(0xFFEFAD72), Color(0xFFF1CFCB), Color.White
        )
    }
    if (style == InterfaceStyle.COMPASS_1) {
        return if (dark) ConceptColors(
            Color(0xFF140A2B), Color(0xFF25163E), Color(0xFF322049), Color(0xFFF9F5FF),
            Color(0xFFC9B8E7), Color(0xFFB774FF), Color(0xFFFFBB88), Color(0xFF655085), Color(0xFF21122F)
        ) else ConceptColors(
            Color(0xFFF7F1FF), Color.White, Color(0xFFF3E8FF), Color(0xFF281C40),
            Color(0xFF766587), Color(0xFF8255CF), Color(0xFFE59B6B), Color(0xFFD7C5E9), Color.White
        )
    }
    return if (dark) ConceptColors(
        Color(0xFF160D30), Color(0xFF281D48), Color(0xFF362655), Color(0xFFFCF8FF),
        Color(0xFFD2C2E8), Color(0xFFA88AFF), Color(0xFF66DEEE), Color(0xFF715799), Color(0xFF21163A)
    ) else ConceptColors(
        Color(0xFFF7F1FF), Color.White, Color(0xFFF0E8FE), Color(0xFF261B3D),
        Color(0xFF6D6285), Color(0xFF7859C8), Color(0xFF30A0BD), Color(0xFFD8C7EE), Color.White
    )
}

private fun labelFor(style: String): String = when (style) {
    InterfaceStyle.ORBIT_COSMOS -> "Космическая дуга"
    InterfaceStyle.STREAM_1 -> "Хронолента"
    InterfaceStyle.COMPASS_1 -> "Компас дня"
    InterfaceStyle.FOCUS_CORAL -> "Импульс"
    InterfaceStyle.ORBIT_LIGHT -> "Орбита Лайт"
    InterfaceStyle.LAYERS_PRISM -> "Сияющие грани"
    InterfaceStyle.ROUTE_CORAL -> "Коралловая ветка"
    InterfaceStyle.ORBIT_ARC -> "Лунная дуга"
    InterfaceStyle.LAYERS_SPECTRUM -> "Спектр"
    else -> "Северное сияние"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleRemainingConceptUi(
    style: String, days: List<ParsedDay>, status: String, isUpdating: Boolean,
    lastCheckTime: Long?, onRefresh: () -> Unit, onSettings: () -> Unit,
    selectedGroup: String, onGroupClick: () -> Unit, groupWarning: String?,
    showDayStrip: Boolean
) {
    val dark = LocalAppDarkTheme.current
    val c = paletteFor(style, dark)
    val today = ScheduleDayStrip.todayKey()
    val preferred = ScheduleDayStrip.initialSelection(days.map { it.date }, today)
    var manual by rememberSaveable(selectedGroup) { mutableIntStateOf(0) }
    val currentKey = manual.takeIf { it != 0 } ?: preferred
    val sortedDays = remember(days) {
        days.sortedBy { ScheduleDayStrip.keyFromScheduleDate(it.date) ?: Int.MAX_VALUE }
    }
    val knownDates = remember(days) { days.mapNotNull { ScheduleDayStrip.keyFromScheduleDate(it.date) }.toSet() }
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    var opened by remember { mutableStateOf<ParsedLesson?>(null) }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        ConceptAmbient(style, c)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            LazyColumn(
                state = scroll, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp)
            ) {
                item(key = "new_brand") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("NNTC Schedule", color = c.text, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                            Text(labelFor(style) + " · Эксперимент", color = c.accent,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Surface(onClick = onSettings, color = c.surface.copy(alpha = .85f),
                            shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, c.border),
                            modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚙", fontSize = 23.sp, color = c.text)
                            }
                        }
                    }
                }
                item(key = "new_group") {
                    Surface(onClick = onGroupClick, color = c.surface.copy(alpha = .88f),
                        border = BorderStroke(1.dp, c.border), shape = RoundedCornerShape(19.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Группа $selectedGroup", color = c.text, modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("⌄", color = c.accent, fontSize = 22.sp)
                        }
                    }
                    if (!groupWarning.isNullOrBlank()) {
                        Text(groupWarning, color = c.accentTwo, fontSize = 12.sp)
                    }
                }
                item(key = "new_refresh") {
                    ExperimentalRefreshControl(status, isUpdating, lastCheckTime, onRefresh,
                        c.accent, c.onAccent, c.surface.copy(alpha = .85f), c.text, c.muted, c.border)
                }
                if (showDayStrip) {
                    item(key = "new_date") {
                        ConceptDateHeading(style, ScheduleDayStrip.displayDate(currentKey), c)
                    }
                    item(key = "new_week") {
                        ConceptWeek(style, currentKey, today, knownDates, c,
                            onDate = { manual = it }, onWeek = { manual = ScheduleDayStrip.shift(currentKey, it) })
                    }
                    item(key = "new_selected") {
                        val selectedDay = sortedDays.firstOrNull { ScheduleDayStrip.keyFromScheduleDate(it.date) == currentKey }
                        if (selectedDay == null || selectedDay.lessons.isEmpty()) {
                            ConceptEmpty(c, if (days.isEmpty()) "Расписание ещё не загружено" else "В этот день занятий нет")
                        } else {
                            ConceptLessons(style, selectedDay.lessons, currentKey == today, c) { opened = it }
                        }
                    }
                } else {
                    if (sortedDays.isEmpty()) item(key = "new_empty") { ConceptEmpty(c, "Расписание ещё не загружено") }
                    items(sortedDays, key = { "concept_day_${it.date}" }) { day ->
                        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            ConceptDateHeading(style, "${day.weekday.replaceFirstChar { char -> char.titlecase(Locale.forLanguageTag("ru-RU")) }}, ${day.date}", c)
                            if (day.lessons.isEmpty()) ConceptEmpty(c, "В этот день занятий нет")
                            else ConceptLessons(style, day.lessons,
                                ScheduleDayStrip.keyFromScheduleDate(day.date) == today, c) { opened = it }
                        }
                    }
                }
            }
            ExperimentalFloatingNavigation(
                onHome = { scope.launch { scroll.animateScrollToItem(0) } },
                onGroups = onGroupClick, onSettings = onSettings,
                surface = c.surface.copy(alpha = .93f), accent = c.accent,
                text = c.text, edge = c.border
            )
        }
    }
    opened?.let { lesson ->
        ModalBottomSheet(onDismissRequest = { opened = null }, containerColor = c.surface) {
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Пара ${lesson.number} · ${lesson.time}", color = c.accent,
                    style = MaterialTheme.typography.titleLarge)
                if (lesson.subgroups.isEmpty()) {
                    Text(lesson.subject, color = c.text, style = MaterialTheme.typography.titleLarge)
                    Text("Кабинет: ${lesson.room.ifBlank { "—" }}", color = c.muted)
                    Text("Преподаватель: ${lesson.teacher.ifBlank { "Не указан" }}", color = c.muted)
                } else {
                    lesson.subgroups.sortedBy { it.number }.forEach { sg ->
                        Text("${sg.number}-я подгруппа · ${sg.subject}", color = c.text,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Каб. ${sg.room} · ${sg.teacher}", color = c.muted)
                    }
                }
                Text("Группа: $selectedGroup", color = c.muted)
                TextButton(onClick = { opened = null }) { Text("Закрыть", color = c.accent) }
            }
        }
    }
}

@Composable
private fun ConceptAmbient(style: String, c: ConceptColors) {
    Canvas(Modifier.fillMaxSize()) {
        val x = size.width; val y = size.height
        drawCircle(brush = Brush.radialGradient(listOf(c.accent.copy(alpha = .21f), Color.Transparent),
            center = Offset(x * .94f, y * .19f), radius = x * .90f),
            radius = x * .90f, center = Offset(x * .94f, y * .19f))
        drawCircle(brush = Brush.radialGradient(listOf(c.accentTwo.copy(alpha = .17f), Color.Transparent),
            center = Offset(x * .10f, y * .87f), radius = x * .95f),
            radius = x * .95f, center = Offset(x * .10f, y * .87f))
        if (style == InterfaceStyle.ORBIT_COSMOS || style == InterfaceStyle.ORBIT_ARC) {
            repeat(3) { k ->
                drawArc(c.accent.copy(alpha = .16f + .04f * k), 200f, 135f, false,
                    topLeft = Offset(-x * (.55f + .10f * k), y * .10f),
                    size = Size(x * (1.5f + .20f * k), x * (1.3f + .18f * k)),
                    style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun ConceptDateHeading(style: String, date: String, c: ConceptColors) {
    if (style == InterfaceStyle.LAYERS_PRISM || style == InterfaceStyle.LAYERS_SPECTRUM) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
            Box(Modifier.fillMaxWidth().height(80.dp).padding(start = 14.dp, top = 8.dp)
                .graphicsLayer { rotationZ = -3f }
                .background(c.accentTwo.copy(alpha = .22f), RoundedCornerShape(22.dp))
                .border(1.dp, c.accentTwo.copy(alpha = .4f), RoundedCornerShape(22.dp)))
            Surface(color = c.surface.copy(alpha = .86f), shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, c.accent.copy(alpha = .65f)), modifier = Modifier.fillMaxWidth()) {
                Text(date, color = c.text, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                    modifier = Modifier.padding(20.dp))
            }
        }
    } else if (style == InterfaceStyle.COMPASS_1) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date, color = c.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Навигатор учебного дня", color = c.muted, fontSize = 12.sp)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, color = c.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (style == InterfaceStyle.ORBIT_COSMOS) Text("Ваша орбита занятий", color = c.accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ConceptWeek(
    style: String, selected: Int, today: Int, saved: Set<Int>, c: ConceptColors,
    onDate: (Int) -> Unit, onWeek: (Int) -> Unit
) {
    val week = ScheduleDayStrip.week(selected, today)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onWeek(-7) }, modifier = Modifier.sizeIn(minWidth = 36.dp, minHeight = 48.dp),
                contentPadding = PaddingValues(0.dp)) { Text("‹", color = c.text, fontSize = 26.sp) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day ->
                    val active = day.key == selected
                    val color = if (active) c.accent else c.surface.copy(alpha = .88f)
                    Surface(
                        onClick = { onDate(day.key) }, modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                        color = color, shape = if (style == InterfaceStyle.ORBIT_ARC) CircleShape else RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (active) c.accent else c.border.copy(alpha = .65f))
                    ) {
                        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(day.weekday, color = if (active) c.onAccent else c.muted, fontSize = 11.sp)
                            Text(day.key.rem(100).toString(), color = if (active) c.onAccent else c.text,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Box(Modifier.size(4.dp).background(if (day.key in saved) c.accentTwo else Color.Transparent, CircleShape))
                        }
                    }
                }
            }
            TextButton(onClick = { onWeek(7) }, modifier = Modifier.sizeIn(minWidth = 36.dp, minHeight = 48.dp),
                contentPadding = PaddingValues(0.dp)) { Text("›", color = c.text, fontSize = 26.sp) }
        }
        if (style == InterfaceStyle.ORBIT_COSMOS || style == InterfaceStyle.ORBIT_ARC) {
            Canvas(Modifier.fillMaxWidth().height(15.dp)) {
                drawArc(c.accent.copy(alpha = .5f), 185f, 170f, false,
                    topLeft = Offset(size.width * .08f, -size.height * 12f),
                    size = Size(size.width * .87f, size.height * 16f),
                    style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun ConceptEmpty(c: ConceptColors, text: String) {
    Surface(color = c.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, c.border)) {
        Text(text, color = c.text, modifier = Modifier.fillMaxWidth().padding(26.dp))
    }
}

private fun chooseHighlighted(lessons: List<ParsedLesson>, today: Boolean, clockMillis: Long): Int {
    if (lessons.isEmpty()) return -1
    if (!today) return 0
    val now = LocalTime.ofInstant(Instant.ofEpochMilli(clockMillis), ZoneId.systemDefault())
    val next = lessons.indexOfFirst { lesson ->
        val (start, end) = experimentalTimeRange(lesson.time)
        start != null && end != null && now.isBefore(end)
    }
    return if (next >= 0) next else lessons.lastIndex
}

@Composable
private fun ConceptLessons(style: String, lessons: List<ParsedLesson>, today: Boolean,
    c: ConceptColors, onOpen: (ParsedLesson) -> Unit
) {
    val clockMillis by produceState(initialValue = System.currentTimeMillis(), today) {
        if (today) while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    val focusIndex = chooseHighlighted(lessons, today, clockMillis)
    val finished = today && lessons.all { lesson ->
        val end = experimentalTimeRange(lesson.time).second
        end != null && LocalTime.ofInstant(Instant.ofEpochMilli(clockMillis), ZoneId.systemDefault()).isAfter(end)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    when (style) {
        InterfaceStyle.COMPASS_1 -> {
            CompassStage(lessons[focusIndex], today, finished, clockMillis, c) { onOpen(lessons[focusIndex]) }
            Text("Остальные занятия", color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            lessons.forEachIndexed { index, lesson ->
                if (index != focusIndex) ConceptLessonCard(lesson, index, style, c, false) { onOpen(lesson) }
            }
        }
        InterfaceStyle.FOCUS_CORAL -> {
            Text(if (finished) "Занятия сегодня завершены" else if (today) "В центре внимания" else "Первая пара дня", color = c.accent,
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
            ConceptLessonCard(lessons[focusIndex], focusIndex, style, c, true) { onOpen(lessons[focusIndex]) }
            Text("Остальные занятия", color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            lessons.forEachIndexed { index, lesson ->
                if (index != focusIndex) ConceptLessonCard(lesson, index, style, c, false) { onOpen(lesson) }
            }
        }
        InterfaceStyle.STREAM_1, InterfaceStyle.ROUTE_CORAL, InterfaceStyle.ROUTE_CYAN -> {
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.matchParentSize()) {
                    val routeX = 26.dp.toPx()
                    drawLine(c.accent.copy(alpha = .72f), Offset(routeX, 0f),
                        Offset(routeX, size.height), strokeWidth = 3.dp.toPx())
                    if (style == InterfaceStyle.ROUTE_CYAN) {
                        drawLine(c.accent.copy(alpha = .16f), Offset(routeX, 0f),
                            Offset(routeX, size.height), strokeWidth = 9.dp.toPx())
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    lessons.forEachIndexed { index, lesson ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            StationNumber(lesson.number, c, Modifier.width(53.dp), highlight = index == focusIndex)
                            Box(Modifier.weight(1f)) {
                                ConceptLessonCard(lesson, index, style, c, index == focusIndex && today) { onOpen(lesson) }
                            }
                        }
                    }
                    if (style != InterfaceStyle.STREAM_1) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.width(53.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(12.dp).background(c.accent, CircleShape))
                            }
                            Surface(color = c.surface, border = BorderStroke(1.dp, c.accent),
                                shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("⚑ Конец маршрута", color = c.text, fontSize = 12.sp)
                                    Text(lessons.last().time.substringAfterLast('–', ""), color = c.accent,
                                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (style == InterfaceStyle.ORBIT_COSMOS) ConceptCosmosStage(lessons, c)
                lessons.forEachIndexed { index, lesson ->
                    ConceptLessonCard(lesson, index, style, c, index == focusIndex && today) { onOpen(lesson) }
                }
            }
        }
    }
    }
}

@Composable
private fun ConceptCosmosStage(lessons: List<ParsedLesson>, c: ConceptColors) {
    Box(Modifier.fillMaxWidth().height(145.dp)
        .clip(RoundedCornerShape(25.dp))
        .background(Brush.verticalGradient(listOf(c.surface.copy(alpha = .7f), c.bg.copy(alpha = .8f))))) {
        Canvas(Modifier.matchParentSize()) {
            repeat(3) { n ->
                drawArc(c.accent.copy(alpha = .45f - n * .10f), 193f, 162f, false,
                    topLeft = Offset(size.width * (.10f - n * .13f), size.height * (-.95f - n * .25f)),
                    size = Size(size.width * (.83f + .27f * n), size.height * (2.85f + n * .5f)),
                    style = Stroke((2.5f - .4f * n).dp.toPx()))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Орбита учебного дня", color = c.text,
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("${lessons.size} пар(ы) · окончание ${lessons.last().time.substringAfterLast('–', "—")}",
                color = c.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StationNumber(num: Int, c: ConceptColors, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Box(modifier.heightIn(min = 50.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = if (highlight) c.accent else c.surface,
            border = BorderStroke(2.dp, if (highlight) c.accentTwo else c.accent),
            modifier = Modifier.size(if (highlight) 47.dp else 38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(num.toString(), color = if (highlight) c.onAccent else c.text,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun CompassStage(lesson: ParsedLesson, today: Boolean, finished: Boolean,
    clockMillis: Long, c: ConceptColors, onOpen: () -> Unit
) {
    val (start, end) = experimentalTimeRange(lesson.time)
    val now = LocalTime.ofInstant(Instant.ofEpochMilli(clockMillis), ZoneId.systemDefault())
    val until = if (today && start != null && now.isBefore(start))
        java.time.Duration.between(now, start).toMinutes() + 1L else null
    val stateText = when {
        !today -> "Выбранная пара"
        finished -> "Занятия завершены"
        start != null && end != null && !now.isBefore(start) && now.isBefore(end) -> "Идёт сейчас"
        until != null && until >= 60L -> "Через ${until / 60} ч ${until % 60} мин"
        until != null -> "Через $until мин"
        else -> "Ближайшая пара"
    }
    Surface(onClick = onOpen, color = c.surface.copy(alpha = .65f), shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, c.border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(188.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(c.accent.copy(alpha = .36f), 115f, 310f, false,
                        topLeft = Offset(9.dp.toPx(),9.dp.toPx()),
                        size = Size(size.width-18.dp.toPx(),size.height-18.dp.toPx()),
                        style = Stroke(width=10.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round))
                    drawArc(c.accent, 115f, 210f, false,
                        topLeft = Offset(9.dp.toPx(),9.dp.toPx()),
                        size = Size(size.width-18.dp.toPx(),size.height-18.dp.toPx()),
                        style = Stroke(width=10.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stateText, color = c.muted, fontSize = 12.sp)
                    Text(lesson.subject.ifBlank { "Занятие" }, color = c.text, fontWeight = FontWeight.Bold,
                        fontSize = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (start != null) "в ${start}" else lesson.time, color = c.accent,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Text("${lesson.number} пара · ${lesson.time} · Каб. ${lesson.room}", color = c.text, fontSize = 13.sp)
            if (lesson.subgroups.isNotEmpty()) Text("Две подгруппы · Подробнее ›", color = c.accent, fontSize = 12.sp)
            else Text(lesson.teacher, color = c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ConceptLessonCard(
    lesson: ParsedLesson, index: Int, style: String, c: ConceptColors,
    hero: Boolean, onOpen: () -> Unit
) {
    val glass = style == InterfaceStyle.LAYERS_PRISM || style == InterfaceStyle.LAYERS_SPECTRUM
    val orbital = style == InterfaceStyle.ORBIT_LIGHT || style == InterfaceStyle.ORBIT_ARC
    val rounded = RoundedCornerShape(if (glass) 23.dp else 19.dp)
    Box(Modifier.fillMaxWidth().padding(start = if (orbital) 6.dp else 0.dp,
        top = if (glass) 7.dp else 0.dp, bottom = if (glass) 6.dp else 0.dp)) {
        if (glass) {
            Box(Modifier.matchParentSize().padding(start = 14.dp, end = 7.dp, top = 0.dp, bottom = 0.dp)
                .graphicsLayer { rotationZ = if (style == InterfaceStyle.LAYERS_PRISM) { if (index % 2 == 0) -4f else 3f } else 2f }
                .shadow(9.dp, rounded)
                .background(c.accentTwo.copy(alpha = .36f), rounded)
                .border(1.dp, c.accentTwo.copy(alpha = .85f), rounded))
            Box(Modifier.matchParentSize().padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 0.dp)
                .graphicsLayer { rotationZ = if (index % 2 == 0) 2f else -2f }
                .background(c.accent.copy(alpha = .19f), rounded)
                .border(1.dp, c.accent.copy(alpha = .65f), rounded))
        }
        if (orbital) {
            Canvas(Modifier.matchParentSize()) {
                drawArc(c.accent.copy(alpha = .62f), 125f, 190f, false,
                    topLeft = Offset(-44.dp.toPx(), -7.dp.toPx()),
                    size = Size(86.dp.toPx(),size.height+14.dp.toPx()),
                    style = Stroke(1.7.dp.toPx()))
            }
        }
        Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = if (hero) 166.dp else 99.dp)
            .padding(start = if (orbital) 23.dp else 0.dp),
            color = if (hero) c.card else c.surface.copy(alpha = if (glass) .71f else .94f),
            shape = rounded, border = BorderStroke(if (hero) 2.dp else 1.dp,
                if (hero) c.accentTwo else if (glass) c.accent.copy(alpha=.75f) else c.border)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = if (hero) 21.dp else 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (style != InterfaceStyle.STREAM_1 && style != InterfaceStyle.ROUTE_CORAL && style != InterfaceStyle.ROUTE_CYAN) {
                    Surface(color = if (hero) c.accent else c.card, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(if (hero) 55.dp else 46.dp),
                        border = BorderStroke(1.dp, c.accent.copy(alpha=.60f))) {
                        Box(contentAlignment=Alignment.Center) {
                            Text(lesson.number.toString(), color=if (hero) c.onAccent else c.text,
                                fontSize=if(hero) 23.sp else 19.sp, fontWeight=FontWeight.Bold)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (hero && style == InterfaceStyle.FOCUS_CORAL) Text("ГЛАВНАЯ ПАРА", color=c.accent,
                        fontSize=12.sp, fontWeight=FontWeight.Bold)
                    Text(lesson.subject.ifBlank { "Предмет уточняется" }, color = c.text,
                        fontWeight = FontWeight.Bold, fontSize = if (hero) 21.sp else 16.sp,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(lesson.time.ifBlank { "Время уточняется" }, color = if (hero) c.accentTwo else c.accent,
                        fontWeight = FontWeight.SemiBold, fontSize = if (hero) 18.sp else 13.sp)
                    if (lesson.subgroups.isNotEmpty()) {
                        lesson.subgroups.sortedBy { it.number }.forEach { sg ->
                            Text("${sg.number}-я: ${sg.subject} · Каб. ${sg.room} · ${sg.teacher}",
                                color = c.muted, fontSize = 12.sp, maxLines = 3)
                        }
                    } else {
                        Text("Каб. ${lesson.room.ifBlank { "—" }} · ${lesson.teacher.ifBlank { "Не указан" }}",
                            color = c.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!hero) Text("›", color = c.accent, fontSize = 25.sp)
            }
        }
    }
}
