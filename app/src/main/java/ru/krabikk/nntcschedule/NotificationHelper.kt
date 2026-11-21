package ru.krabikk.nntcschedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


object NotificationHelper {

    private const val CHANNEL_ID =
        "schedule_updates"

    private const val CHANNEL_NAME =
        "Изменения расписания"

    private const val CHANNEL_DESCRIPTION =
        "Уведомления об изменениях расписания"

    /*
     * Один ID используется для уведомлений расписания.
     *
     * Поэтому новое уведомление заменяет предыдущее,
     * а в шторке не появляется несколько одинаковых
     * уведомлений об обновлении.
     */
    private const val NOTIFICATION_ID =
        2001

    data class ScheduleNotification(
        val title: String,
        val text: String
    )

    // -------------------------------------------------------------
    // Создание канала уведомлений
    // -------------------------------------------------------------

    fun createChannel(
        context: Context
    ) {

        if (
            android.os.Build.VERSION.SDK_INT <
            android.os.Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {

                description =
                    CHANNEL_DESCRIPTION
            }

        val notificationManager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        notificationManager.createNotificationChannel(
            channel
        )
    }

    // -------------------------------------------------------------
    // Обычное уведомление об изменении расписания
    // -------------------------------------------------------------

    fun showScheduleChangedNotification(
        context: Context,
        title: String,
        text: String
    ) {

        showScheduleChangedNotifications(
            context =
                context,

            notifications =
                listOf(
                    ScheduleNotification(
                        title =
                            title,

                        text =
                            text
                    )
                )
        )
    }

    // -------------------------------------------------------------
    // Универсальное уведомление расписания
    // -------------------------------------------------------------

    fun showScheduleChangedNotifications(
        context: Context,
        notifications:
        List<ScheduleNotification>
    ) {

        if (
            notifications.isEmpty()
        ) {
            return
        }

        // ---------------------------------------------------------
        // Проверка разрешения
        // ---------------------------------------------------------

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.TIRAMISU
        ) {

            val permissionGranted =
                context.checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) ==
                        PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                return
            }
        }

        // ---------------------------------------------------------
        // Канал
        // ---------------------------------------------------------

        createChannel(
            context
        )

        // ---------------------------------------------------------
        // Объединяем уведомления
        // ---------------------------------------------------------

        val title: String
        val text: String

        if (
            notifications.size == 1
        ) {

            val notification =
                notifications.first()

            title =
                notification.title

            text =
                notification.text

        } else {

            title =
                "Изменения расписания"

            text =
                notifications.joinToString(
                    separator =
                        "\n\n"
                ) { notification ->

                    "${notification.title}\n" +
                            notification.text
                }
        }

        // ---------------------------------------------------------
        // Короткий текст для свернутого уведомления
        // ---------------------------------------------------------

        val shortText =
            buildShortNotificationText(
                title =
                    title,

                text =
                    text
            )

        // ---------------------------------------------------------
        // Открытие приложения
        // ---------------------------------------------------------

        val pendingIntent =
            createPendingIntent(
                context
            )

        // ---------------------------------------------------------
        // Создание уведомления
        // ---------------------------------------------------------

        val notification =
            NotificationCompat
                .Builder(
                    context,
                    CHANNEL_ID
                )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    shortText
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            text
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_EVENT
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(
                    true
                )

                /*
                 * Каждое новое обновление должно
                 * снова уведомлять пользователя.
                 */
                .setOnlyAlertOnce(
                    false
                )
                .setWhen(
                    System.currentTimeMillis()
                )
                .setShowWhen(
                    true
                )
                .build()

        // ---------------------------------------------------------
        // Показываем / заменяем уведомление
        // ---------------------------------------------------------

        NotificationManagerCompat
            .from(
                context
            )
            .notify(
                NOTIFICATION_ID,
                notification
            )
    }

    // -------------------------------------------------------------
    // Короткий текст уведомления
    // -------------------------------------------------------------

    private fun buildShortNotificationText(
        title: String,
        text: String
    ): String {

        val firstLine =
            text
                .lineSequence()
                .firstOrNull {
                    it.isNotBlank()
                }
                ?.trim()
                .orEmpty()

        if (
            firstLine.isBlank()
        ) {
            return title
        }

        val maxLength =
            180

        return if (
            firstLine.length > maxLength
        ) {

            firstLine.take(
                maxLength - 1
            ) + "…"

        } else {

            firstLine
        }
    }

    // -------------------------------------------------------------
    // PendingIntent
    // -------------------------------------------------------------

    private fun createPendingIntent(
        context: Context
    ): PendingIntent {

        val intent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        return PendingIntent.getActivity(
            context,

            0,

            intent,

            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }
}