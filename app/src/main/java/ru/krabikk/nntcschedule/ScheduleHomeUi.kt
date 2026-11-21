package ru.krabikk.nntcschedule

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.graphicsLayer
import ru.krabikk.nntcschedule.ui.theme.selectedDayGradient
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import ru.krabikk.nntcschedule.ui.theme.LocalInterfaceStyle
import ru.krabikk.nntcschedule.ui.theme.themedActionModifier
import ru.krabikk.nntcschedule.ui.theme.themedPanelColor
import ru.krabikk.nntcschedule.ui.theme.themedPanelBorder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.krabikk.nntcschedule.ui.theme.NNTCScheduleTheme
import java.util.Locale

/** Только интерфейс: получение PDF, Room и WorkManager здесь не затрагиваются. */
@Composable
fun ScheduleHomeUi(
    days: List<ParsedDay>,
    todayLessons: Int,
    tomorrowLessons: Int,
    status: String,
    isUpdating: Boolean,
    lastCheckTime: Long?,
    lastChangeTime: Long?,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    showDayStrip: Boolean = true,
    lastSyncReport: SyncReport? = null,
    selectedGroup: String = "2РПУ-25-1",
    onGroupClick: () -> Unit = {},
    groupWarning: String? = null
) {
    if (RemainingConcepts.isSupported(LocalInterfaceStyle.current)) {
        ScheduleRemainingConceptUi(
            style = LocalInterfaceStyle.current, days = days,
            status = status, isUpdating = isUpdating, lastCheckTime = lastCheckTime,
            onRefresh = onRefresh, onSettings = onSettings,
            selectedGroup = selectedGroup, onGroupClick = onGroupClick,
            groupWarning = groupWarning, showDayStrip = showDayStrip
        )
        return
    }
    if (LocalInterfaceStyle.current == InterfaceStyle.LAYERS_1) {
        ScheduleExperimentalDayHost(
            style = InterfaceStyle.LAYERS_1, days = days,
            status = status, isUpdating = isUpdating,
            lastCheckTime = lastCheckTime, onRefresh = onRefresh,
            onSettings = onSettings, selectedGroup = selectedGroup,
            onGroupClick = onGroupClick, groupWarning = groupWarning,
            showDayStrip = showDayStrip
        )
        return
    }
    if (LocalInterfaceStyle.current == InterfaceStyle.ROUTE_2) {
        ScheduleRoute2Ui(
            days = days, status = status, isUpdating = isUpdating,
            lastCheckTime = lastCheckTime, onRefresh = onRefresh,
            onSettings = onSettings, selectedGroup = selectedGroup,
            onGroupClick = onGroupClick, groupWarning = groupWarning,
            showDayStrip = showDayStrip
        )
        return
    }
    if (LocalInterfaceStyle.current == InterfaceStyle.FOCUS_1) {
        ScheduleFocus1Ui(
            days = days, status = status, isUpdating = isUpdating,
            lastCheckTime = lastCheckTime, onRefresh = onRefresh,
            onSettings = onSettings, selectedGroup = selectedGroup,
            onGroupClick = onGroupClick, groupWarning = groupWarning,
            showDayStrip = showDayStrip
        )
        return
    }
    if (LocalInterfaceStyle.current == InterfaceStyle.ORBIT_1) {
        ScheduleExperimentalDayHost(
            style = InterfaceStyle.ORBIT_1, days = days,
            status = status, isUpdating = isUpdating,
            lastCheckTime = lastCheckTime, onRefresh = onRefresh,
            onSettings = onSettings, selectedGroup = selectedGroup,
            onGroupClick = onGroupClick, groupWarning = groupWarning,
            showDayStrip = showDayStrip
        )
        return
    }
    if (LocalInterfaceStyle.current == InterfaceStyle.LAYERS_2) {
        ScheduleLayers2Ui(
            days = days, status = status, isUpdating = isUpdating,
            lastCheckTime = lastCheckTime, onRefresh = onRefresh,
            onSettings = onSettings, selectedGroup = selectedGroup,
            onGroupClick = onGroupClick, groupWarning = groupWarning,
            showDayStrip = showDayStrip
        )
        return
    }
    if (LocalInterfaceStyle.current == InterfaceStyle.HOLOGRAM_1) {
        ScheduleHologram1Ui(
            days = days, status = status, isUpdating = isUpdating,
            lastCheckTime = lastCheckTime, onRefresh = onRefresh,
            onSettings = onSettings, selectedGroup = selectedGroup,
            onGroupClick = onGroupClick, groupWarning = groupWarning,
            showDayStrip = showDayStrip
        )
        return
    }
    if (LocalInterfaceStyle.current == InterfaceStyle.ROUTE_1) {
        ScheduleRoute1Ui(
            days = days,
            status = status,
            isUpdating = isUpdating,
            lastCheckTime = lastCheckTime,
            onRefresh = onRefresh,
            onSettings = onSettings,
            selectedGroup = selectedGroup,
            onGroupClick = onGroupClick,
            groupWarning = groupWarning,
            showDayStrip = showDayStrip
        )
        return
    }
    if (LocalInterfaceStyle.current == InterfaceStyle.ROUTE) {
        ScheduleRouteUi(
            days = days,
            status = status,
            isUpdating = isUpdating,
            lastCheckTime = lastCheckTime,
            onRefresh = onRefresh,
            onSettings = onSettings,
            selectedGroup = selectedGroup,
            onGroupClick = onGroupClick,
            groupWarning = groupWarning,
            showDayStrip = showDayStrip
        )
        return
    }
    // Selection is purely local UI state: no writes to Room and no network calls.
    // 0 means auto-selection. When the DB is populated, the nearest saved date is shown.
    val todayKey = ScheduleDayStrip.todayKey()
    val savedKeys = remember(days) {
        days.mapNotNull { ScheduleDayStrip.keyFromScheduleDate(it.date) }.distinct().sorted()
    }
    var manuallySelectedKey by rememberSaveable { mutableIntStateOf(0) }
    // При смене группы не оставляем дату, выбранную в расписании другой группы.
    LaunchedEffect(selectedGroup) { manuallySelectedKey = 0 }
    val preferredKey = ScheduleDayStrip.initialSelection(days.map { it.date }, todayKey)
    val selectedKey = manuallySelectedKey.takeIf { it != 0 } ?: preferredKey
    val nextSavedKey = ScheduleDayStrip.nextSavedDate(savedKeys, selectedKey)
    val chooseDay: (Int) -> Unit = { manuallySelectedKey = it }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 25.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "NNTC Schedule",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Расписание",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Surface(
                    color = themedPanelColor(MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(17.dp),
                    border = themedPanelBorder()
                ) {
                    IconButton(onClick = onSettings, modifier = Modifier.size(53.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Открыть настройки",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item(key = "group") {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onGroupClick),
                shape = RoundedCornerShape(19.dp),
                color = themedPanelColor(MaterialTheme.colorScheme.surfaceVariant),
                border = themedPanelBorder()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ВЫБРАННАЯ ГРУППА",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedGroup, style = MaterialTheme.typography.titleLarge)
                        Text("Нажми, чтобы изменить группу",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                        if (groupWarning != null) {
                            Text(
                                groupWarning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Icon(Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Выбрать другую группу",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item(key = "refresh") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .then(themedActionModifier(18.dp)),
                    onClick = onRefresh,
                    enabled = !isUpdating,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (LocalInterfaceStyle.current == InterfaceStyle.MINIMAL)
                            MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (LocalInterfaceStyle.current == InterfaceStyle.MINIMAL)
                            MaterialTheme.colorScheme.onPrimary else Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .70f),
                        disabledContentColor = Color.White
                    )
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            color = if (LocalInterfaceStyle.current == InterfaceStyle.MINIMAL)
                                MaterialTheme.colorScheme.onPrimary else Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isUpdating) "Проверяю расписание" else "Проверить обновления",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
                UpdateStatusRow(status = status, isUpdating = isUpdating)
            }
        }

        item(key = "summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = themedPanelColor(MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                border = themedPanelBorder()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Сегодня: ${lessonCount(todayLessons)}  •  Завтра: ${lessonCount(tomorrowLessons)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item(key = "schedule_view") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AnimatedVisibility(
                    visible = showDayStrip,
                    enter = fadeIn(tween(240)) + expandVertically(tween(320)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(280))
                ) {
                    WeekDayStrip(
                        selectedKey = selectedKey,
                        todayKey = todayKey,
                        datesWithLessons = savedKeys.toSet(),
                        onSelectDate = chooseDay
                    )
                }

                AnimatedContent(
                    targetState = showDayStrip,
                    transitionSpec = {
                        fadeIn(tween(290)) togetherWith fadeOut(tween(180))
                    },
                    label = "scheduleModeTransition"
                ) { stripEnabled ->
                    if (stripEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            AnimatedContent(
                                targetState = selectedKey,
                                transitionSpec = {
                                    val forward = targetState >= initialState
                                    (fadeIn(tween(260)) + slideInHorizontally(
                                        animationSpec = tween(340),
                                        initialOffsetX = { if (forward) it / 5 else -it / 5 }
                                    )) togetherWith (fadeOut(tween(150)) + slideOutHorizontally(
                                        animationSpec = tween(230),
                                        targetOffsetX = { if (forward) -it / 6 else it / 6 }
                                    ))
                                },
                                label = "selectedDayTransition"
                            ) { dateKey ->
                                val matchingDay = days.firstOrNull {
                                    ScheduleDayStrip.keyFromScheduleDate(it.date) == dateKey
                                }
                                if (matchingDay != null) {
                                    ModernDayCard(matchingDay)
                                } else {
                                    MissingDayCard(
                                        dayKey = dateKey,
                                        hasSavedData = days.isNotEmpty(),
                                        onReturnToSchedule = { chooseDay(preferredKey) }
                                    )
                                }
                            }

                            if (nextSavedKey != null) {
                                NextSavedDayCard(
                                    dateKey = nextSavedKey,
                                    count = days.firstOrNull {
                                        ScheduleDayStrip.keyFromScheduleDate(it.date) == nextSavedKey
                                    }?.lessons?.size ?: 0,
                                    onClick = { chooseDay(nextSavedKey) }
                                )
                            }
                        }
                    } else {
                        // Точно как до alpha.6: все сохранённые дни идут друг за другом.
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (days.isEmpty()) {
                                Card(
                                    shape = RoundedCornerShape(26.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = themedPanelColor(),
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = null
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Расписание пока пустое", style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            "Нажми «Проверить обновления», чтобы загрузить занятия.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                days.forEach { day ->
                                    ModernDayCard(day)
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "sync_info") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(19.dp),
                color = themedPanelColor(MaterialTheme.colorScheme.surfaceVariant),
                border = themedPanelBorder()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (lastSyncReport != null) {
                            val report = lastSyncReport
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    if (report.successful) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (report.successful) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    report.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (report.successful) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                "Последняя попытка: ${formatLastCheckTime(report.timestampMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!report.successful && report.details.isNotBlank()) {
                                Text(
                                    report.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            "Успешная проверка: ${formatLastCheckTime(lastCheckTime)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Последнее обнаруженное изменение: ${formatLastChangeTime(lastChangeTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Horizontal seven-day strip. Dots mean stored lessons exist, not that other days are free. */
@Composable
private fun WeekDayStrip(
    selectedKey: Int,
    todayKey: Int,
    datesWithLessons: Set<Int>,
    onSelectDate: (Int) -> Unit
) {
    val monday = ScheduleDayStrip.monday(selectedKey)
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Неделя ${ScheduleDayStrip.weekLabel(selectedKey)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Выбери день",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onSelectDate(todayKey) }) {
                Icon(Icons.Outlined.Today, contentDescription = "Перейти к сегодняшней дате", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onSelectDate(ScheduleDayStrip.shift(selectedKey, -7)) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Предыдущая неделя")
            }
            IconButton(onClick = { onSelectDate(ScheduleDayStrip.shift(selectedKey, 7)) }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Следующая неделя")
            }
        }
        Crossfade(
            targetState = monday,
            animationSpec = tween(durationMillis = 240),
            label = "weekTransition"
        ) { displayedMonday ->
            val week = remember(displayedMonday, todayKey) {
                ScheduleDayStrip.week(displayedMonday, todayKey)
            }
            val listState = rememberLazyListState()
            LaunchedEffect(selectedKey, displayedMonday) {
                val index = week.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
                listState.animateScrollToItem((index - 2).coerceAtLeast(0))
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(week, key = { it.key }) { day ->
                    DayStripChip(
                        day = day,
                        isSelected = selectedKey == day.key,
                        hasLessons = datesWithLessons.contains(day.key),
                        onClick = { onSelectDate(day.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayStripChip(
    day: ScheduleDayStrip.Day,
    isSelected: Boolean,
    hasLessons: Boolean,
    onClick: () -> Unit
) {
    val style = LocalInterfaceStyle.current
    val cs = MaterialTheme.colorScheme
    val gradient = selectedDayGradient()
    val contrast = if (style == InterfaceStyle.MINIMAL) cs.onPrimary else Color.White
    val chipShape = RoundedCornerShape(18.dp)
    val animatedColor by animateColorAsState(
        targetValue = when {
            isSelected -> cs.primary
            day.isToday -> cs.primaryContainer
            else -> cs.surfaceVariant
        },
        animationSpec = tween(240),
        label = "dayChipColor"
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = tween(240),
        label = "dayChipElevation"
    )
    val selectedScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else .96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "dayChipScale"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.width(78.dp).height(86.dp).graphicsLayer {
            scaleX = selectedScale
            scaleY = selectedScale
        },
        shape = chipShape,
        color = animatedColor,
        border = null,
        shadowElevation = animatedElevation
    ) {
        Box(
            modifier = Modifier.fillMaxSize().then(
                if (isSelected && style != InterfaceStyle.MINIMAL)
                    Modifier.background(Brush.horizontalGradient(gradient), chipShape)
                else Modifier
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    day.weekday,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) contrast else cs.onSurface
                )
                Text(
                    day.dayAndMonth,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) contrast else cs.onSurfaceVariant,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier.size(if (hasLessons) 6.dp else 3.dp).background(
                        when {
                            isSelected -> contrast
                            hasLessons -> cs.primary
                            else -> cs.outline.copy(alpha = .45f)
                        }, CircleShape
                    )
                )
            }
        }
    }
}

@Composable
private fun MissingDayCard(
    dayKey: Int,
    hasSavedData: Boolean,
    onReturnToSchedule: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(
            containerColor = themedPanelColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = themedPanelBorder()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(31.dp))
            Text(ScheduleDayStrip.displayDate(dayKey), style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasSavedData) "На эту дату в загруженном расписании нет записей"
                else "Расписание ещё не загружено",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                if (hasSavedData) "Это не обязательно выходной: колледж мог ещё не опубликовать данные на этот день."
                else "Нажми «Проверить обновления», чтобы получить расписание.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasSavedData) {
                TextButton(onClick = onReturnToSchedule) {
                    Text("Перейти к ближайшему дню с занятиями")
                }
            }
        }
    }
}

@Composable
private fun NextSavedDayCard(dateKey: Int, count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = themedPanelColor(MaterialTheme.colorScheme.surfaceVariant),
        border = themedPanelBorder()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text("Следующий день с занятиями", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${ScheduleDayStrip.displayDate(dateKey)} · ${lessonCount(count)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Открыть следующий день", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun UpdateStatusRow(status: String, isUpdating: Boolean) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val text = if (isUpdating) "Проверяю новые данные…" else status
    val lower = text.lowercase(Locale.forLanguageTag("ru-RU"))
    val isError = listOf("ошибка", "не удалось", "не найд", "не удалось надёжно").any { lower.contains(it) }
    val isSuccess = lower.contains("актуальн") || lower.contains("обновлено")
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        isSuccess -> if (dark) Color(0xFF8CDEB5) else Color(0xFF25875A)
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when {
        isError -> Icons.Outlined.ErrorOutline
        isSuccess -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Info
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) color else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun lessonCount(count: Int): String {
    val word = if (count % 100 in 11..14) "пар" else when (count % 10) {
        1 -> "пара"
        2, 3, 4 -> "пары"
        else -> "пар"
    }
    return "$count $word"
}

@Composable
private fun ModernDayCard(day: ParsedDay) {
    var expanded by rememberSaveable(day.date, day.weekday) { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(27.dp),
        colors = CardDefaults.cardColors(
            containerColor = themedPanelColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(4.dp, 4.dp, 4.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Box(modifier = Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        day.weekday.uppercase(Locale.forLanguageTag("ru-RU")),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(day.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text(
                        lessonCount(day.lessons.size),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Свернуть день" else "Развернуть день",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(230)) + expandVertically(tween(320)),
                exit = fadeOut(tween(130)) + shrinkVertically(tween(280))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    day.lessons.sortedBy { it.number }.forEach { lesson ->
                        ModernLessonCard(lesson = lesson)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernLessonCard(lesson: ParsedLesson) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        // Tonal separation instead of the old 1dp border.
        color = themedPanelColor(MaterialTheme.colorScheme.surfaceVariant),
        border = null,
        shadowElevation = 0.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(13.dp)) {
            // Короткие названия на широком экране — в две колонки,
            // длинные предметы и узкие устройства — в одну, без обрезания.
            val twoColumns = lesson.subgroups.isEmpty() && maxWidth >= 300.dp && lesson.subject.length <= 15 && lesson.teacher.length <= 22
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(13.dp)) {
                    Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Text(
                            lesson.number.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                if (lesson.subgroups.isNotEmpty()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Занятия по подгруппам", style = MaterialTheme.typography.titleSmall)
                        if (lesson.time.isNotBlank()) Text(
                            lesson.time, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        lesson.subgroups.sortedBy { it.number }.forEach { subgroup ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${subgroup.number}-я подгруппа · ${subgroup.subject}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Кабинет ${subgroup.room} · ${subgroup.teacher}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (twoColumns) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(lesson.subject, style = MaterialTheme.typography.titleMedium)
                        if (lesson.time.isNotBlank()) Text(
                            lesson.time,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(modifier = Modifier.widthIn(max = 132.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        LessonDetails(lesson)
                    }
                } else {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(lesson.subject, style = MaterialTheme.typography.titleMedium)
                        if (lesson.time.isNotBlank()) Text(
                            lesson.time,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        LessonDetails(lesson)
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonDetails(lesson: ParsedLesson) {
    if (lesson.room.isNotBlank()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Text("Кабинет ${lesson.room}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (lesson.teacher.isNotBlank()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                Icons.Outlined.PersonOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Text(lesson.teacher, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val sampleDays = listOf(
    ParsedDay(
        date = "17 ноября 2026 г.", weekday = "ВТОРНИК",
        lessons = listOf(
            ParsedLesson(1, "08:10–09:40", "54", "УП.04", "Федурин В.И."),
            ParsedLesson(2, "10:00–11:30", "54", "УП.04", "Федурин В.И."),
            ParsedLesson(3, "11:50–13:20", "54", "УП.04", "Федурин В.И.")
        )
    ),
    ParsedDay(
        date = "18 ноября 2026 г.", weekday = "СРЕДА",
        lessons = listOf(ParsedLesson(1, "08:10–09:40", "54", "УП.04", "Федурин В.И."))
    )
)

@Preview(name = "Главная — светлая", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewLightHome() {
    NNTCScheduleTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}

@Preview(name = "Главная — тёмная", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewDarkHome() {
    NNTCScheduleTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}

@Preview(name = "Главная — Aurora", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewAuroraHome() {
    NNTCScheduleTheme(darkTheme = false, interfaceStyle = InterfaceStyle.AURORA) {
        Box(modifier = Modifier.fillMaxSize()) {
            ru.krabikk.nntcschedule.ui.theme.AppBackdrop(Modifier.fillMaxSize())
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}

@Preview(name = "Главная — Neon Dark", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewNeonHome() {
    NNTCScheduleTheme(darkTheme = true, interfaceStyle = InterfaceStyle.NEON) {
        Box(modifier = Modifier.fillMaxSize()) {
            ru.krabikk.nntcschedule.ui.theme.AppBackdrop(Modifier.fillMaxSize())
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}

@Preview(name = "Главная — Aurora Dark", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewAuroraDarkHome() {
    NNTCScheduleTheme(darkTheme = true, interfaceStyle = InterfaceStyle.AURORA) {
        Box(modifier = Modifier.fillMaxSize()) {
            ru.krabikk.nntcschedule.ui.theme.AppBackdrop(Modifier.fillMaxSize())
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}

@Preview(name = "Главная — Neon Light", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewNeonLightHome() {
    NNTCScheduleTheme(darkTheme = false, interfaceStyle = InterfaceStyle.NEON) {
        Box(modifier = Modifier.fillMaxSize()) {
            ru.krabikk.nntcschedule.ui.theme.AppBackdrop(Modifier.fillMaxSize())
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}


@Preview(name = "Список без ленты — Minimal", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewListWithoutStrip() {
    NNTCScheduleTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ScheduleHomeUi(
                days = sampleDays,
                showDayStrip = false,
                todayLessons = 3,
                tomorrowLessons = 3,
                status = "Расписание актуально — изменений нет",
                isUpdating = false,
                lastCheckTime = null,
                lastChangeTime = null,
                onRefresh = {},
                onSettings = {}
            )
        }
    }
}

@Preview(name = "Главная — Lavender Light", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewLavenderHome() {
    NNTCScheduleTheme(darkTheme = false, interfaceStyle = InterfaceStyle.LAVENDER) {
        Box(modifier = Modifier.fillMaxSize()) {
            ru.krabikk.nntcschedule.ui.theme.AppBackdrop(Modifier.fillMaxSize())
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}

@Preview(name = "Главная — Lavender Dark", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewLavenderDarkHome() {
    NNTCScheduleTheme(darkTheme = true, interfaceStyle = InterfaceStyle.LAVENDER) {
        Box(modifier = Modifier.fillMaxSize()) {
            ru.krabikk.nntcschedule.ui.theme.AppBackdrop(Modifier.fillMaxSize())
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}

@Preview(name = "Главная — Ocean Light", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewOceanHome() {
    NNTCScheduleTheme(darkTheme = false, interfaceStyle = InterfaceStyle.OCEAN) {
        Box(modifier = Modifier.fillMaxSize()) {
            ru.krabikk.nntcschedule.ui.theme.AppBackdrop(Modifier.fillMaxSize())
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}

@Preview(name = "Главная — Ocean Dark", showBackground = true, widthDp = 410, heightDp = 880)
@Composable
private fun PreviewOceanDarkHome() {
    NNTCScheduleTheme(darkTheme = true, interfaceStyle = InterfaceStyle.OCEAN) {
        Box(modifier = Modifier.fillMaxSize()) {
            ru.krabikk.nntcschedule.ui.theme.AppBackdrop(Modifier.fillMaxSize())
            ScheduleHomeUi(sampleDays, 3, 3, "Расписание актуально — изменений нет", false, null, null, {}, {})
        }
    }
}
