package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.ChargeHistoryItem
import com.hack.kidhack.domain.model.QrPaymentHistoryItem
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import com.hack.kidhack.domain.repository.CardRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.QrPayRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.WalletRepository
import com.hack.kidhack.feature.efin.TxAuthCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 지갑 화면의 두 갈래. 내 돈을 보는 곳과 자녀 돈을 보는 곳은 할 일이 다르다. */
enum class WalletTab { MINE, CHILD }

/** 내 지갑 원장 거르기. 서버 `type` 파라미터로 그대로 넘어간다. */
enum class WalletTxFilter(val serverType: String?) {
    ALL(null),
    OUT("TRANSFER_OUT"),
    IN("TRANSFER_IN"),
    CHARGE("CHARGE"),
}

/** 열려 있는 미니 시트. 화면을 옮기지 않고 그 자리에서 끝내는 동작들이다. */
sealed interface WalletSheet {
    data object Charge : WalletSheet
    data object Send : WalletSheet
    data object Recover : WalletSheet
}

/**
 * 되돌리기 어려워 한 번 더 묻는 동작.
 *
 * <p>환불·취소는 돈이 움직이고 서버에 멱등성이 없다([VULN-WALLET-3/10]). 눌린 김에 두 번
 * 나가면 두 배가 되므로 확인을 거친다.
 */
sealed interface WalletConfirm {
    data class RefundCharge(val merchantUid: String, val amount: Int) : WalletConfirm
    data class CancelCharge(val merchantUid: String, val amount: Int) : WalletConfirm
    data class CancelPayment(val qrToken: String, val storeName: String, val amount: Int) : WalletConfirm
}

/**
 * 자녀 한 명의 지갑 요약.
 *
 * <p>이름은 자녀 목록에, 잔액은 지갑 조회에 따로 있다. 카드 한 장에 둘 다 그려야 하므로
 * 화면이 두 곳을 맞춰 보지 않도록 여기서 합쳐 둔다.
 */
data class ChildWalletSummary(val id: String, val name: String, val balance: Int)


data class WalletUiState(
    val wallet: Wallet? = null,
    val cards: List<Card> = emptyList(),
    val children: List<ChildWalletSummary> = emptyList(),
    val selectedChildId: String? = null,
    val tab: WalletTab = WalletTab.MINE,
    val txFilter: WalletTxFilter = WalletTxFilter.ALL,
    val transactions: List<WalletTransaction> = emptyList(),
    val chargeHistory: List<ChargeHistoryItem> = emptyList(),
    /** 고른 자녀의 전체 원장(이체·결제 모두). */
    val childTransactions: List<WalletTransaction> = emptyList(),
    /** 고른 자녀와 주고받은 이체·회수만. */
    val childTransfers: List<WalletTransaction> = emptyList(),
    val childPayments: List<QrPaymentHistoryItem> = emptyList(),
    val sheet: WalletSheet? = null,
    /** 시트에서 고른 대상 자녀. 자녀 지갑 탭에서 열면 그 자녀로 미리 채운다. */
    val sheetChildId: String? = null,
    val amountInput: String = "",
    val selectedCardId: String? = null,
    val confirm: WalletConfirm? = null,
    /** 첫 조회 중. */
    val loading: Boolean = true,
    /** 돈이 움직이는 요청을 보내는 중 — 버튼을 잠가 두 번 나가지 않게 한다. */
    val working: Boolean = false,
    val error: String? = null,
    /** 방금 끝낸 일을 알리는 문구(문자열 리소스). 화면이 문구를 고르지 않게 id 만 들고 있는다. */
    val messageRes: Int? = null,
) {
    val selectedChild: ChildWalletSummary?
        get() = children.firstOrNull { it.id == selectedChildId }

    val sheetChild: ChildWalletSummary?
        get() = children.firstOrNull { it.id == sheetChildId }

    val amount: Int get() = amountInput.toIntOrNull() ?: 0
}

/**
 * 부모 지갑.
 *
 * <p>마이페이지의 잔액 칸과 지갑 화면이 같은 뷰모델을 쓴다. 둘이 보여 주는 것이 다르므로
 * 조회를 [load](잔액·카드·자녀 잔액)와 [loadDetail](원장·충전내역·자녀 상세)로 나눴다 —
 * 마이페이지가 쓰지도 않을 원장을 매번 끌어오지 않게 하려는 것이다.
 */
