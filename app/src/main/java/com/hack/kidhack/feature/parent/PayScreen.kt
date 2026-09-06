package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.QrPaymentHistoryItem
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import com.hack.kidhack.domain.repository.CardRepository
import com.hack.kidhack.domain.repository.QrPayRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.WalletRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class PayTab { CHARGE, SEND, RECLAIM, HISTORY }
enum class TxFilter { ALL, OUT, IN, PAY }

/** 확인이 필요한 결제 동작 — 충전 환불(PAID)·충전 취소(READY)·자녀 매장결제 취소(PAID). */
sealed interface PayAction {
    val label: String
    data class RefundCharge(val merchantUid: String, val amount: Int) : PayAction {
        override val label get() = "${formatWonText(amount)} 충전을 환불할까요? 지갑 잔액에서 차감돼요."
    }
    data class CancelCharge(val merchantUid: String, val amount: Int) : PayAction {
        override val label get() = "${formatWonText(amount)} 충전 주문을 취소할까요?"
    }
    data class CancelPayment(val qrToken: String, val storeName: String, val amount: Int) : PayAction {
        override val label get() = "$storeName 결제 ${formatWonText(amount)} 를 취소할까요? 자녀 지갑으로 환급돼요."
    }
}

data class PayUiState(
    val wallet: Wallet? = null,
    val childWallet: Wallet? = null,
    val cards: List<Card> = emptyList(),
    val transactions: List<WalletTransaction> = emptyList(),
    /** 자녀별 이체 이력(자녀 선택 시). */
    val childTransfers: List<WalletTransaction> = emptyList(),
    /** 충전 통합 내역. */
    val chargeHistory: List<com.hack.kidhack.domain.model.ChargeHistoryItem> = emptyList(),
    /** 선택한 자녀의 QR 매장결제 내역(태그 19). 부모가 여기서 취소(태그 18)할 수 있다. */
    val childPayments: List<QrPaymentHistoryItem> = emptyList(),
    /** 확인 다이얼로그가 떠 있는 동작. */
    val pendingAction: PayAction? = null,
    val tab: PayTab = PayTab.SEND,
    val txFilter: TxFilter = TxFilter.ALL,
    val selectedCardId: String? = null,
    val amountInput: String = "",
    val memoInput: String = "",
    val childId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null
)

