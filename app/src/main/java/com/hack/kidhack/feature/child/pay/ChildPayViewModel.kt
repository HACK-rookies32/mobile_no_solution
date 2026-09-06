package com.hack.kidhack.feature.child.pay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.QrPaymentHistoryItem
import com.hack.kidhack.domain.model.QrTicket
import com.hack.kidhack.domain.repository.QrPayRepository
import com.hack.kidhack.domain.repository.StoreTerminalRepository
import com.hack.kidhack.domain.repository.WalletRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChildPayUiState(
    val loading: Boolean = true,
    val balance: Int = 0,
    val history: List<QrPaymentHistoryItem> = emptyList(),
    val ticket: QrTicket? = null,
    val secondsLeft: Int = 0,
    val issuing: Boolean = false,
    /** 테스트 결제(매장 단말 승인 흉내) 호출 중. 다이얼로그 버튼을 잠근다. */
    val testPaying: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * 자녀 모드 루키즈페이 (서버 연동).
 *
 * 잔액의 공급원은 부모의 「용돈 보내기」다. 결제는 **서버가 발급한 QR 토큰**([QrPayRepository.issueQr])을
 * 화면에 띄우고, **매장 단말**이 승인(태그 16)할 때 확정된다. 자녀 앱은 승인하지 않고
 * [QrPayRepository.qrStatus] 로 상태만 폴링한다 — 옛 자가승인 로직은 제거했다.
 * 금액은 자녀가 정하지 않는다(단말 입력).
 */
class ChildPayViewModel(
    private val qrPayRepository: QrPayRepository,
    private val walletRepository: WalletRepository,
    // 테스트 결제 버튼이 매장 단말 승인을 대신 호출하는 데 쓴다. 실제 결제 흐름에서는
    // 매장 기기가 하는 일이라, 자녀 앱이 이걸 직접 부르는 건 화면 테스트용이다.
    private val storeTerminalRepository: StoreTerminalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildPayUiState())
    val state: StateFlow<ChildPayUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val balance = runCatching { walletRepository.getMyWallet().balance }
            val history = runCatching { qrPayRepository.paymentHistory(childId = null) }
            _state.update {
                it.copy(
                    loading = false,
                    balance = balance.getOrDefault(it.balance),
                    history = history.getOrDefault(it.history),
                    error = if (balance.isFailure) "잔액을 불러오지 못했어요" else it.error,
                )
            }
        }
    }

    /** 결제 QR 발급 — 서버가 토큰을 만든다. 발급 후 상태를 폴링해 매장 승인을 감지한다. */
    fun issueQr() {
        if (_state.value.issuing || _state.value.ticket != null) return
        viewModelScope.launch {
            _state.update { it.copy(issuing = true, error = null, message = null) }
            runCatching { qrPayRepository.issueQr() }
                .onSuccess { ticket ->
                    _state.update { it.copy(issuing = false, ticket = ticket) }
                    startCountdownAndPoll(ticket)
                }
                .onFailure { t ->
                    _state.update { it.copy(issuing = false, error = t.userMessage("QR 발급에 실패했어요")) }
                }
        }
    }

    fun cancelTicket() {
        pollJob?.cancel()
        _state.update { it.copy(ticket = null, secondsLeft = 0) }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    /**
     * 화면에서 바로 결제를 끝내 보는 테스트용 승인.
     *
     * <p>실제로는 매장 단말이 QR 을 찍고 승인하는데, 데모에서는 단말이 없으니 자녀 앱이 그
     * 역할을 대신한다: 임시 매장 단말 토큰을 하나 발급받아 지금 떠 있는 QR 토큰으로 [amount]
     * 만큼 승인을 호출한다. 승인이 되면 서버가 QR 을 PAID 로 바꾸고, 이미 돌고 있는 폴링이
     * 그걸 잡아 화면을 「결제 완료」로 넘기고 잔액을 새로고침한다 — 실제 결제와 같은 경로다.
     */
    fun testPay(storeName: String, amount: Int) {
        val ticket = _state.value.ticket ?: return
        if (amount <= 0 || _state.value.testPaying) return
        viewModelScope.launch {
            _state.update { it.copy(testPaying = true, error = null, message = null) }
            runCatching {
                val terminal = storeTerminalRepository.issueTerminal(storeName.ifBlank { "테스트 매장" })
                storeTerminalRepository.approve(terminal.terminalToken, ticket.qrToken, amount)
            }.onSuccess {
                // 성공 메시지·잔액·QR 상태는 폴링이 PAID 를 잡아 처리한다. 여기서는 플래그만 내린다.
                _state.update { it.copy(testPaying = false) }
            }.onFailure { t ->
                _state.update { it.copy(testPaying = false, error = t.userMessage("테스트 결제에 실패했어요")) }
            }
        }
    }

    private fun startCountdownAndPoll(ticket: QrTicket) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var sinceLastPoll = 0
            while (isActive) {
                val current = _state.value.ticket ?: return@launch
                val left = ((current.expiresAtMillis - System.currentTimeMillis()) / 1000).toInt()
                if (left <= 0) {
                    _state.update {
                        it.copy(ticket = null, secondsLeft = 0, error = "QR 이 만료됐어요. 다시 만들어 주세요")
                    }
                    return@launch
                }
                _state.update { it.copy(secondsLeft = left) }

                // 매장 단말 승인 여부를 서버에서 확인(2초 간격).
                if (sinceLastPoll >= POLL_INTERVAL_SECONDS) {
                    sinceLastPoll = 0
                    val status = runCatching { qrPayRepository.qrStatus(current.qrToken) }.getOrNull()
                    when (status?.status) {
                        "PAID" -> {
                            _state.update { it.copy(ticket = null, secondsLeft = 0, message = "결제가 끝났어요!") }
                            refresh()
                            return@launch
                        }
                        "CANCELLED" -> {
                            _state.update { it.copy(ticket = null, secondsLeft = 0, error = "결제가 취소됐어요") }
                            refresh()
                            return@launch
                        }
                        "EXPIRED" -> {
                            _state.update { it.copy(ticket = null, secondsLeft = 0, error = "QR 이 만료됐어요. 다시 만들어 주세요") }
                            return@launch
                        }
                    }
                }
                delay(1000)
                sinceLastPoll += 1
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_SECONDS = 2
    }
}
