package com.hack.kidhack.background

import android.content.Context
import com.hack.kidhack.background.capture.ScreenCaptureWorker
import com.hack.kidhack.background.command.ChildCommandService
import com.hack.kidhack.background.location.GpsStatusMonitor
import com.hack.kidhack.background.location.LocationTrackingService
import com.hack.kidhack.background.alert.ServerAlertPollWorker
import com.hack.kidhack.background.usage.AppUsageWorker
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Starts child-role platform services only after login, onboarding, and required permissions.
 * Parent sessions never start these workers/services.
 */
class ChildPlatformController(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val onboardingStore: ChildOnboardingStore,
    private val gpsStatusMonitor: GpsStatusMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reevaluateTick = MutableStateFlow(0)
    private var running = false

    fun bind() {
        scope.launch {
            combine(
                sessionRepository.session,
                onboardingStore.done,
                reevaluateTick,
            ) { session, done, _ ->
                session?.user?.role == Role.CHILD &&
                    done &&
                    KidhackPermissions.hasAllRequiredForChildControl(context)
            }.distinctUntilChanged().collect { active ->
                if (active) start() else stop()
            }
        }
    }

    /** Re-check permissions after onboarding/signup grants (when [done] may already be true). */
    fun reevaluate() {
        reevaluateTick.update { it + 1 }
    }

    private fun start() {
        if (running) return
        running = true
        LocationTrackingService.start(context)
        gpsStatusMonitor.start()
        AppUsageWorker.enqueuePeriodic(context)
        // 주기만 두면 페어링하고 최대 한 시간이 지나야 부모 화면에 앱 목록이 찬다.
        // 첫 실행이 사용량과 설치 목록을 함께 올린다.
        AppUsageWorker.enqueueNow(context)
        ScreenCaptureWorker.enqueuePeriodic(context)
        // FCM 대체 경로: 상주 WebSocket
        ChildCommandService.start(context)
        // 소켓이 없던 동안 쌓인 안전 사건(유해 차단 등)을 보호자 쪽으로 밀어 올린다.
        ChildAlertUploadWorker.enqueuePeriodic(context)
        ChildAlertUploadWorker.enqueueNow(context)
        // 서버 알림(채팅·스크린타임 한도 등) 팝업. 부모와 같은 경로를 쓴다.
        // 로컬 개발에서는 FCM 이 mock 이라 푸시가 오지 않으므로 이 폴링이 유일한 통로다.
        ServerAlertPollWorker.enqueuePeriodic(context)
        ServerAlertPollWorker.enqueueNow(context)
    }

    private fun stop() {
        if (!running) {
            LocationTrackingService.stop(context)
            ChildCommandService.stop(context)
            return
        }
        running = false
        LocationTrackingService.stop(context)
        gpsStatusMonitor.stop()
        AppUsageWorker.cancel(context)
        ScreenCaptureWorker.cancel(context)
        ChildCommandService.stop(context)
        ChildAlertUploadWorker.cancel(context)
        ServerAlertPollWorker.cancel(context)
    }
}
