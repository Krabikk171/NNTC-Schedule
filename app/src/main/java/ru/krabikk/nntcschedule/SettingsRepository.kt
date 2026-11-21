package ru.krabikk.nntcschedule

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


private val Context.settingsDataStore by preferencesDataStore(
    name = "settings"
)


object SettingsRepository {

    // -------------------------------------------------------------
    // Ключи DataStore
    // -------------------------------------------------------------

    private val CHECK_INTERVAL_MINUTES =
        longPreferencesKey(
            "check_interval_minutes"
        )

    private val THEME_MODE =
        stringPreferencesKey(
            "theme_mode"
        )

    private val INTERFACE_STYLE =
        stringPreferencesKey("interface_style")

    // Для существующих пользователей alpha.6 лента остаётся включённой,
    // пока пользователь сам её не отключит. На парсер не влияет.
    private val DAY_STRIP_ENABLED =
        booleanPreferencesKey("day_strip_enabled")

    // Выбор группы не зависит от темы и хранится между запусками.
    private val SELECTED_GROUP = stringPreferencesKey("selected_group")

    // Введено в 3.1.0-alpha.5. Прежние установки проходят мастер один раз,
    // затем открывают расписание сразу. Этот флаг независим от версии приложения.
    private val PROFILE_SETUP_COMPLETED = booleanPreferencesKey("profile_setup_completed_v310")
    private val PROFILE_ROLE = stringPreferencesKey("profile_role")

    private val LAST_CHECK_TIME =
        longPreferencesKey(
            "last_check_time"
        )

    // The latest ATTEMPT may fail; LAST_CHECK_TIME below tracks only success.
    private val LAST_SYNC_OUTCOME = stringPreferencesKey("last_sync_outcome")
    private val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
    private val LAST_SYNC_DETAILS = stringPreferencesKey("last_sync_details")

    private val LAST_CHANGE_TIME =
        longPreferencesKey(
            "last_change_time"
        )


    // -------------------------------------------------------------
    // Режимы темы
    // -------------------------------------------------------------

    object ThemeMode {

        const val SYSTEM =
            "system"

        const val LIGHT =
            "light"

        const val DARK =
            "dark"
    }


    // -------------------------------------------------------------
    // Интервал автопроверки
    // -------------------------------------------------------------

    fun getCheckInterval(
        context: Context
    ): Flow<Long> {

        return context
            .settingsDataStore
            .data
            .map { preferences ->

                val value =
                    preferences[
                        CHECK_INTERVAL_MINUTES
                    ]


                when {

                    value == null ->
                        30L

                    value < 0L ->
                        30L

                    else ->
                        value
                }
            }
    }


    suspend fun setCheckInterval(
        context: Context,
        minutes: Long
    ) {

        val safeValue =
            when {

                minutes < 0L ->
                    30L

                else ->
                    minutes
            }


        context
            .settingsDataStore
            .edit { preferences ->

                preferences[
                    CHECK_INTERVAL_MINUTES
                ] = safeValue
            }
    }


    // -------------------------------------------------------------
    // Тема оформления
    // -------------------------------------------------------------

    fun getThemeMode(
        context: Context
    ): Flow<String> {

        return context
            .settingsDataStore
            .data
            .map { preferences ->

                when (
                    preferences[
                        THEME_MODE
                    ]
                ) {

                    ThemeMode.LIGHT ->
                        ThemeMode.LIGHT

                    ThemeMode.DARK ->
                        ThemeMode.DARK

                    else ->
                        ThemeMode.SYSTEM
                }
            }
    }


    suspend fun setThemeMode(
        context: Context,
        mode: String
    ) {

        val safeMode =
            when (mode) {

                ThemeMode.LIGHT ->
                    ThemeMode.LIGHT

                ThemeMode.DARK ->
                    ThemeMode.DARK

                else ->
                    ThemeMode.SYSTEM
            }


        context
            .settingsDataStore
            .edit { preferences ->

                preferences[
                    THEME_MODE
                ] = safeMode
            }
    }


    // -------------------------------------------------------------
    // Визуальный стиль. По умолчанию прежний Minimal, отдельный
    // от системного/светлого/тёмного режима. Сохраняем в DataStore.
    // -------------------------------------------------------------

    fun getInterfaceStyle(context: Context): Flow<String> =
        context.settingsDataStore.data.map { preferences ->
            InterfaceStyle.normalized(preferences[INTERFACE_STYLE])
        }

