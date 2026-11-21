package ru.krabikk.nntcschedule.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.krabikk.nntcschedule.ParsedLesson

class GroupSubgroupParserTest {
    @Test fun separatesTwoDifferentTeachersAndClassrooms() {
        val lesson = ParsedLesson(
            1, "08:10–09:40", "41/21",
            "Информатика/1 подгруппа/Ивкова М.Н./Основы черчения /2 подгруппа",
            "Дубровина Л.И."
        )
        val parts = GroupSubgroupParser.parse(lesson)
        assertEquals(2, parts.size)
        assertEquals(GroupSubgroup(1, "Информатика", "41", "Ивкова М.Н."), parts[0])
        assertEquals(GroupSubgroup(2, "Основы черчения", "21", "Дубровина Л.И."), parts[1])
    }

    @Test fun retainsForeignLanguageSubgroups() {
        val lesson = ParsedLesson(2, "10:00–11:30", "32/33",
            "Иностранный язык в профессиональной деятельности/1 подгруппа/Каримова А.Э./Иностранный язык в профессиональной деятельности/2 подгруппа",
            "Котлярова Н.Н.")
        val parts = GroupSubgroupParser.parse(lesson)
        assertEquals(2, parts.size)
        assertEquals("Иностранный язык в профессиональной деятельности", parts[1].subject)
        assertEquals("32", parts[0].room)
        assertEquals("33", parts[1].room)
    }

    @Test fun incompleteSecondTeacherMustRemainUnsplit() {
        val ambiguous = ParsedLesson(1, "08:10–09:40", "41/21",
            "Информатика/1 подгруппа/Ивкова М.Н./Основы черчения/2 подгруппа", "")
        assertTrue(GroupSubgroupParser.hasMultipleSubgroups(ambiguous))
        assertTrue(GroupSubgroupParser.parse(ambiguous).isEmpty())
    }

    @Test fun normalLessonNotSplit() {
        val normal = ParsedLesson(3, "11:50–13:20", "54", "УП.04", "Федурин В.И.")
        assertTrue(GroupSubgroupParser.parse(normal).isEmpty())
    }
}