class PayViewModel(
    private val walletRepository: WalletRepository,
    private val cardRepository: CardRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val qrPayRepository: QrPayRepository,
    private val txAuth: com.hack.kidhack.feature.efin.TxAuthCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(PayUiState())
    val state: StateFlow<PayUiState> = _state.asStateFlow()

    init { load() }

    fun onTab(tab: PayTab) = _state.update { it.copy(tab = tab, error = null, successMessage = null, amountInput = "", memoInput = "") }
    fun onAmount(v: String) = _state.update { it.copy(amountInput = v.filter { it.isDigit() }) }
    fun onMemo(v: String) = _state.update { it.copy(memoInput = v) }
    fun onFilter(f: TxFilter) {
        _state.update { it.copy(txFilter = f) }
        loadTransactions()
    }
    fun onSelectCard(id: String) = _state.update { it.copy(selectedCardId = id) }

    /** 카드 관리 화면에서 돌아왔을 때 목록만 갱신한다. 고른 카드가 남아 있으면 선택을 유지한다. */
    fun refreshCards() {
        viewModelScope.launch {
            runCatching { cardRepository.list() }
                .onSuccess { list ->
                    _state.update { s ->
                        val kept = s.selectedCardId?.takeIf { id -> list.any { it.cardId == id } }
                        s.copy(cards = list, selectedCardId = kept ?: list.firstOrNull()?.cardId)
                    }
                }
        }
    }

    /** 마지막 전체 조회 시각 — 화면 재진입 시 과한 재조회를 막는 용도. */
    private var lastLoadedAt = 0L

    /**
     * 화면 재진입용(매장 단말 화면·카드 관리에서 돌아올 때). 뷰모델이 유지되면 init 의 load() 가 다시 돌지 않아
     * 자녀 잔액·매장 결제 내역이 옛 값으로 남던 문제 — 최근 3초 안에 조회했으면 건너뛴다.
     */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastLoadedAt < 3_000L) return
        load()
    }

    fun load() {
        lastLoadedAt = System.currentTimeMillis()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val childId = selectedChildRepository.current()
                val wallet = walletRepository.getMyWallet()
                val cards = cardRepository.list()
                val childWallet = childId?.let { walletRepository.getChildWallet(it) }
                Triple(wallet, cards, childWallet to childId)
            }.onSuccess { (w, c, cw) ->
                _state.update { it.copy(
                    loading = false,
                    wallet = w,
                    cards = c,
                    childWallet = cw.first,
                    childId = cw.second,
                    selectedCardId = c.firstOrNull()?.cardId
                ) }
                loadTransactions()
                loadChargeHistory()
                cw.second?.let {
                    loadChildTransfers(it)
                    loadChildPayments(it)
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    private fun loadChildPayments(childId: String) {
        viewModelScope.launch {
            runCatching { qrPayRepository.paymentHistory(childId, 0) }
                .onSuccess { list -> _state.update { it.copy(childPayments = list) } }
        }
    }

    fun request(action: PayAction) = _state.update { it.copy(pendingAction = action, error = null, successMessage = null) }
    fun dismissAction() = _state.update { it.copy(pendingAction = null) }

    /** 확인 다이얼로그에서 「확인」. 서버 반영 뒤 전체를 다시 읽어 잔액·내역을 맞춘다. */
    fun confirmAction() {
        val action = _state.value.pendingAction ?: return
        _state.update { it.copy(pendingAction = null, loading = true) }
        viewModelScope.launch {
            runCatching {
                when (action) {
                    is PayAction.RefundCharge -> walletRepository.refundCharge(action.merchantUid).let { "환불이 완료되었습니다." }
                    is PayAction.CancelCharge -> walletRepository.cancelCharge(action.merchantUid).let { "충전 주문을 취소했습니다." }
                    is PayAction.CancelPayment -> qrPayRepository.cancel(action.qrToken).let { "결제를 취소했습니다. 자녀 지갑으로 환급됐어요." }
                }
            }.onSuccess { msg ->
                _state.update { it.copy(successMessage = msg) }
                load()
            }.onFailure { handlePayError(it) }
        }
    }

    private fun loadChargeHistory() {
        viewModelScope.launch {
            runCatching { walletRepository.getChargeHistory(0) }
                .onSuccess { list -> _state.update { it.copy(chargeHistory = list) } }
        }
    }

    private fun loadChildTransfers(childId: String) {
        viewModelScope.launch {
            runCatching { walletRepository.getChildTransfers(childId, 0) }
                .onSuccess { list -> _state.update { it.copy(childTransfers = list) } }
        }
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            val type = when (_state.value.txFilter) {
                TxFilter.ALL -> null
                TxFilter.OUT -> "TRANSFER_OUT"
                TxFilter.IN -> "TRANSFER_IN"
                TxFilter.PAY -> "PAYMENT"
            }
            runCatching { walletRepository.getTransactions(type, 0) }
                .onSuccess { list -> _state.update { it.copy(transactions = list) } }
        }
    }

    // [VULN-MOBILE-PAY-2] 충전·송금(send)·회수(reclaim)·QR 결제 금액을 클라이언트 입력값 그대로 서버에 전송한다.
    // 서버 금액 미검증(백엔드 VULN-WALLET-1/7)과 결합하면 금액 변조 표면이 된다. 클라이언트측 신뢰 경계 결함.
    // 의도된 취약점(OWASP Mobile M4 Insufficient Input/Output Validation). 보존: 고치지 말 것.
    // 명세: mobile/docs/vuln-spec-mobile.md
    fun charge() {
        val amount = _state.value.amountInput.toIntOrNull() ?: return
        val cardId = _state.value.selectedCardId ?: return
        if (amount <= 0) return
        
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                // 공용 승인(계좌비번+OTP) — 취소하면 중단.
                val auth = txAuth.authorize(amount, null, 0L) ?: return@launch stopLoading()
                val prep = walletRepository.prepareCharge(amount, "CARD", cardId, auth)
                walletRepository.confirmCharge(prep.chargeId, prep.paymentKey ?: "", amount)
            }.onSuccess {
                _state.update { it.copy(successMessage = "충전이 완료되었습니다.", amountInput = "") }
                load()
            }.onFailure { handlePayError(it) }
        }
    }

    fun send() {
        val childId = _state.value.childId ?: return
        val amount = _state.value.amountInput.toIntOrNull() ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val auth = txAuth.authorize(amount, null, childId.toLongOrNull() ?: 0L) ?: return@launch stopLoading()
                walletRepository.transferToChild(childId, amount, _state.value.memoInput.ifBlank { null }, auth)
            }.onSuccess {
                _state.update { it.copy(successMessage = "송금이 완료되었습니다.", amountInput = "", memoInput = "") }
                load()
            }.onFailure { handlePayError(it) }
        }
    }

    fun reclaim() {
        val childId = _state.value.childId ?: return
        val amount = _state.value.amountInput.toIntOrNull() ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val auth = txAuth.authorize(amount, null, childId.toLongOrNull() ?: 0L) ?: return@launch stopLoading()
                walletRepository.reclaimFromChild(childId, amount, auth)
            }.onSuccess {
                _state.update { it.copy(successMessage = "회수가 완료되었습니다.", amountInput = "") }
                load()
            }.onFailure { handlePayError(it) }
        }
    }

    private fun stopLoading() = _state.update { it.copy(loading = false) }

    private fun handlePayError(t: Throwable) {
        val msg = when {
            t.message?.contains("402") == true -> "잔액이 부족합니다."
            t.message?.contains("403") == true -> "요청 권한이 없습니다."
            t.message?.contains("400") == true -> "잘못된 입력입니다."
            else -> t.userMessage("오류가 발생했습니다.")
        }
        _state.update { it.copy(loading = false, error = msg) }
    }
}

