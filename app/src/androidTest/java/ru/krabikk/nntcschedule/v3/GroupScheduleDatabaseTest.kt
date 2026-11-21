package ru.krabikk.nntcschedule.v3

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupScheduleDatabaseTest {
    @Test
    fun importReplacesOnlyTheIndependentCatalogAndKeepsGroupSeparation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, GroupScheduleDatabase::class.java).build()
        try {
            val dao = database.groupScheduleDao()
            val groups = listOf(
                GroupInfoEntity("2РПУ-25-1", "STRUCTURAL_OK", 1, 0),
                GroupInfoEntity("2ТМ-25-1", "NEEDS_REVIEW", 1, 1)
            )
            val rows = listOf(
                GroupLessonEntity(groupName = "2РПУ-25-1", date = "19 ноября 2026 г.",
                    weekday = "ЧЕТВЕРГ", number = 3, time = "11:50–13:20", subject = "УП.04",
                    room = "54", teacher = "Федурин В.И."),
                GroupLessonEntity(groupName = "2ТМ-25-1", date = "19 ноября 2026 г.",
                    weekday = "ЧЕТВЕРГ", number = 1, time = "08:10–09:40",
                    subject = "Физическая культура", room = "с/з", teacher = "Лысенков А.В.")
            )
            val subgroupRows = listOf(
                GroupSubgroupEntity("2ТМ-25-1", "19 ноября 2026 г.", 1, 1,
                    "Информатика", "41", "Ивкова М.Н."),
                GroupSubgroupEntity("2ТМ-25-1", "19 ноября 2026 г.", 1, 2,
                    "Основы черчения", "21", "Дубровина Л.И.")
            )
            dao.replaceCatalog(groups, rows, subgroupRows)
            assertEquals(2, dao.observeSubgroups().first().size)
            assertEquals(2, dao.observeGroups().first().size)
            assertEquals(2, dao.observeLessons().first().size)
            assertEquals("УП.04", dao.observeLessons().first().first { it.groupName == "2РПУ-25-1" }.subject)
            dao.replaceCatalog(groups.take(1), rows.take(1), emptyList())
            assertEquals(0, dao.observeSubgroups().first().size)
            assertEquals(1, dao.observeGroups().first().size)
            assertEquals(1, dao.observeLessons().first().size)
        } finally {
            database.close()
        }
    }
}
