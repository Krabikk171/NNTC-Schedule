@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.krabikk.nntcschedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.krabikk.nntcschedule.ui.orbit1.Orbit1Lesson
import ru.krabikk.nntcschedule.ui.orbit1.ScheduleOrbit1Ui
import ru.krabikk.nntcschedule.ui.theme.LocalAppDarkTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presentation-only bridge for the standalone day designs (СЛОИ 1 / ОРБИТА 1).
 * Actual dates and lessons come from the same ParsedDay model as the core screen.
 * This screen does not write to Room or fetch schedules and never fabricates lessons.
 */

@Composable
fun ScheduleExperimentalDayHost(
    style: String,
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
    val dark = LocalAppDarkTheme.current
    val keys = remember(days) {
        days.mapNotNull { ScheduleDayStrip.keyFromScheduleDate(it.date) }.distinct().sorted()
    }
    val dayMap = remember(days) {
        days.mapNotNull { day ->
            ScheduleDayStrip.keyFromScheduleDate(day.date)?.let { it to day }
        }.toMap()
    }
    val today = ScheduleDayStrip.todayKey()
    val preferred = ScheduleDayStrip.initialSelection(days.map { it.date }, today)
    var manualKey by rememberSaveable(selectedGroup) { mutableIntStateOf(0) }
    val key = manualKey.takeIf { it != 0 } ?: preferred
    val day = dayMap[key]
    val mappedRows = remember(day, key) { experimentalDayRows(key, day?.lessons.orEmpty()) }
    var detail by remember(selectedGroup) { mutableStateOf<ExperimentalDayRow?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = LocalDateTime.now()
        }
    }
    val isOrbit = style == InterfaceStyle.ORBIT_1
    val background = if (isOrbit) {
        if (dark) androidx.compose.ui.graphics.Color(0xFF140F28)
        else androidx.compose.ui.graphics.Color(0xFFF6F2FF)
    } else {
        if (dark) androidx.compose.ui.graphics.Color(0xFF071C30)
        else androidx.compose.ui.graphics.Color(0xFFE7F5FF)
    }
    val foreground = if (dark) androidx.compose.ui.graphics.Color.White
        else androidx.compose.ui.graphics.Color(0xFF202B44)

    Column(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NNTC Schedule", color = foreground.copy(alpha = .70f), fontSize = 12.sp)
                Text(if (isOrbit) "Орбитальный поток" else "Призма", color = foreground,
                    fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(onClick = onGroupClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = foreground.copy(alpha = .10f), shape = RoundedCornerShape(14.dp)) {
            Text("Группа $selectedGroup · изменить", color = foreground,
                modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
        }
        if (!groupWarning.isNullOrBlank()) {
            Text(groupWarning, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp), fontSize = 12.sp)
        }
        ExperimentalRefreshControl(
            status = status, isUpdating = isUpdating, lastCheckTime = lastCheckTime,
            onRefresh = onRefresh,
            accent = if (isOrbit) androidx.compose.ui.graphics.Color(0xFFAE94F9)
                else androidx.compose.ui.graphics.Color(0xFF86E8FF),
            buttonInk = androidx.compose.ui.graphics.Color(0xFF211637),
            surface = foreground.copy(alpha = if (dark) .10f else .07f),
            text = foreground, muted = foreground.copy(alpha = .69f),
            edge = foreground.copy(alpha = .32f),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
        if (showDayStrip) {
            val week = ScheduleDayStrip.week(key, today)
            val accent = if (isOrbit) androidx.compose.ui.graphics.Color(0xFFAE94F9)
                else androidx.compose.ui.graphics.Color(0xFF86E8FF)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = { manualKey = ScheduleDayStrip.shift(key, -7) },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.ChevronLeft, "Предыдущая неделя", tint = foreground)
                }
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(week, key = { it.key }) { d ->
                        val active = d.key == key
                        Surface(
                            onClick = { manualKey = d.key },
                            shape = RoundedCornerShape(17.dp),
                            color = if (active) accent else foreground.copy(alpha = .10f),
                            modifier = Modifier.width(56.dp).heightIn(min = 60.dp)
                        ) {
                            Column(
                                Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val ink = if (active) androidx.compose.ui.graphics.Color(0xFF1C1432) else foreground
                                Text(d.weekday, color = ink, style = MaterialTheme.typography.labelSmall)
                                Text((d.key % 100).toString(), color = ink,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                IconButton(onClick = { manualKey = ScheduleDayStrip.shift(key, 7) },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.ChevronRight, "Следующая неделя", tint = foreground)
                }
            }
        } else {
            // No horizontal strip: navigate directly between stored days instead of nesting scrollables.
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                val previous = keys.lastOrNull { it < key }
                val next = keys.firstOrNull { it > key }
                IconButton(onClick = { if (previous != null) manualKey = previous }, enabled = previous != null) {
                    Icon(Icons.Outlined.ChevronLeft, "Предыдущий день", tint = foreground)
                }
                Text(ScheduleDayStrip.displayDate(key), color = foreground, fontWeight = FontWeight.Bold)
                IconButton(onClick = { if (next != null) manualKey = next }, enabled = next != null) {
                    Icon(Icons.Outlined.ChevronRight, "Следующий день", tint = foreground)
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (isOrbit) {
                val orbitEntries = mappedRows.map { row ->
                    val (start, end) = experimentalTimeRange(row.original.time)
                    Orbit1Lesson(
                        stableId = row.stableId, number = row.original.number,
                        subject = row.subject, start = start, end = end,
                        room = row.room, teacher = row.teacher,
                        subgroup = row.subgroupNumber?.let { "$it-я подгруппа" }
                    )
                }
                ScheduleOrbit1Ui(
                    date = LocalDate.of(key / 10000, key / 100 % 100, key % 100),
                    lessons = orbitEntries, now = now,
                    onLessonClick = { selected ->
                        detail = mappedRows.firstOrNull { it.stableId == selected.stableId }
                    },
                    dark = dark,
                    modifier = Modifier.fillMaxSize(),
                    heading = ScheduleDayStrip.displayDate(key),
                    emptyMessage = if (days.isEmpty()) "Расписание ещё не загружено" else "На этот день записей нет"
                )
            } else {
                val prismEntries = mappedRows.map { row ->
                    Layers1Lesson(
                        key = row.stableId, number = row.original.number,
                        title = row.subject, time = row.original.time,
                        room = row.room, teacher = row.teacher,
                        subgroup = row.subgroupNumber?.let { "$it-я подгруппа" }
                    )
                }
                ScheduleLayers1Ui(
                    dateTitle = ScheduleDayStrip.displayDate(key),
                    lessons = prismEntries, isDark = dark,
                    onLessonClick = { selected ->
                        detail = mappedRows.firstOrNull { it.stableId == selected.key }
                    },
                    modifier = Modifier.fillMaxSize(),
                    emptyMessage = if (days.isEmpty()) "Расписание ещё не загружено" else "На этот день записей нет"
                )
            }
        }
        ExperimentalFloatingNavigation(
            onHome = { manualKey = 0 },
            onGroups = onGroupClick,
            onSettings = onSettings,
            surface = if (dark) androidx.compose.ui.graphics.Color(0xFF2B2244)
                else androidx.compose.ui.graphics.Color(0xFFF1ECFF),
            accent = if (isOrbit) androidx.compose.ui.graphics.Color(0xFFAE94F9)
                else androidx.compose.ui.graphics.Color(0xFF0086B0),
            text = foreground,
            edge = foreground.copy(alpha = .24f)
        )
    }

    detail?.let { row ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Пара №${row.original.number}", style = MaterialTheme.typography.headlineSmall)
                if (row.subgroupNumber != null) Text("Подгруппа ${row.subgroupNumber}")
                Text(row.subject.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleLarge)
                if (row.original.time.isNotBlank()) Text("Время: ${row.original.time}")
                if (row.room.isNotBlank()) Text("Кабинет: ${row.room}")
                if (row.teacher.isNotBlank()) Text("Преподаватель: ${row.teacher}")
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { detail = null }) { Text("Закрыть") }
            }
        }
    }
}
