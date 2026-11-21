package ru.krabikk.nntcschedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme

/** Аквастекло v2: layered transparent light-catching surfaces, not opaque flat cards. */
@Composable
fun ScheduleLayers2Ui(
    days: List<ParsedDay>, status: String, isUpdating: Boolean, lastCheckTime: Long?,
    onRefresh: () -> Unit, onSettings: () -> Unit, selectedGroup: String,
    onGroupClick: () -> Unit, groupWarning: String?, showDayStrip: Boolean
) {
    val dark = LocalAppDarkTheme.current
    val c = if (dark) DuoPalette(
        bg = Color(0xFF061B2D), surface = Color(0xFF214562), text = Color(0xFFF8FCFF),
        muted = Color(0xFFC8DAEA), accent = Color(0xFF80E9FF), secondary = Color(0xFFB6A0FD),
        edge = Color(0xFF4D9FC9), glow = Color(0xFF2689B6)
    ) else DuoPalette(
        bg = Color(0xFFEDF9FF), surface = Color(0xFFDAEEF8), text = Color(0xFF102B3D),
        muted = Color(0xFF3C5A6C), accent = Color(0xFF006E91), secondary = Color(0xFF6850B8),
        edge = Color(0xFF75B8D7), glow = Color(0xFFA2DDEC)
    )
    ExperimentalDuoShell(
        days = days, status = status, isUpdating = isUpdating, lastCheckTime = lastCheckTime,
        onRefresh = onRefresh, onSettings = onSettings, selectedGroup = selectedGroup,
        onGroupClick = onGroupClick, groupWarning = groupWarning, showDayStrip = showDayStrip,
        palette = c, label = "Аквастекло · СЛОИ 2",
        backgroundBrush = Brush.verticalGradient(
            listOf(c.bg, if (dark) Color(0xFF0B3957) else Color(0xFFD6F4F5), c.bg)
        ),
        dateDecoration = { title -> AquaGlassDate(title, c, dark) },
        lessonRenderer = { entry, onClick -> AquaGlassCard(entry, c, dark, onClick) }
    )
}

@Composable
private fun AquaGlassDate(title: String, c: DuoPalette, dark: Boolean) {
    val shape = RoundedCornerShape(25.dp)
    Box(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 12.dp, end = 7.dp)) {
        // Underlying glass panels are slightly displaced; the title itself stays horizontal.
        Box(Modifier.matchParentSize().offset(x = 9.dp, y = (-10).dp)
            .graphicsLayer { rotationZ = 2.8f }
            .background(c.secondary.copy(alpha = if (dark) .20f else .15f), shape)
            .border(1.dp, c.secondary.copy(alpha = .48f), shape))
        Box(Modifier.matchParentSize().offset(x = (-5).dp, y = 7.dp)
            .graphicsLayer { rotationZ = -1.8f }
            .background(c.glow.copy(alpha = .32f), shape)
            .border(1.dp, c.accent.copy(alpha = .40f), shape))
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 86.dp),
            color = c.surface.copy(alpha = if (dark) .68f else .80f),
            shape = shape, border = BorderStroke(1.6.dp, c.accent.copy(alpha = .78f))
        ) {
            Box {
                AquaSheen(Modifier.matchParentSize().clip(shape), c, dark)
                Box(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                }
            }
        }
    }
}

@Composable
private fun AquaGlassCard(entry: DuoEntry, c: DuoPalette, dark: Boolean, onClick: () -> Unit) {
    val edge = if (entry.index % 2 == 0) c.accent else c.secondary
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().padding(start = 7.dp, end = 9.dp, top = 14.dp, bottom = 12.dp)) {
        // Visible glass plate behind the content: cyan / violet bounce light.
        Box(Modifier.matchParentSize().offset(x = 9.dp, y = (-10).dp)
            .graphicsLayer { rotationZ = if (entry.index % 2 == 0) 2.1f else -2.1f }
            .background(
                Brush.linearGradient(listOf(edge.copy(alpha = .42f), c.glow.copy(alpha = .14f))), shape)
            .border(1.dp, edge.copy(alpha = .52f), shape))
        Box(Modifier.matchParentSize().offset(x = (-5).dp, y = 7.dp)
            .graphicsLayer { rotationZ = if (entry.index % 2 == 0) -1.5f else 1.5f }
            .background(c.secondary.copy(alpha = .18f), shape)
            .border(1.dp, c.secondary.copy(alpha = .45f), shape))
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 119.dp),
            color = c.surface.copy(alpha = if (dark) .74f else .84f),
            shape = shape,
            border = BorderStroke(1.4.dp, edge.copy(alpha = .95f)),
            shadowElevation = 5.dp
        ) {
            Box {
                AquaSheen(Modifier.matchParentSize().clip(shape), c, dark)
                Row(
                    Modifier.padding(horizontal = 17.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        Modifier.size(49.dp).background(
                            Brush.linearGradient(listOf(edge.copy(alpha = .34f), c.secondary.copy(alpha = .12f))),
                            RoundedCornerShape(14.dp)
                        ).border(1.dp, edge.copy(alpha = .65f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(entry.lesson.number.toString(), color = c.text,
                            fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                    Box(Modifier.weight(1f)) { DuoLessonText(entry.lesson, c) }
                }
            }
        }
    }
}

/** Transparent reflection behind lesson text, clipped to the current glass pane. */
@Composable
private fun AquaSheen(modifier: Modifier, c: DuoPalette, dark: Boolean) {
    Canvas(modifier) {
        val shine = Path().apply {
            moveTo(size.width * .46f, 0f)
            lineTo(size.width * .83f, 0f)
            lineTo(size.width * .47f, size.height)
            lineTo(size.width * .12f, size.height)
            close()
        }
        drawPath(shine, Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (dark) .12f else .24f),
            c.accent.copy(alpha = .04f), Color.Transparent)))
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, c.accent.copy(alpha = .92f), Color.Transparent)),
            start = Offset(0f, 1.5.dp.toPx()), end = Offset(size.width, 1.5.dp.toPx()),
            strokeWidth = 2.dp.toPx()
        )
    }
}
