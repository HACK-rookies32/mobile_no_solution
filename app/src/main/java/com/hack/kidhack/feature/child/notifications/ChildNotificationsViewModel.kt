package com.hack.kidhack.feature.child.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.notificationRecipientId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildNotificationsUiState(
    val loading: Boolean = true,
    val items: List<AppNotification> = emptyList(),
    val error: String? = null,
)

/**
 * 자녀 알림 화면.
 *
 * <p>여태 자녀 앱에는 알림을 <b>읽음 처리하는 화면이 없어서</b>, 서버의 안 읽은 수가 줄지
 * 않고 계속 쌓였다(읽음 호출을 아무도 안 함). 이 화면이 열릴 때 목록을 받아 보여 주고 곧바로
 * 전체 읽음 처리한다 — 부모 알림 화면과 같은 방식이다.
 *
 * <p>표시에 쓰는 [AppNotification.read] 는 <b>불러온 시점의 값</b>이라, 이번에 안 읽었던 알림은
 * 이 화면에서 강조된 채로 남는다. 서버는 이미 읽음으로 바뀌었으니 다음에 오면 강조가 사라진다.
 */
class ChildNotificationsViewModel(
    private val notificationRepository: NotificationRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildNotificationsUiState())
    val state: StateFlow<ChildNotificationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val recipientId = sessionRepository.notificationRecipientId()
            runCatching { notificationRepository.list(recipientId) }
                .onSuccess { items ->
                    _state.update {
                        it.copy(
                            loading = false,
                            // 서버가 최신순으로 주지 않을 수 있어 여기서 한 번 더 정렬한다.
                            items = items.sortedByDescending { n -> n.createdAt },
                        )
                    }
                    // 목록을 보여 준 뒤 전체 읽음 처리. 실패해도 화면은 그대로 둔다.
                    runCatching { notificationRepository.markAllRead(recipientId) }
                }
                .onFailure {
                    _state.update { it.copy(loading = false, error = "알림을 불러오지 못했어요") }
                }
        }
    }
}
