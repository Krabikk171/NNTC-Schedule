package ru.krabikk.nntcschedule.ui.orbit1

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Presentation data only; sourced from the existing schedule model by the host. */
data class Orbit1Lesson(
    val stableId: String,
    val number: Int,
    val subject: String,
    val start: LocalTime?,
    val end: LocalTime?,
    val room: String = "",
    val teacher: String = "",
    val subgroup: String? = null
)

private val Ink = Color(0xFF140F28)
private val Violet = Color(0xFFA78BFA)
private val Gold = Color(0xFFFFC46B)
private val Ice = Color(0xFFEDE8FF)

/** Pure calculation: never marks a future/other date as "current". */
fun orbit1AutomaticFocus(date: LocalDate, now: LocalDateTime, lessons: List<Orbit1Lesson>): Int {
    if (lessons.isEmpty() || date != now.toLocalDate()) return 0
    val current = lessons.indexOfFirst {
        it.start != null && it.end != null &&
            !now.toLocalTime().isBefore(it.start) && now.toLocalTime().isBefore(it.end)
    }
    if (current >= 0) return current
    val next = lessons.indexOfFirst { it.start != null && now.toLocalTime().isBefore(it.start) }
    return if (next >= 0) next else lessons.lastIndex
}

/**
 * True orbital movement: the y coordinate follows sin(theta), x follows cos(theta).
 * As the focused lesson changes, animateFloatAsState rotates ALL visible stations.
 * The compact full list below the orbital stage prevents long schedules from being hidden.
 */