class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val cardRepository: CardRepository,
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val qrPayRepository: QrPayRepository,
    private val txAuth: TxAuthCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    init { load() }

    /**
     * 잔액 · 카드 · 자녀별 잔액.
     *
     * <p>자녀 잔액은 한 번에 주는 API 가 없어 자녀마다 따로 부른다. 한 명이 실패해도 나머지는
     * 보여 줘야 하므로 각각 감싸고, 못 받은 자녀는 0 으로 둔다.
     */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val wallet = walletRepository.getMyWallet()
                val cards = runCatching { cardRepository.list() }.getOrDefault(emptyList())
                val profiles = runCatching { childRepository.children() }.getOrDefault(emptyList())
                val selected = selectedChildRepository.resolveId(profiles)
                val children = profiles.map { p ->
                    val balance = runCatching { walletRepository.getChildWallet(p.id).balance }.getOrDefault(0)
                    ChildWalletSummary(id = p.id, name = p.name, balance = balance)
                }
                Loaded(wallet, cards, children, selected)
            }.onSuccess { loaded ->
                _state.update {
                    it.copy(
                        loading = false,
                        wallet = loaded.wallet,
                        cards = loaded.cards,
                        children = loaded.children,
                        selectedChildId = loaded.selectedChildId,
                        // 고른 카드가 사라졌으면(삭제) 첫 카드로 되돌린다.
                        selectedCardId = it.selectedCardId?.takeIf { id -> loaded.cards.any { c -> c.cardId == id } }
                            ?: loaded.cards.firstOrNull()?.cardId,
                    )
                }
                loaded.selectedChildId?.let { loadChildDetail(it) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.userMessage("지갑을 불러오지 못했어요")) }
            }
        }
    }

    private data class Loaded(
        val wallet: Wallet,
        val cards: List<Card>,
        val children: List<ChildWalletSummary>,
        val selectedChildId: String?,
    )

    /** 지갑 화면에서만 쓰는 목록들. 실패해도 잔액 화면은 살아 있어야 하므로 각각 감싼다. */
    fun loadDetail() {
        loadTransactions()
        viewModelScope.launch {
            runCatching { walletRepository.getChargeHistory(0) }
                .onSuccess { list -> _state.update { it.copy(chargeHistory = list) } }
        }
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            val type = _state.value.txFilter.serverType
            runCatching { walletRepository.getTransactions(type, 0) }
                .onSuccess { list -> _state.update { it.copy(transactions = list) } }
        }
    }

    private fun loadChildDetail(childId: String) {
        viewModelScope.launch {
            runCatching { walletRepository.getChildTransactions(childId) }
                .onSuccess { list -> _state.update { it.copy(childTransactions = list) } }
        }
        viewModelScope.launch {
            runCatching { walletRepository.getChildTransfers(childId, 0) }
                .onSuccess { list -> _state.update { it.copy(childTransfers = list) } }
        }
        viewModelScope.launch {
            runCatching { qrPayRepository.paymentHistory(childId, 0) }
                .onSuccess { list -> _state.update { it.copy(childPayments = list) } }
        }
    }

    fun selectTab(tab: WalletTab) = _state.update { it.copy(tab = tab, messageRes = null, error = null) }

    fun selectFilter(filter: WalletTxFilter) {
        _state.update { it.copy(txFilter = filter) }
        loadTransactions()
    }

    /**
     * 자녀를 고른다.
     *
     * <p>고른 자녀는 앱 전체의 "지금 보고 있는 자녀"라 저장소에도 남긴다. 지갑에서 민준을
     * 보다가 리포트로 넘어갔는데 서연이 나오면 누구 것인지 헷갈린다.
     */
    fun selectChild(childId: String) {
        if (_state.value.selectedChildId == childId) return
        _state.update {
            it.copy(
                selectedChildId = childId,
                childTransactions = emptyList(),
                childTransfers = emptyList(),
                childPayments = emptyList(),
            )
        }
        viewModelScope.launch { selectedChildRepository.select(childId) }
        loadChildDetail(childId)
    }

    /** 카드 관리에서 돌아왔을 때 목록만 다시 읽는다. */
    fun refreshCards() {
        viewModelScope.launch {
            runCatching { cardRepository.list() }.onSuccess { list ->
                _state.update { s ->
                    s.copy(
                        cards = list,
                        selectedCardId = s.selectedCardId?.takeIf { id -> list.any { it.cardId == id } }
                            ?: list.firstOrNull()?.cardId,
                    )
                }
            }
        }
    }

    // --- 미니 시트 ---

    /** @param childId 자녀 지갑 탭에서 열면 그 자녀로 미리 채운다. 마이페이지에서는 null. */
    fun openSheet(sheet: WalletSheet, childId: String? = null) = _state.update {
        it.copy(
            sheet = sheet,
            sheetChildId = childId ?: it.selectedChildId ?: it.children.firstOrNull()?.id,
            amountInput = "",
            messageRes = null,
            error = null,
        )
    }

    fun closeSheet() = _state.update { it.copy(sheet = null, amountInput = "", error = null) }

    /** 금액은 숫자만, 9자리까지. 자릿수를 막지 않으면 천 단위 표기가 Long 을 넘긴다. */
    fun onAmount(raw: String) = _state.update {
        it.copy(amountInput = raw.filter { c -> c.isDigit() }.take(MAX_AMOUNT_DIGITS).trimStart('0'))
    }

    fun addAmount(delta: Int) = _state.update {
        val next = (it.amountInput.toLongOrNull() ?: 0L) + delta
        it.copy(amountInput = next.coerceAtMost(MAX_AMOUNT).toString())
    }

    fun selectCard(cardId: String) = _state.update { it.copy(selectedCardId = cardId) }
    fun selectSheetChild(childId: String) = _state.update { it.copy(sheetChildId = childId) }

    /**
     * 열린 시트에 맞는 동작을 보낸다.
     *
     * 돈이 움직이는 세 동작(송금·회수·충전)은 모두 공용 승인 플로우([TxAuthCoordinator])를 거친다 —
     * 계좌비밀번호 + OTP 를 받아 서명·nonce 를 붙인 뒤 실제 거래를 실행한다. 승인 다이얼로그는 앱 루트에
     * 하나만 떠 있어(TxAuthHost), 화면마다 붙일 필요가 없다.
     */
    fun submitSheet() {
        val s = _state.value
        val amount = s.amount
        if (amount <= 0 || s.working) return
        val sheet = s.sheet ?: return
        val childId = s.sheetChildId
        val childName = s.children.firstOrNull { it.id == childId }?.name

        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null, sheet = null) }
            runCatching {
                when (sheet) {
                    WalletSheet.Send -> {
                        val cid = requireNotNull(childId)
                        // 공용 승인 — 계좌비번+OTP. 취소하면 null → 거래 중단.
                        val auth = txAuth.authorize(amount, childName, cid.toLongOrNull() ?: 0L)
                            ?: return@launch clearWorking()
                        // [VULN-MOBILE-PAY-2] 금액은 클라이언트 입력값 그대로 전송(의도된 취약점, 보존).
                        walletRepository.transferToChild(cid, amount, null, auth)
                        R.string.parent_wallet_done_send
                    }
                    WalletSheet.Recover -> {
                        val cid = requireNotNull(childId)
                        val auth = txAuth.authorize(amount, childName, cid.toLongOrNull() ?: 0L)
                            ?: return@launch clearWorking()
                        walletRepository.reclaimFromChild(cid, amount, auth)
                        R.string.parent_wallet_done_recover
                    }
                    WalletSheet.Charge -> {
                        // 충전도 거래 관문(계좌비번+OTP)을 거친다. 대상 없음 → refId=0.
                        val auth = txAuth.authorize(amount, null, 0L) ?: return@launch clearWorking()
                        val prepared = walletRepository.prepareCharge(amount, "CARD", s.selectedCardId.orEmpty(), auth)
                        walletRepository.confirmCharge(prepared.chargeId, prepared.paymentKey.orEmpty(), amount)
                        R.string.parent_wallet_done_charge
                    }
                }
            }.onSuccess { done ->
                _state.update { it.copy(working = false, amountInput = "", messageRes = done) }
                reloadAll()
            }.onFailure { t -> fail(t) }
        }
    }

    private fun clearWorking() = _state.update { it.copy(working = false) }

    // --- 확인이 필요한 동작 ---

    fun requestConfirm(confirm: WalletConfirm) = _state.update {
        it.copy(confirm = confirm, messageRes = null, error = null)
    }

    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    fun runConfirm() {
        val confirm = _state.value.confirm ?: return
        viewModelScope.launch {
            _state.update { it.copy(confirm = null, working = true, error = null) }
            runCatching {
                when (confirm) {
                    is WalletConfirm.RefundCharge -> {
                        walletRepository.refundCharge(confirm.merchantUid)
                        R.string.parent_wallet_done_refund
                    }
                    is WalletConfirm.CancelCharge -> {
                        walletRepository.cancelCharge(confirm.merchantUid)
                        R.string.parent_wallet_done_cancel_charge
                    }
                    is WalletConfirm.CancelPayment -> {
                        qrPayRepository.cancel(confirm.qrToken)
                        R.string.parent_wallet_done_cancel_payment
                    }
                }
            }.onSuccess { done ->
                _state.update { it.copy(working = false, messageRes = done) }
                reloadAll()
            }.onFailure { t -> fail(t) }
        }
    }

    private fun reloadAll() {
        load()
        loadDetail()
    }

    /**
     * 실패 문구.
     *
     * <p>지갑에서 나는 오류는 잔액 부족·권한처럼 부모가 스스로 고칠 수 있는 것이라 상태
     * 코드별로 다르게 적는다. 서버 원문을 그대로 띄우면 무엇을 해야 할지 알 수 없다.
     */
    private fun fail(t: Throwable) {
        val message = when {
            t.message?.contains("402") == true -> "잔액이 부족해요."
            t.message?.contains("403") == true -> "권한이 없어요."
            t.message?.contains("400") == true -> "입력한 값을 다시 확인해 주세요."
            else -> t.userMessage("처리하지 못했어요.")
        }
        _state.update { it.copy(working = false, error = message) }
    }
}

