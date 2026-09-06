package com.hack.kidhack.feature.child.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.repository.WalletRepository
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import com.hack.kidhack.feature.child.formatDurationSec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildMypageUiState(
    val name: String = "",
    val email: String = "",
    val screenTime: String = formatDurationSec(0),
    val chatCount: Int = 0,
    val blockedCount: Int = 0,
    val payBalance: Int = 0,
)

class ChildMypageViewModel(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val usageCollector: AppUsageCollector,
    private val notificationRepository: NotificationRepository,
    private val walletRepository: WalletRepository,
    private val alertStore: ChildAlertStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildMypageUiState())
    val state: StateFlow<ChildMypageUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val user = sessionRepository.current()?.user
            val childId = sessionRepository.childId()

            // 채팅 건수용. 「연결된 보호자」 표시는 걷어냈다 — 채팅방 유무로 판단하던 값이라
            // 부모가 채팅을 한 번도 열지 않으면 페어링이 돼 있어도 「없음」으로 보였다.
            val rooms = runCatching { chatRepository.rooms() }.getOrDefault(emptyList())
            val chatCount = rooms.firstOrNull()
                ?.let { room -> runCatching { chatRepository.messages(room.id).messages.size }.getOrDefault(0) }
                ?: 0
            // 서비스 현황 탭과 같은 값을 보여야 하므로 동일한 수집기를 쓴다.
            val usage = usageCollector.todayUsage()
            val notifications = runCatching { notificationRepository.list(childId) }.getOrDefault(emptyList())
            val balance = runCatching { walletRepository.getMyWallet().balance }.getOrDefault(0)
            val blockedToday = alertStore.countToday(ChildAlertStore.TYPE_HARMFUL_BLOCK)

            _state.update {
                it.copy(
                    name = user?.name.orEmpty(),
                    email = user?.email.orEmpty(),
                    screenTime = formatDurationSec(usage.totalSeconds),
                    chatCount = chatCount,
                    blockedCount = blockedToday + notifications.count { n ->
                        val type = n.type.uppercase()
                        type.contains("HARMFUL") || type.contains("BLOCK")
                    },
                    payBalance = balance,
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { authRepository.logout() }
        }
    }
}
