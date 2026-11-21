package ru.krabikk.nntcschedule

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit


private data class WorkerUiState(
    val state: WorkInfo.State,
    val stateText: String,
    val progressStatus: String?,
    val progressMessage: String?,
    val result: String?,
    val changesCount: Int,
    val newDaysCount: Int,
    val removedDaysCount: Int,
    val downloadTimeMs: Long,
    val pdfParseTimeMs: Long,
    val scheduleParseTimeMs: Long,
    val databaseCompareTimeMs: Long,
    val databaseSaveTimeMs: Long,
    val totalTimeMs: Long,
    val runAttemptCount: Int
)


@Composable
fun DeveloperScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val database = remember {
        ScheduleDatabase.getInstance(context)
    }

    val versionName = remember {
        context.packageManager
            .getPackageInfo(
                context.packageName,
                0
            )
            .versionName
    }

    val checkInterval by SettingsRepository
        .getCheckInterval(context)
        .collectAsState(initial = 30L)

    // Текущий стиль читается из уже существующего DataStore, новые ключи не нужны.
    val selectedInterfaceStyle by SettingsRepository
        .getInterfaceStyle(context)
        .collectAsState(initial = InterfaceStyle.MINIMAL)


    // ---------------------------------------------------------
    // Информация о базе данных
    // ---------------------------------------------------------

    var savedDaysCount by remember {
        mutableStateOf(0)
    }

    var savedLessonsCount by remember {
        mutableStateOf(0)
    }

    var databaseHasSchedule by remember {
        mutableStateOf(false)
    }

    var firstSavedDayDate by remember {
        mutableStateOf<String?>(null)
    }

    var firstSavedDayWeekday by remember {
        mutableStateOf<String?>(null)
    }


    // ---------------------------------------------------------
    // Единый блокировщик тестовых операций БД
    // ---------------------------------------------------------

    var isDatabaseOperationRunning by remember {
        mutableStateOf(false)
    }

    var databaseOperationText by remember {
        mutableStateOf("")
    }


    // ---------------------------------------------------------
    // Общий статус
    // ---------------------------------------------------------

    var developerStatus by remember {
        mutableStateOf("")
    }


    // ---------------------------------------------------------
    // Тест загрузки расписания
    // ---------------------------------------------------------

    var isLoadingTest by remember {
        mutableStateOf(false)
    }


    // ---------------------------------------------------------
    // Worker
    // ---------------------------------------------------------

    var trackedWorkerId by remember {
        mutableStateOf<UUID?>(null)
    }

    var isWorkerStarting by remember {
        mutableStateOf(false)
    }


    val trackedWorkerInfo by produceState<WorkInfo?>(
        initialValue = null,
        key1 = trackedWorkerId
    ) {
        val workerId = trackedWorkerId

        if (workerId == null) {
            value = null
        } else {
            WorkManager
                .getInstance(context)
                .getWorkInfoByIdFlow(workerId)
                .collect { info ->
                    value = info
                }
        }
    }


    // ---------------------------------------------------------
    // Тест анимаций
    // ---------------------------------------------------------

    var showScheduleTest by remember {
        mutableStateOf(false)
    }

    var testDays by remember {
        mutableStateOf(createInitialTestDays())
    }

    var testPairAdded by remember {
        mutableStateOf(false)
    }

    var testDayAdded by remember {
        mutableStateOf(false)
    }


    // ---------------------------------------------------------
    // Сортировка сохранённых занятий
    // ---------------------------------------------------------

    fun sortSavedLessons(
        lessons: List<ScheduleEntity>
    ): List<ScheduleEntity> {
        return lessons.sortedWith(
            compareBy(
                { it.date },
                { it.number },
                { it.time },
                { it.subject },
                { it.room },
                { it.teacher }
            )
        )
    }


    // ---------------------------------------------------------
    // Получение информации о БД
    // ---------------------------------------------------------

    fun refreshDatabaseInfo() {
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    sortSavedLessons(
                        database
                            .scheduleDao()
                            .getAll()
                    )
                }

            savedLessonsCount = result.size

            savedDaysCount =
                result
                    .map {
                        it.date to it.weekday
                    }
                    .distinct()
                    .size

            databaseHasSchedule =
                result.isNotEmpty()

            val firstLesson =
                result.firstOrNull()

            firstSavedDayDate =
                firstLesson?.date

            firstSavedDayWeekday =
                firstLesson?.weekday
        }
    }


    // ---------------------------------------------------------
    // Унифицированный запуск тестовой операции БД
    // ---------------------------------------------------------

    fun launchDatabaseOperation(
        startMessage: String,
        operation: suspend () -> String
    ) {
        if (isDatabaseOperationRunning) {
            return
        }

        isDatabaseOperationRunning = true
        databaseOperationText = "Выполнение..."

        developerStatus = startMessage

        scope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        operation()
                    }

                refreshDatabaseInfo()
                developerStatus = result

            } catch (e: Exception) {
                developerStatus =
                    "Ошибка тестовой операции: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                isDatabaseOperationRunning = false
                databaseOperationText = ""
            }
        }
    }


    // ---------------------------------------------------------
    // Обновляем БД при открытии
    // ---------------------------------------------------------

    LaunchedEffect(Unit) {
        refreshDatabaseInfo()
    }


    // ---------------------------------------------------------
    // Реакция на завершение Worker
    // ---------------------------------------------------------

    LaunchedEffect(
        trackedWorkerInfo?.id,
        trackedWorkerInfo?.state
    ) {
        when (
            trackedWorkerInfo?.state
        ) {
            WorkInfo.State.SUCCEEDED -> {
                refreshDatabaseInfo()

                val result =
                    trackedWorkerInfo
                        ?.outputData
                        ?.getString("result")

                developerStatus =
                    when (result) {
                        "updated" ->
                            "Worker завершён: расписание обновлено"

                        "no_changes" ->
                            "Worker завершён: изменений нет"

                        else ->
                            "Worker завершён успешно"
                    }
            }

            WorkInfo.State.FAILED -> {
                developerStatus =
                    "Worker завершён с ошибкой"
            }

            WorkInfo.State.CANCELLED -> {
                developerStatus =
                    "Worker отменён"
            }

            else -> {
                // Ничего не делаем
            }
        }
    }


    // ---------------------------------------------------------
    // UI-состояние Worker
    // ---------------------------------------------------------

    val workerUiState =
        trackedWorkerInfo?.let { info ->

            val stateText =
                when (info.state) {

                    WorkInfo.State.ENQUEUED -> {

                        if (info.runAttemptCount > 0) {
                            "Ожидает повторной попытки"
                        } else {
                            "В очереди"
                        }
                    }

                    WorkInfo.State.RUNNING ->
                        "Выполняется"

                    WorkInfo.State.SUCCEEDED ->
                        "Завершён"

                    WorkInfo.State.FAILED ->
                        "Завершён с ошибкой"

                    WorkInfo.State.BLOCKED ->
                        "Заблокирован"

                    WorkInfo.State.CANCELLED ->
                        "Отменён"
                }

            WorkerUiState(
                state = info.state,

                stateText = stateText,

                progressStatus =
                    info.progress.getString("status"),

                progressMessage =
                    info.progress.getString("message"),

                result =
                    info.outputData.getString("result"),

                changesCount =
                    info.outputData.getInt(
                        "changes_count",
                        -1
                    ),

                newDaysCount =
                    info.outputData.getInt(
                        "new_days_count",
                        -1
                    ),

                removedDaysCount =
                    info.outputData.getInt(
                        "removed_days_count",
                        -1
                    ),

                downloadTimeMs =
                    info.outputData.getLong(
                        "download_time_ms",
                        -1L
                    ),

                pdfParseTimeMs =
                    info.outputData.getLong(
                        "pdf_parse_time_ms",
                        -1L
                    ),

                scheduleParseTimeMs =
                    info.outputData.getLong(
                        "schedule_parse_time_ms",
                        -1L
                    ),

                databaseCompareTimeMs =
                    info.outputData.getLong(
                        "database_compare_time_ms",
                        -1L
                    ),

                databaseSaveTimeMs =
                    info.outputData.getLong(
                        "database_save_time_ms",
                        -1L
                    ),

                totalTimeMs =
                    info.outputData.getLong(
                        "total_time_ms",
                        -1L
                    ),

                runAttemptCount =
                    info.runAttemptCount
            )
        }
    val workerIsActive =
        trackedWorkerInfo?.state ==
                WorkInfo.State.ENQUEUED ||
                trackedWorkerInfo?.state ==
                WorkInfo.State.RUNNING ||
                trackedWorkerInfo?.state ==
                WorkInfo.State.BLOCKED

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        item {
            Spacer(
                modifier =
                    Modifier.padding(
                        top = 24.dp
                    )
            )
        }


        // -----------------------------------------------------
        // Заголовок
        // -----------------------------------------------------

        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack
                ) {
                    Text("← Назад")
                }

                Text(
                    text = "Разработка",
                    style =
                        MaterialTheme.typography.headlineMedium,
                    modifier =
                        Modifier.padding(
                            start = 8.dp
                        )
                )
            }
        }


        // -----------------------------------------------------
        // Экспериментальные дизайны: только для меню «Разработка».
        // Сохранение выбора уже реализовано SettingsRepository.
        // -----------------------------------------------------
        item(key = "experimental_designs") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Экспериментальные дизайны",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Прототипы для NNTC Schedule 4.0.0. " +
                            "Они скрыты от обычного выбора оформления и могут меняться до релиза.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExperimentalDesigns.available.forEach { experiment ->
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                experiment.title + if (selectedInterfaceStyle == experiment.style) " · Активен" else "",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                experiment.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        SettingsRepository.setInterfaceStyle(context, experiment.style)
                                    }
                                },
                                enabled = selectedInterfaceStyle != experiment.style,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (selectedInterfaceStyle == experiment.style)
                                        "Используется: ${experiment.title}"
                                    else
                                        "Включить: ${experiment.title}"
                                )
                            }
                        }
                    }
                    if (ExperimentalDesigns.isExperimental(selectedInterfaceStyle)) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    SettingsRepository.setInterfaceStyle(context, InterfaceStyle.MINIMAL)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Вернуться к обычному стилю Minimal")
                        }
                    }
                    Text(
                        "Другие обычные стили доступны в Настройки → Стиль интерфейса. " +
                            "Выбор оформления не меняет данные расписания.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // -----------------------------------------------------
        // Состояние приложения
        // -----------------------------------------------------

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),

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
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text =
                            "Состояние приложения",

                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text =
                            "Версия: $versionName"
                    )

                    Text(
                        text =
                            "Автопроверка: ${
                                formatCheckInterval(
                                    checkInterval
                                )
                            }"
                    )

                    Text(
                        text =
                            "Расписание в БД: ${
                                if (databaseHasSchedule) {
                                    "есть"
                                } else {
                                    "нет"
                                }
                            }"
                    )

                    Text(
                        text =
                            "Дней в БД: $savedDaysCount"
                    )

                    Text(
                        text =
                            "Пар в БД: $savedLessonsCount"
                    )

                    Text(
                        text =
                            "Уведомления: ${
                                if (
                                    notificationsAllowed(
                                        context
                                    )
                                ) {
                                    "разрешены"
                                } else {
                                    "запрещены"
                                }
                            }",

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    TextButton(
                        onClick = {
                            refreshDatabaseInfo()

                            developerStatus =
                                "Информация о БД обновлена"
                        }
                    ) {
                        Text(
                            "Обновить информацию"
                        )
                    }
                }
            }
        }


        // Безопасная read-only диагностика перед релизом.
        item { ReleaseDiagnosticsCard() }

        // 3.1.0-alpha.1: безопасная экспериментальная диагностика всех групп.
        item { GroupCatalogDiagnosticsCard() }

        // -----------------------------------------------------
        // Тестовые изменения БД
        // -----------------------------------------------------

        item {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec =
                                tween(220)
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
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text =
                            "Тестовые изменения БД",

                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text =
                            "Эти инструменты специально меняют сохранённое расписание, чтобы проверить обнаружение изменений.",

                        style =
                            MaterialTheme.typography.bodyMedium,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )


                    AnimatedContent(
                        targetState =
                            isDatabaseOperationRunning,

                        transitionSpec = {
                            fadeIn(
                                animationSpec =
                                    tween(150)
                            ) togetherWith
                                    fadeOut(
                                        animationSpec =
                                            tween(100)
                                    )
                        },

                        label =
                            "databaseOperationState"
                    ) { running ->

                        if (running) {
                            Text(
                                text =
                                    "Сейчас: $databaseOperationText",

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        } else {
                            Text(
                                text =
                                    "Тестовые операции готовы",

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }
                    }


                    // -------------------------------------------------
                    // Информация о первом дне
                    // -------------------------------------------------

                    if (
                        firstSavedDayDate != null
                    ) {
                        Text(
                            text =
                                "Первый сохранённый день: " +
                                        firstSavedDayDate +
                                        if (
                                            !firstSavedDayWeekday
                                                .isNullOrBlank()
                                        ) {
                                            " ($firstSavedDayWeekday)"
                                        } else {
                                            ""
                                        },

                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text =
                                "В базе данных нет сохранённого расписания.",

                            style =
                                MaterialTheme.typography.bodyMedium,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }


                    // -------------------------------------------------
                    // Удалить целый день
                    // -------------------------------------------------

                    Button(
                        onClick = {
                            if (
                                firstSavedDayDate == null
                            ) {
                                return@Button
                            }

                            launchDatabaseOperation(
                                startMessage =
                                    "Тест: удаляю сохранённый день..."
                            ) {
                                val lessons =
                                    sortSavedLessons(
                                        database
                                            .scheduleDao()
                                            .getAll()
                                    )

                                val firstLesson =
                                    lessons.firstOrNull()

                                if (
                                    firstLesson == null
                                ) {
                                    return@launchDatabaseOperation(
                                            "Тест: в БД нет расписания"
                                            )
                                }

                                val targetDate =
                                    firstLesson.date

                                val remainingLessons =
                                    lessons.filter {
                                        it.date !=
                                                targetDate
                                    }

                                database
                                    .scheduleDao()
                                    .replaceAll(
                                        remainingLessons
                                    )

                                "Тест: удалён день $targetDate. Теперь запусти Worker."
                            }
                        },

                        enabled =
                            !isDatabaseOperationRunning &&
                                    firstSavedDayDate != null,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        AnimatedContent(
                            targetState =
                                isDatabaseOperationRunning,

                            transitionSpec = {
                                fadeIn(
                                    animationSpec =
                                        tween(150)
                                ) togetherWith
                                        fadeOut(
                                            animationSpec =
                                                tween(100)
                                        )
                            },

                            label =
                                "deleteDayButton"
                        ) { running ->
                            Text(
                                if (running) {
                                    "Выполнение..."
                                } else {
                                    "Удалить сохранённый день"
                                }
                            )
                        }
                    }


                    // -------------------------------------------------
                    // Изменить первую пару
                    // -------------------------------------------------

                    Button(
                        onClick = {
                            launchDatabaseOperation(
                                startMessage =
                                    "Тест: изменяю первую сохранённую пару..."
                            ) {
                                val lessons =
                                    sortSavedLessons(
                                        database
                                            .scheduleDao()
                                            .getAll()
                                    )

                                val firstLesson =
                                    lessons.firstOrNull()

                                if (
                                    firstLesson == null
                                ) {
                                    return@launchDatabaseOperation(
                                            "Тест: в БД нет расписания"
                                            )
                                }

                                val modifiedLessons =
                                    lessons.map { lesson ->
                                        if (
                                            lesson.date ==
                                            firstLesson.date &&
                                            lesson.number ==
                                            firstLesson.number
                                        ) {
                                            lesson.copy(
                                                subject =
                                                    "ТЕСТОВОЕ ИЗМЕНЕНИЕ",

                                                room =
                                                    "999"
                                            )
                                        } else {
                                            lesson
                                        }
                                    }

                                database
                                    .scheduleDao()
                                    .replaceAll(
                                        modifiedLessons
                                    )

                                "Тест: пара №${firstLesson.number} изменена. Теперь запусти Worker."
                            }
                        },

                        enabled =
                            !isDatabaseOperationRunning &&
                                    databaseHasSchedule,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Изменить первую пару"
                        )
                    }


                    // -------------------------------------------------
                    // Удалить первую пару
                    // -------------------------------------------------

                    Button(
                        onClick = {
                            launchDatabaseOperation(
                                startMessage =
                                    "Тест: удаляю первую пару из БД..."
                            ) {
                                val lessons =
                                    sortSavedLessons(
                                        database
                                            .scheduleDao()
                                            .getAll()
                                    )

                                val firstLesson =
                                    lessons.firstOrNull()

                                if (
                                    firstLesson == null
                                ) {
                                    return@launchDatabaseOperation(
                                            "Тест: в БД нет расписания"
                                            )
                                }

                                val remainingLessons =
                                    lessons.filter { lesson ->
                                        !(
                                                lesson.date ==
                                                        firstLesson.date &&
                                                        lesson.number ==
                                                        firstLesson.number
                                                )
                                    }

                                database
                                    .scheduleDao()
                                    .replaceAll(
                                        remainingLessons
                                    )

                                "Тест: удалена пара №${firstLesson.number}. Теперь запусти Worker."
                            }
                        },

                        enabled =
                            !isDatabaseOperationRunning &&
                                    databaseHasSchedule,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Удалить первую пару"
                        )
                    }


                    // -------------------------------------------------
                    // Добавить фальшивую пару №99
                    // -------------------------------------------------

                    Button(
                        onClick = {
                            launchDatabaseOperation(
                                startMessage =
                                    "Тест: добавляю фальшивую пару №99..."
                            ) {
                                val lessons =
                                    sortSavedLessons(
                                        database
                                            .scheduleDao()
                                            .getAll()
                                    )

                                val firstLesson =
                                    lessons.firstOrNull()

                                if (
                                    firstLesson == null
                                ) {
                                    return@launchDatabaseOperation(
                                            "Тест: в БД нет расписания"
                                            )
                                }

                                val dayAlreadyHasFakeLesson =
                                    lessons.any { lesson ->
                                        lesson.date ==
                                                firstLesson.date &&
                                                lesson.number == 99
                                    }

                                if (
                                    dayAlreadyHasFakeLesson
                                ) {
                                    return@launchDatabaseOperation(
                                            "Тест: пара №99 уже существует. Теперь запусти Worker."
                                            )
                                }

                                val fakeLesson =
                                    ScheduleEntity(
                                        date =
                                            firstLesson.date,

                                        weekday =
                                            firstLesson.weekday,

                                        number =
                                            99,

                                        time =
                                            "16:00–17:30",

                                        room =
                                            "999",

                                        subject =
                                            "ФАЛЬШИВАЯ ПАРА",

                                        teacher =
                                            "Тестовый преподаватель"
                                    )

                                database
                                    .scheduleDao()
                                    .replaceAll(
                                        lessons + fakeLesson
                                    )

                                "Тест: добавлена фальшивая пара №99. Теперь запусти Worker."
                            }
                        },

                        enabled =
                            !isDatabaseOperationRunning &&
                                    databaseHasSchedule,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        AnimatedContent(
                            targetState =
                                isDatabaseOperationRunning,

                            transitionSpec = {
                                fadeIn(
                                    animationSpec =
                                        tween(150)
                                ) togetherWith
                                        fadeOut(
                                            animationSpec =
                                                tween(100)
                                        )
                            },

                            label =
                                "fakeLessonButton"
                        ) { running ->
                            Text(
                                if (running) {
                                    "Выполнение..."
                                } else {
                                    "Добавить фальшивую пару №99"
                                }
                            )
                        }
                    }
                }
            }
        }


        // -----------------------------------------------------
        // Информация
        // -----------------------------------------------------

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),

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
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text =
                            "Экран разработчика",

                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text =
                            "Здесь находятся инструменты для тестирования приложения.",

                        style =
                            MaterialTheme.typography.bodyMedium,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }
        }


        // -----------------------------------------------------
        // Анимации
        // -----------------------------------------------------

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),

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
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text =
                            "Анимации",

                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text =
                            "Инструменты для проверки анимаций интерфейса.",

                        style =
                            MaterialTheme.typography.bodyMedium,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            showScheduleTest =
                                !showScheduleTest
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (showScheduleTest) {
                                "Закрыть тест расписания"
                            } else {
                                "Тест изменения расписания"
                            }
                        )
                    }
                }
            }
        }


        // -----------------------------------------------------
        // Тестовое расписание
        // -----------------------------------------------------

        if (showScheduleTest) {

            item {
                Card(
                    modifier =
                        Modifier.fillMaxWidth(),

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
                            Modifier.padding(16.dp),

                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            text =
                                "Тестовое расписание",

                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Text(
                            text =
                                "Эти данные существуют только на экране разработчика и не влияют на настоящее расписание.",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )


                        // Добавление / удаление пары

                        Button(
                            onClick = {
                                if (!testPairAdded) {

                                    testDays =
                                        testDays.mapIndexed {
                                                index,
                                                day ->

                                            if (
                                                index == 0
                                            ) {
                                                day.copy(
                                                    lessons =
                                                        day.lessons +
                                                                ParsedLesson(
                                                                    number =
                                                                        day.lessons.size + 1,

                                                                    time =
                                                                        "15:10–16:40",

                                                                    room =
                                                                        "29",

                                                                    subject =
                                                                        "ТЕСТОВАЯ ПАРА",

                                                                    teacher =
                                                                        "Тестовый преподаватель"
                                                                )
                                                )
                                            } else {
                                                day
                                            }
                                        }

                                    testPairAdded =
                                        true
                                } else {

                                    testDays =
                                        testDays.mapIndexed {
                                                index,
                                                day ->

                                            if (
                                                index == 0
                                            ) {
                                                day.copy(
                                                    lessons =
                                                        day.lessons
                                                            .dropLast(1)
                                                )
                                            } else {
                                                day
                                            }
                                        }

                                    testPairAdded =
                                        false
                                }
                            },

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (testPairAdded) {
                                    "Удалить тестовую пару"
                                } else {
                                    "Добавить тестовую пару"
                                }
                            )
                        }


                        // Добавление / удаление дня

                        Button(
                            onClick = {
                                if (!testDayAdded) {

                                    testDays =
                                        testDays +
                                                ParsedDay(
                                                    date =
                                                        "23 сентября 2026 г.",

                                                    weekday =
                                                        "Тестовая среда",

                                                    lessons =
                                                        listOf(
                                                            ParsedLesson(
                                                                number = 1,

                                                                time =
                                                                    "8:10–9:40",

                                                                room =
                                                                    "101",

                                                                subject =
                                                                    "ТЕСТОВЫЙ ПРЕДМЕТ",

                                                                teacher =
                                                                    "Тестовый преподаватель"
                                                            ),

                                                            ParsedLesson(
                                                                number = 2,

                                                                time =
                                                                    "10:00–11:30",

                                                                room =
                                                                    "102",

                                                                subject =
                                                                    "ТЕСТОВЫЙ ПРЕДМЕТ 2",

                                                                teacher =
                                                                    "Другой преподаватель"
                                                            )
                                                        )
                                                )

                                    testDayAdded =
                                        true

                                } else {

                                    testDays =
                                        testDays.dropLast(1)

                                    testDayAdded =
                                        false
                                }
                            },

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (testDayAdded) {
                                    "Удалить тестовый день"
                                } else {
                                    "Добавить тестовый день"
                                }
                            )
                        }


                        // Сброс

                        TextButton(
                            onClick = {
                                testDays =
                                    createInitialTestDays()

                                testPairAdded =
                                    false

                                testDayAdded =
                                    false
                            },

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Сбросить тест"
                            )
                        }
                    }
                }
            }


            items(
                items = testDays,

                key = { day ->
                    "test_${day.date}_${day.weekday}"
                },

                contentType = {
                    "test_day"
                }
            ) { day ->

                DayCard(
                    day = day,

                    modifier =
                        Modifier.animateItem()
                )
            }
        }


        // -----------------------------------------------------
        // Worker
        // -----------------------------------------------------

        item {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec =
                                tween(220)
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
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text =
                            "Фоновая проверка",

                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text =
                            "Ручной запуск того же Worker, который используется для автоматической проверки.",

                        style =
                            MaterialTheme.typography.bodyMedium,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )


                    // -------------------------------------------------
                    // Состояние Worker
                    // -------------------------------------------------

                    AnimatedContent(
                        targetState =
                            workerUiState,

                        transitionSpec = {
                            fadeIn(
                                animationSpec =
                                    tween(180)
                            ) togetherWith
                                    fadeOut(
                                        animationSpec =
                                            tween(120)
                                    )
                        },

                        label =
                            "workerState"
                    ) { state ->

                        if (state != null) {

                            Column(
                                modifier =
                                    Modifier.animateContentSize(
                                        animationSpec =
                                            tween(220)
                                    ),

                                verticalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {

                                Text(
                                    text =
                                        "Состояние Worker: ${state.stateText}",

                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium
                                )

                                if (
                                    !state.progressStatus
                                        .isNullOrBlank()
                                ) {
                                    Text(
                                        text =
                                            "Этап: ${state.progressStatus}",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )
                                }

                                if (
                                    !state.progressMessage
                                        .isNullOrBlank()
                                ) {
                                    Text(
                                        text =
                                            state.progressMessage!!,

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
                                    !state.result
                                        .isNullOrBlank()
                                ) {
                                    Text(
                                        text =
                                            "Результат: ${
                                                formatWorkerResult(
                                                    state.result!!
                                                )
                                            }",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )
                                }

                                if (
                                    state.changesCount >= 0
                                ) {
                                    Text(
                                        text =
                                            "Изменений: ${state.changesCount}",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )
                                }

                                if (
                                    state.newDaysCount >= 0
                                ) {
                                    Text(
                                        text =
                                            "Новых дней: ${state.newDaysCount}",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )
                                }

                                if (
                                    state.removedDaysCount >= 0
                                ) {
                                    Text(
                                        text =
                                            "Удалённых дней: ${state.removedDaysCount}",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )
                                }

                                if (
                                    state.totalTimeMs >= 0L
                                ) {

                                    Spacer(
                                        modifier =
                                            Modifier.padding(
                                                top = 4.dp
                                            )
                                    )

                                    Text(
                                        text =
                                            "Время этапов",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleSmall
                                    )

                                    Text(
                                        text =
                                            "Скачивание PDF: ${
                                                formatWorkerDuration(
                                                    state.downloadTimeMs
                                                )
                                            }",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )

                                    Text(
                                        text =
                                            "Разбор PDF: ${
                                                formatWorkerDuration(
                                                    state.pdfParseTimeMs
                                                )
                                            }",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )

                                    Text(
                                        text =
                                            "Разбор расписания: ${
                                                formatWorkerDuration(
                                                    state.scheduleParseTimeMs
                                                )
                                            }",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )

                                    Text(
                                        text =
                                            "Сравнение с БД: ${
                                                formatWorkerDuration(
                                                    state.databaseCompareTimeMs
                                                )
                                            }",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )

                                    Text(
                                        text =
                                            "Сохранение в БД: ${
                                                formatWorkerDuration(
                                                    state.databaseSaveTimeMs
                                                )
                                            }",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )

                                    Text(
                                        text =
                                            "Всего Worker: ${
                                                formatWorkerDuration(
                                                    state.totalTimeMs
                                                )
                                            }",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )
                                }

                                if (
                                    state.runAttemptCount > 0
                                ) {
                                    Text(
                                        text =
                                            "Попытка: ${
                                                state.runAttemptCount + 1
                                            }",

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
                            }
                        }
                    }


                    // -------------------------------------------------
                    // Обычный запуск Worker
                    // -------------------------------------------------

                    AnimatedContent(
                        targetState =
                            when {
                                isWorkerStarting ->
                                    "Worker запускается..."

                                workerIsActive ->
                                    "Worker выполняется..."

                                else ->
                                    "Запустить Worker сейчас"
                            },

                        transitionSpec = {
                            fadeIn(
                                animationSpec =
                                    tween(150)
                            ) togetherWith
                                    fadeOut(
                                        animationSpec =
                                            tween(100)
                                    )
                        },

                        label =
                            "workerButtonText"
                    ) { buttonText ->

                        Button(
                            onClick = {

                                if (
                                    workerIsActive ||
                                    isWorkerStarting
                                ) {
                                    return@Button
                                }

                                isWorkerStarting =
                                    true

                                developerStatus =
                                    "Worker: постановка в очередь..."

                                val workRequest =
                                    OneTimeWorkRequestBuilder<
                                            ScheduleUpdateWorker
                                            >()
                                        .build()

                                trackedWorkerId =
                                    workRequest.id

                                WorkManager
                                    .getInstance(context)
                                    .enqueue(
                                        workRequest
                                    )

                                isWorkerStarting =
                                    false

                                developerStatus =
                                    "Worker поставлен в очередь"
                            },

                            enabled =
                                !workerIsActive &&
                                        !isWorkerStarting,

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text =
                                    buttonText
                            )
                        }
                    }


                    // -------------------------------------------------
                    // Тест retry
                    // -------------------------------------------------

                    Button(
                        onClick = {

                            if (
                                workerIsActive ||
                                isWorkerStarting
                            ) {
                                return@Button
                            }

                            isWorkerStarting =
                                true

                            developerStatus =
                                "Тест retry: запускаю Worker..."

                            val testData =
                                Data.Builder()
                                    .putBoolean(
                                        "force_error",
                                        true
                                    )
                                    .build()

                            val workRequest =
                                OneTimeWorkRequestBuilder<
                                        ScheduleUpdateWorker
                                        >()
                                    .setInputData(
                                        testData
                                    )
                                    .setBackoffCriteria(
                                        BackoffPolicy.EXPONENTIAL,
                                        30L,
                                        TimeUnit.SECONDS
                                    )
                                    .build()

                            trackedWorkerId =
                                workRequest.id

                            WorkManager
                                .getInstance(context)
                                .enqueue(
                                    workRequest
                                )

                            isWorkerStarting =
                                false

                            developerStatus =
                                "Тест retry запущен"
                        },

                        enabled =
                            !workerIsActive &&
                                    !isWorkerStarting,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Тест retry Worker"
                        )
                    }
                }
            }
        }


        // -----------------------------------------------------
        // Прочие инструменты
        // -----------------------------------------------------

        item {
            Card(
                modifier =
                    Modifier.fillMaxWidth(),

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
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text =
                            "Прочие инструменты",

                        style =
                            MaterialTheme.typography.titleMedium
                    )


                    // -------------------------------------------------
                    // Тест уведомления
                    // -------------------------------------------------

                    Button(
                        onClick = {

                            val permissionGranted =
                                notificationsAllowed(
                                    context
                                )

                            if (
                                !permissionGranted
                            ) {

                                developerStatus =
                                    "Уведомления запрещены для приложения"

                            } else {

                                NotificationHelper
                                    .createChannel(
                                        context
                                    )

                                NotificationHelper
                                    .showScheduleChangedNotification(
                                        context =
                                            context,

                                        title =
                                            "Тест уведомления",

                                        text =
                                            "Это тестовое уведомление"
                                    )

                                developerStatus =
                                    "Тестовое уведомление отправлено"
                            }
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Тест уведомления"
                        )
                    }


                    // -------------------------------------------------
                    // Тест загрузки расписания
                    // -------------------------------------------------

                    Button(
                        onClick = {

                            if (isLoadingTest) {
                                return@Button
                            }

                            isLoadingTest =
                                true

                            developerStatus =
                                "Тест: начинаю загрузку расписания..."

                            scope.launch {

                                try {

                                    val result =
                                        withContext(
                                            Dispatchers.IO
                                        ) {

                                            ScheduleRepository
                                                .updateSchedule(
                                                    context =
                                                        context,

                                                    database =
                                                        database,

                                                    onStatus = {
                                                            status ->

                                                        withContext(
                                                            Dispatchers.Main
                                                        ) {
                                                            developerStatus =
                                                                "Тест: $status"
                                                        }
                                                    }
                                                )
                                        }

                                    when (result) {

                                        is ScheduleUpdateResult.Success -> {

                                            developerStatus =
                                                buildLoadTestSuccessMessage(
                                                    result
                                                )

                                            refreshDatabaseInfo()
                                        }

                                        is ScheduleUpdateResult.NoChanges -> {

                                            developerStatus =
                                                "Тест загрузки завершён: изменений в расписании нет"

                                            refreshDatabaseInfo()
                                        }

                                        is ScheduleUpdateResult.Error -> {

                                            developerStatus =
                                                "Ошибка теста загрузки: ${result.message}"
                                        }
                                    }

                                } catch (e: Exception) {

                                    developerStatus =
                                        "Ошибка теста загрузки: ${
                                            e.message
                                                ?: "неизвестная ошибка"
                                        }"

                                } finally {

                                    isLoadingTest =
                                        false
                                }
                            }
                        },

                        enabled =
                            !isLoadingTest,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            if (isLoadingTest) {
                                "Загрузка..."
                            } else {
                                "Тест загрузки расписания"
                            }
                        )
                    }
                }
            }
        }


        // -----------------------------------------------------
        // Общий статус
        // -----------------------------------------------------

        item {

            if (
                developerStatus.isNotBlank()
            ) {

                AnimatedContent(
                    targetState =
                        developerStatus,

                    transitionSpec = {
                        fadeIn() togetherWith
                                fadeOut()
                    },

                    label =
                        "developerStatus"
                ) { status ->

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(16.dp),

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                            )
                    ) {

                        Text(
                            text =
                                status,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,

                            modifier =
                                Modifier.padding(
                                    16.dp
                                )
                        )
                    }
                }
            }
        }


        item {
            Spacer(
                modifier =
                    Modifier.padding(
                        bottom = 16.dp
                    )
            )
        }
    }
}