/** 금액 입력 상한 — 천 단위 표기가 Long 을 넘지 않도록. */
private const val MAX_AMOUNT_DIGITS = 9
private const val MAX_AMOUNT = 999_999_999L

/* ------------------------------------------------------------------ */
/* 화면                                                                */
/* ------------------------------------------------------------------ */

/**
 * 부모 지갑 화면.
 *
 * <p>마이페이지 잔액 칸에서만 들어온다. 「내 지갑」은 내 돈이 들어오고 나간 기록,
 * 「자녀 지갑」은 아이별 잔액과 그 아이가 쓴 내역이다. 물음이 다르니 탭으로 나눴다.
 */
@Composable
fun ParentWalletScreen(
    onBack: () -> Unit = {},
    onRegisterCard: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    viewModel: WalletViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    // 카드 관리·매장 단말에서 돌아오면 잔액도 카드도 달라져 있다. 뷰모델이 살아 있으면 init 이
    // 다시 돌지 않으므로 화면이 앞으로 나올 때마다 읽는다.
    LifecycleResumeRefresh {
        viewModel.load()
        viewModel.loadDetail()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxWidth().background(colors.card)) {
            ReportTitleBar(title = stringResource(R.string.parent_wallet_title), onBack = onBack)

            WalletTabs(
                titles = listOf(
                    stringResource(R.string.parent_wallet_tab_mine),
                    stringResource(R.string.parent_wallet_tab_child),
                ),
                selected = state.tab.ordinal,
                onSelect = { viewModel.selectTab(WalletTab.entries[it]) },
            )
            KidHeaderDivider()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            WalletBanner(error = state.error, messageRes = state.messageRes)

            // 첫 조회 전에는 목록을 그리지 않는다. 빈 목록을 먼저 보여 주면 "거래가 없다"로
            // 읽혔다가 값이 나타나 화면이 두 번 바뀐다.
            if (state.loading && state.wallet == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
                }
            } else {
                when (state.tab) {
                    WalletTab.MINE -> MineWalletTab(state, viewModel)
                    WalletTab.CHILD -> ChildWalletTab(state, viewModel)
                }
            }

            // 매장 단말 테스트 진입점 — 디버그/api 빌드에서만 보이는 숨은 링크(회원 앱 기능 아님).
            if (com.hack.kidhack.BuildConfig.DEBUG) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "🧪 매장 단말 (테스트)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .clickable(onClick = onOpenTerminal)
                        .padding(vertical = 12.dp),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    WalletSheetHost(state = state, viewModel = viewModel, onRegisterCard = onRegisterCard)
    WalletConfirmDialog(state = state, viewModel = viewModel)
    // 거래 승인(계좌비번+OTP) 다이얼로그는 앱 루트(TxAuthHost)에 하나만 있다 — 여기서 띄우지 않는다.
}

