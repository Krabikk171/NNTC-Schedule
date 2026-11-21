package ru.krabikk.nntcschedule

import ru.krabikk.nntcschedule.v3.GroupScheduleDiff
import ru.krabikk.nntcschedule.v3.GroupScheduleDatabase
import ru.krabikk.nntcschedule.v3.GroupLessonEntity
import ru.krabikk.nntcschedule.v3.GroupInfoEntity

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import ru.krabikk.nntcschedule.ui.theme.NNTCScheduleTheme
import ru.krabikk.nntcschedule.ui.theme.AppBackdrop
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {

                android.util.Log.d(
                    "NOTIFICATIONS",
                    "Разрешение на уведомления получено"
                )

            } else {

                android.util.Log.d(
                    "NOTIFICATIONS",
                    "Разрешение на уведомления отклонено"
                )
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )


        NotificationHelper.createChannel(
            this
        )


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }


        AppUpdateWorkScheduler.schedule(applicationContext)

        PDFBoxResourceLoader.init(
            applicationContext
        )


        setContent {

            val themeMode by SettingsRepository
                .getThemeMode(
                    this@MainActivity
                )
                .collectAsState(
                    initial =
                        SettingsRepository
                            .ThemeMode
                            .SYSTEM
                )


            val interfaceStyle by SettingsRepository
                .getInterfaceStyle(this@MainActivity)
                .collectAsState(initial = InterfaceStyle.MINIMAL)

            val isDarkTheme =
                when (themeMode) {

                    SettingsRepository
                        .ThemeMode.DARK ->
                        true

                    SettingsRepository
                        .ThemeMode.LIGHT ->
                        false

                    else ->
                        isSystemInDarkTheme()
                }


            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = !isDarkTheme
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightNavigationBars = !isDarkTheme
            }

            NNTCScheduleTheme(darkTheme = isDarkTheme, interfaceStyle = interfaceStyle) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppBackdrop(modifier = Modifier.fillMaxSize())
                    ScheduleScreen()
                }
            }
        }
    }
}


// -------------------------------------------------------------
// Формат даты расписания
// -------------------------------------------------------------

private val scheduleDateFormatter =
    SimpleDateFormat(
        "d MMMM yyyy 'г.'",
        Locale("ru")
    )


private fun parseScheduleDate(
    date: String
): Date? {

    return try {

        scheduleDateFormatter.parse(
            date
        )

    } catch (
        e: Exception
    ) {

        null
    }
}


// -------------------------------------------------------------
// Проверка совпадения даты расписания
// -------------------------------------------------------------

fun isSameScheduleDate(
    scheduleDate: String,
    calendar: Calendar
): Boolean {

    return try {

        val formatter =
            SimpleDateFormat(
                "d MMMM yyyy 'г.'",
                Locale("ru")
            )


        val parsedDate =
            formatter.parse(
                scheduleDate
            )
                ?: return false


        val scheduleCalendar =
            Calendar.getInstance()


        scheduleCalendar.time =
            parsedDate


        scheduleCalendar.get(
            Calendar.YEAR
        ) ==
                calendar.get(
                    Calendar.YEAR
                ) &&

                scheduleCalendar.get(
                    Calendar.DAY_OF_YEAR
                ) ==
                calendar.get(
                    Calendar.DAY_OF_YEAR
                )

    } catch (
        e: Exception
    ) {

        false
    }
}


// -------------------------------------------------------------
// Формат интервала проверки
// -------------------------------------------------------------

fun formatCheckInterval(
    minutes: Long
): String {

    return when (minutes) {

        0L ->
            "выключена"

        60L ->
            "1 час"

        120L ->
            "2 часа"

        300L ->
            "5 часов"

        else ->
            "$minutes минут"
    }
}


// -------------------------------------------------------------
// Последняя проверка
// -------------------------------------------------------------

