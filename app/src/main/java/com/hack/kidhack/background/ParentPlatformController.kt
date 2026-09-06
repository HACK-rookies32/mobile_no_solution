package com.hack.kidhack.background

import android.content.Context
import com.hack.kidhack.background.alert.ServerAlertPollWorker
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 부모 세션에서만 도는 백그라운드 작업.
 *
 * 지금은 알림 폴링 하나뿐이다. 자녀 쪽 [ChildPlatformController] 와 짝을 이룬다.
 */
class ParentPlatformController(
    private val context: Context,
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun bind() {
        scope.launch {
            sessionRepository.session
                .map { it?.user?.role == Role.PARENT }
                .distinctUntilChanged()
                .collect { isParent ->
                    if (isParent) {
                        ServerAlertPollWorker.enqueuePeriodic(context)
                        ServerAlertPollWorker.enqueueNow(context)
                    } else {
                        ServerAlertPollWorker.cancel(context)
                    }
                }
        }
    }
}