/** 화면이 다시 앞으로 나올 때 한 번 부른다. 첫 진입은 뷰모델 init 이 이미 읽었으므로 건너뛴다. */
@Composable
internal fun LifecycleResumeRefresh(onResume: () -> Unit) {
    var firstResume by rememberSaveable { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) firstResume = false else onResume()
    }
}

/* ---- 내 지갑 탭 ---- */

@Composable
private fun MineWalletTab(state: WalletUiState, viewModel: WalletViewModel) {
    Spacer(Modifier.height(14.dp))
    WalletBalanceCard(
        wallet = state.wallet,
        showMonthly = true,
        onCharge = { viewModel.openSheet(WalletSheet.Charge) },
        onSend = { viewModel.openSheet(WalletSheet.Send) },
        sendEnabled = state.children.isNotEmpty(),
    )

    GroupLabel(stringResource(R.string.parent_wallet_group_transactions))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 10.dp)) {
        WalletChip(stringResource(R.string.parent_wallet_filter_all), state.txFilter == WalletTxFilter.ALL) {
            viewModel.selectFilter(WalletTxFilter.ALL)
        }
        WalletChip(stringResource(R.string.parent_wallet_filter_out), state.txFilter == WalletTxFilter.OUT) {
            viewModel.selectFilter(WalletTxFilter.OUT)
        }
        WalletChip(stringResource(R.string.parent_wallet_filter_in), state.txFilter == WalletTxFilter.IN) {
            viewModel.selectFilter(WalletTxFilter.IN)
        }
        WalletChip(stringResource(R.string.parent_wallet_filter_charge), state.txFilter == WalletTxFilter.CHARGE) {
            viewModel.selectFilter(WalletTxFilter.CHARGE)
        }
    }
    WalletCard(padded = false) {
        if (state.transactions.isEmpty()) {
            EmptyLine(stringResource(R.string.parent_wallet_no_transactions))
            return@WalletCard
        }
        state.transactions.forEachIndexed { index, tx ->
            if (index > 0) RowDivider()
            TransactionLine(tx)
        }
    }

    GroupLabel(stringResource(R.string.parent_wallet_group_charges))
    WalletCard(padded = false) {
        if (state.chargeHistory.isEmpty()) {
            EmptyLine(stringResource(R.string.parent_wallet_no_charges))
            return@WalletCard
        }
        state.chargeHistory.forEachIndexed { index, item ->
            if (index > 0) RowDivider()
            ChargeLine(item, enabled = !state.working, onAction = viewModel::requestConfirm)
        }
    }
}

/* ---- 자녀 지갑 탭 ---- */

@Composable
private fun ChildWalletTab(state: WalletUiState, viewModel: WalletViewModel) {
    if (state.children.isEmpty()) {
        Spacer(Modifier.height(24.dp))
        WalletCard { EmptyLine(stringResource(R.string.parent_wallet_no_children)) }
        return
    }

    Spacer(Modifier.height(16.dp))
    // 카드 앱처럼 큰 칸으로 고른다. 작은 칩으로 두면 누구 지갑을 보고 있는지 놓치기 쉽다.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        state.children.forEach { child ->
            ChildWalletCard(
                child = child,
                selected = child.id == state.selectedChildId,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.selectChild(child.id) },
            )
        }
    }

    val selected = state.selectedChild ?: return
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 카드 밖에 홀로 서는 버튼이라 앱 공용 버튼을 그대로 쓴다.
        KidButton(
            text = stringResource(R.string.parent_wallet_action_send),
            onClick = { viewModel.openSheet(WalletSheet.Send, selected.id) },
            modifier = Modifier.weight(1f),
            enabled = !state.working,
        )
        KidButton(
            text = stringResource(R.string.parent_wallet_action_recover),
            onClick = { viewModel.openSheet(WalletSheet.Recover, selected.id) },
            modifier = Modifier.weight(1f),
            variant = KidButtonVariant.Secondary,
            enabled = !state.working && selected.balance > 0,
        )
    }

    GroupLabel(stringResource(R.string.parent_wallet_child_recent, selected.name))
    WalletCard(padded = false) {
        if (state.childTransactions.isEmpty()) {
            EmptyLine(stringResource(R.string.parent_wallet_no_transactions))
            return@WalletCard
        }
        state.childTransactions.forEachIndexed { index, tx ->
            if (index > 0) RowDivider()
            TransactionLine(tx)
        }
    }

    GroupLabel(stringResource(R.string.parent_wallet_child_transfers))
    WalletCard(padded = false) {
        if (state.childTransfers.isEmpty()) {
            EmptyLine(stringResource(R.string.parent_wallet_no_transactions))
            return@WalletCard
        }
        state.childTransfers.forEachIndexed { index, tx ->
            if (index > 0) RowDivider()
            TransactionLine(tx)
        }
    }

    GroupLabel(stringResource(R.string.parent_wallet_child_payments))
    WalletCard(padded = false) {
        if (state.childPayments.isEmpty()) {
            EmptyLine(stringResource(R.string.parent_wallet_no_payments))
            return@WalletCard
        }
        state.childPayments.forEachIndexed { index, payment ->
            if (index > 0) RowDivider()
            PaymentLine(payment, enabled = !state.working, onAction = viewModel::requestConfirm)
        }
    }
}

