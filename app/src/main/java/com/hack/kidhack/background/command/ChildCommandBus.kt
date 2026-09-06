package com.hack.kidhack.background.command

import com.hack.kidhack.domain.model.RemoteCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 알림 탭 → UI 사이의 통로.
 *
 * 알림을 누르면 `MainActivity` 가 인텐트를 여기로 흘려 보내고,
 * `KidhackNavHost` 가 이를 구독해 카메라/캡처 화면으로 이동시킨다.
 * replay = 1 이라 앱이 죽어 있다가 켜지는 콜드 스타트에서도 요청을 놓치지 않는다.
 */
class ChildCommandBus {
    private val _pending = MutableSharedFlow<RemoteCommand>(replay = 1, extraBufferCapacity = 8)
    val pending: SharedFlow<RemoteCommand> = _pending.asSharedFlow()

    fun post(command: RemoteCommand) {
        _pending.tryEmit(command)
    }

    /** 화면이 요청을 소비하고 나면 replay 캐시를 비운다(회전 시 재실행 방지). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consume() {
        _pending.resetReplayCache()
    }
}