    suspend fun setInterfaceStyle(context: Context, style: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[INTERFACE_STYLE] = InterfaceStyle.normalized(style)
        }
    }

    // -------------------------------------------------------------
    // Только отображение расписания: лента дат или список всех дней.
    // Храним отдельно от выбора визуального стиля и цветовой темы.
    // -------------------------------------------------------------

    fun getDayStripEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DAY_STRIP_ENABLED] ?: true
        }

    suspend fun setDayStripEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DAY_STRIP_ENABLED] = enabled
        }
    }

    // -------------------------------------------------------------
    // Выбранная группа. У старых установок автоматически 2РПУ-25-1.
    // -------------------------------------------------------------
    fun getSelectedGroup(context: Context): Flow<String> =
        context.settingsDataStore.data.map { preferences ->
            preferences[SELECTED_GROUP]?.takeIf { it.isNotBlank() } ?: "2РПУ-25-1"
        }

    suspend fun setSelectedGroup(context: Context, group: String) {
        val name = group.trim()
        require(name.isNotEmpty() && name.length <= 48)
        context.settingsDataStore.edit { preferences -> preferences[SELECTED_GROUP] = name }
    }

    // -------------------------------------------------------------
    // Настройка профиля при первом запуске / после перехода с прежней версии.
    // Nullable поток помогает избежать вспышки мастера до чтения DataStore.
    // Режим преподавателя пока недоступен: не сохраняем фиктивный профиль.
    // -------------------------------------------------------------
    fun getProfileSetupCompleted(context: Context): Flow<Boolean?> =
        context.settingsDataStore.data.map { prefs ->
            val completed: Boolean? = prefs[PROFILE_SETUP_COMPLETED] ?: false
            completed
        }

    suspend fun completeStudentProfile(context: Context, group: String) {
        val name = group.trim()
        require(name.isNotEmpty() && name.length <= 48)
        context.settingsDataStore.edit { prefs ->
            prefs[SELECTED_GROUP] = name
            prefs[PROFILE_ROLE] = "student"
            prefs[PROFILE_SETUP_COMPLETED] = true
        }
    }

    // -------------------------------------------------------------
    // Время последней проверки
    // -------------------------------------------------------------

    fun getLastCheckTime(
        context: Context
    ): Flow<Long?> {

        return context
            .settingsDataStore
            .data
            .map { preferences ->

                preferences[
                    LAST_CHECK_TIME
                ]
            }
    }


    suspend fun setLastCheckTime(
        context: Context,
        timeMillis: Long =
            System.currentTimeMillis()
    ) {

        if (timeMillis < 0L) {
            return
        }


        context
            .settingsDataStore
            .edit { preferences ->

                preferences[
                    LAST_CHECK_TIME
                ] = timeMillis
            }
    }


    // -------------------------------------------------------------
    // Время последнего изменения расписания
    // -------------------------------------------------------------

    fun getLastChangeTime(
        context: Context
    ): Flow<Long?> {

        return context
            .settingsDataStore
            .data
            .map { preferences ->

                preferences[
                    LAST_CHANGE_TIME
                ]
            }
    }


    suspend fun setLastChangeTime(
        context: Context,
        timeMillis: Long =
            System.currentTimeMillis()
    ) {

        if (timeMillis < 0L) {
            return
        }

        context
            .settingsDataStore
            .edit { preferences ->

                preferences[
                    LAST_CHANGE_TIME
                ] = timeMillis
            }
    }

    /** Last attempt, including failures. Earlier alpha versions simply have no record. */
    fun getLastSyncReport(context: Context): Flow<SyncReport?> =
        context.settingsDataStore.data.map { preferences ->
            SyncReport.fromStorage(
                preferences[LAST_SYNC_OUTCOME],
                preferences[LAST_SYNC_TIME],
                preferences[LAST_SYNC_DETAILS]
            )
        }

    /** One atomic DataStore write for the outcome of a completed manual/background check. */
    suspend fun setLastSyncReport(
        context: Context,
        outcome: SyncOutcome,
        details: String = "",
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        if (timestampMillis < 0L) return
        context.settingsDataStore.edit { preferences ->
            preferences[LAST_SYNC_OUTCOME] = outcome.name
            preferences[LAST_SYNC_TIME] = timestampMillis
            preferences[LAST_SYNC_DETAILS] = details.trim().take(240)
        }
    }

}