// -------------------------------------------------------------
// Форматирование результата Worker
// -------------------------------------------------------------

private fun formatWorkerResult(
    result: String
): String {
    return when (result) {
        "updated" ->
            "расписание обновлено"

        "no_changes" ->
            "изменений нет"

        else ->
            result
    }
}

// -------------------------------------------------------------
// Форматирование времени Worker
// -------------------------------------------------------------

private fun formatWorkerDuration(
    milliseconds: Long
): String {

    if (
        milliseconds < 0L
    ) {
        return "—"
    }

    if (
        milliseconds < 1000L
    ) {
        return "$milliseconds мс"
    }

    val seconds =
        milliseconds / 1000.0

    return String.format(
        java.util.Locale.getDefault(),
        "%.2f с",
        seconds
    )
}

// -------------------------------------------------------------
// Сообщение о результате реальной загрузки
// -------------------------------------------------------------

private fun buildLoadTestSuccessMessage(
    result: ScheduleUpdateResult.Success
): String {
    val parts =
        mutableListOf<String>()

    if (result.changes.isNotEmpty()) {
        parts +=
            "изменений пар: ${result.changes.size}"
    }

    if (result.newDays.isNotEmpty()) {
        parts +=
            "новых дней: ${result.newDays.size}"
    }

    if (result.removedDays.isNotEmpty()) {
        parts +=
            "удалённых дней: ${result.removedDays.size}"
    }

    if (parts.isEmpty()) {
        return "Тест загрузки завершён: расписание обработано успешно"
    }

    return "Тест загрузки завершён: " +
            parts.joinToString(", ")
}


