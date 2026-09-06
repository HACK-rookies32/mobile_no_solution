package com.hack.kidhack.background.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hack.kidhack.R
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.data.local.ChildAlertStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * 1시간 주기 정기 캡처 (REQ-M-12).
 *
 * MediaProjection 토큰은 저장할 수 없고 안드로이드가 매번 사용자 동의를 요구한다.
 * 그래서 워커는 직접 캡처하지 않고 **동의를 유도하는 알림**만 띄운다.
 * 자녀가 알림을 누르면 `trigger=SCHEDULED` 로 캡처가 진행된다.
 */
class ScreenCaptureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val notifier: ChildCommandNotifier by inject()
    private val alertStore: ChildAlertStore by inject()

    override suspend fun doWork(): Result {
        val requestId = SCHEDULED_PREFIX + System.currentTimeMillis()
        notifier.notify(
            RemoteCommand(
                requestId = requestId,
                type = RemoteCommandType.SCREEN_CAPTURE,
                childId = "",
            ),
        )
        // 알림만 띄우면 지나가는 순간 사라진다. 홈 「최근 알림」에서도 볼 수 있게 남긴다.
        alertStore.record(
            type = ChildAlertStore.TYPE_SCHEDULED_CAPTURE,
            title = applicationContext.getString(R.string.child_alert_scheduled_capture),
            body = applicationContext.getString(R.string.child_alert_scheduled_capture_body),
            dedupeKey = ChildAlertStore.TYPE_SCHEDULED_CAPTURE,
            dedupeWindowMs = DEDUPE_MS,
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE = "kidhack.screen.capture"

        /** requestId 가 이 접두사면 서버 ack 대상이 아닌 자체 스케줄 캡처다. */
        const val SCHEDULED_PREFIX = "scheduled-"

        /** 같은 정기 캡처 안내가 연달아 쌓이지 않도록. */
        private const val DEDUPE_MS = 50 * 60 * 1000L

        /**
         * 1시간 주기 등록.
         *
         * 주의할 점 두 가지가 있다.
         *  - `PeriodicWorkRequest` 는 초기 지연이 없으면 **등록 직후 한 번 바로 실행**된다.
         *    로그인할 때마다 이 함수가 불리므로, 지연이 없으면 로그인마다 캡처 알림이 떴다.
         *  - 정책을 UPDATE 로 두면 재등록이 주기를 다시 시작시킨다. 이미 돌고 있으면
         *    그대로 두는 KEEP 이 맞다.
         */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScreenCaptureWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
