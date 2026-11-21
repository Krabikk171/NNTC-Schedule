package ru.krabikk.nntcschedule.v3

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Separate storage: it never migrates or clears the stable 3.0 schedule database. */
@Entity(tableName = "group_schedule_lessons")
data class GroupLessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupName: String,
    val date: String,
    val weekday: String,
    val number: Int,
    val time: String,
    val subject: String,
    val room: String,
    val teacher: String
)

/** Одна пара может содержать сразу два разных предмета/преподавателя/кабинета. */
@Entity(tableName = "group_subgroup_lessons", primaryKeys = ["groupName", "date", "number", "subgroupNumber"])
data class GroupSubgroupEntity(
    val groupName: String,
    val date: String,
    val number: Int,
    val subgroupNumber: Int,
    val subject: String,
    val room: String,
    val teacher: String
)

@Entity(tableName = "group_catalog")
data class GroupInfoEntity(
    @PrimaryKey val name: String,
    val quality: String,
    val lessonCount: Int,
    val warnings: Int
)

@Dao
interface GroupScheduleDao {
    @Query("SELECT * FROM group_schedule_lessons ORDER BY groupName, date, number")
    fun observeLessons(): Flow<List<GroupLessonEntity>>

    @Query("SELECT * FROM group_subgroup_lessons ORDER BY groupName, date, number, subgroupNumber")
    fun observeSubgroups(): Flow<List<GroupSubgroupEntity>>

    @Query("SELECT * FROM group_catalog ORDER BY name")
    fun observeGroups(): Flow<List<GroupInfoEntity>>

    @Query("SELECT COUNT(*) FROM group_catalog")
    suspend fun groupCount(): Int

    @Query("SELECT * FROM group_schedule_lessons WHERE groupName = :groupName ORDER BY date, number")
    suspend fun lessonsForGroup(groupName: String): List<GroupLessonEntity>

    @Query("SELECT * FROM group_catalog WHERE name = :groupName LIMIT 1")
    suspend fun infoForGroup(groupName: String): GroupInfoEntity?

    @Query("DELETE FROM group_schedule_lessons")
    suspend fun clearLessons()

    @Query("DELETE FROM group_subgroup_lessons")
    suspend fun clearSubgroups()

    @Query("DELETE FROM group_catalog")
    suspend fun clearGroups()

    @Insert
    suspend fun insertLessons(lessons: List<GroupLessonEntity>)

    @Insert
    suspend fun insertSubgroups(subgroups: List<GroupSubgroupEntity>)

    @Insert
    suspend fun insertGroups(groups: List<GroupInfoEntity>)

    /** A failed import must not erase a previous complete catalog. */
    @Transaction
    suspend fun replaceCatalog(
        groups: List<GroupInfoEntity>, lessons: List<GroupLessonEntity>,
        subgroups: List<GroupSubgroupEntity>
    ) {
        require(groups.isNotEmpty() && lessons.isNotEmpty())
        clearSubgroups()
        clearLessons()
        clearGroups()
        insertGroups(groups)
        insertLessons(lessons)
        if (subgroups.isNotEmpty()) insertSubgroups(subgroups)
    }
}

@Database(
    entities = [GroupLessonEntity::class, GroupInfoEntity::class, GroupSubgroupEntity::class],
    version = 2,
    exportSchema = false
)
abstract class GroupScheduleDatabase : RoomDatabase() {
    abstract fun groupScheduleDao(): GroupScheduleDao

    companion object {
        @Volatile private var instance: GroupScheduleDatabase? = null

        /** Без destructive migration: пользовательский кэш alpha.7 сохраняется. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `group_subgroup_lessons` (
                    `groupName` TEXT NOT NULL,
                    `date` TEXT NOT NULL,
                    `number` INTEGER NOT NULL,
                    `subgroupNumber` INTEGER NOT NULL,
                    `subject` TEXT NOT NULL,
                    `room` TEXT NOT NULL,
                    `teacher` TEXT NOT NULL,
                    PRIMARY KEY(`groupName`, `date`, `number`, `subgroupNumber`)
                )""".trimIndent())
            }
        }

        fun get(context: Context): GroupScheduleDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                GroupScheduleDatabase::class.java,
                "nntc_group_catalog_v1.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
