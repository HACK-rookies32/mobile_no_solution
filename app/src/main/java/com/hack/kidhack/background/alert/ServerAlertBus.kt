package com.hack.kidhack.background.alert

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 푸시 알림 탭 → 알림 탭 이동 통로.
 *
 * `MainActivity` 가 인텐트를 여기로 흘려 보내고 `KidhackNavHost` 가 구독한다.
 * replay = 1 이라 앱이 꺼져 있다 켜지는 콜드 스타트에서도 이동을 놓치지 않는다.
 */
class ServerAlertBus {
    private val _pending = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
    val pending: SharedFlow<String> = _pending.asSharedFlow()

    fun post(alertId: String) {
        _pending.tryEmit(alertId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consume() {
        _pending.resetReplayCache()
    }
}