/* ------------------------------------------------------------------ */
/* 공용 조각 — 마이페이지도 쓴다                                        */
/* ------------------------------------------------------------------ */

/**
 * 잔액 칸.
 *
 * <p>마이페이지와 지갑 화면이 같은 칸을 쓰되 붙는 것이 다르다 — 마이페이지는 아래에 상세로
 * 들어가는 줄이, 지갑 화면은 이번 달 합계 두 칸이 붙는다.
 */
@Composable
internal fun WalletBalanceCard(
    wallet: Wallet?,
    onCharge: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    showMonthly: Boolean = false,
    sendEnabled: Boolean = true,
    onDetail: (() -> Unit)? = null,
) {
    val colors = LocalKidhackColors.current
    WalletCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.parent_wallet_my_balance),
                    modifier = Modifier.weight(1f),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
                // 「자세히」를 위로 올려 카드 아래쪽을 액션에 온전히 내준다. 아래에 두면
                // 구분선 + 링크가 한 층 더 쌓여 카드가 다섯 층이 된다.
                if (onDetail != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable(onClick = onDetail)
                            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.parent_wallet_detail_link),
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                        )
                        Text(" ›", color = colors.muted, fontSize = 15.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // 금액은 잉크색으로 적는다. 주황은 「누를 수 있는 것」에만 남긴다 — 잔액까지
            // 브랜드색이면 화면에서 가장 큰 글씨가 버튼인 척한다.
            AmountText(amount = wallet?.balance ?: 0, size = 28.sp)

            if (showMonthly) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonthlyStat(
                        label = stringResource(R.string.parent_wallet_monthly_charge),
                        amount = wallet?.monthlyChargeTotal ?: 0,
                        modifier = Modifier.weight(1f).padding(end = 14.dp),
                    )
                    Box(
                        Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(colors.border.copy(alpha = 0.7f)),
                    )
                    MonthlyStat(
                        label = stringResource(R.string.parent_wallet_monthly_spend),
                        amount = wallet?.monthlySpendTotal ?: 0,
                        modifier = Modifier.weight(1f).padding(start = 14.dp),
                    )
                }
            }
        }

        // 채운 알약 두 개를 나란히 두는 대신, 카드 밑단을 반으로 갈라 누를 자리로 쓴다.
        // 카드 폭을 다 쓰므로 과녁이 커지고, 주황 덩어리가 사라져 화면이 조용해진다.
        HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            WalletAction(
                label = stringResource(R.string.parent_wallet_action_charge),
                accent = true,
                modifier = Modifier.weight(1f),
                onClick = onCharge,
            )
            Box(Modifier.fillMaxHeight().width(1.dp).background(colors.border.copy(alpha = 0.6f)))
            WalletAction(
                label = stringResource(R.string.parent_wallet_action_send),
                accent = false,
                modifier = Modifier.weight(1f),
                enabled = sendEnabled,
                onClick = onSend,
            )
        }
    }
}

/**
 * 금액 한 줄.
 *
 * <p>「12,000원」으로 적는다. `₩ 12,000` 은 숫자 앞에 기호가 서서 눈이 금액에 닿기까지
 * 한 글자를 더 지나야 하고, 한국 앱에서 읽는 순서와도 다르다.
 */
@Composable
private fun AmountText(
    amount: Int,
    size: androidx.compose.ui.unit.TextUnit,
    color: Color? = null,
) {
    val colors = LocalKidhackColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = formatWon(amount),
            color = color ?: colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = size,
        )
        Text(
            text = stringResource(R.string.parent_wallet_won_suffix),
            modifier = Modifier.padding(start = 2.dp, bottom = size.value.times(0.1f).dp),
            color = color ?: colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = size.times(0.55f),
        )
    }
}

/** 이번 달 충전·사용. 파스텔 칸을 씌우지 않는다 — 색칸 두 개가 잔액보다 먼저 눈에 든다. */
@Composable
private fun MonthlyStat(label: String, amount: Int, modifier: Modifier = Modifier) {
    val colors = LocalKidhackColors.current
    Column(modifier = modifier) {
        Text(label, color = colors.muted, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 11.5.sp)
        Spacer(Modifier.height(3.dp))
        AmountText(amount = amount, size = 15.sp)
    }
}