fun formatLastCheckTime(
    timeMillis: Long?
): String {

    if (timeMillis == null) {
        return "ещё не выполнялась"
    }


    val formatter =
        SimpleDateFormat(
            "d MMMM, HH:mm",
            Locale("ru")
        )


    return formatter.format(
        Date(timeMillis)
    )
}


// -------------------------------------------------------------
// Последнее изменение
// -------------------------------------------------------------

fun formatLastChangeTime(
    timeMillis: Long?
): String {

    if (timeMillis == null) {
        return "изменений ещё не было"
    }


    val formatter =
        SimpleDateFormat(
            "d MMMM, HH:mm",
            Locale("ru")
        )


    return formatter.format(
        Date(timeMillis)
    )
}


// -------------------------------------------------------------
// Формирование текста после ручной проверки
// -------------------------------------------------------------

private fun buildManualUpdateStatus(
    result: ScheduleUpdateResult.Success
): String {

    val changedPairs =
        result.changes.count {
            it.oldLesson != null &&
                    it.newLesson != null
        }


    val addedPairs =
        result.changes.count {
            it.oldLesson == null &&
                    it.newLesson != null
        }


    val removedPairs =
        result.changes.count {
            it.oldLesson != null &&
                    it.newLesson == null
        }


    val parts =
        mutableListOf<String>()


    if (changedPairs > 0) {

        parts +=
            "изменено пар: $changedPairs"
    }


    if (addedPairs > 0) {

        parts +=
            "добавлено пар: $addedPairs"
    }


    if (removedPairs > 0) {

        parts +=
            "удалено пар: $removedPairs"
    }


    if (
        result.newDays.isNotEmpty()
    ) {

        parts +=
            "добавлено дней: ${result.newDays.size}"
    }


    if (
        result.removedDays.isNotEmpty()
    ) {

        parts +=
            "удалено дней: ${result.removedDays.size}"
    }


    return if (
        parts.isEmpty()
    ) {

        "Расписание успешно обновлено"

    } else {

        "Расписание обновлено: " +
                parts.joinToString(
                    separator = ", "
                )
    }
}


// -------------------------------------------------------------
// Главный экран
// -------------------------------------------------------------

