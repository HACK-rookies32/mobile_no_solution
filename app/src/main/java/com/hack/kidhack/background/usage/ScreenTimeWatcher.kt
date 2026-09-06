package com.hack.kidhack.background.usage

import android.content.Context
import com.hack.kidhack.background.ChildAlertUploadWorker
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId

/**
 * 스크린타임 한도 도달을 감시한다.
 *
 * 한도는 **부모가 서버에 설정한 값**이다(`GET /appcontrol/screentime/{childId}`).
 * 예전에는 서버에 그 개념이 없어 180분을 상수로 들고 있었고, 부모가 무엇을 설정하든
 * 모든 자녀에게 3시간으로 보였다.
 *
 * 한도가 없으면(`null`) 감시하지 않는다 — 제한 없음이다. 0 은 "한도 0분"이라 의미가 달라
 * 곧바로 도달로 판정된다.
 *
 * 판정을 앱이 하는 이유: 사용량을 실측하는 쪽이 앱이다. 서버가 집계로 판정하려면 주기
 * 배치가 필요한데 그 집계도 결국 앱이 올린 값이라 신뢰 수준은 같으면서 지연만 늘어난다.
 */
class ScreenTimeWatcher(
    private val alertStore: ChildAlertStore,
    private val context: Context,
    private val appControlRepository: AppControlRepository,
    private val sessionRepository: SessionRepository,
) {
    /**
     * 마지막으로 읽은 한도. 접근성 서비스처럼 매번 네트워크를 탈 수 없는 곳이 참조한다.
     * null 은 「제한 없음 또는 아직 모름」이다.
     */
    @Volatile
    var cachedLimitMinutes: Int? = null
        private set

    /** 오늘 한도에 닿았는지. 접근성 서비스가 앱 차단 여부를 이 값으로 판단한다. */
    @Volatile
    var limitReached: Boolean = false
        private set

    /** 서버에서 한도를 다시 읽어 캐시한다. 실패하면 이전 값을 유지한다. */
    suspend fun refreshLimit(): Int? {
        val id = sessionRepository.childId().takeIf { it.isNotBlank() } ?: return cachedLimitMinutes
        runCatching { appControlRepository.screenTimeLimit(id) }
            .onSuccess { cachedLimitMinutes = it }
        return cachedLimitMinutes
    }

    /**
     * 한도에 닿았으면 하루 한 번만 기록하고 서버에 보고한다.
     *
     * 처음 닿은 순간에는 사용량을 **즉시** 서버로 보낸다. 그러지 않으면 다음 정시
     * (1시간 주기 [AppUsageWorker])까지 서버가 모르고, 보호자 알림도 그만큼 늦게 뜬다.
     *
     * @param reportToServer 이미 사용량을 보고하는 중이면(=워커 내부) false 로 꺼서
     *  같은 보고를 두 번 예약하지 않는다.
     * @return 이번에 처음 한도에 닿았으면 true.
     */
    suspend fun check(usedMinutes: Int, reportToServer: Boolean = true): Boolean {
        val limit = refreshLimit() ?: run {
            limitReached = false
            return false
        }
        if (usedMinutes < limit) {
            limitReached = false
            return false
        }
        limitReached = true

        val recorded = alertStore.recordOncePerDay(
            type = ChildAlertStore.TYPE_SCREEN_TIME,
            title = "오늘 스크린타임 한도에 도달했어요",
            body = "오늘 " + formatMinutes(usedMinutes) + " 사용 · 한도 " + formatMinutes(limit),
        )
        if (recorded) {
            // 서버가 부모·자녀 양쪽에 알림을 발행한다. 실패해도 로컬 기록은 남는다.
            val id = sessionRepository.childId()
            if (id.isNotBlank()) {
                runCatching { appControlRepository.reportScreenTimeReached(id, usedMinutes) }
            }
            // 자녀 홈에 남긴 이 사건을 보호자에게도 나른다(docs/child-push-contract.md 4절).
            ChildAlertUploadWorker.enqueueNow(context)
            if (reportToServer) AppUsageWorker.enqueueNow(context)
        }
        return recorded
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 -> hours.toString() + "시간 " + rest + "분"
            hours > 0 -> hours.toString() + "시간"
            else -> rest.toString() + "분"
        }
    }
}
