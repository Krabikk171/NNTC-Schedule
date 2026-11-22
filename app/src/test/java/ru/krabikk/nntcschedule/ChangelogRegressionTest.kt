package ru.krabikk.nntcschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogRegressionTest {
    @Test
    fun releaseVersionMatchesTopEntry() {
        assertEquals("3.1.1-dev.1", Changelog.CURRENT_VERSION)
        assertEquals(Changelog.CURRENT_VERSION, Changelog.versions.first().version)
        assertTrue(Changelog.versions.first().changes.size >= 4)
        assertTrue(Changelog.versions.any { it.version == "3.0.0" && it.changes.size >= 18 })
    }

    @Test
    fun releaseHistoryContainsMilestones() {
        val all = Changelog.versions.map { it.version }.toSet()
        listOf(
            "3.0.0-alpha.1", "3.0.0-alpha.2", "3.0.0-alpha.3",
            "3.0.0-alpha.4", "3.0.0-alpha.5", "3.0.0-alpha.6",
            "3.0.0-alpha.6.1", "3.0.0-alpha.6.2", "3.0.0-alpha.6.3",
            "3.0.0-beta.1", "3.0.0-beta.1.1",
            "3.1.0-alpha.8.5", "3.1.0-beta.1", "3.1.0"
        ).forEach { assertTrue("Missing changelog for $it", it in all) }
    }
}
