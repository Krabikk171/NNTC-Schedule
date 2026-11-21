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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
 * «Слои» — третий экспериментальный Compose-экран.
 * Только получает существующие UI-данные и делегирует действия наверх.
 * Не обращается к парсерам, сети, Room, DataStore или WorkManager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleLayersUi(
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
    val c = layersColors()
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

    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(c.background, c.backgroundDeep, c.backgroundBottom))
    )) {
        // Мягкие цветовые рефлексы позади интерфейса. Это НЕ blur текста.
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
            // Две стеклянные дуги создают ощущение глубины как на референсе.
            val waveTop = Path().apply {
                moveTo(size.width * .44f, -size.height * .09f)
                cubicTo(size.width * 1.24f, size.height * .10f,
                    size.width * .38f, size.height * .33f,
                    size.width * 1.16f, size.height * .51f)
            }
            drawPath(
                path = waveTop,
                brush = Brush.linearGradient(listOf(c.aqua.copy(alpha = .17f), c.violet.copy(alpha = .04f))),
                style = Stroke(width = 40.dp.toPx())
            )
            val waveBottom = Path().apply {
                moveTo(-size.width * .18f, size.height * .69f)
                cubicTo(size.width * .50f, size.height * .73f,
                    size.width * .13f, size.height * .96f,
                    size.width * 1.12f, size.height * 1.06f)
            }
            drawPath(
                path = waveBottom,
                brush = Brush.linearGradient(listOf(c.violet.copy(alpha = .18f), c.aqua.copy(alpha = .04f))),
                style = Stroke(width = 64.dp.toPx())
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 15.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "layers_header") { LayersHeader(c) }
                item(key = "layers_group") {
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
                if (showDayStrip) {
                    item(key = "layers_date") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LayersDateBar(selectedKey, c,
                                onPrev = { userKey = ScheduleDayStrip.shift(selectedKey, -7) },
                                onNext = { userKey = ScheduleDayStrip.shift(selectedKey, 7) })
                            LayersWeek(selectedKey, todayKey, saved.toSet(), c) { userKey = it }
                        }
                    }
                    item(key = "layers_selected_day") {
                        AnimatedContent(
                            targetState = selectedKey,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "layersDayTransition"
                        ) { key ->
                            val chosen = days.firstOrNull { ScheduleDayStrip.keyFromScheduleDate(it.date) == key }
                            if (chosen?.lessons.isNullOrEmpty()) {
                                LayersEmpty(c, hasSaved = days.isNotEmpty()) { userKey = preferred }
                            } else {
                                LayersLessonList(chosen!!.lessons, c) { detail = it }
                            }
                        }
                    }
                } else {
                    if (ordered.isEmpty()) {
                        item(key = "layers_nodata") { LayersEmpty(c, hasSaved = false, onClick = onRefresh) }
                    } else {
                        items(ordered, key = { "layers_${it.date}" }) { day ->
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                LayersDayTitle(day, c)
                                if (day.lessons.isEmpty()) LayersEmpty(c, hasSaved = true, onClick = onRefresh)
                                else LayersLessonList(day.lessons, c) { detail = it }
                            }
                        }
                    }
                }
                item(key = "layers_sync") { LayersSync(c, status, isUpdating, lastCheckTime, onRefresh) }
            }
            LayersBottomNav(c,
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
                            LayersDetailRow(Icons.Outlined.LocationOn, "Кабинет: ${sg.room}", c)
                            LayersDetailRow(Icons.Outlined.PersonOutline, "Преподаватель: ${sg.teacher}", c)
                        }
                    }
                } else {
                    Text(lesson.subject.ifBlank { "Название уточняется" },
                        style = MaterialTheme.typography.headlineSmall, color = c.text)
                    if (lesson.room.isNotBlank()) LayersDetailRow(Icons.Outlined.LocationOn, "Кабинет: ${lesson.room}", c)
                    if (lesson.teacher.isNotBlank()) LayersDetailRow(Icons.Outlined.PersonOutline, "Преподаватель: ${lesson.teacher}", c)
                }
                Text("Группа: $selectedGroup", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { detail = null }) { Text("Закрыть", color = c.aqua) }
            }
        }
    }
}

private data class LayersColors(
    val background: Color, val backgroundDeep: Color, val backgroundBottom: Color,
    val sheet: Color, val front: Color, val chip: Color,
    val text: Color, val muted: Color, val aqua: Color, val violet: Color,
    val pink: Color, val warning: Color, val edge: Color,
    val backA: Color, val backB: Color, val glowA: Color, val glowB: Color,
    val glassTop: Color, val glassBottom: Color, val specular: Color
)