@Composable
fun ParentPayScreen(
    onBack: () -> Unit = {},
    onRegisterCard: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    viewModel: PayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    // 단말 테스트·카드 관리 화면에서 돌아오면 잔액·내역을 다시 읽는다.
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshCards()
        onPauseOrDispose { }
    }

    ParentScreenScaffold(
        header = {
            ParentHeaderBar("루키즈 페이", onBack)
            Spacer(Modifier.height(16.dp))
            // 오렌지 헤더 위에 흰 불투명 카드를 얹어 경계를 확실히 드러낸다.
            KidCard {
                Text("내 지갑 잔액", color = colors.muted, fontWeight = FontBold, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    formatWonText(state.wallet?.balance ?: 0),
                    color = colors.brand,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 28.sp,
                )

                if (state.childId != null) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = colors.border)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👶", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "자녀 지갑 잔액",
                            modifier = Modifier.weight(1f),
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 13.sp,
                        )
                        Text(
                            formatWonText(state.childWallet?.balance ?: 0),
                            color = colors.text,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PayTabBtn("보내기", state.tab == PayTab.SEND, Modifier.weight(1f)) { viewModel.onTab(PayTab.SEND) }
                PayTabBtn("회수하기", state.tab == PayTab.RECLAIM, Modifier.weight(1f)) { viewModel.onTab(PayTab.RECLAIM) }
                PayTabBtn("충전", state.tab == PayTab.CHARGE, Modifier.weight(1f)) { viewModel.onTab(PayTab.CHARGE) }
                PayTabBtn("내역", state.tab == PayTab.HISTORY, Modifier.weight(1f)) { viewModel.onTab(PayTab.HISTORY) }
            }
        },
    ) {
        if (state.childId == null && state.tab != PayTab.CHARGE && state.tab != PayTab.HISTORY) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("선택된 자녀가 없습니다.", color = colors.muted)
            }
        } else {
            ParentLoadingOrError(state.loading && state.wallet == null, state.error, viewModel::load)
            
            if (state.successMessage != null) {
                KidCard(containerColor = colors.safe.copy(alpha = 0.1f)) {
                    Text(state.successMessage!!, color = colors.safe, fontWeight = FontBold, fontSize = 14.sp)
                }
            }

            when (state.tab) {
                PayTab.SEND -> {
                    KidCard {
                        KidTextField(state.amountInput, viewModel::onAmount, "보낼 금액", placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(Modifier.height(8.dp))
                        KidTextField(state.memoInput, viewModel::onMemo, "메모 (선택)", placeholder = "맛있는 거 사먹어!")
                        Spacer(Modifier.height(16.dp))
                        KidButton("보내기", viewModel::send, Modifier.fillMaxWidth(), enabled = state.amountInput.isNotEmpty())
                    }
                }
                PayTab.RECLAIM -> {
                    KidCard {
                        val childBal = state.childWallet?.balance ?: 0
                        Text("자녀 잔액 " + formatWonText(childBal), color = colors.muted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        KidTextField(state.amountInput, viewModel::onAmount, "회수할 금액", placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(Modifier.height(16.dp))
                        val amt = state.amountInput.toIntOrNull() ?: 0
                        KidButton("회수하기", viewModel::reclaim, Modifier.fillMaxWidth(), enabled = amt > 0 && amt <= childBal)
                    }
                }
                PayTab.CHARGE -> {
                    if (state.cards.isEmpty()) {
                        KidCard {
                            Text("등록된 카드가 없습니다.", color = colors.muted, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            KidButton("카드 등록하러 가기", onRegisterCard, Modifier.fillMaxWidth())
                        }
                    } else {
                        Text("결제 수단", fontWeight = FontBold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.cards) { card ->
                                val sel = state.selectedCardId == card.cardId
                                Box(
                                    Modifier
                                        .size(width = 140.dp, height = 80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) colors.brand else colors.surface)
                                        .border(2.dp, if (sel) colors.brandDark else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable { viewModel.onSelectCard(card.cardId) }
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(card.ownerName, color = if (sel) Color.White else colors.text, fontSize = 11.sp)
                                        Spacer(Modifier.weight(1f))
                                        Text("**** ${card.last4}", color = if (sel) Color.White else colors.text, fontWeight = FontBold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        KidCard {
                            KidTextField(state.amountInput, viewModel::onAmount, "충전 금액", placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Spacer(Modifier.height(16.dp))
                            KidButton(
                                text = "충전하기",
                                onClick = viewModel::charge,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.amountInput.isNotEmpty() && state.selectedCardId != null
                            )
                        }
                        if (state.chargeHistory.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            KidCard {
                                Text("충전 내역", fontWeight = FontBold, fontSize = 14.sp)
                                state.chargeHistory.forEachIndexed { i, item ->
                                    ChargeHistoryRow(item) { viewModel.request(it) }
                                    if (i < state.chargeHistory.lastIndex) HorizontalDivider(color = colors.border.copy(0.5f))
                                }
                            }
                        }
                    }
                }
                PayTab.HISTORY -> {
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip("전체", state.txFilter == TxFilter.ALL) { viewModel.onFilter(TxFilter.ALL) }
                        FilterChip("보낸내역", state.txFilter == TxFilter.OUT) { viewModel.onFilter(TxFilter.OUT) }
                        FilterChip("받은내역", state.txFilter == TxFilter.IN) { viewModel.onFilter(TxFilter.IN) }
                        FilterChip("사용내역", state.txFilter == TxFilter.PAY) { viewModel.onFilter(TxFilter.PAY) }
                    }
                    KidCard {
                        if (state.transactions.isEmpty()) {
                            Text("거래 내역이 없습니다.", color = colors.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                        }
                        state.transactions.forEachIndexed { i, tx ->
                            TransactionRow(tx)
                            if (i < state.transactions.lastIndex) HorizontalDivider(color = colors.border.copy(0.5f))
                        }
                    }
                    if (state.childId != null && state.childTransfers.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        KidCard {
                            Text("자녀 이체 내역", fontWeight = FontBold, fontSize = 14.sp)
                            state.childTransfers.forEachIndexed { i, tx ->
                                TransactionRow(tx)
                                if (i < state.childTransfers.lastIndex) HorizontalDivider(color = colors.border.copy(0.5f))
                            }
                        }
                    }
                    if (state.childId != null) {
                        Spacer(Modifier.height(12.dp))
                        KidCard {
                            Text("자녀 매장 결제 내역", fontWeight = FontBold, fontSize = 14.sp)
                            Text("결제를 취소하면 자녀 지갑으로 환급돼요", color = colors.muted, fontSize = 11.sp)
                            if (state.childPayments.isEmpty()) {
                                Text("아직 결제 내역이 없어요", color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                            }
                            state.childPayments.forEachIndexed { i, p ->
                                ChildPaymentRow(p) { viewModel.request(it) }
                                if (i < state.childPayments.lastIndex) HorizontalDivider(color = colors.border.copy(0.5f))
                            }
                        }
                    }
                }
            }
        }
        state.pendingAction?.let { action ->
            AlertDialog(
                onDismissRequest = viewModel::dismissAction,
                title = { Text("확인", fontWeight = FontBold) },
                text = { Text(action.label) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmAction) {
                        Text("확인", color = colors.danger, fontWeight = FontBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissAction) { Text("취소", color = colors.muted) }
                },
            )
        }
        // 매장 단말 테스트 진입점 — 디버그/api 빌드에서만 보이는 숨은 링크(회원 앱 정상 기능 아님).
        if (com.hack.kidhack.BuildConfig.DEBUG) {
            Spacer(Modifier.height(16.dp))
            KidButton(
                text = "🧪 매장 단말 (테스트)",
                onClick = onOpenTerminal,
                modifier = Modifier.fillMaxWidth(),
                variant = KidButtonVariant.Ghost,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 충전 1건. PAID 는 「환불」(태그 11 refund), READY 는 「취소」(cancel) 버튼이 붙는다. */
@Composable
private fun ChargeHistoryRow(
    item: com.hack.kidhack.domain.model.ChargeHistoryItem,
    onAction: (PayAction) -> Unit = {},
) {
    val colors = LocalKidhackColors.current
    val statusLabel = when (item.status) {
        "PAID" -> "충전완료"
        "REFUNDED" -> "환불됨"
        "READY" -> "대기중"
        "CANCELLED" -> "취소됨"
        "FAILED" -> "실패"
        "EXPIRED" -> "만료됨"
        else -> item.status
    }
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(statusLabel, fontWeight = FontBold, fontSize = 14.sp)
            Text(
                item.refundedAtLabel?.let { "환불 $it" } ?: item.createdAtLabel,
                color = colors.muted,
                fontSize = 11.sp,
            )
        }
        Text(formatWonText(item.amount), fontWeight = FontBlack, fontSize = 14.sp, color = colors.text)
        val action = when (item.status) {
            "PAID" -> "환불" to PayAction.RefundCharge(item.merchantUid, item.amount)
            "READY" -> "취소" to PayAction.CancelCharge(item.merchantUid, item.amount)
            else -> null
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                action.first,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.danger.copy(alpha = 0.1f))
                    .clickable { onAction(action.second) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = colors.danger,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
        }
    }
}

/** 자녀 매장 결제 1건(태그 19). PAID 면 부모가 「취소」(태그 18)할 수 있다. */
@Composable
private fun ChildPaymentRow(item: QrPaymentHistoryItem, onAction: (PayAction) -> Unit) {
    val colors = LocalKidhackColors.current
    val cancelled = item.status == "CANCELLED"
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (cancelled) "↩️" else "🧾", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.storeName, fontWeight = FontBold, fontSize = 14.sp)
            Text(item.paidAtLabel, color = colors.muted, fontSize = 11.sp)
        }
        Text(
            if (cancelled) "취소됨" else "-" + formatWonText(item.amount),
            fontWeight = FontBlack,
            fontSize = 14.sp,
            color = if (cancelled) colors.muted else colors.text,
        )
        if (!cancelled) {
            Spacer(Modifier.width(8.dp))
            Text(
                "취소",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.danger.copy(alpha = 0.1f))
                    .clickable { onAction(PayAction.CancelPayment(item.qrToken, item.storeName, item.amount)) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = colors.danger,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TransactionRow(tx: WalletTransaction) {
    val colors = LocalKidhackColors.current
    val isOut = tx.type.contains("OUT") || tx.type == "PAYMENT"
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (isOut) colors.brandLight else Color(0xFFDCFCE7)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isOut) "↑" else "↓", color = if (isOut) colors.brand else Color(0xFF166534), fontWeight = FontBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val label = when(tx.type) {
                "CHARGE" -> "충전"
                "TRANSFER_OUT" -> "보내기"
                "TRANSFER_IN" -> "받기"
                "PAYMENT" -> "결제"
                "RECLAIM" -> "회수"
                else -> tx.type
            }
            Text(label, fontWeight = FontBold, fontSize = 14.sp)
            Text(tx.createdAt, color = colors.muted, fontSize = 11.sp)
        }
        Text(
            (if (isOut) "-" else "+") + formatWonText(Math.abs(tx.amount)),
            fontWeight = FontBlack,
            fontSize = 14.sp,
            color = if (isOut) colors.text else colors.safe
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) colors.brand else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) Color.White else colors.text,
        fontWeight = FontBold,
        fontSize = 11.sp
    )
}

@Composable
private fun PayTabBtn(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        color = if (selected) colors.brand else Color.White,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 11.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
