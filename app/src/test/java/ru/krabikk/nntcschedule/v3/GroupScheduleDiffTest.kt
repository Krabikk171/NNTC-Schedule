package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupScheduleDiffTest {
    private fun row(date: String, number: Int, subject: String = "УП.04", room: String = "54") =
        GroupLessonSnapshot(date, number, "08:10–09:40", subject, room, "Федурин В.И.")

    private val a = "18 ноября 2026 г."
    private val b = "19 ноября 2026 г."
    private val c = "20 ноября 2026 г."
    private val good = GroupScheduleDiff.TRUSTED
    private fun compare(old: List<GroupLessonSnapshot>, next: List<GroupLessonSnapshot>,
                        oldQuality: String? = good, nextQuality: String? = good) =
        GroupScheduleDiff.compare("2ТМ-25-1", oldQuality, nextQuality, old, next)

    @Test fun legacyNotificationRequiresChangesAndTheSameSelection() {
        val target = "2РПУ-25-1"
        assertTrue(GroupScheduleDiff.shouldNotifyLegacy(target, target, true))
        assertFalse(GroupScheduleDiff.shouldNotifyLegacy(target, target, false))
        assertFalse(GroupScheduleDiff.shouldNotifyLegacy(target, "2ТМ-25-1", true))
        assertFalse(GroupScheduleDiff.shouldNotifyLegacy("2ТМ-25-1", target, true))
    }

    @Test fun unchangedScheduleHasNoNotification() {
        assertNull(compare(listOf(row(a, 1)), listOf(row(a, 1))))
    }
    @Test fun firstImportHasNoNotification() {
        assertNull(compare(emptyList(), listOf(row(a, 1))))
    }
    @Test fun detectChangedRoomWithinSameDate() {
        val delta = compare(listOf(row(a, 1)), listOf(row(a, 1, room="42")))!!
        assertEquals(1, delta.changedLessons)
        assertTrue(delta.newDays.isEmpty())
    }
    @Test fun detectAddedAndRemovedLessons() {
        assertEquals(1, compare(listOf(row(a,1)), listOf(row(a,1),row(a,2)))!!.changedLessons)
        assertEquals(1, compare(listOf(row(a,1),row(a,2)), listOf(row(a,1)))!!.changedLessons)
    }
    @Test fun newPublishedDayAlertsButRollingOldDayDoesNot() {
        val delta = compare(listOf(row(a,1),row(b,1)), listOf(row(b,1),row(c,1)))!!
        assertEquals(0, delta.changedLessons)
        assertEquals(listOf(c), delta.newDays)
        assertNull(compare(listOf(row(a,1),row(b,1)), listOf(row(b,1))))
    }
    @Test fun nonoverlappingWindowsHaveNoAlert() {
        assertNull(compare(listOf(row(a,1)), listOf(row(b,1))))
    }
    @Test fun warningsBlockNotifications() {
        assertNull(compare(listOf(row(a,1)), listOf(row(a,1,room="42")), nextQuality="NEEDS_REVIEW"))
        assertNull(compare(listOf(row(a,1)), listOf(row(a,1,room="42")), oldQuality=null))
    }
    @Test fun malformedEntriesBlockNotifications() {
        assertNull(compare(listOf(row(a,1)), listOf(row(a,0))))
        assertNull(compare(listOf(row(a,1)), listOf(row(a,1),row(a,1))))
    }
    @Test fun selectedGroupRoutingPreventsForeignNotifications() {
        val change = GroupScheduleChangeSet("2ТМ-25-1", 1, emptyList())
        assertTrue(GroupScheduleDiff.relevantForSelection("2ТМ-25-1", change))
        assertTrue(!GroupScheduleDiff.relevantForSelection("1МРП-26-1", change))
        assertTrue(!GroupScheduleDiff.relevantForSelection("2РПУ-25-1", change))
        assertTrue(!GroupScheduleDiff.relevantForSelection("2ТМ-25-1", null))
    }
    @Test fun firstRunOrWarningsCannotBeForwarded() {
        val unchanged = GroupScheduleChangeSet("2ТМ-25-1", 0, emptyList())
        assertTrue(!GroupScheduleDiff.relevantForSelection("2ТМ-25-1", unchanged))
    }
    @Test fun whitespaceNormalizationPreventsFalsePositive() {
        val normalized = row(a,1).copy(teacher="Федурин   В.И.",subject=" УП.04  ")
        assertNull(compare(listOf(row(a,1)), listOf(normalized)))
    }
}
