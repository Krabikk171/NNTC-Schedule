package ru.krabikk.nntcschedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit


object ScheduleWorkScheduler {

    internal const val PERIODIC_TAG = "periodic_schedule_sync"
    private const val WORK_NAME =
        "schedule_auto_update"


    fun schedule(
        context: Context,
        intervalMinutes: Long
    ) {

        val workManager =
            WorkManager.getInstance(
                context
            )


        // WorkManager не поддерживает
        // периодические задачи чаще одного
        // раза в 15 минут.

        if (
            intervalMinutes < 15L
        ) {

            workManager.cancelUniqueWork(
                WORK_NAME
            )

            return
        }


        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    NetworkType.CONNECTED
                )
                .build()


        val workRequest =
            PeriodicWorkRequestBuilder<
                    ScheduleUpdateWorker
                    >(
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .addTag(PERIODIC_TAG)
                .setConstraints(
                    constraints
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .build()


        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,

            ExistingPeriodicWorkPolicy.UPDATE,

            workRequest
        )
    }
}