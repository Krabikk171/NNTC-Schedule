package ru.krabikk.nntcschedule

import android.content.Context


object ChangelogManager {

    private const val PREFS_NAME =
        "changelog"

    private const val SHOWN_VERSION_KEY =
        "shown_version"


    // -------------------------------------------------------------
    // Нужно ли показать changelog
    // -------------------------------------------------------------

    fun shouldShow(
        context: Context
    ): Boolean {

        val preferences =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )


        val shownVersion =
            preferences.getString(
                SHOWN_VERSION_KEY,
                null
            )


        return shownVersion !=
                Changelog.CURRENT_VERSION
    }


    // -------------------------------------------------------------
    // Помечаем текущую версию как просмотренную
    // -------------------------------------------------------------

    fun markAsShown(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                SHOWN_VERSION_KEY,
                Changelog.CURRENT_VERSION
            )
            .apply()
    }
}