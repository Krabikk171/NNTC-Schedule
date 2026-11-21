package ru.krabikk.nntcschedule.v3

/** Reject an obviously corrupted catalog before replacing the Room snapshot.
 * This does NOT turn any warnings into success: questionable data is kept out.
 */
object GroupCatalogImportPolicy {
    fun mayReplace(groupCount: Int, lessonCount: Int, cleanCount: Int): Boolean {
        if (groupCount < 10 || lessonCount <= 0 || cleanCount < 0 || cleanCount > groupCount) return false
        // Do not replace a cached usable timetable if almost all groups suddenly
        // fail to parse. Other groups can still have legitimate isolated warnings.
        val minimumClean = (groupCount + 4) / 5 // 20% rounded up
        return cleanCount >= minimumClean
    }
}