/**
 * 미니 시트 묶음.
 *
 * <p>마이페이지와 지갑 화면이 같은 시트를 쓰므로 한 줄로 끼워 넣을 수 있게 묶었다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletSheetHost(
    state: WalletUiState,
    viewModel: WalletViewModel,
    onRegisterCard: () -> Unit,
) {
    val sheet = state.sheet ?: return
    val colors = LocalKidhackColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = viewModel::closeSheet,
        sheetState = sheetState,
        containerColor = colors.card,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier
                    .padding(bottom = 14.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.border),
            )
            when (sheet) {
                WalletSheet.Charge -> ChargeSheetBody(state, viewModel, onRegisterCard)
                WalletSheet.Send -> TransferSheetBody(state, viewModel, recover = false)
                WalletSheet.Recover -> TransferSheetBody(state, viewModel, recover = true)
            }
        }
    }
}

@Composable
private fun ChargeSheetBody(state: WalletUiState, viewModel: WalletViewModel, onRegisterCard: () -> Unit) {
    val colors = LocalKidhackColors.current
    SheetHeading(
        title = stringResource(R.string.parent_wallet_sheet_charge_title),
        hint = stringResource(R.string.parent_wallet_sheet_charge_hint),
    )

    if (state.cards.isEmpty()) {
        // 카드가 없으면 금액을 받아 봐야 보낼 곳이 없다. 등록으로만 안내한다.
        Text(
            text = stringResource(R.string.parent_wallet_sheet_charge_no_card),
            color = colors.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        SheetSubmit(stringResource(R.string.parent_wallet_sheet_charge_add_card), enabled = true) {
            viewModel.closeSheet()
            onRegisterCard()
        }
        return
    }

    SheetLabel(stringResource(R.string.parent_wallet_sheet_charge_amount))
    AmountField(state.amountInput, viewModel::onAmount)
    Spacer(Modifier.height(8.dp))
    QuickAmounts(CHARGE_QUICK_AMOUNTS, viewModel::addAmount)

    Spacer(Modifier.height(16.dp))
    SheetLabel(stringResource(R.string.parent_wallet_sheet_charge_method))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.cards.forEach { card ->
            MiniCard(card = card, selected = card.cardId == state.selectedCardId) {
                viewModel.selectCard(card.cardId)
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    SheetSubmit(
        label = stringResource(R.string.parent_wallet_sheet_charge_submit, formatWon(state.amount)),
        enabled = state.amount > 0 && state.selectedCardId != null && !state.working,
        onClick = viewModel::submitSheet,
    )
}

/**
 * 송금·회수 시트.
 *
 * <p>둘은 방향만 반대라 한 몸으로 둔다. 서버 이체 본문에 메모 칸이 없어 입력도 받지 않는다 —
 * 받아 두면 보낸 사람은 적었다고 여기는데 아이에게는 아무것도 가지 않는다.
 */
@Composable
private fun TransferSheetBody(state: WalletUiState, viewModel: WalletViewModel, recover: Boolean) {
    SheetHeading(
        title = stringResource(
            if (recover) R.string.parent_wallet_sheet_recover_title else R.string.parent_wallet_sheet_send_title
        ),
        hint = stringResource(
            if (recover) R.string.parent_wallet_sheet_recover_hint else R.string.parent_wallet_sheet_send_hint
        ),
    )

    // 자녀가 한 명이면 고를 것이 없다. 칩 한 개짜리 줄은 자리만 차지한다.
    if (state.children.size > 1) {
        SheetLabel(
            stringResource(
                if (recover) R.string.parent_wallet_sheet_recover_target else R.string.parent_wallet_sheet_send_target
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            state.children.forEach { child ->
                WalletChip(
                    label = child.name + " · " + formatWonText(child.balance),
                    selected = child.id == state.sheetChildId,
                ) { viewModel.selectSheetChild(child.id) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    SheetLabel(
        stringResource(
            if (recover) R.string.parent_wallet_sheet_recover_amount else R.string.parent_wallet_sheet_send_amount
        )
    )
    AmountField(state.amountInput, viewModel::onAmount)
    Spacer(Modifier.height(8.dp))
    QuickAmounts(TRANSFER_QUICK_AMOUNTS, viewModel::addAmount)

    // 회수는 자녀 잔액을 넘을 수 없다. 서버도 막지만 먼저 알려 주는 편이 낫다.
    val childBalance = state.sheetChild?.balance ?: 0
    val overBalance = recover && state.amount > childBalance

    Spacer(Modifier.height(18.dp))
    SheetSubmit(
        label = stringResource(
            if (recover) R.string.parent_wallet_sheet_recover_submit else R.string.parent_wallet_sheet_send_submit,
            formatWon(state.amount),
        ),
        enabled = state.amount > 0 && state.sheetChildId != null && !overBalance && !state.working,
        onClick = viewModel::submitSheet,
    )
}

/** 충전 시트의 빠른 금액. 지갑에 넣는 단위라 이체보다 크다. */
private val CHARGE_QUICK_AMOUNTS = listOf(10_000, 30_000, 50_000, 100_000)

/** 송금·회수 시트의 빠른 금액. 용돈 단위. */
private val TRANSFER_QUICK_AMOUNTS = listOf(5_000, 10_000, 30_000, 50_000)

@Composable
internal fun WalletConfirmDialog(state: WalletUiState, viewModel: WalletViewModel) {
    val confirm = state.confirm ?: return
    val colors = LocalKidhackColors.current
    val message = when (confirm) {
        is WalletConfirm.RefundCharge ->
            stringResource(R.string.parent_wallet_confirm_refund, formatWon(confirm.amount))
        is WalletConfirm.CancelCharge ->
            stringResource(R.string.parent_wallet_confirm_cancel_charge, formatWon(confirm.amount))
        is WalletConfirm.CancelPayment ->
            stringResource(R.string.parent_wallet_confirm_cancel_payment, confirm.storeName, formatWon(confirm.amount))
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissConfirm,
        title = { Text(stringResource(R.string.parent_wallet_confirm_title), fontWeight = FontBold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = viewModel::runConfirm) {
                Text(stringResource(R.string.parent_wallet_confirm_ok), color = colors.danger, fontWeight = FontBold)
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissConfirm) {
                Text(stringResource(R.string.parent_wallet_confirm_cancel), color = colors.muted)
            }
        },
    )
}

/** 성공·실패 한 줄. 둘 다 없으면 자리를 차지하지 않는다. */
@Composable
internal fun WalletBanner(error: String?, messageRes: Int?) {
    val colors = LocalKidhackColors.current
    when {
        error != null -> Text(
            text = error,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            color = colors.danger,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
        messageRes != null -> Text(
            text = stringResource(messageRes),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            color = colors.safe,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
    }
}

/* ------------------------------------------------------------------ */
/* 작은 조각                                                            */
/* ------------------------------------------------------------------ */

/** 지갑의 카드 한 장. #43 규칙 — 16dp 둥근 흰 칸에 옅은 테두리. */
@Composable
private fun WalletCard(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    // 카드 폭을 끝까지 쓰는 구분선이 필요하면 여백을 0 으로 넘기고 안에서 직접 준다.
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    // 테두리 대신 은은한 그림자로 띄워, 마이페이지의 KidSectionCard 와 같은 결을 낸다.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        shadowElevation = 1.5.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                // 목록 카드는 줄마다 위아래 여백을 스스로 갖고, 구분선은 카드 폭을 다 써야 한다.
                .padding(
                    contentPadding ?: PaddingValues(
                        horizontal = if (padded) 16.dp else 14.dp,
                        vertical = if (padded) 16.dp else 0.dp,
                    ),
                ),
            content = content,
        )
    }
}

/** 묶음 위의 작은 회색 이름. */
@Composable
private fun GroupLabel(text: String) {
    val colors = LocalKidhackColors.current
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        color = colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

@Composable
private fun RowDivider() {
    val colors = LocalKidhackColors.current
    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
}

@Composable
private fun EmptyLine(text: String) {
    val colors = LocalKidhackColors.current
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 18.dp),
        color = colors.muted,
        fontSize = 13.sp,
    )
}

/** 왼쪽 정렬 · 글자 폭만큼만 밑줄. 화면 폭을 반씩 나누면 버튼처럼 보인다. */
@Composable
private fun WalletTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                    color = if (active) colors.text else colors.muted,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (active) colors.brand else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun WalletChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) colors.brand else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        color = if (selected) Color.White else colors.text,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 11.5.sp,
    )
}

/** 잔액 칸·자녀 탭이 함께 쓰는 버튼. 채운 것과 테두리만 있는 것 두 가지. */
@Composable
private fun WalletAction(
    label: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> colors.muted.copy(alpha = 0.6f)
                accent -> colors.brandDark
                else -> colors.text
            },
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
        )
    }
}