@Composable
fun ScheduleScreen() {

    val context =
        LocalContext.current


    var showSettings by remember {
        mutableStateOf(false)
    }

    var showGroupPicker by remember { mutableStateOf(false) }
    var showProfileEditor by rememberSaveable { mutableStateOf(false) }
    val profileSetupCompleted by remember(context) {
        SettingsRepository.getProfileSetupCompleted(context)
    }.collectAsState(initial = null)
    val selectedGroup by remember(context) {
        SettingsRepository.getSelectedGroup(context)
    }.collectAsState(initial = "2РПУ-25-1")
    val groupDatabase = remember(context) { GroupScheduleDatabase.get(context) }
    val availableGroups by remember(groupDatabase) {
        groupDatabase.groupScheduleDao().observeGroups()
    }.collectAsState(initial = emptyList<GroupInfoEntity>())
    val cachedGroupLessons by remember(groupDatabase) {
        groupDatabase.groupScheduleDao().observeLessons()
    }.collectAsState(initial = emptyList<GroupLessonEntity>())
    val cachedGroupSubgroups by remember(groupDatabase) {
        groupDatabase.groupScheduleDao().observeSubgroups()
    }.collectAsState(initial = emptyList<ru.krabikk.nntcschedule.v3.GroupSubgroupEntity>())


    var showChangelog by remember {

        mutableStateOf(
            ChangelogManager.shouldShow(
                context
            )
        )
    }


    val currentChangelog =
        Changelog.versions.firstOrNull {
            it.version ==
                    Changelog.CURRENT_VERSION
        }


    // ---------------------------------------------------------
    // Настройки
    // ---------------------------------------------------------

    val checkInterval by SettingsRepository
        .getCheckInterval(
            context
        )
        .collectAsState(
            initial = 30L
        )


    // ---------------------------------------------------------
    // Время последней проверки / изменения
    // ---------------------------------------------------------

    val lastCheckTime by SettingsRepository
        .getLastCheckTime(
            context
        )
        .collectAsState(
            initial = null
        )


    val lastChangeTime by SettingsRepository
        .getLastChangeTime(
            context
        )
        .collectAsState(
            initial = null
        )

    val lastSyncReport by SettingsRepository
        .getLastSyncReport(context)
        .collectAsState(initial = null)


    // ---------------------------------------------------------
    // Состояние проверки
    // ---------------------------------------------------------

    var downloadStatus by remember {

        mutableStateOf(
            "Нажмите кнопку для проверки расписания"
        )
    }


    var isUpdating by remember {
        mutableStateOf(false)
    }


    // ---------------------------------------------------------
    // Расписание
    // ---------------------------------------------------------

    var parsedDays by remember {

        mutableStateOf<List<ParsedDay>>(
            emptyList()
        )
    }


    val today =
        Calendar.getInstance()


    val tomorrow =
        Calendar.getInstance().apply {

            add(
                Calendar.DAY_OF_YEAR,
                1
            )
        }


    val visibleDays = if (selectedGroup == "2РПУ-25-1") parsedDays else {
        val subgroupsByLesson = cachedGroupSubgroups.filter { it.groupName == selectedGroup }
            .groupBy { it.date to it.number }
        cachedGroupLessons.filter { it.groupName == selectedGroup }
            .groupBy { it.date to it.weekday }
            .map { (info, lessons) ->
                ParsedDay(
                    date = info.first,
                    weekday = info.second,
                    lessons = lessons.sortedBy { it.number }.map { lesson ->
                        ParsedLesson(
                            lesson.number, lesson.time, lesson.room, lesson.subject, lesson.teacher,
                            subgroups = subgroupsByLesson[lesson.date to lesson.number].orEmpty().map { sub ->
                                ParsedSubgroup(sub.subgroupNumber, sub.subject, sub.room, sub.teacher)
                            }
                        )
                    }
                )
            }
            .sortedBy { ScheduleDayStrip.keyFromScheduleDate(it.date) ?: Int.MAX_VALUE }
    }
    val groupQuality = availableGroups.firstOrNull { it.name == selectedGroup }?.quality
    val groupWarning = when {
        selectedGroup == "2РПУ-25-1" -> null
        groupQuality == null -> "Нет данных для группы. Обнови расписание."
        groupQuality == "UNSUPPORTED" -> "⚠ Данные группы не удалось достоверно распознать"
        groupQuality == "NEEDS_REVIEW" -> "⚠ Проверь занятия по оригинальному PDF"
        else -> "Экспериментальная поддержка групп · 3.1"
    }

    val todayLessons =
        visibleDays
            .firstOrNull { day ->

                isSameScheduleDate(
                    day.date,
                    today
                )
            }
            ?.lessons
            ?.size
            ?: 0


    val tomorrowLessons =
        visibleDays
            .firstOrNull { day ->

                isSameScheduleDate(
                    day.date,
                    tomorrow
                )
            }
            ?.lessons
            ?.size
            ?: 0


    val scope =
        rememberCoroutineScope()


    val database =
        remember {

            ScheduleDatabase.getInstance(
                context
            )
        }


    // ---------------------------------------------------------
    // Запуск / обновление фоновой проверки
    // ---------------------------------------------------------

    LaunchedEffect(
        checkInterval
    ) {

        ScheduleWorkScheduler.schedule(
            context =
                context,

            intervalMinutes =
                checkInterval
        )
    }


    // ---------------------------------------------------------
    // Наблюдение за БД
    // ---------------------------------------------------------

    LaunchedEffect(Unit) {

        database
            .scheduleDao()
            .observeAll()
            .collectLatest { savedLessons ->

                if (
                    savedLessons.isNotEmpty()
                ) {

                    val savedDays =
                        savedLessons
                            .groupBy {
                                it.date to
                                        it.weekday
                            }
                            .map {
                                    (
                                        dayInfo,
                                        lessons
                                    ) ->

                                ParsedDay(

                                    date =
                                        dayInfo.first,

                                    weekday =
                                        dayInfo.second,

                                    lessons =
                                        lessons
                                            .sortedBy {
                                                it.number
                                            }
                                            .map {
                                                    entity ->

                                                ParsedLesson(

                                                    number =
                                                        entity.number,

                                                    time =
                                                        entity.time,

                                                    room =
                                                        entity.room,

                                                    subject =
                                                        entity.subject,

                                                    teacher =
                                                        entity.teacher
                                                )
                                            }
                                )
                            }
                            .sortedWith(
                                compareBy<ParsedDay> {

                                    parseScheduleDate(
                                        it.date
                                    )?.time
                                        ?: Long.MAX_VALUE

                                }.thenBy {
                                    it.weekday
                                }
                            )


                    parsedDays =
                        savedDays

                } else {

                    parsedDays =
                        emptyList()
                }
            }
    }


    // ---------------------------------------------------------
    // Основное содержимое расписания
    // ---------------------------------------------------------

    @Composable
    fun ScheduleContent() {
        val dayStripEnabled by remember(context) {
            SettingsRepository.getDayStripEnabled(context)
        }.collectAsState(initial = true)
        ScheduleHomeUi(
            days = visibleDays,
            selectedGroup = selectedGroup,
            groupWarning = groupWarning,
            onGroupClick = { showGroupPicker = true },
            showDayStrip = dayStripEnabled,
            todayLessons = todayLessons,
            tomorrowLessons = tomorrowLessons,
            status = downloadStatus,
            isUpdating = isUpdating,
            lastCheckTime = lastCheckTime,
            lastChangeTime = lastChangeTime,
            lastSyncReport = lastSyncReport,
            onSettings = { showSettings = true },
            onRefresh = {
                if (!isUpdating) {
                    isUpdating = true
                    downloadStatus = "Проверяю расписание..."
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                ScheduleRepository.updateSchedule(
                                    context = context,
                                    database = database,
                                    onStatus = { message ->
                                        withContext(Dispatchers.Main) { downloadStatus = message }
                                    }
                                )
                            }
                            when (result) {
                                is ScheduleUpdateResult.Success -> {
                                    parsedDays = result.days
                                    SettingsRepository.setLastCheckTime(context)
                                    val selectedChanged = if (selectedGroup == "2РПУ-25-1") {
                                        result.changes.isNotEmpty() || result.newDays.isNotEmpty() || result.removedDays.isNotEmpty()
                                    } else {
                                        GroupScheduleDiff.relevantForSelection(selectedGroup, result.selectedGroupChanges)
                                    }
                                    if (selectedChanged) SettingsRepository.setLastChangeTime(context)
                                    SettingsRepository.setLastSyncReport(context,
                                        if (selectedChanged) SyncOutcome.UPDATED else SyncOutcome.UNCHANGED)
                                    downloadStatus = if (selectedGroup != "2РПУ-25-1") {
                                        if (selectedChanged) "Расписание выбранной группы обновлено"
                                        else "Расписание выбранной группы актуально — изменений нет"
                                    } else buildManualUpdateStatus(result)
                                }
                                is ScheduleUpdateResult.NoChanges -> {
                                    // Non-legacy catalog updates return no legacy days. Keep saved legacy data.
                                    if (selectedGroup == "2РПУ-25-1") parsedDays = result.days
                                    SettingsRepository.setLastCheckTime(context)
                                    val selectedChanged = GroupScheduleDiff.relevantForSelection(
                                        selectedGroup, result.selectedGroupChanges
                                    )
                                    if (selectedChanged) SettingsRepository.setLastChangeTime(context)
                                    SettingsRepository.setLastSyncReport(context,
                                        if (selectedChanged) SyncOutcome.UPDATED else SyncOutcome.UNCHANGED)
                                    downloadStatus = if (selectedChanged) "Расписание выбранной группы обновлено"
                                        else "Расписание актуально — изменений нет"
                                }
                                is ScheduleUpdateResult.Error -> {
                                    SettingsRepository.setLastSyncReport(
                                        context, SyncOutcome.FAILED, result.message
                                    )
                                    downloadStatus = result.message
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val message = "Ошибка проверки: ${e.message ?: "неизвестная ошибка"}"
                            downloadStatus = message
                            SettingsRepository.setLastSyncReport(context, SyncOutcome.FAILED, message)
                        } finally {
                            isUpdating = false
                        }
                    }
                }
            }
        )
    }


    // ---------------------------------------------------------
    // Первый запуск / обновление с ранней версии: выбор роли и группы.
    // Повторный запуск мастера доступен из настроек.
    // Не показываем changelog поверх мастера.
    // ---------------------------------------------------------
    if (profileSetupCompleted == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    if (profileSetupCompleted == false || showProfileEditor) {
        ProfileSetupScreen(
            groups = availableGroups,
            initialGroup = selectedGroup,
            onStudentReady = { group ->
                scope.launch {
                    SettingsRepository.completeStudentProfile(context, group)
                    showProfileEditor = false
                }
            },
            onCancel = if (profileSetupCompleted == true) {
                { showProfileEditor = false }
            } else null
        )
        return
    }

    // ---------------------------------------------------------
    // Переход расписание ↔ настройки
    // ---------------------------------------------------------

    AnimatedContent(

        targetState =
            showSettings,

        transitionSpec = {

            (
                    fadeIn(
                        animationSpec =
                            tween(250)
                    ) +

                            slideInHorizontally(
                                animationSpec =
                                    tween(250),

                                initialOffsetX = {

                                    if (targetState) {
                                        it / 4
                                    } else {
                                        -it / 4
                                    }
                                }
                            )

                    ) togetherWith (

                    fadeOut(
                        animationSpec =
                            tween(200)
                    ) +

                            slideOutHorizontally(
                                animationSpec =
                                    tween(200),

                                targetOffsetX = {

                                    if (targetState) {
                                        -it / 4
                                    } else {
                                        it / 4
                                    }
                                }
                            )
                    )
        },

        label =
            "screenTransition"

    ) { settings ->

        if (settings) {

            SettingsScreen(
                onBack = { showSettings = false },
                onEditProfile = {
                    showSettings = false
                    showProfileEditor = true
                }
            )

        } else {

            ScheduleContent()
        }
    }


    if (showGroupPicker) {
        GroupPickerDialog(
            groups = availableGroups,
            selected = selectedGroup,
            onSelect = { group ->
                scope.launch { SettingsRepository.setSelectedGroup(context, group) }
                showGroupPicker = false
            },
            onDismiss = { showGroupPicker = false }
        )
    }

    // ---------------------------------------------------------
    // Окно изменений
    // ---------------------------------------------------------

    if (
        showChangelog &&
        currentChangelog != null
    ) {

        ChangelogDialog(

            version =
                currentChangelog,

            onDismiss = {

                ChangelogManager.markAsShown(
                    context
                )

                showChangelog =
                    false
            }
        )
    }
}


