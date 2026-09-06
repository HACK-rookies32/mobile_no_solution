package com.hack.kidhack.background.alert

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * 앱이 꺼져 있어도 부모가 알림을 받게 하는 백업 경로.
 *
 * FCM 을 쓰지 않으므로 15분(WorkManager 최소 주기)마다 서버 알림 목록을 긁어와
 * 새 알림만 푸시로 띄운다. 앱을 켜 두었을 때는 화면 새로고침이 같은 [ServerAlertSync] 를
 * 먼저 실행하므로 여기서는 대부분 새 알림 없이 끝난다.
 */
class ServerAlertPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val alertSync: ServerAlertSync by inject()
    private val watcher: ParentChildWatcher by inject()

    override suspend fun doWork(): Result {
        val result = runCatching { alertSync.sync() }.getOrElse { return Result.retry() }
        // 서버가 알림을 만들어 주지 않는 사건은 보호자 앱이 직접 판정한다.
        runCatching { watcher.evaluateAll() }
        return if (result.remoteFailed) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE = "kidhack.parent.alert.poll"
        private const val UNIQUE_NOW = "kidhack.parent.alert.poll.now"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServerAlertPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** 로그인 직후처럼 15분을 기다릴 수 없을 때 한 번 바로 돌린다. */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ServerAlertPollWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(UNIQUE)
                cancelUniqueWork(UNIQUE_NOW)
            }
        }

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