/** 자녀 한 명의 큰 칸. 고른 아이는 테두리와 바탕으로 구분한다. */
@Composable
private fun ChildWalletCard(
    child: ChildWalletSummary,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.brandLight else colors.card)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.brand else colors.border.copy(alpha = 0.9f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) colors.card else TintBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text("👦", fontSize = 26.sp)
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = child.name,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.5.sp,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatWonText(child.balance),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 16.sp,
            color = colors.text,
        )
    }
}

/** 원장 한 줄. 나가는 돈과 들어오는 돈을 색과 화살표로 구분한다. */
@Composable
private fun TransactionLine(tx: WalletTransaction) {
    val colors = LocalKidhackColors.current
    val outgoing = tx.type == "TRANSFER_OUT" || tx.type == "PAYMENT" || tx.type == "REFUND"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (outgoing) TintOrange else TintGreen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (outgoing) "↑" else "↓",
                color = if (outgoing) colors.brandDark else colors.safe,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = txLabel(tx.type),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.5.sp,
                color = colors.text,
            )
            Text(tx.createdAt, color = colors.muted, fontSize = 11.5.sp)
        }
        Text(
            text = (if (outgoing) "−" else "+") + formatWonText(tx.amount),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 13.5.sp,
            color = if (outgoing) colors.text else colors.safe,
        )
    }
}

/** 서버 원장 유형을 부모가 읽는 말로. 모르는 값은 그대로 둔다(서버가 늘어나도 안 깨진다). */
private fun txLabel(type: String): String = when (type) {
    "CHARGE" -> "충전"
    "REFUND" -> "환불"
    "TRANSFER_OUT" -> "보내기"
    "TRANSFER_IN" -> "받기"
    "PAYMENT" -> "결제"
    "PAYMENT_CANCEL" -> "결제 취소"
    "ADJUST" -> "잔액 조정"
    else -> type
}

/** 충전 한 줄. 아직 결제 전(READY)이면 「취소」, 결제됐으면(PAID) 「환불」이 붙는다. */
@Composable
private fun ChargeLine(item: ChargeHistoryItem, enabled: Boolean, onAction: (WalletConfirm) -> Unit) {
    val colors = LocalKidhackColors.current
    val done = item.status == "PAID"
    val action: Pair<Int, WalletConfirm>? = when (item.status) {
        "PAID" -> R.string.parent_wallet_charge_refund to WalletConfirm.RefundCharge(item.merchantUid, item.amount)
        "READY" -> R.string.parent_wallet_charge_cancel to WalletConfirm.CancelCharge(item.merchantUid, item.amount)
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = chargeStatusLabel(item.status),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.5.sp,
                color = if (done || action != null) colors.text else colors.muted,
            )
            Text(
                text = item.refundedAtLabel?.let { "환불 $it" } ?: item.createdAtLabel,
                color = colors.muted,
                fontSize = 11.5.sp,
            )
        }
        Text(
            text = formatWonText(item.amount),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 13.5.sp,
            color = if (action != null) colors.text else colors.muted,
        )
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            DangerPill(stringResource(action.first), enabled) { onAction(action.second) }
        }
    }
}

