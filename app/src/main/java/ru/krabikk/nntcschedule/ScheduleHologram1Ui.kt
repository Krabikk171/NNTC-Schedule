package ru.krabikk.nntcschedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme

/**
 * Люмен v2. The surface is a stack of projected translucent planes, not opaque list cards.
 * GPU friendly: no RenderEffect / third-party backdrop blur. Every lesson remains tappable.
 */
@Composable
fun ScheduleHologram1Ui(
    days: List<ParsedDay>, status: String, isUpdating: Boolean, lastCheckTime: Long?,
    onRefresh: () -> Unit, onSettings: () -> Unit, selectedGroup: String,
    onGroupClick: () -> Unit, groupWarning: String?, showDayStrip: Boolean
) {
    val dark = LocalAppDarkTheme.current
    val c = if (dark) DuoPalette(
        bg = Color(0xFF100C23), surface = Color(0xFF332454), text = Color(0xFFFFFAFF),
        muted = Color(0xFFD7C8E9), accent = Color(0xFFD3AAFF), secondary = Color(0xFF88EAFE),
        edge = Color(0xFF8B5FC7), glow = Color(0xFF7936CC)
    ) else DuoPalette(
        bg = Color(0xFFF6F0FF), surface = Color(0xFFE5DAF6), text = Color(0xFF26143C),
        muted = Color(0xFF55446D), accent = Color(0xFF713BB6), secondary = Color(0xFF006C8F),
        edge = Color(0xFFA889D3), glow = Color(0xFFCDA7F8)
    )
    // Number alone is not a stable identity when subgroup rows share the same lesson number.
    var focusedIndex by remember(selectedGroup) { mutableIntStateOf(0) }
    ExperimentalDuoShell(
        days = days, status = status, isUpdating = isUpdating, lastCheckTime = lastCheckTime,
        onRefresh = onRefresh, onSettings = onSettings, selectedGroup = selectedGroup,
        onGroupClick = onGroupClick, groupWarning = groupWarning, showDayStrip = showDayStrip,
        palette = c, label = "Люмен · ГОЛОГРАММА 1",
        backgroundBrush = Brush.verticalGradient(
            listOf(c.bg, if (dark) Color(0xFF1D1040) else Color(0xFFEDE4FA), c.bg)
        ),
        dateDecoration = { title ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Коснитесь проекции, чтобы выбрать занятие", color = c.muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        lessonRenderer = { entry, onClick ->
            val selected = focusedIndex == entry.index
            HologramProjectedCard(entry.lesson, selected, c, dark) {
                focusedIndex = entry.index
                onClick()
            }
        },
        footerDecoration = { HologramProjector(c, dark) }
    )
}

@Composable
private fun HologramProjectedCard(
    lesson: ParsedLesson,
    selected: Boolean,
    c: DuoPalette,
    dark: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (selected) 1f else .965f, label = "hologram-depth-scale")
    val edge = if (selected) c.accent else c.edge
    val shape = RoundedCornerShape(23.dp)
    Box(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = if (selected) 3.dp else 10.dp, vertical = 9.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        // Back plates carry the perspective. Do not transform the text plane.
        Box(Modifier.matchParentSize().offset(x = 8.dp, y = 12.dp)
            .graphicsLayer { rotationZ = if (selected) -3f else -1f }
            .background(c.glow.copy(alpha = if (selected) .44f else .16f), shape)
            .border(1.dp, c.secondary.copy(alpha = .5f), shape))
        Box(Modifier.matchParentSize().offset(x = (-6).dp, y = (-7).dp)
            .graphicsLayer { rotationZ = if (selected) 2.8f else 1.4f }
            .background(c.accent.copy(alpha = if (selected) .22f else .10f), shape)
            .border(1.dp, c.accent.copy(alpha = .42f), shape))
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = if (selected) 123.dp else 97.dp),
            shape = shape,
            color = c.surface.copy(alpha = if (dark) .68f else .84f),
            border = BorderStroke(if (selected) 1.7.dp else 1.dp, edge.copy(alpha = .93f)),
            shadowElevation = if (selected) 9.dp else 2.dp
        ) {
            Box {
                // Holographic specular reflection is behind the content, not across the text.
                Canvas(Modifier.matchParentSize().clip(shape)) {
                    val flare = Path().apply {
                        moveTo(size.width * .56f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width * .72f, size.height)
                        lineTo(size.width * .40f, size.height)
                        close()
                    }
                    drawPath(flare, Brush.linearGradient(listOf(
                        Color.White.copy(alpha = if (dark) .11f else .22f),
                        c.secondary.copy(alpha = .05f), Color.Transparent)))
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, c.secondary, Color.Transparent)),
                        start = Offset(0f, 1.5.dp.toPx()), end = Offset(size.width, 1.5.dp.toPx()),
                        strokeWidth = if (selected) 2.dp.toPx() else 1.dp.toPx()
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(if (selected) 52.dp else 44.dp)
                            .background(Brush.linearGradient(listOf(
                                c.accent.copy(alpha = .36f), c.secondary.copy(alpha = .12f))), CircleShape)
                            .border(1.dp, edge.copy(alpha = .9f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(lesson.number.toString(), color = c.text,
                            fontWeight = FontWeight.Bold, fontSize = if (selected) 24.sp else 20.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DuoLessonText(lesson, c, compact = !selected)
                        if (selected) Text("В фокусе проекции", color = c.secondary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun HologramProjector(c: DuoPalette, dark: Boolean) {
    // Elliptical projector under the whole lesson stack: functional visual metaphor.
    // The previous implementation was just a horizontal colored rectangle.
    Canvas(Modifier.fillMaxWidth().height(118.dp)) {
        val cx = size.width / 2f
        val centerY = size.height * .60f
        val rx = size.width * .40f
        val ry = 20.dp.toPx()
        val fill = Brush.radialGradient(
            colors = listOf(c.secondary.copy(alpha = .28f), c.glow.copy(alpha = .22f), Color.Transparent),
            center = Offset(cx, centerY), radius = rx
        )
        drawOval(brush = fill,
            topLeft = Offset(cx - rx, centerY - ry * 2f),
            size = Size(rx * 2f, ry * 4f))
        for (ratio in listOf(1f, .79f, .56f)) {
            drawOval(
                color = c.accent.copy(alpha = (if (dark) .69f else .48f) * ratio),
                topLeft = Offset(cx - rx * ratio, centerY - ry * ratio),
                size = Size(rx * 2f * ratio, ry * 2f * ratio),
                style = Stroke(width = if (ratio == 1f) 2.dp.toPx() else 1.dp.toPx())
            )
        }
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, c.secondary, Color.Transparent)),
            start = Offset(cx - rx * .70f, centerY), end = Offset(cx + rx * .70f, centerY),
            strokeWidth = 3.dp.toPx()
        )
        drawLine(
            brush = Brush.verticalGradient(listOf(Color.Transparent, c.accent.copy(alpha = .40f), Color.Transparent)),
            start = Offset(cx, 0f), end = Offset(cx, centerY),
            strokeWidth = 20.dp.toPx()
        )
    }
}
