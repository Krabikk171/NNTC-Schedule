package ru.krabikk.nntcschedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val channel = AppUpdatePreferences.getChannel(applicationContext)
        val offer = AppUpdateRepository.check(applicationContext, channel)
        if (offer != null && AppUpdatePreferences.shouldNotify(applicationContext, channel, offer.versionCode)) {
            AppUpdateNotification.show(applicationContext, offer)
            AppUpdatePreferences.markNotified(applicationContext, channel, offer.versionCode)
        }
        Result.success()
    } catch (_: IOException) {
        Result.retry()
    } catch (_: Exception) {
        // Bad metadata must not crash periodic processing; retry on the next run.
        Result.success()
    }
}

object AppUpdateWorkScheduler {
    private const val WORK = "nntc_apk_update_check"
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = PeriodicWorkRequestBuilder<AppUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP, work
        )
    }
}

private object AppUpdateNotification {
    private const val CHANNEL = "app_version_updates"
    private const val NOTIFICATION_ID = 3101

    fun show(context: Context, offer: AppUpdateOffer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Новые версии приложения", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val activity = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, 3101, activity,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("NNTC Schedule ${offer.versionName}")
                .setContentText("Доступна новая версия. Откройте Настройки → Обновление приложения.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }
}
