package ru.krabikk.nntcschedule

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(
    tableName = "schedule"
)
data class ScheduleEntity(

    @PrimaryKey(
        autoGenerate = true
    )
    val id: Int = 0,

    val date: String,

    val weekday: String,

    val number: Int,

    val time: String,

    val room: String,

    val subject: String,

    val teacher: String
)