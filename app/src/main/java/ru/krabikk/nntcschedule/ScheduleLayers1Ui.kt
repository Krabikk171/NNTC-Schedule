package ru.krabikk.nntcschedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * СЛОИ 1 / Призма. Изолированный UI-компонент без доступа к репозиторию и DataStore.
 * Передавайте только реальные данные из существующего экрана и реальные обработчики.
 * Наклонены декоративные пластины, текст остаётся горизонтальным.
 */
data class Layers1Lesson(
    val key: String,
    val number: Int,
    val title: String,
    val time: String,
    val room: String,
    val teacher: String,
    val subgroup: String? = null
)

@Composable
fun ScheduleLayers1Ui(
    dateTitle: String,
    lessons: List<Layers1Lesson>,
    isDark: Boolean,
    onLessonClick: (Layers1Lesson) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "На этот день занятий нет"
) {
    val background = if (isDark) Color(0xFF071C30) else Color(0xFFE7F5FF)
    val foreground = if (isDark) Color.White else Color(0xFF10243C)
    Box(modifier.fillMaxSize().background(background)) {
        Canvas(Modifier.fillMaxSize()) {
            val cyan = if (isDark) Color(0xFF08D8FF) else Color(0xFF65B4DD)
            val violet = if (isDark) Color(0xFFAA66FA) else Color(0xFFAFA1E4)
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(-w * .18f, h * .22f)
                cubicTo(w * .35f, h * .03f, w * .58f, h * .46f, w * 1.14f, h * .17f)
            }
            drawPath(p, Brush.linearGradient(listOf(cyan.copy(alpha = .20f), violet.copy(alpha = .08f))), style = Stroke(width = 90.dp.toPx()))
            val q = Path().apply {
                moveTo(-w * .22f, h * .85f)
                cubicTo(w * .35f, h * .55f, w * .62f, h * 1.06f, w * 1.20f, h * .72f)
            }
            drawPath(q, Brush.linearGradient(listOf(violet.copy(alpha = .23f), cyan.copy(alpha = .09f))), style = Stroke(width = 110.dp.toPx()))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(dateTitle, color = foreground, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            if (lessons.isEmpty()) {
                item { Text(emptyMessage, color = foreground, modifier = Modifier.padding(vertical = 32.dp)) }
            }
            itemsIndexed(lessons, key = { index, lesson -> "${lesson.key}-$index" }) { index, lesson ->
                PrismLessonCard(lesson, index, isDark, onLessonClick)
            }
        }
    }
}

@Composable
private fun PrismLessonCard(
    lesson: Layers1Lesson,
    index: Int,
    isDark: Boolean,
    onClick: (Layers1Lesson) -> Unit
) {
    val cyan = Color(0xFF62E8FF)
    val violet = Color(0xFFC185FF)
    val white = if (isDark) Color.White else Color(0xFF132B4A)
    val glass = if (isDark) Color(0xB51B385E) else Color(0xDDF4FBFF)
    val corners = RoundedCornerShape(19.dp)
    val angle = if (index % 2 == 0) -5f else 4f
    Box(Modifier.fillMaxWidth().heightIn(min = 138.dp).padding(vertical = 5.dp)) {
        Box(
            Modifier.matchParentSize().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
                .graphicsLayer { rotationZ = angle; translationX = 5.dp.toPx() }
                .shadow(13.dp, corners)
                .background(Brush.linearGradient(listOf(cyan.copy(alpha = .47f), violet.copy(alpha = .45f))), corners)
                .border(1.dp, Brush.linearGradient(listOf(cyan, violet)), corners)
        )
        Box(
            Modifier.matchParentSize().padding(start = 4.dp, end = 5.dp, top = 3.dp, bottom = 9.dp)
                .graphicsLayer { rotationZ = -angle * .55f }
                .background(Brush.linearGradient(listOf(violet.copy(alpha = .22f), cyan.copy(alpha = .17f))), corners)
                .border(1.dp, cyan.copy(alpha = .45f), corners)
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 125.dp)
                .clip(corners)
                .background(Brush.linearGradient(listOf(glass, glass.copy(alpha = .88f))), corners)
                .border(1.dp, Brush.linearGradient(listOf(cyan.copy(alpha = .90f), violet.copy(alpha = .85f))), corners)
                .clickable { onClick(lesson) }
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(cyan.copy(alpha = .35f), violet.copy(alpha = .58f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(lesson.number.toString(), fontSize = 29.sp, fontWeight = FontWeight.Bold, color = white)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(lesson.title, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = white, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(lesson.time, fontSize = 14.sp, color = white)
                Text("⌖ ${lesson.room}", fontSize = 12.sp, color = white.copy(alpha = .90f), maxLines = 2)
                Text("♙ ${lesson.teacher}", fontSize = 12.sp, color = white.copy(alpha = .90f), maxLines = 2)
                lesson.subgroup?.let { Text(it, fontSize = 12.sp, color = white.copy(alpha = .95f)) }
            }
        }
    }
}