// -------------------------------------------------------------
// Проверка разрешения на уведомления
// -------------------------------------------------------------

private fun notificationsAllowed(
    context: android.content.Context
): Boolean {

    return if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU
    ) {

        context.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS
        ) ==
                android.content.pm.PackageManager
                    .PERMISSION_GRANTED

    } else {
        true
    }
}


// -------------------------------------------------------------
// Начальное тестовое расписание
// -------------------------------------------------------------

private fun createInitialTestDays(): List<ParsedDay> {

    return listOf(

        ParsedDay(
            date =
                "21 сентября 2026 г.",

            weekday =
                "Тестовый понедельник",

            lessons =
                listOf(

                    ParsedLesson(
                        number =
                            1,

                        time =
                            "8:10–9:40",

                        room =
                            "29",

                        subject =
                            "Основы электротехники",

                        teacher =
                            "Тестовый преподаватель"
                    ),

                    ParsedLesson(
                        number =
                            2,

                        time =
                            "10:00–11:30",

                        room =
                            "29",

                        subject =
                            "Основы радиотехники",

                        teacher =
                            "Тестовый преподаватель"
                    ),

                    ParsedLesson(
                        number =
                            3,

                        time =
                            "11:50–13:20",

                        room =
                            "21",

                        subject =
                            "Технология машиностроения",

                        teacher =
                            "Тестовый преподаватель"
                    )
                )
        ),

        ParsedDay(
            date =
                "22 сентября 2026 г.",

            weekday =
                "Тестовый вторник",

            lessons =
                listOf(

                    ParsedLesson(
                        number =
                            1,

                        time =
                            "8:10–9:40",

                        room =
                            "15",

                        subject =
                            "Математика",

                        teacher =
                            "Тестовый преподаватель"
                    ),

                    ParsedLesson(
                        number =
                            2,

                        time =
                            "10:00–11:30",

                        room =
                            "15",

                        subject =
                            "Информатика",

                        teacher =
                            "Тестовый преподаватель"
                    )
                )
        )
    )
}