// -------------------------------------------------------------
// Карточка дня
// -------------------------------------------------------------

@Composable
fun DayCard(
    day: ParsedDay,
    modifier: Modifier = Modifier
) {

    var expanded by rememberSaveable(
        day.date,
        day.weekday
    ) {

        mutableStateOf(true)
    }


    val arrowRotation by animateFloatAsState(

        targetValue =
            if (expanded) {
                180f
            } else {
                0f
            },

        label =
            "arrowRotation"
    )


    Card(

        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    vertical = 4.dp
                ),

        shape =
            RoundedCornerShape(20.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
            )
    ) {

        Column(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {

                        expanded =
                            !expanded
                    }
                    .animateContentSize(

                        animationSpec =
                            tween(
                                durationMillis = 220
                            )
                    )
                    .padding(16.dp)
        ) {


            // -------------------------------------------------
            // Шапка дня
            // -------------------------------------------------

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(

                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            day.weekday,

                        style =
                            MaterialTheme
                                .typography
                                .titleLarge
                    )


                    Text(
                        text =
                            day.date,

                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }


                // -------------------------------------------------
                // Количество пар
                // -------------------------------------------------

                AnimatedContent(

                    targetState =
                        day.lessons.size,

                    transitionSpec = {

                        fadeIn(
                            animationSpec =
                                tween(160)
                        ) togetherWith
                                fadeOut(
                                    animationSpec =
                                        tween(120)
                                )
                    },

                    label =
                        "dayLessonCount"

                ) { lessonCount ->

                    Text(

                        text =
                            "$lessonCount пар",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )
                }


                Text(

                    text =
                        "⌄",

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,

                    modifier =
                        Modifier
                            .padding(
                                start = 12.dp
                            )
                            .graphicsLayer {

                                rotationZ =
                                    arrowRotation
                            }
                )
            }


            // -------------------------------------------------
            // Пары
            // -------------------------------------------------

            if (expanded) {

                Column(

                    modifier =
                        Modifier
                            .padding(
                                top = 12.dp
                            )
                            .animateContentSize(

                                animationSpec =
                                    tween(
                                        durationMillis = 220
                                    )
                            ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    day.lessons.forEach { lesson ->

                        key(
                            lesson.number
                        ) {

                            LessonCard(
                                lesson =
                                    lesson
                            )
                        }
                    }
                }
            }
        }
    }
}