@Composable
fun ScheduleOrbit1Ui(
    date: LocalDate,
    lessons: List<Orbit1Lesson>,
    now: LocalDateTime,
    onLessonClick: (Orbit1Lesson) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    motionEnabled: Boolean = true,
    heading: String? = null,
    emptyMessage: String = "Занятий нет"
) {
    val ordered = remember(lessons) {
        lessons.sortedWith(compareBy<Orbit1Lesson> { it.start ?: LocalTime.MAX }.thenBy { it.number })
    }
    val auto = orbit1AutomaticFocus(date, now, ordered)
    var selectedId by remember(date, ordered.map { it.stableId }) { mutableStateOf<String?>(null) }
    val chosen = selectedId?.let { id -> ordered.indexOfFirst { it.stableId == id } }
        ?.takeIf { it >= 0 } ?: auto
    val foreground = if (dark) Ice else Color(0xFF261D44)
    val muted = if (dark) Color(0xFFCFC5E4) else Color(0xFF655979)
    val background = if (dark) Ink else Color(0xFFF5F1FF)
    val accent = if (dark) Violet else Color(0xFF6335AA)
    val canOrbit = LocalDensity.current.fontScale <= 1.15f

    LazyColumn(
        modifier = modifier.fillMaxSize().background(background),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item(key = "orbit_heading") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (heading != null) {
                    Text(heading, fontWeight = FontWeight.Bold, color = foreground,
                        style = MaterialTheme.typography.titleLarge)
                }
                Text("Орбитальный поток · ОРБИТА 1", color = accent,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        if (ordered.isEmpty()) {
            item(key = "orbit_empty") {
                Surface(color = accent.copy(alpha = .12f), shape = RoundedCornerShape(22.dp)) {
                    Text(emptyMessage, color = foreground,
                        modifier = Modifier.fillMaxWidth().padding(25.dp))
                }
            }
        } else {
            item(key = "orbit_state") {
                val current = ordered[chosen]
                val status = when {
                    date != now.toLocalDate() -> "Выбрана пара №${current.number}"
                    selectedId != null && chosen != auto -> "Выбрана пара №${current.number}"
                    current.start != null && current.end != null &&
                        !now.toLocalTime().isBefore(current.start) &&
                        now.toLocalTime().isBefore(current.end) -> "Сейчас пара №${current.number}"
                    current.start != null && now.toLocalTime().isBefore(current.start) ->
                        "Следующая пара №${current.number}"
                    else -> "Учебный день завершён"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, color = accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).semantics { contentDescription = status })
                    if (selectedId != null && date == now.toLocalDate()) {
                        TextButton(onClick = { selectedId = null }) {
                            Icon(Icons.Outlined.MyLocation, contentDescription = null,
                                modifier = Modifier.size(18.dp), tint = accent)
                            Text("К актуальной", color = accent, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (canOrbit) {
                item(key = "orbit_motion_stage") {
                    OrbitMotionStage(
                        ordered = ordered, focus = chosen, dark = dark,
                        motionEnabled = motionEnabled,
                        onSelect = { index ->
                            selectedId = ordered[index].stableId
                            onLessonClick(ordered[index])
                        },
                        onNavigate = { index -> selectedId = ordered[index].stableId }
                    )
                }
            }
            item(key = "orbit_all_caption") {
                Text(
                    if (canOrbit) "Все занятия · ${ordered.size}" else "Занятия · ${ordered.size}",
                    color = foreground, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            itemsIndexed(ordered, key = { _, l -> "list_${l.stableId}" }) { index, l ->
                val isActive = index == chosen
                val shape = RoundedCornerShape(17.dp)
                Surface(
                    onClick = { selectedId = l.stableId; onLessonClick(l) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 77.dp),
                    shape = shape,
                    color = if (dark) Color(0xFF2D2349) else Color.White,
                    border = BorderStroke(if (isActive) 1.5.dp else .7.dp,
                        if (isActive) Gold else accent.copy(alpha = .32f))
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(37.dp).background(
                            accent.copy(alpha = if (isActive) .68f else .22f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text(l.number.toString(), color = if (isActive) Color.White else foreground,
                                fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(l.subject.ifBlank { "Название уточняется" }, color = foreground,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(orbitTimeLabel(l), color = if (isActive) Gold else muted,
                                style = MaterialTheme.typography.bodySmall)
                            Text(orbitMetadata(l), color = muted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            l.subgroup?.let { Text(it, color = accent, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Geometry rule: the front, preceding and following lessons travel on a real ellipse,
 * with its centre off the left edge of the phone. Only stations within 1.18
 * positions of the animated focus are laid out. The stage is tall enough for all
 * visible cards to remain fully inside its top/bottom bounds at every animation
 * step, while adjacent card centres remain over 148dp apart.
 * An accessible complete list is always rendered below the stage.
 */
@Composable
private fun OrbitMotionStage(
    ordered: List<Orbit1Lesson>,
    focus: Int,
    dark: Boolean,
    motionEnabled: Boolean,
    onSelect: (Int) -> Unit,
    onNavigate: (Int) -> Unit
) {
    val animatedFocus by animateFloatAsState(
        targetValue = focus.toFloat(),
        animationSpec = tween(durationMillis = if (motionEnabled) 500 else 0),
        label = "orbit-arc-position"
    )
    val fg = if (dark) Ice else Color(0xFF261D44)
    val muted = if (dark) Color(0xFFD1C8E6) else Color(0xFF554773)
    val accent = if (dark) Violet else Color(0xFF6335AA)
    val base = if (dark) Color(0xFF30234D) else Color.White
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(600.dp)
                .clipToBounds()
                .background(
                    Brush.radialGradient(listOf(accent.copy(alpha = .11f), Color.Transparent)),
                    RoundedCornerShape(22.dp)
                )
        ) {
            // The orbit centre deliberately lives outside the left edge.
            // Every position below is derived from the same ellipse, not arbitrary x/y shifts.
            val centreX = -80f
            val horizontalRadius = 119f
            val centreY = 300f
            val verticalRadius = 225f
            val angleStep = .88f
            val cardHeight = 148.dp
            Canvas(Modifier.matchParentSize()) {
                val cx = centreX.dp.toPx()
                val cy = centreY.dp.toPx()
                val rx = horizontalRadius.dp.toPx()
                val ry = verticalRadius.dp.toPx()
                for (factor in listOf(1f, 1.38f)) {
                    drawOval(
                        color = accent.copy(alpha = if (factor == 1f) .38f else .12f),
                        topLeft = Offset(cx - rx * factor, cy - ry * factor),
                        size = Size(rx * 2f * factor, ry * 2f * factor),
                        style = Stroke(width = (if (factor == 1f) 2.dp else 1.dp).toPx())
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(listOf(accent.copy(alpha = .33f), Color.Transparent)),
                    radius = 66.dp.toPx(), center = Offset(cx, cy)
                )
            }
            ordered.forEachIndexed { index, lesson ->
                val relative = index.toFloat() - animatedFocus
                // Moving outside the screen is preferable to putting two cards on top of one another.
                if (abs(relative) <= 1.18f) {
                    androidx.compose.runtime.key(lesson.stableId) {
                        val angle = relative * angleStep
                        val stationX = centreX + horizontalRadius * cos(angle)
                        val stationY = centreY + verticalRadius * sin(angle)
                        val cardLeft = (stationX + 39f).coerceAtLeast(24f).dp
                        val cardWidth = (maxWidth - cardLeft - 8.dp).coerceAtLeast(1.dp)
                        val proximity = (1f - abs(relative)).coerceIn(0f, 1f)
                        val isFocused = index == focus
                        val stationDiameter = (44f + proximity * 11f).dp
                        val tint = if (isFocused) accent else accent.copy(alpha = .72f)
                        Box(
                            Modifier.offset(
                                x = stationX.dp - stationDiameter / 2,
                                y = stationY.dp - stationDiameter / 2
                            ).zIndex(if (isFocused) 4f else 2f)
                                .size(stationDiameter)
                                .background(tint, CircleShape)
                                .border(1.5.dp, fg.copy(alpha = .75f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                lesson.number.toString(),
                                color = if (dark) Color(0xFF190F31) else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (isFocused) 20.sp else 16.sp
                            )
                        }
                        Surface(
                            onClick = { onSelect(index) },
                            modifier = Modifier.offset(x = cardLeft, y = stationY.dp - cardHeight / 2)
                                .zIndex(if (isFocused) 5f else 3f)
                                .width(cardWidth).height(cardHeight),
                            shape = RoundedCornerShape(21.dp),
                            color = if (isFocused) (if (dark) Color(0xFF47335F) else Color(0xFFEAE2FF)) else base,
                            border = BorderStroke(if (isFocused) 1.8.dp else 1.dp,
                                if (isFocused) Gold else accent.copy(alpha = .55f)),
                            shadowElevation = if (isFocused) 10.dp else 4.dp
                        ) {
                            Column(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    lesson.subject.ifBlank { "Предмет уточняется" },
                                    color = fg, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    fontSize = 15.sp
                                )
                                Text(orbitTimeLabel(lesson), color = if (isFocused) Gold else fg,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(orbitMetadata(lesson), color = muted,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                                lesson.subgroup?.let {
                                    Text(it, color = accent, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onNavigate((focus - 1).coerceAtLeast(0)) },
                enabled = focus > 0, modifier = Modifier.size(48.dp)
            ) { Icon(Icons.Outlined.ChevronLeft, "Предыдущее занятие", tint = accent) }
            Text("Пара ${focus + 1} из ${ordered.size}", color = fg,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp))
            IconButton(
                onClick = { onNavigate((focus + 1).coerceAtMost(ordered.lastIndex)) },
                enabled = focus < ordered.lastIndex, modifier = Modifier.size(48.dp)
            ) { Icon(Icons.Outlined.ChevronRight, "Следующее занятие", tint = accent) }
        }
    }
}

private fun orbitTimeLabel(lesson: Orbit1Lesson): String =
    if (lesson.start != null && lesson.end != null) "${lesson.start}–${lesson.end}"
    else "Время уточняется"

private fun orbitMetadata(lesson: Orbit1Lesson): String = listOfNotNull(
    lesson.room.takeIf { it.isNotBlank() }?.let { "Каб. $it" },
    lesson.teacher.takeIf { it.isNotBlank() }
).joinToString(" · ").ifBlank { "Информация уточняется" }
