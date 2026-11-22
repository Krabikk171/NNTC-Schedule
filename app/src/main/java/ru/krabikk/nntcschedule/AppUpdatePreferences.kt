package ru.krabikk.nntcschedule

import android.content.Context

/** Stored separately to avoid changing the established schedule DataStore schema. */
object AppUpdatePreferences {
    private const val STORE = "app_update_preferences"
    private const val CHANNEL = "channel"

    fun getChannel(context: Context): AppUpdateChannel = AppUpdateChannel.fromValue(
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(CHANNEL, AppUpdateChannel.STABLE.value)
    )

    fun setChannel(context: Context, channel: AppUpdateChannel) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(CHANNEL, channel.value).apply()
    }

    fun shouldNotify(context: Context, channel: AppUpdateChannel, code: Long): Boolean {
        val key = "notified_${channel.value}"
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getLong(key, 0L) < code
    }

    fun markNotified(context: Context, channel: AppUpdateChannel, code: Long) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putLong("notified_${channel.value}", code).apply()
    }
}