// -------------------------------------------------------------
// Карточка пары
// -------------------------------------------------------------

@Composable
fun LessonCard(
    lesson: ParsedLesson
) {

    Card(

        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(

                    animationSpec =
                        tween(
                            durationMillis = 180
                        )
                ),

        shape =
            RoundedCornerShape(16.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
            )
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),

            verticalAlignment =
                Alignment.Top
        ) {


            // -------------------------------------------------
            // Номер пары
            // -------------------------------------------------

            Card(

                shape =
                    RoundedCornerShape(12.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )
            ) {

                Text(

                    text =
                        lesson.number.toString(),

                    modifier =
                        Modifier.padding(

                            horizontal =
                                12.dp,

                            vertical =
                                8.dp
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onPrimary
                )
            }


            // -------------------------------------------------
            // Содержимое пары
            // -------------------------------------------------

            AnimatedContent(

                targetState =
                    lesson,

                transitionSpec = {

                    fadeIn(
                        animationSpec =
                            tween(180)
                    ) togetherWith
                            fadeOut(
                                animationSpec =
                                    tween(140)
                            )
                },

                label =
                    "lessonContent"

            ) { currentLesson ->

                Column(

                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(
                                start = 12.dp
                            ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            2.dp
                        )
                ) {

                    Text(

                        text =
                            currentLesson.subject,

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )


                    if (
                        currentLesson.time
                            .isNotBlank()
                    ) {

                        Text(

                            text =
                                currentLesson.time,

                            modifier =
                                Modifier.padding(
                                    top = 2.dp
                                ),

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
                    }


                    if (
                        currentLesson.room
                            .isNotBlank()
                    ) {

                        Text(

                            text =
                                "Кабинет ${currentLesson.room}",

                            modifier =
                                Modifier.padding(
                                    top = 1.dp
                                ),

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }


                    if (
                        currentLesson.teacher
                            .isNotBlank()
                    ) {

                        Text(

                            text =
                                currentLesson.teacher,

                            modifier =
                                Modifier.padding(
                                    top = 1.dp
                                ),

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}