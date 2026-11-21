package ru.krabikk.nntcschedule

import ru.krabikk.nntcschedule.v3.ScheduleTableParser
import ru.krabikk.nntcschedule.v3.GroupScheduleStore
import ru.krabikk.nntcschedule.v3.GroupScheduleChangeSet

import android.content.Context
import java.io.File
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


// ============================================================================
// Результат проверки расписания
// ============================================================================
//
// Worker, MainActivity и DeveloperScreen получают отсюда результат проверки.
//
// Success    -> расписание проверено, найдены изменения
// NoChanges  -> расписание проверено, изменений нет
// Error      -> во время проверки произошла ошибка
//

sealed class ScheduleUpdateResult {

    data class Success(
        val days: List<ParsedDay>,

        // Отдельные изменения пар:
        // добавлена, удалена или изменена конкретная пара.
        val changes: List<ScheduleChange>,

        // Полностью новый день расписания.
        val newDays: List<NewScheduleDay> = emptyList(),

        // Полностью удалённый день расписания.
        val removedDays: List<RemovedScheduleDay> = emptyList(),
        val selectedGroupChanges: GroupScheduleChangeSet? = null
    ) : ScheduleUpdateResult()


    data class NoChanges(
        val days: List<ParsedDay>,
        val selectedGroupChanges: GroupScheduleChangeSet? = null
    ) : ScheduleUpdateResult()


    data class Error(
        val message: String
    ) : ScheduleUpdateResult()
}


// ============================================================================
// Изменение отдельной пары
// ============================================================================
//
// oldLesson == null -> пара добавилась
// newLesson == null -> пара удалилась
// оба не null      -> пара изменилась
//

data class ScheduleChange(
    val date: String,
    val weekday: String,
    val number: Int,
    val oldLesson: ScheduleEntity?,
    val newLesson: ScheduleEntity?
)


// ============================================================================
// Новый день расписания
// ============================================================================

data class NewScheduleDay(
    val date: String,
    val weekday: String,
    val lessons: List<ScheduleEntity>
)


// ============================================================================
// Удалённый день расписания
// ============================================================================

data class RemovedScheduleDay(
    val date: String,
    val weekday: String,
    val lessons: List<ScheduleEntity>
)


// ============================================================================
// Основной репозиторий расписания
// ============================================================================
//
// Здесь происходит вся основная работа:
//
// 1. Скачиваем PDF.
// 2. Один раз разбираем PDF.
// 3. Находим дни.
// 4. Извлекаем расписание нашей группы.
// 5. Сравниваем с сохранённым расписанием.
// 6. Определяем добавленные / удалённые / изменённые пары.
// 7. Сохраняем новое расписание.
// 8. Возвращаем результат Worker'у.
//
// ============================================================================

object ScheduleRepository {

    // Защита от одновременного запуска двух проверок.
    //
    // Например:
    // - пользователь нажал "Проверить"
    // - одновременно запустился Worker
    //
    // В таком случае второй запуск будет ждать завершения первого.
    private val updateMutex =
        Mutex()


    // ========================================================================
    // Работа с датами
    // ========================================================================

    // Номер месяца по русскому названию.
    private val russianMonths =
        mapOf(
            "января" to 1,
            "февраля" to 2,
            "марта" to 3,
            "апреля" to 4,
            "мая" to 5,
            "июня" to 6,
            "июля" to 7,
            "августа" to 8,
            "сентября" to 9,
            "октября" to 10,
            "ноября" to 11,
            "декабря" to 12
        )


    // Из строки вида:
    //
    // 21 сентября 2026 г.
    //
    // получаем:
    // 21
    // сентября
    // 2026
    //
    private val dateRegex =
        Regex(
            """(\d{1,2})\s+([А-Яа-яЁё]+)\s+(\d{4})"""
        )


    // ========================================================================
    // Основная проверка расписания
    // ========================================================================

