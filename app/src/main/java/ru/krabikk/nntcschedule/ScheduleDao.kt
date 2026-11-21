package ru.krabikk.nntcschedule

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow


@Dao
interface ScheduleDao {

    // -------------------------------------------------------------
    // Добавление занятий
    // -------------------------------------------------------------

    @Insert
    suspend fun insertAll(
        lessons: List<ScheduleEntity>
    )

    // -------------------------------------------------------------
    // Получение сохранённого расписания
    // -------------------------------------------------------------

    @Query(
        "SELECT * FROM schedule ORDER BY date, number"
    )
    suspend fun getAll():
            List<ScheduleEntity>

    // -------------------------------------------------------------
    // Наблюдение за расписанием
    // -------------------------------------------------------------

    @Query(
        "SELECT * FROM schedule ORDER BY date, number"
    )
    fun observeAll():
            Flow<List<ScheduleEntity>>

    // -------------------------------------------------------------
    // Удаление всего расписания
    // -------------------------------------------------------------

    @Query(
        "DELETE FROM schedule"
    )
    suspend fun deleteAll()

    // -------------------------------------------------------------
    // Полная замена расписания
    // -------------------------------------------------------------

    @Transaction
    suspend fun replaceAll(
        lessons: List<ScheduleEntity>
    ) {

        deleteAll()

        insertAll(
            lessons
        )
    }
}