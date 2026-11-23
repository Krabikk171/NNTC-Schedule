package ru.krabikk.nntcschedule.v3

/** Compare only trustworthy snapshots for ONE selected group.
 *  This class has no Android dependencies, so it can be regression tested separately.
 */
data class GroupLessonSnapshot(
    val date: String,
    val number: Int,
    val time: String,
    val subject: String,
    val room: String,
    val teacher: String
)

data class GroupScheduleChangeSet(
    val group: String,
    val changedLessons: Int,
    val newDays: List<String>
) {
    val hasChanges: Boolean get() = changedLessons > 0 || newDays.isNotEmpty()
    fun describe(): String = buildList {
        if (changedLessons > 0) add("Изменено пар: $changedLessons")
        if (newDays.isNotEmpty()) add("Добавлено дней: ${newDays.size}")
    }.joinToString(" · ")
}

object GroupScheduleDiff {
    fun shouldNotifyLegacy(selectionAtComparison: String, currentSelection: String, changed: Boolean): Boolean =
        changed && selectionAtComparison == "2РПУ-25-1" && currentSelection == selectionAtComparison

    const val TRUSTED = "STRUCTURAL_OK"

    /** This catalog notification belongs only to the active non-legacy profile. */
    fun relevantForSelection(activeGroup: String, changes: GroupScheduleChangeSet?): Boolean =
        activeGroup != "2РПУ-25-1" && changes?.let { it.group == activeGroup && it.hasChanges } == true

    /**
     * Null means *do not notify*: first load, non-trusted parsing, no date overlap,
     * no effective change, or wrong selection. No report of disappearing *old dates*
     * because a PDF routinely rolls its visible window forward.
     */
    fun compare(
        selectedGroup: String,
        previousQuality: String?,
        nextQuality: String?,
        previous: List<GroupLessonSnapshot>,
        next: List<GroupLessonSnapshot>
    ): GroupScheduleChangeSet? {
        if (selectedGroup.isBlank() || previousQuality != TRUSTED || nextQuality != TRUSTED) return null
        if (previous.isEmpty() || next.isEmpty()) return null
        if ((previous + next).any { it.date.isBlank() || it.number <= 0 || it.subject.isBlank() || it.time.isBlank() }) return null
        val oldDates = previous.map { it.date }.toSet()
        val newDates = next.map { it.date }.toSet()
        val commonDates = oldDates intersect newDates
        if (commonDates.isEmpty()) return null

        fun normalize(s: String) = s.trim().replace(Regex("\\s+"), " ")
        fun digest(item: GroupLessonSnapshot): List<String> = listOf(
            normalize(item.time), normalize(item.subject), normalize(item.room), normalize(item.teacher)
        )
        val oldRows = previous.filter { it.date in commonDates }.associateBy { it.date to it.number }
        val newRows = next.filter { it.date in commonDates }.associateBy { it.date to it.number }
        // Duplicate (date, number) entries indicate an ambiguous parse; never alert.
        if (oldRows.size != previous.count { it.date in commonDates } ||
            newRows.size != next.count { it.date in commonDates }) return null
        val changed = (oldRows.keys + newRows.keys).count { key ->
            val old = oldRows[key]
            val new = newRows[key]
            old == null || new == null || digest(old) != digest(new)
        }
        // A new date is useful news; removing an old date is a routine PDF window shift.
        val addedDates = (newDates - oldDates).sorted()
        return GroupScheduleChangeSet(selectedGroup, changed, addedDates).takeIf { it.hasChanges }
    }
}