@Composable
private fun layersColors(): LayersColors = if (LocalAppDarkTheme.current) LayersColors(
    background = Color(0xFF081728), backgroundDeep = Color(0xFF08233A),
    backgroundBottom = Color(0xFF1B1640), sheet = Color(0xE0122740),
    front = Color(0xC01E4D6C), chip = Color(0x9B1B4765),
    text = Color(0xFFF8FBFF), muted = Color(0xFFDBE7F7),
    aqua = Color(0xFF81EAFF), violet = Color(0xFFC39CFF), pink = Color(0xFFFC93E2),
    warning = Color(0xFFFFCD85), edge = Color(0xFF73CBF0),
    backA = Color(0xFF23CFE8), backB = Color(0xFFAF60F4),
    glowA = Color(0x8856C5FF), glowB = Color(0x666A4BDE),
    glassTop = Color(0xB12B7691), glassBottom = Color(0xBF2A1C61),
    specular = Color(0xFFCBFAFF)
) else LayersColors(
    background = Color(0xFFF4F9FF), backgroundDeep = Color(0xFFE9F6FD),
    backgroundBottom = Color(0xFFF3EAFE), sheet = Color(0xEDFFFFFF),
    front = Color(0xDDF9FDFF), chip = Color(0xD3E0F1FA),
    text = Color(0xFF142744), muted = Color(0xFF354C66),
    aqua = Color(0xFF007F9D), violet = Color(0xFF6943B2), pink = Color(0xFFA6378E),
    warning = Color(0xFF975300), edge = Color(0xFF70B5D0),
    backA = Color(0xFF7BCEE8), backB = Color(0xFFC3A5FF),
    glowA = Color(0x8895DAEF), glowB = Color(0x77D7B3FE),
    glassTop = Color(0xE3F4FDFF), glassBottom = Color(0xD2EAE0FF),
    specular = Color(0xFFFFFFFF)
)

@Composable
private fun LayersHeader(c: LayersColors) {
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
            Text("СЛОИ · экспериментальный дизайн", fontSize = 11.sp, color = c.muted)
        }
    }
}

