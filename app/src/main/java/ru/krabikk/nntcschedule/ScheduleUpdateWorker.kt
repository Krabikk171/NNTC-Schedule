package ru.krabikk.nntcschedule

import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.krabikk.nntcschedule.v3.GroupScheduleDiff


class ScheduleUpdateWorker(
    context: android.content.Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    context,
    workerParams
) {

    override suspend fun doWork(): Result {

        Log.d(
            "SCHEDULE_WORKER",
            "Worker запущен"
        )

        // ---------------------------------------------------------
        // Таймеры этапов Worker
        // ---------------------------------------------------------

        val totalStartTime =
            SystemClock.elapsedRealtime()

        var downloadStartTime = 0L
        var pdfParseStartTime = 0L
        var scheduleParseStartTime = 0L
        var databaseCompareStartTime = 0L
        var databaseSaveStartTime = 0L

        var downloadTimeMs = -1L
        var pdfParseTimeMs = -1L
        var scheduleParseTimeMs = -1L
        var databaseCompareTimeMs = -1L
        var databaseSaveTimeMs = -1L

        var totalTimeMs = -1L

        // ---------------------------------------------------------
        // Тест retry
        // ---------------------------------------------------------

        val forceError =
            inputData.getBoolean(
                "force_error",
                false
            )

        if (
            forceError &&
            runAttemptCount == 0
        ) {

            Log.d(
                "SCHEDULE_WORKER",
                "ТЕСТ: имитируем ошибку и retry"
            )

            setWorkerStatus(
                status = "Ошибка",
                message =
                    "Тестовая ошибка. Worker будет запущен повторно."
            )

            return Result.retry()
        }


        // ---------------------------------------------------------
        // Подготовка
        // ---------------------------------------------------------

        setWorkerStatus(
            status = "Запущен",
            message =
                "Подготовка проверки расписания..."
        )


        val database =
            ScheduleDatabase.getInstance(
                applicationContext
            )


        setWorkerStatus(
            status = "Проверка",
            message =
                "Начинаю проверку расписания..."
        )


        // ---------------------------------------------------------
        // Обновление расписания
        // ---------------------------------------------------------

        val result =
            ScheduleRepository.updateSchedule(
                context =
                    applicationContext,

                database =
                    database,

                onStatus = { status ->

                    val now =
                        SystemClock.elapsedRealtime()

                    when {

                        status == "Скачиваю PDF..." -> {

                            downloadStartTime =
                                now
                        }


                        status.startsWith("PDF скачан.") -> {

                            if (
                                downloadStartTime > 0L
                            ) {
                                downloadTimeMs =
                                    now - downloadStartTime
                            }

                            pdfParseStartTime =
                                now
                        }


                        status == "Проверяю данные расписания..." -> {

                            if (
                                pdfParseStartTime > 0L
                            ) {
                                pdfParseTimeMs =
                                    now - pdfParseStartTime
                            }

                            scheduleParseStartTime =
                                now
                        }


                        status == "Сравниваю с сохранённым расписанием..." -> {

                            if (
                                scheduleParseStartTime > 0L
                            ) {
                                scheduleParseTimeMs =
                                    now - scheduleParseStartTime
                            }

                            databaseCompareStartTime =
                                now
                        }

                        status == "Сравнение с БД завершено." -> {

                            if (
                                databaseCompareStartTime > 0L
                            ) {
                                databaseCompareTimeMs =
                                    now - databaseCompareStartTime
                            }
                        }

                        status == "Сохраняю расписание..." -> {

                            if (
                                databaseCompareStartTime > 0L
                            ) {
                                databaseCompareTimeMs =
                                    now - databaseCompareStartTime
                            }

                            databaseSaveStartTime =
                                now
                        }
                        status == "Сохранение в БД завершено." -> {

                            if (
                                databaseSaveStartTime > 0L
                            ) {
                                databaseSaveTimeMs =
                                    now - databaseSaveStartTime
                            }
                        }
                    }


                    setWorkerStatus(
                        status = "Проверка",
                        message = status
                    )
                }
            )

        // ---------------------------------------------------------
        // Завершаем измерение времени
        // ---------------------------------------------------------

        val finishedTime =
            SystemClock.elapsedRealtime()

        totalTimeMs =
            finishedTime - totalStartTime

        if (
            databaseSaveStartTime > 0L
        ) {
            databaseSaveTimeMs =
                finishedTime - databaseSaveStartTime
        } else {
            databaseSaveTimeMs = 0L
        }

        Log.d(
            "SCHEDULE_WORKER",
            "Результат: ${result::class.simpleName}"
        )


        // ---------------------------------------------------------
        // Обрабатываем результат
        // ---------------------------------------------------------

        return when (result) {

            // =====================================================
            // Расписание изменилось
            // =====================================================

            is ScheduleUpdateResult.Success -> {

                SettingsRepository.setLastCheckTime(
                    applicationContext
                )

                val activeGroup = SettingsRepository.getSelectedGroup(applicationContext).first()
                val hasSelectedCatalogChanges = GroupScheduleDiff.relevantForSelection(
                    activeGroup, result.selectedGroupChanges
                )

                val hasChanges =
                    result.changes.isNotEmpty() ||
                            result.newDays.isNotEmpty() ||
                            result.removedDays.isNotEmpty()

                val activeScheduleChanged = if (activeGroup == "2РПУ-25-1") hasChanges else hasSelectedCatalogChanges
                SettingsRepository.setLastSyncReport(
                    applicationContext,
                    if (activeScheduleChanged) SyncOutcome.UPDATED else SyncOutcome.UNCHANGED
                )


                if (
                    activeScheduleChanged
                ) {

                    SettingsRepository.setLastChangeTime(
                        applicationContext
                    )
                }


                Log.d(
                    "SCHEDULE_WORKER",
                    "Изменений пар: ${result.changes.size}"
                )


                Log.d(
                    "SCHEDULE_WORKER",
                    "Новых дней: ${result.newDays.size}"
                )


                Log.d(
                    "SCHEDULE_WORKER",
                    "Удалённых дней: ${result.removedDays.size}"
                )


                // -------------------------------------------------
                // Логи изменений
                // -------------------------------------------------

                result.changes.forEach { change ->

                    Log.d(
                        "SCHEDULE_WORKER",
                        buildChangeLog(
                            change
                        )
                    )
                }


                result.newDays.forEach { newDay ->

                    Log.d(
                        "SCHEDULE_WORKER",
                        buildNewDayLog(
                            newDay
                        )
                    )
                }


                result.removedDays.forEach { removedDay ->

                    Log.d(
                        "SCHEDULE_WORKER",
                        buildRemovedDayLog(
                            removedDay
                        )
                    )
                }


                // -------------------------------------------------
                // Уведомление
                // -------------------------------------------------

                // Notify only about the currently selected group.
                // No notifications on first import or dubious group extraction.
                if (activeGroup == "2РПУ-25-1" && hasChanges) {
                    NotificationHelper.showScheduleChangedNotification(
                        context = applicationContext,
                        title = buildGroupedNotificationTitle(result),
                        text = buildGroupedNotificationText(result)
                    )
                } else if (hasSelectedCatalogChanges) {
                    // Recheck after a long import: user may have changed the group.
                    notifySelectedGroup(result.selectedGroupChanges)
                }


                // -------------------------------------------------
                // Итоговый статус
                // -------------------------------------------------

                val logicalChanges = countLogicalChanges(result)
                val reportingLegacy = activeGroup == "2РПУ-25-1"
                val reportedLessons = if (reportingLegacy) result.changes.size
                    else result.selectedGroupChanges?.changedLessons ?: 0
                val reportedNewDays = if (reportingLegacy) result.newDays.size
                    else result.selectedGroupChanges?.newDays?.size ?: 0
                val reportedRemovedDays = if (reportingLegacy) result.removedDays.size else 0
                val summaryText = when {
                    !activeScheduleChanged -> "Изменений для выбранной группы нет"
                    reportingLegacy -> "Расписание обновлено. $logicalChanges"
                    else -> "Расписание ${activeGroup} обновлено: ${result.selectedGroupChanges?.describe().orEmpty()}"
                }

                setWorkerStatus(
                    status = "Завершён",

                    message =
                        summaryText,

                    result =
                        if (activeScheduleChanged) "updated" else "no_changes",

                    changesCount =
                        reportedLessons,

                    newDaysCount =
                        reportedNewDays,

                    removedDaysCount =
                        reportedRemovedDays
                )


                Result.success(
                    Data.Builder()

                        .putString(
                            "result",
                            if (activeScheduleChanged) "updated" else "no_changes"
                        )

                        .putInt(
                            "changes_count",
                            reportedLessons
                        )

                        .putInt(
                            "new_days_count",
                            reportedNewDays
                        )

                        .putInt(
                            "removed_days_count",
                            reportedRemovedDays
                        )

                        .putString(
                            "message",
                            logicalChanges
                        )

                        .putLong(
                            "download_time_ms",
                            downloadTimeMs
                        )

                        .putLong(
                            "pdf_parse_time_ms",
                            pdfParseTimeMs
                        )

                        .putLong(
                            "schedule_parse_time_ms",
                            scheduleParseTimeMs
                        )

                        .putLong(
                            "database_compare_time_ms",
                            databaseCompareTimeMs
                        )

                        .putLong(
                            "database_save_time_ms",
                            databaseSaveTimeMs
                        )

                        .putLong(
                            "total_time_ms",
                            totalTimeMs
                        )

                        .build()
                )
            }


            // =====================================================
            // Изменений нет
            // =====================================================

            is ScheduleUpdateResult.NoChanges -> {

                SettingsRepository.setLastCheckTime(applicationContext)
                val activeGroup = SettingsRepository.getSelectedGroup(applicationContext).first()
                val groupChanges = result.selectedGroupChanges
                val selectedChanged = GroupScheduleDiff.relevantForSelection(activeGroup, groupChanges)
                SettingsRepository.setLastSyncReport(
                    applicationContext,
                    if (selectedChanged) SyncOutcome.UPDATED else SyncOutcome.UNCHANGED
                )
                if (selectedChanged) {
                    SettingsRepository.setLastChangeTime(applicationContext)
                    notifySelectedGroup(groupChanges)
                }


                Log.d(
                    "SCHEDULE_WORKER",
                    if (selectedChanged) "Выбранная группа обновлена" else "Расписание не изменилось"
                )


                setWorkerStatus(
                    status = "Завершён",

                    message =
                        if (selectedChanged) "Расписание выбранной группы обновлено" else "Изменений в расписании нет",

                    result =
                        if (selectedChanged) "updated" else "no_changes",

                    changesCount =
                        if (selectedChanged) groupChanges?.changedLessons ?: 0 else 0,

                    newDaysCount =
                        if (selectedChanged) groupChanges?.newDays?.size ?: 0 else 0,

                    removedDaysCount =
                        0
                )


                Result.success(
                    Data.Builder()

                        .putString(
                            "result",
                            if (selectedChanged) "updated" else "no_changes"
                        )

                        .putInt(
                            "changes_count",
                            if (selectedChanged) groupChanges?.changedLessons ?: 0 else 0
                        )

                        .putInt(
                            "new_days_count",
                            if (selectedChanged) groupChanges?.newDays?.size ?: 0 else 0
                        )

                        .putInt(
                            "removed_days_count",
                            0
                        )



                        .putString(
                            "message",
                            if (selectedChanged) "Расписание выбранной группы обновлено" else "Изменений в расписании нет"
                        )

                        .putLong(
                            "download_time_ms",
                            downloadTimeMs
                        )

                        .putLong(
                            "pdf_parse_time_ms",
                            pdfParseTimeMs
                        )

                        .putLong(
                            "schedule_parse_time_ms",
                            scheduleParseTimeMs
                        )

                        .putLong(
                            "database_compare_time_ms",
                            databaseCompareTimeMs
                        )

                        .putLong(
                            "database_save_time_ms",
                            databaseSaveTimeMs
                        )

                        .putLong(
                            "total_time_ms",
                            totalTimeMs
                        )

                        .build()
                )
            }


            // =====================================================
            // Ошибка
            // =====================================================

            is ScheduleUpdateResult.Error -> {

                SettingsRepository.setLastSyncReport(
                    applicationContext, SyncOutcome.FAILED, result.message
                )

                Log.e(
                    "SCHEDULE_WORKER",
                    "Ошибка: ${result.message}"
                )


                setWorkerStatus(
                    status = "Ошибка",

                    message =
                        result.message
                )


                Result.retry()
            }
        }
    }


    // -------------------------------------------------------------
    // Состояние Worker
    // -------------------------------------------------------------

    private suspend fun setWorkerStatus(
        status: String,
        message: String,
        result: String? = null,
        changesCount: Int? = null,
        newDaysCount: Int? = null,
        removedDaysCount: Int? = null
    ) {

        val builder =
            Data.Builder()
                .putString(
                    "status",
                    status
                )
                .putString(
                    "message",
                    message
                )


        if (
            result != null
        ) {

            builder.putString(
                "result",
                result
            )
        }


        if (
            changesCount != null
        ) {

            builder.putInt(
                "changes_count",
                changesCount
            )
        }


        if (
            newDaysCount != null
        ) {

            builder.putInt(
                "new_days_count",
                newDaysCount
            )
        }


        if (
            removedDaysCount != null
        ) {

            builder.putInt(
                "removed_days_count",
                removedDaysCount
            )
        }


        setProgress(
            builder.build()
        )
    }


    // -------------------------------------------------------------
    // Заголовок уведомления
    // -------------------------------------------------------------

    private fun buildGroupedNotificationTitle(
        result: ScheduleUpdateResult.Success
    ): String {

        val hasChanges =
            result.changes.isNotEmpty()

        val hasNewDays =
            result.newDays.isNotEmpty()

        val hasRemovedDays =
            result.removedDays.isNotEmpty()


        return when {

            hasNewDays ||
                    hasRemovedDays ->

                "Изменения расписания"


            hasChanges ->

                "Изменения расписания"


            else ->

                "Расписание обновлено"
        }
    }


    // -------------------------------------------------------------
    // Основной текст уведомления
    // -------------------------------------------------------------
    //
    // ВАЖНО:
    //
    // Здесь больше НЕТ отдельного preview.
    // И нет повторного вывода той же информации.
    //
    // Сначала идёт одна строка-сводка,
    // затем каждый конкретный день и изменение.
    //

    private fun buildGroupedNotificationText(
        result: ScheduleUpdateResult.Success
    ): String {

        val builder =
            StringBuilder()


        // ---------------------------------------------------------
        // Сводка
        // ---------------------------------------------------------

        val addedCount =
            result.changes.count {

                it.oldLesson == null &&
                        it.newLesson != null
            }


        val removedCount =
            result.changes.count {

                it.oldLesson != null &&
                        it.newLesson == null
            }


        val changedCount =
            result.changes.count {

                it.oldLesson != null &&
                        it.newLesson != null
            }


        val summaryParts =
            mutableListOf<String>()


        if (
            addedCount > 0
        ) {

            summaryParts +=
                "Добавлено пар: $addedCount"
        }


        if (
            changedCount > 0
        ) {

            summaryParts +=
                "Изменено пар: $changedCount"
        }


        if (
            removedCount > 0
        ) {

            summaryParts +=
                "Удалено пар: $removedCount"
        }


        if (
            result.newDays.isNotEmpty()
        ) {

            summaryParts +=
                "Добавлено дней: ${result.newDays.size}"
        }


        if (
            result.removedDays.isNotEmpty()
        ) {

            summaryParts +=
                "Удалено дней: ${result.removedDays.size}"
        }


        if (
            summaryParts.isNotEmpty()
        ) {

            builder.append(
                summaryParts.joinToString(
                    separator = " • "
                )
            )
        }


        // ---------------------------------------------------------
        // Изменения существующих дней
        // ---------------------------------------------------------

        val changesByDate =
            result.changes
                .groupBy {
                    it.date
                }
                .toSortedMap()


        changesByDate.forEach { (date, changes) ->

            if (
                builder.isNotEmpty()
            ) {

                builder.append(
                    "\n\n"
                )
            }


            builder.append(
                "$date:"
            )


            builder.append(
                "\n"
            )


            builder.append(
                buildDayChangeLines(
                    changes
                )
                    .joinToString(
                        separator = "\n"
                    ) {
                        "• $it"
                    }
            )
        }


        // ---------------------------------------------------------
        // Новые дни
        // ---------------------------------------------------------

        result.newDays
            .sortedBy {
                it.date
            }
            .forEach { newDay ->

                if (
                    builder.isNotEmpty()
                ) {

                    builder.append(
                        "\n\n"
                    )
                }


                builder.append(
                    "${newDay.date} (${newDay.weekday}) — добавлен день:"
                )


                builder.append(
                    "\n"
                )


                builder.append(
                    newDay.lessons
                        .sortedBy {
                            it.number
                        }
                        .joinToString(
                            separator = "\n"
                        ) {
                            "• №${it.number} — ${formatLesson(it)}"
                        }
                )
            }


        // ---------------------------------------------------------
        // Удалённые дни
        // ---------------------------------------------------------

        result.removedDays
            .sortedBy {
                it.date
            }
            .forEach { removedDay ->

                if (
                    builder.isNotEmpty()
                ) {

                    builder.append(
                        "\n\n"
                    )
                }


                builder.append(
                    "${removedDay.date} (${removedDay.weekday}) — удалён день:"
                )


                builder.append(
                    "\n"
                )


                builder.append(
                    removedDay.lessons
                        .sortedBy {
                            it.number
                        }
                        .joinToString(
                            separator = "\n"
                        ) {
                            "• №${it.number} — ${formatLesson(it)}"
                        }
                )
            }


        // ---------------------------------------------------------
        // Ограничение размера уведомления
        // ---------------------------------------------------------

        val text =
            builder
                .toString()
                .trim()


        return if (
            text.length > 3500
        ) {

            text.take(
                3497
            ) + "..."

        } else {

            text
        }
    }


    // -------------------------------------------------------------
    // Изменения внутри одного дня
    // -------------------------------------------------------------

    private fun buildDayChangeLines(
        changes: List<ScheduleChange>
    ): List<String> {

        return changes
            .sortedBy {
                it.number
            }
            .map { change ->

                when {

                    // -------------------------------------------------
                    // Добавлена пара
                    // -------------------------------------------------

                    change.oldLesson == null &&
                            change.newLesson != null ->

                        "добавлена пара №${change.number}: " +
                                formatLessonShort(
                                    change.newLesson
                                )


                    // -------------------------------------------------
                    // Удалена пара
                    // -------------------------------------------------

                    change.oldLesson != null &&
                            change.newLesson == null ->

                        "удалена пара №${change.number}: " +
                                formatLessonShort(
                                    change.oldLesson
                                )


                    // -------------------------------------------------
                    // Изменена пара
                    // -------------------------------------------------

                    change.oldLesson != null &&
                            change.newLesson != null -> {

                        val details =
                            buildLessonChanges(
                                change.oldLesson,
                                change.newLesson
                            )


                        if (
                            details.isBlank()
                        ) {

                            "изменена пара №${change.number}"

                        } else {

                            "изменена пара №${change.number}: $details"
                        }
                    }


                    else ->

                        "изменена пара №${change.number}"
                }
            }
    }


    // -------------------------------------------------------------
    // Какие поля пары изменились
    // -------------------------------------------------------------

    private fun buildLessonChanges(
        oldLesson: ScheduleEntity,
        newLesson: ScheduleEntity
    ): String {

        val changes =
            mutableListOf<String>()


        if (
            oldLesson.subject !=
            newLesson.subject
        ) {

            changes +=
                "предмет: «${oldLesson.subject}» → «${newLesson.subject}»"
        }


        if (
            oldLesson.time !=
            newLesson.time
        ) {

            changes +=
                "время: «${oldLesson.time}» → «${newLesson.time}»"
        }


        if (
            oldLesson.room !=
            newLesson.room
        ) {

            changes +=
                "кабинет: «${oldLesson.room}» → «${newLesson.room}»"
        }


        if (
            oldLesson.teacher !=
            newLesson.teacher
        ) {

            changes +=
                "преподаватель: «${oldLesson.teacher}» → «${newLesson.teacher}»"
        }


        return changes.joinToString(
            separator = "; "
        )
    }


    // -------------------------------------------------------------
    // Короткое описание пары
    // -------------------------------------------------------------

    private fun formatLessonShort(
        lesson: ScheduleEntity
    ): String {

        val parts =
            mutableListOf<String>()


        if (
            lesson.subject.isNotBlank()
        ) {

            parts +=
                lesson.subject
        }


        if (
            lesson.time.isNotBlank()
        ) {

            parts +=
                lesson.time
        }


        if (
            lesson.room.isNotBlank()
        ) {

            parts +=
                "каб. ${lesson.room}"
        }


        return if (
            parts.isEmpty()
        ) {

            "без подробностей"

        } else {

            parts.joinToString(
                separator = ", "
            )
        }
    }


    // -------------------------------------------------------------
    // Подсчёт логических изменений
    // -------------------------------------------------------------

    private fun countLogicalChanges(
        result: ScheduleUpdateResult.Success
    ): String {

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


        val changedPairs =
            result.changes.count {

                it.oldLesson != null &&
                        it.newLesson != null
            }


        val parts =
            mutableListOf<String>()


        if (
            addedPairs > 0
        ) {

            parts +=
                "добавлено пар: $addedPairs"
        }


        if (
            changedPairs > 0
        ) {

            parts +=
                "изменено пар: $changedPairs"
        }


        if (
            removedPairs > 0
        ) {

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

            "Изменения найдены"

        } else {

            parts.joinToString(
                separator = ", "
            )
        }
    }


    // -------------------------------------------------------------
    // Лог отдельной пары
    // Only a CLEAN comparison, based on two nonempty snapshots with shared dates,
    // can reach this method. Send nothing if the user switched groups mid-update.
    private suspend fun notifySelectedGroup(changes: ru.krabikk.nntcschedule.v3.GroupScheduleChangeSet?) {
        if (changes == null || !changes.hasChanges) return
        if (SettingsRepository.getSelectedGroup(applicationContext).first() != changes.group) return
        NotificationHelper.showScheduleChangedNotification(
            context = applicationContext,
            title = "Изменения расписания · ${changes.group}",
            text = changes.describe()
        )
    }

    // -------------------------------------------------------------

    private fun buildChangeLog(
        change: ScheduleChange
    ): String {

        return when {

            change.oldLesson == null &&
                    change.newLesson != null ->

                "Добавлена пара: " +
                        "${change.date}, " +
                        "№${change.number}: " +
                        formatLesson(
                            change.newLesson
                        )


            change.oldLesson != null &&
                    change.newLesson == null ->

                "Удалена пара: " +
                        "${change.date}, " +
                        "№${change.number}: " +
                        formatLesson(
                            change.oldLesson
                        )


            else -> {

                val oldText =
                    formatLesson(
                        change.oldLesson
                    )


                val newText =
                    formatLesson(
                        change.newLesson
                    )


                "Изменена пара: " +
                        "${change.date}, " +
                        "№${change.number}: " +
                        "[$oldText] → [$newText]"
            }
        }
    }


    // -------------------------------------------------------------
    // Лог нового дня
    // -------------------------------------------------------------

    private fun buildNewDayLog(
        newDay: NewScheduleDay
    ): String {

        val lessons =
            newDay.lessons.joinToString(
                separator = "; "
            ) {

                "№${it.number} — " +
                        formatLesson(it)
            }


        return "Добавлен новый день: " +
                "${newDay.date} " +
                "(${newDay.weekday}): " +
                lessons
    }


    // -------------------------------------------------------------
    // Лог удалённого дня
    // -------------------------------------------------------------

    private fun buildRemovedDayLog(
        removedDay: RemovedScheduleDay
    ): String {

        val lessons =
            removedDay.lessons.joinToString(
                separator = "; "
            ) {

                "№${it.number} — " +
                        formatLesson(it)
            }


        return "Удалён учебный день: " +
                "${removedDay.date} " +
                "(${removedDay.weekday}): " +
                lessons
    }


    // -------------------------------------------------------------
    // Полное описание пары
    // -------------------------------------------------------------

    private fun formatLesson(
        lesson: ScheduleEntity?
    ): String {

        if (
            lesson == null
        ) {

            return "нет"
        }


        return buildString {

            append(
                lesson.subject
            )


            if (
                lesson.time.isNotBlank()
            ) {

                append(
                    ", ${lesson.time}"
                )
            }


            if (
                lesson.room.isNotBlank()
            ) {

                append(
                    ", каб. ${lesson.room}"
                )
            }


            if (
                lesson.teacher.isNotBlank()
            ) {

                append(
                    ", ${lesson.teacher}"
                )
            }
        }
    }


    // -------------------------------------------------------------
    // Правильное склонение слова "пара"
    // -------------------------------------------------------------

    private fun lessonWord(
        count: Int
    ): String {

        return when {

            count % 10 == 1 &&
                    count % 100 != 11 ->

                "пара"


            count % 10 in 2..4 &&
                    count % 100 !in 12..14 ->

                "пары"


            else ->

                "пар"
        }
    }
}