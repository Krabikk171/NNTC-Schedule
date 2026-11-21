package ru.krabikk.nntcschedule.v3

import android.content.Context
import ru.krabikk.nntcschedule.PdfTextItem
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import ru.krabikk.nntcschedule.SettingsRepository

/** Generates a separate experimental catalog from the same already parsed PDF. */
object GroupScheduleStore {
    data class ImportSummary(
        val groups: Int,
        val lessons: Int,
        val warnings: Int,
        val selectedChanges: GroupScheduleChangeSet? = null
    )

    suspend fun refresh(context: Context, items: List<PdfTextItem>): ImportSummary {
        val report = withContext(Dispatchers.Default) { GroupCatalog.analyze(items) }
        // A malformed PDF, empty document or broken layout must not replace the cache.
        require(GroupCatalogImportPolicy.mayReplace(report.groups.size, report.lessonCount, report.cleanCount)) {
            "Новый PDF содержит слишком много ошибок распознавания: " +
                "${report.cleanCount}/${report.groups.size} групп без структурных замечаний. " +
                "Прежняя версия каталога сохранена."
        }
        val groups = report.groups.map {
            GroupInfoEntity(it.name, it.quality.name, it.rows.size, it.notes.size)
        }
        val lessons = report.groups.flatMap { group ->
            group.rows.map { row ->
                GroupLessonEntity(
                    groupName = group.name,
                    date = row.date,
                    weekday = row.weekday,
                    number = row.lesson.number,
                    time = row.lesson.time,
                    subject = row.lesson.subject,
                    room = row.lesson.room,
                    teacher = row.lesson.teacher
                )
            }
        }
        val subgroups = report.groups.flatMap { group ->
            group.rows.flatMap { row ->
                GroupSubgroupParser.parse(row.lesson).map { sub ->
                    GroupSubgroupEntity(
                        groupName = group.name,
                        date = row.date,
                        number = row.lesson.number,
                        subgroupNumber = sub.number,
                        subject = sub.subject,
                        room = sub.room,
                        teacher = sub.teacher
                    )
                }
            }
        }
        val dao = GroupScheduleDatabase.get(context).groupScheduleDao()
        val selected = SettingsRepository.getSelectedGroup(context).first()
        // Notifications for the legacy group still use its already-tested 3.0 mechanism.
        val previousQuality = if (selected != "2РПУ-25-1") dao.infoForGroup(selected)?.quality else null
        val previous = if (previousQuality != null) dao.lessonsForGroup(selected) else emptyList()
        val nextQuality = groups.firstOrNull { it.name == selected }?.quality
        val next = lessons.filter { it.groupName == selected }
        val selectedChanges = GroupScheduleDiff.compare(
            selectedGroup = selected,
            previousQuality = previousQuality,
            nextQuality = nextQuality,
            previous = previous.map { it.toSnapshot() },
            next = next.map { it.toSnapshot() }
        )
        dao.replaceCatalog(groups, lessons, subgroups)
        Log.i("GroupScheduleStore", "Каталог: ${groups.size} групп, ${lessons.size} пар, ${subgroups.size} записей подгрупп")
        return ImportSummary(groups.size, lessons.size, report.needReviewCount + report.unsupportedCount, selectedChanges)
    }
    private fun GroupLessonEntity.toSnapshot() = GroupLessonSnapshot(
        date = date, number = number, time = time,
        subject = subject, room = room, teacher = teacher
    )
}