@Composable
private fun LayersDateBar(key: Int, c: LayersColors, onPrev: () -> Unit, onNext: () -> Unit) {
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
private fun LayersWeek(selected: Int, today: Int, saved: Set<Int>, c: LayersColors, onSelect: (Int) -> Unit) {
    val week = ScheduleDayStrip.week(selected, today)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.forEach { d ->
            val active = d.key == selected
            val color = if (active) c.aqua else c.muted
            Surface(onClick = { onSelect(d.key) }, modifier = Modifier.weight(1f).height(70.dp),
                shape = RoundedCornerShape(17.dp),
                color = if (active) c.aqua.copy(alpha = if (LocalAppDarkTheme.current) .30f else .20f)
                    else c.front.copy(alpha = .72f),
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
private fun LayersDayTitle(day: ParsedDay, c: LayersColors) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(day.weekday.uppercase(Locale.forLanguageTag("ru-RU")), fontWeight = FontWeight.Bold,
            fontSize = 21.sp, color = c.text)
        Text(day.date, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

@Composable
private fun LayersLessonList(lessons: List<ParsedLesson>, c: LayersColors, onClick: (ParsedLesson) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        lessons.forEachIndexed { index, lesson ->
            LayersLessonCard(lesson, index, c, onClick = { onClick(lesson) })
        }
    }
}

@Composable
private fun LayersLessonCard(lesson: ParsedLesson, index: Int, c: LayersColors, onClick: () -> Unit) {
    val dark = LocalAppDarkTheme.current
    val accent = when (index % 3) { 0 -> c.aqua; 1 -> c.violet; else -> c.pink }
    val paneShape = RoundedCornerShape(24.dp)
    val brightRim = Brush.linearGradient(listOf(
        c.specular.copy(alpha = if (dark) .9f else .85f),
        accent.copy(alpha = .95f),
        c.violet.copy(alpha = .68f),
        c.specular.copy(alpha = .55f)
    ))
    val glassSurface = Brush.linearGradient(
        0.0f to c.glassTop,
        .46f to c.front,
        1.0f to c.glassBottom
    )
    // Стеклянные листы: каждый на своей глубине. Они реально полупрозрачные;
    // блики рисуются позади текста, а большая контрастность сохраняется спереди.
    Box(Modifier.fillMaxWidth().padding(start = 9.dp, end = 12.dp, top = 20.dp, bottom = 19.dp)) {
        Box(Modifier.matchParentSize()
            .offset(x = 11.dp, y = (-14).dp)
            .graphicsLayer { rotationZ = -2.4f }
            .background(
                Brush.linearGradient(listOf(
                    c.backB.copy(alpha = if (dark) .48f else .32f),
                    c.backA.copy(alpha = if (dark) .28f else .25f),
                    c.backB.copy(alpha = if (dark) .65f else .48f)
                )), paneShape
            )
            .border(1.dp, Brush.linearGradient(listOf(
                c.violet.copy(alpha = .93f), c.specular.copy(alpha = .35f), c.pink.copy(alpha = .62f)
            )), paneShape)
        )
        Box(Modifier.matchParentSize()
            .offset(x = (-8).dp, y = 11.dp)
            .graphicsLayer { rotationZ = 1.9f }
            .background(
                Brush.linearGradient(listOf(
                    c.backA.copy(alpha = if (dark) .57f else .33f),
                    c.backB.copy(alpha = if (dark) .43f else .35f)
                )), paneShape
            )
            .border(1.3.dp, Brush.linearGradient(listOf(
                c.aqua.copy(alpha = .88f), c.specular.copy(alpha = .4f), c.violet.copy(alpha = .68f)
            )), paneShape)
        )

        Box(Modifier.fillMaxWidth()
            .shadow(elevation = if (dark) 11.dp else 6.dp, shape = paneShape)
            .clip(paneShape)
            .background(glassSurface)
            .border(1.25.dp, brightRim, paneShape)
            .clickable(onClick = onClick)
        ) {
            // Световая диагональ и контровой рефлекс — на слое фона,
            // поэтому белый блик НЕ перекрывает названия и время.
            Canvas(Modifier.matchParentSize()) {
                val shard = Path().apply {
                    moveTo(size.width * .24f, 0f)
                    lineTo(size.width * .71f, 0f)
                    lineTo(size.width * .26f, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(shard, Brush.linearGradient(listOf(
                    c.specular.copy(alpha = if (dark) .09f else .30f),
                    c.specular.copy(alpha = .015f)
                )))
                drawCircle(
                    brush = Brush.radialGradient(listOf(
                        accent.copy(alpha = if (dark) .19f else .11f), Color.Transparent
                    ), center = Offset(size.width * .91f, size.height * 1.04f),
                        radius = size.width * .73f),
                    center = Offset(size.width * .91f, size.height * 1.04f),
                    radius = size.width * .73f
                )
            }
            Column(Modifier.padding(horizontal = 13.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(46.dp)
                        .background(Brush.linearGradient(listOf(
                            accent.copy(alpha = if (dark) .70f else .42f),
                            c.backA.copy(alpha = if (dark) .64f else .43f)
                        )), RoundedCornerShape(14.dp))
                        .border(1.dp, c.specular.copy(alpha = .52f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(lesson.number.toString(), color = c.text, fontSize = 25.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(lesson.time.ifBlank { "Время уточняется" }, color = c.text,
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (lesson.subgroups.isNotEmpty()) {
                            Text("Занятия по подгруппам", color = c.text,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        } else {
                            Text(lesson.subject.ifBlank { "Название уточняется" }, color = c.text,
                                fontWeight = FontWeight.Bold, fontSize = 17.sp,
                                maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (lesson.subgroups.isEmpty()) {
                        Column(horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.widthIn(max = 117.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Outlined.LocationOn, null, tint = c.muted,
                                    modifier = Modifier.size(15.dp))
                                Text("Каб. ${lesson.room.ifBlank { "—" }}", color = c.muted,
                                    fontSize = 12.sp, maxLines = 2)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Outlined.PersonOutline, null, tint = c.muted,
                                    modifier = Modifier.size(15.dp))
                                Text(lesson.teacher.ifBlank { "Уточняется" }, color = c.muted,
                                    fontSize = 12.sp, maxLines = 2)
                            }
                        }
                    }
                }
                if (lesson.subgroups.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lesson.subgroups.sortedBy { it.number }.forEach { sg ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(c.chip.copy(alpha = .70f), RoundedCornerShape(12.dp))
                                    .border(.7.dp, c.specular.copy(alpha = .20f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("${sg.number}-я подгруппа · ${sg.subject}", color = c.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Каб. ${sg.room.ifBlank { "—" }} · ${sg.teacher.ifBlank { "Преподаватель уточняется" }}",
                                    color = c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayersEmpty(c: LayersColors, hasSaved: Boolean, onClick: () -> Unit) {
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
private fun LayersSync(c: LayersColors, status: String, isUpdating: Boolean, lastCheckTime: Long?, onRefresh: () -> Unit) {
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
private fun LayersBottomNav(c: LayersColors, onHome: () -> Unit, onGroups: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .background(Brush.horizontalGradient(listOf(c.sheet, c.chip.copy(alpha = .88f), c.sheet)),
            RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp))
        .border(.7.dp, c.specular.copy(alpha = .18f),
            RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp))
        .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        LayersNavItem("Расписание", Icons.Outlined.CalendarMonth, true, c, onHome, Modifier.weight(1f))
        LayersNavItem("Группы", Icons.Outlined.Groups, false, c, onGroups, Modifier.weight(1f))
        LayersNavItem("Настройки", Icons.Outlined.Settings, false, c, onSettings, Modifier.weight(1f))
    }
}

@Composable
private fun LayersNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean, c: LayersColors, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
private fun LayersDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, c: LayersColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.aqua, modifier = Modifier.size(20.dp))
        Text(text, color = c.muted, style = MaterialTheme.typography.bodyMedium)
    }
}