private fun chargeStatusLabel(status: String): String = when (status) {
    "PAID" -> "충전완료"
    "REFUNDED" -> "환불됨"
    "READY" -> "대기중"
    "CANCELLED" -> "취소됨"
    "FAILED" -> "실패"
    "EXPIRED" -> "만료됨"
    else -> status
}

/** 자녀 매장 결제 한 줄. 아직 살아 있는 결제만 취소할 수 있다. */
@Composable
private fun PaymentLine(item: QrPaymentHistoryItem, enabled: Boolean, onAction: (WalletConfirm) -> Unit) {
    val colors = LocalKidhackColors.current
    val cancelled = item.status == "CANCELLED"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (cancelled) colors.surface else TintPink),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (cancelled) "↩" else "🧾", fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.storeName,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.5.sp,
                color = if (cancelled) colors.muted else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.paidAtLabel, color = colors.muted, fontSize = 11.5.sp)
        }
        Text(
            text = if (cancelled) {
                stringResource(R.string.parent_wallet_payment_cancelled)
            } else {
                "−" + formatWonText(item.amount)
            },
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 13.5.sp,
            color = if (cancelled) colors.muted else colors.text,
        )
        if (!cancelled) {
            Spacer(Modifier.width(8.dp))
            DangerPill(stringResource(R.string.parent_wallet_payment_cancel), enabled) {
                onAction(WalletConfirm.CancelPayment(item.qrToken, item.storeName, item.amount))
            }
        }
    }
}

/** 되돌리는 동작에 붙는 작은 빨간 버튼. */
@Composable
private fun DangerPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.danger.copy(alpha = 0.1f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (enabled) colors.danger else colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 11.5.sp,
    )
}

/* ---- 시트 조각 ---- */

@Composable
private fun SheetHeading(title: String, hint: String) {
    val colors = LocalKidhackColors.current
    Text(title, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 18.sp, color = colors.text)
    Spacer(Modifier.height(4.dp))
    Text(hint, color = colors.muted, fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SheetLabel(text: String) {
    val colors = LocalKidhackColors.current
    Text(
        text = text,
        modifier = Modifier.padding(bottom = 7.dp),
        color = colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

@Composable
private fun SheetSubmit(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) colors.brand else colors.border)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp),
        color = if (enabled) Color.White else colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 15.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** 눌러서 금액을 더하는 칩. 0 부터 한 자리씩 치는 것보다 빠르다. */
@Composable
private fun QuickAmounts(amounts: List<Int>, onAdd: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        amounts.forEach { amount ->
            WalletChip(label = "+" + quickAmountLabel(amount), selected = false) { onAdd(amount) }
        }
    }
}

/** "10000" 이 아니라 "1만". 칩이 좁아 숫자를 다 적으면 넉 줄이 안 들어간다. */
private fun quickAmountLabel(amount: Int): String = when {
    amount >= 10_000 -> (amount / 10_000).toString() + "만"
    else -> (amount / 1_000).toString() + "천"
}

/**
 * 금액 입력칸.
 *
 * <p>담긴 값은 숫자만이고, 보이는 것만 천 단위로 끊는다([ThousandsTransformation]).
 * 값 자체에 쉼표를 넣으면 지울 때 커서가 튄다.
 */
@Composable
private fun AmountField(value: String, onChange: (String) -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("0", color = colors.muted, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = colors.text,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                visualTransformation = ThousandsTransformation,
                cursorBrush = SolidColor(colors.brand),
            )
        }
        // 단위는 뒤에 붙인다. 앞에 세우면 커서가 늘 기호 뒤에서 시작하는 것처럼 보인다.
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.parent_wallet_won_suffix),
            color = colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 15.sp,
        )
    }
}

/**
 * 숫자만 담긴 값을 "30,000" 으로 보이게 한다.
 *
 * <p>커서 위치를 옮기는 계산이 틀리면 곧바로 튕기므로, 되돌리는 쪽은 실제 글자를 세어
 * 구한다 — 자리수 규칙을 두 번 적는 것보다 어긋날 일이 없다.
 */
private object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val grouped = buildString {
            digits.forEachIndexed { index, c ->
                if (index > 0 && (digits.length - index) % 3 == 0) append(',')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safe = offset.coerceIn(0, digits.length)
                val commasTotal = (digits.length - 1) / 3
                val commasBefore = (commasTotal - (digits.length - safe) / 3).coerceAtLeast(0)
                return (safe + commasBefore).coerceIn(0, grouped.length)
            }

            override fun transformedToOriginal(offset: Int): Int =
                grouped.take(offset.coerceIn(0, grouped.length)).count { it.isDigit() }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

/** 충전 시트에서 고르는 카드. */
@Composable
private fun MiniCard(card: Card, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .size(width = 128.dp, height = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.brand else colors.surface)
            .border(2.dp, if (selected) colors.brandDark else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = card.ownerName,
            color = if (selected) Color.White else colors.text,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "**** " + card.last4,
            color = if (selected) Color.White else colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
    }
}