    suspend fun updateSchedule(
        context: Context,
        database: ScheduleDatabase,
        onStatus: suspend (String) -> Unit = {}
    ): ScheduleUpdateResult {

        // Не даём двум проверкам идти одновременно.
        return updateMutex.withLock {

            // PDFBox нужно инициализировать перед работой с PDF.
            PDFBoxResourceLoader.init(
                context.applicationContext
            )


            // ----------------------------------------------------------------
            // 1. Скачиваем PDF
            // ----------------------------------------------------------------

            onStatus(
                "Скачиваю PDF..."
            )

            val download = PdfDownloader.downloadPdf()
            val pdf = download.bytes
            if (pdf == null) {
                return@withLock ScheduleUpdateResult.Error(
                    download.error ?: "Не удалось скачать PDF"
                )
            }


            onStatus(
                "PDF скачан. Быстро обрабатываю..."
            )


            try {

                // ============================================================
                // 2. Разбираем PDF
                // ============================================================
                //
                // В старой версии здесь PDF открывался два раза:
                //
                // extractText()
                // extractItems()
                //
                // Теперь используется один parse():
                //
                // PDF -> текст + координаты одновременно.
                //
                // Это одна из основных оптимизаций 2.2.1.
                //

                val pdfData =
                    PdfParser.parse(
                        pdf
                    )

                // Каталог всех групп обновляем ПЕРВЫМ и независимо от
                // распознавания контрольной группы 2РПУ-25-1.
                // Иначе PDF, где у контрольной группы нет занятий, блокировал
                // обновление для остальных пользователей.
                val activeGroup = SettingsRepository.getSelectedGroup(context).first()
                var selectedGroupChanges: GroupScheduleChangeSet? = null
                try {
                    onStatus("Обновляю каталог групп...")
                    selectedGroupChanges = GroupScheduleStore.refresh(context, pdfData.items).selectedChanges
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (e: Exception) {
                    Log.w("GroupScheduleStore", "Каталог групп не обновлён", e)
                    if (activeGroup != ScheduleTableParser.TARGET_GROUP) {
                        return@withLock ScheduleUpdateResult.Error(
                            "Ошибка обновления выбранной группы: ${e.message ?: "PDF не распознан"}. " +
                            "Прежнее расписание сохранено."
                        )
                    }
                }
                if (activeGroup != ScheduleTableParser.TARGET_GROUP) {
                    // Для остальных групп результат каталога уже сохранён.
                    // Не требуем присутствия старой контрольной группы в PDF.
                    // Worker учитывает selectedGroupChanges в NoChanges.
                    return@withLock ScheduleUpdateResult.NoChanges(
                        days = emptyList(), selectedGroupChanges = selectedGroupChanges
                    )
                }

                // Рабочий разбор 3.0: PDF-страница -> строка группы -> ячейки пар.
                // Не сохраняем данные при неполном распознавании, чтобы не
                // затереть корректное расписание в базе.
                onStatus("Распознаю таблицу для группы ${ScheduleTableParser.TARGET_GROUP}...")
                val extraction = ScheduleTableParser.parse(
                    items = pdfData.items,
                    documentText = pdfData.text
                )
                extraction.warnings.forEach { Log.w("ScheduleTableParser", it) }
                if (!extraction.isValid) {
                    return@withLock ScheduleUpdateResult.Error(
                        extraction.error ?: "Не удалось распознать таблицу расписания. Старые данные сохранены."
                    )
                }
                // Переходим к нормализованной таблице CSV (одна строка = одна пара).
                // CSV изолирует от остальной части приложения PDF-координаты.
                val csv = ScheduleCsvCodec.encode(extraction.days, ScheduleTableParser.TARGET_GROUP)
                val parsed = ScheduleCsvCodec.decode(csv, ScheduleTableParser.TARGET_GROUP)
                // Диагностический CSV сохраняем в приватной папке приложения,
                // он доступен через Device Explorer Android Studio.
                runCatching {
                    File(context.filesDir, "last_schedule.csv").writeText(csv, Charsets.UTF_8)
                }.onFailure { Log.w("ScheduleTableParser", "Не удалось записать отладочный CSV", it) }
                Log.i("ScheduleTableParser", "Дней: ${parsed.size}, пар: ${parsed.sumOf { it.lessons.size }}")
                // ============================================================
                // 5. Преобразуем ParsedDay -> ScheduleEntity
                // ============================================================

                val entities =
                    parsed.flatMap { day ->

                        day.lessons.map { lesson ->

                            ScheduleEntity(
                                date =
                                    day.date,

                                weekday =
                                    day.weekday,

                                number =
                                    lesson.number,

                                time =
                                    lesson.time,

                                room =
                                    lesson.room,

                                subject =
                                    lesson.subject,

                                teacher =
                                    lesson.teacher
                            )
                        }
                    }


                if (
                    entities.isEmpty()
                ) {

                    return@withLock ScheduleUpdateResult.Error(
                        "В расписании не найдено занятий"
                    )
                }


                // ============================================================
                // 6. Получаем старое расписание
                // ============================================================

                onStatus(
                    "Сравниваю с сохранённым расписанием..."
                )

                val savedEntities =
                    database
                        .scheduleDao()
                        .getAll()


                val isFirstLoad =
                    savedEntities.isEmpty()


                // ============================================================
                // 7. Определяем даты старого и нового расписания
                // ============================================================

                val oldDates =
                    savedEntities
                        .asSequence()
                        .map {
                            it.date
                        }
                        .toSet()


                val newDates =
                    entities
                        .asSequence()
                        .map {
                            it.date
                        }
                        .toSet()


                // Даты, которые есть и там, и там.
                val commonDates =
                    oldDates intersect newDates


                val hasDateOverlap =
                    commonDates.isNotEmpty()


                // ============================================================
                // 8. Определяем полностью добавленные дни
                // ============================================================
                //
                // Важно:
                //
                // PDF может просто "сдвинуть окно".
                //
                // Например:
                //
                // было:
                // 15, 16, 17, 18, 19
                //
                // стало:
                // 16, 17, 18, 19, 20
                //
                // Исчезновение 15-го в таком случае НЕ означает,
                // что расписание на 15-е кто-то удалил.
                //
                // Поэтому реальные добавления/удаления дней
                // считаем только если диапазоны пересекаются.
                //

                val newDateValues =
                    if (
                        isFirstLoad ||
                        !hasDateOverlap
                    ) {
                        emptySet()
                    } else {
                        newDates - oldDates
                    }


                val newDays =
                    newDateValues
                        .sortedWith(
                            dateComparator
                        )
                        .mapNotNull { date ->

                            val dayLessons =
                                entities
                                    .asSequence()
                                    .filter {
                                        it.date == date
                                    }
                                    .sortedBy {
                                        it.number
                                    }
                                    .toList()


                            if (
                                dayLessons.isEmpty()
                            ) {

                                null

                            } else {

                                val firstLesson =
                                    dayLessons.first()

                                NewScheduleDay(
                                    date =
                                        date,

                                    weekday =
                                        firstLesson.weekday,

                                    lessons =
                                        dayLessons
                                )
                            }
                        }


                // ============================================================
                // 9. Определяем полностью удалённые дни
                // ============================================================

                val newMinDate =
                    newDates
                        .mapNotNull(
                            ::parseDateKey
                        )
                        .minOrNull()


                val newMaxDate =
                    newDates
                        .mapNotNull(
                            ::parseDateKey
                        )
                        .maxOrNull()


                val removedDateValues =
                    if (
                        isFirstLoad ||
                        !hasDateOverlap ||
                        newMinDate == null ||
                        newMaxDate == null
                    ) {

                        emptySet()

                    } else {

                        (oldDates - newDates)
                            .filter { date ->

                                val dateKey =
                                    parseDateKey(
                                        date
                                    )
                                        ?: return@filter false

                                // Удаляем только дни внутри нового
                                // диапазона расписания.
                                dateKey >= newMinDate &&
                                        dateKey <= newMaxDate
                            }
                            .toSet()
                    }


                val removedDays =
                    removedDateValues
                        .sortedWith(
                            dateComparator
                        )
                        .mapNotNull { date ->

                            val dayLessons =
                                savedEntities
                                    .asSequence()
                                    .filter {
                                        it.date == date
                                    }
                                    .sortedBy {
                                        it.number
                                    }
                                    .toList()


                            if (
                                dayLessons.isEmpty()
                            ) {

                                null

                            } else {

                                val firstLesson =
                                    dayLessons.first()

                                RemovedScheduleDay(
                                    date =
                                        date,

                                    weekday =
                                        firstLesson.weekday,

                                    lessons =
                                        dayLessons
                                )
                            }
                        }


                // ============================================================
                // 10. Создаём быстрые таблицы:
                //
                // дата + номер пары -> занятие
                //
                // Это позволяет не перебирать весь список каждый раз.
                // ============================================================

                val oldByLesson =
                    savedEntities
                        .associateBy {

                            LessonKey(
                                date =
                                    it.date,

                                number =
                                    it.number
                            )
                        }


                val newByLesson =
                    entities
                        .associateBy {

                            LessonKey(
                                date =
                                    it.date,

                                number =
                                    it.number
                            )
                        }


                // Оставляем только пары на общих датах.
                val commonOldLessons =
                    oldByLesson
                        .filterKeys {
                            it.date in commonDates
                        }


                val commonNewLessons =
                    newByLesson
                        .filterKeys {
                            it.date in commonDates
                        }


                // ============================================================
                // 11. Ищем изменения существующих пар
                // ============================================================

                val commonKeys =
                    (
                            commonOldLessons.keys +
                                    commonNewLessons.keys
                            )
                        .sortedWith(
                            compareBy<LessonKey>(
                                {
                                    parseDateKey(
                                        it.date
                                    )
                                },
                                {
                                    it.number
                                }
                            )
                        )


                val changes =
                    mutableListOf<ScheduleChange>()


                commonKeys.forEach { key ->

                    val oldLesson =
                        oldByLesson[key]

                    val newLesson =
                        newByLesson[key]


                    if (
                        !lessonsAreEqual(
                            oldLesson,
                            newLesson
                        )
                    ) {

                        changes +=
                            ScheduleChange(
                                date =
                                    key.date,

                                weekday =
                                    newLesson?.weekday
                                        ?: oldLesson?.weekday
                                        ?: "",

                                number =
                                    key.number,

                                oldLesson =
                                    oldLesson,

                                newLesson =
                                    newLesson
                            )
                    }
                }


                // ============================================================
                // 12. Ищем новые пары внутри уже существующего дня
                // ============================================================

                commonNewLessons.keys
                    .filter {
                        it !in commonOldLessons.keys
                    }
                    .sortedWith(
                        compareBy<LessonKey>(
                            {
                                parseDateKey(
                                    it.date
                                )
                            },
                            {
                                it.number
                            }
                        )
                    )
                    .forEach { key ->

                        val newLesson =
                            newByLesson[key]
                                ?: return@forEach


                        changes +=
                            ScheduleChange(
                                date =
                                    key.date,

                                weekday =
                                    newLesson.weekday,

                                number =
                                    key.number,

                                oldLesson =
                                    null,

                                newLesson =
                                    newLesson
                            )
                    }


                // ============================================================
                // 13. Ищем удалённые пары внутри существующего дня
                // ============================================================

                commonOldLessons.keys
                    .filter {
                        it !in commonNewLessons.keys
                    }
                    .sortedWith(
                        compareBy<LessonKey>(
                            {
                                parseDateKey(
                                    it.date
                                )
                            },
                            {
                                it.number
                            }
                        )
                    )
                    .forEach { key ->

                        val oldLesson =
                            oldByLesson[key]
                                ?: return@forEach


                        changes +=
                            ScheduleChange(
                                date =
                                    key.date,

                                weekday =
                                    oldLesson.weekday,

                                number =
                                    key.number,

                                oldLesson =
                                    oldLesson,

                                newLesson =
                                    null
                            )
                    }


                // ============================================================
                // 14. Убираем возможные дубликаты
                // ============================================================

                val uniqueChanges =
                    changes
                        .distinctBy { change ->

                            ChangeKey(
                                date =
                                    change.date,

                                number =
                                    change.number,

                                oldLesson =
                                    change.oldLesson
                                        ?.copy(
                                            id = 0
                                        ),

                                newLesson =
                                    change.newLesson
                                        ?.copy(
                                            id = 0
                                        )
                            )
                        }
                        .sortedWith(
                            compareBy<ScheduleChange>(
                                {
                                    parseDateKey(
                                        it.date
                                    )
                                },
                                {
                                    it.number
                                }
                            )
                        )


                // ============================================================
                // 15. Проверяем, изменилось ли расписание вообще
                // ============================================================

                val scheduleIsSame =
                    areSchedulesEqual(
                        savedEntities,
                        entities
                    )

                onStatus(
                    "Сравнение с БД завершено."
                )

                // Если ничего не изменилось, не делаем лишнюю
                // DELETE + INSERT операцию в Room.
                if (
                    !scheduleIsSame
                ) {

                    onStatus(
                        "Сохраняю расписание..."
                    )

                    database
                        .scheduleDao()
                        .replaceAll(
                            entities
                        )
                    onStatus(
                        "Сохранение в БД завершено."
                    )
                }


                // ============================================================
                // 16. Если изменений нет
                // ============================================================

                if (
                    uniqueChanges.isEmpty() &&
                    newDays.isEmpty() &&
                    removedDays.isEmpty()
                ) {

                    return@withLock ScheduleUpdateResult.NoChanges(
                        parsed,
                        selectedGroupChanges = selectedGroupChanges
                    )
                }


                // ============================================================
                // 17. Возвращаем найденные изменения
                // ============================================================

                ScheduleUpdateResult.Success(
                    days =
                        parsed,

                    changes =
                        uniqueChanges,

                    newDays =
                        newDays,

                    removedDays = removedDays,
                    selectedGroupChanges = selectedGroupChanges
                )

            } catch (e: Exception) {

                Log.e(
                    "SCHEDULE",
                    "Ошибка обновления расписания",
                    e
                )

                ScheduleUpdateResult.Error(
                    "Ошибка при обработке PDF: ${e.message}"
                )
            }
        }
    }


    // =========================================================================
    // Сравнение двух полных расписаний
    // =========================================================================

    private fun areSchedulesEqual(
        oldLessons: List<ScheduleEntity>,
        newLessons: List<ScheduleEntity>
    ): Boolean {

        if (
            oldLessons.size !=
            newLessons.size
        ) {
            return false
        }


        // ID нам не интересен:
        // Room создаёт новые ID после replaceAll().
        val oldNormalized =
            oldLessons
                .map {
                    it.copy(
                        id = 0
                    )
                }
                .sortedWith(
                    compareBy<ScheduleEntity>(
                        {
                            parseDateKey(
                                it.date
                            )
                        },
                        {
                            it.date
                        },
                        {
                            it.number
                        },
                        {
                            it.subject
                        },
                        {
                            it.time
                        },
                        {
                            it.room
                        },
                        {
                            it.teacher
                        },
                        {
                            it.weekday
                        }
                    )
                )


        val newNormalized =
            newLessons
                .map {
                    it.copy(
                        id = 0
                    )
                }
                .sortedWith(
                    compareBy<ScheduleEntity>(
                        {
                            parseDateKey(
                                it.date
                            )
                        },
                        {
                            it.date
                        },
                        {
                            it.number
                        },
                        {
                            it.subject
                        },
                        {
                            it.time
                        },
                        {
                            it.room
                        },
                        {
                            it.teacher
                        },
                        {
                            it.weekday
                        }
                    )
                )


        return oldNormalized ==
                newNormalized
    }


    // =========================================================================
    // Сравнение двух отдельных пар
    // =========================================================================

    private fun lessonsAreEqual(
        oldLesson: ScheduleEntity?,
        newLesson: ScheduleEntity?
    ): Boolean {

        if (
            oldLesson == null &&
            newLesson == null
        ) {
            return true
        }


        if (
            oldLesson == null ||
            newLesson == null
        ) {
            return false
        }


        return oldLesson.copy(
            id = 0
        ) ==
                newLesson.copy(
                    id = 0
                )
    }


    // =========================================================================
    // Преобразование даты:
    //
    // "21 сентября 2026 г."
    //
    // ->
    //
    // ScheduleDate(2026, 9, 21)
    //
    // Это нужно, чтобы даты сортировались именно по календарю,
    // а не как обычные строки.
    // =========================================================================

    private fun parseDateKey(
        date: String
    ): ScheduleDate? {

        val match =
            dateRegex.find(
                date
            )
                ?: return null


        val day =
            match
                .groupValues[1]
                .toIntOrNull()
                ?: return null


        val month =
            russianMonths[
                match
                    .groupValues[2]
                    .lowercase()
            ]
                ?: return null


        val year =
            match
                .groupValues[3]
                .toIntOrNull()
                ?: return null


        return ScheduleDate(
            year =
                year,

            month =
                month,

            day =
                day
        )
    }


    // Компаратор для сортировки дат.
    private val dateComparator =
        Comparator<String> { first, second ->

            compareValues(
                parseDateKey(
                    first
                ),

                parseDateKey(
                    second
                )
            )
        }
}


// ============================================================================
// Ключ пары
// ============================================================================
//
// Комбинация:
//
// дата + номер пары
//
// однозначно определяет конкретное занятие.
// ============================================================================

private data class LessonKey(
    val date: String,
    val number: Int
)


// ============================================================================
// Ключ для удаления дублей изменений
// ============================================================================

private data class ChangeKey(
    val date: String,
    val number: Int,
    val oldLesson: ScheduleEntity?,
    val newLesson: ScheduleEntity?
)


// ============================================================================
// Удобное представление даты для сравнения
// ============================================================================

private data class ScheduleDate(
    val year: Int,
    val month: Int,
    val day: Int
) : Comparable<ScheduleDate> {

    override operator fun compareTo(
        other: ScheduleDate
    ): Int {

        return compareValuesBy(
            this,
            other,
            {
                it.year
            },
            {
                it.month
            },
            {
                it.day
            }
        )
    }
}
