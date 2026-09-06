package com.hack.kidhack.feature.parent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.repository.CardRepository
import com.hack.kidhack.domain.repository.EfinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 카드 등록(접근매체 발급) 위저드 단계. 첫 카드면 실명확인→결제비번 설정을 거친 뒤 카드 입력. */
enum class CardStep { IDENTITY, SET_PW, FORM }

data class CardUiState(
    val items: List<Card> = emptyList(),
    val loading: Boolean = true,
    val adding: Boolean = false,
    /** 삭제 요청 중인 카드 id. 진행 중엔 다시 못 누르게 하고, 스피너를 그 카드에 보인다. */
    val deletingId: String? = null,
    val error: String? = null,

    // 위저드
    val step: CardStep = CardStep.FORM,
    val checking: Boolean = false,
    val stepBusy: Boolean = false,
    val stepError: String? = null,
    // 실명확인 입력
    val idName: String = "",
    val idBirth: String = "",
    val idType: String = "RESIDENT",
    val idNumber: String = "",
    val idIssued: String = "",
    // 결제비번 최초 설정 입력
    val newPw: String = "",
    val newPw2: String = "",

    // 카드 폼
    val cardNumber: String = "",
    val expiry: String = "",
    val cvc: String = "",
    val cardPassword2: String = "",
    val birthDate: String = "",
    val ownerName: String = "",
    /** 카드 발급 본인확인용 결제 비밀번호(4자리). */
    val paymentPw: String = "",
)

class CardViewModel(
    private val cardRepository: CardRepository,
    private val efinRepository: EfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CardUiState())
    val state: StateFlow<CardUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { cardRepository.list() }
                .onSuccess { list -> _state.update { it.copy(items = list, loading = false) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    /**
     * 카드 추가 시작 — 접근매체 발급 관문 판단. 첫 카드면 실명확인→결제비번 설정부터,
     * 이미 끝난 회원이면 곧바로 카드 입력 폼으로 간다.
     */
    fun toggleAdding(show: Boolean) {
        if (!show) {
            _state.update { it.copy(adding = false, stepError = null) }
            return
        }
        _state.update { it.copy(adding = true, checking = true, stepError = null, error = null) }
        viewModelScope.launch {
            val verified = runCatching { efinRepository.identityVerified() }.getOrDefault(false)
            val pwSet = runCatching { efinRepository.paymentPasswordSet() }.getOrDefault(false)
            _state.update {
                it.copy(
                    checking = false,
                    step = when {
                        !verified -> CardStep.IDENTITY
                        !pwSet -> CardStep.SET_PW
                        else -> CardStep.FORM
                    },
                )
            }
        }
    }

    // --- 실명확인 단계 ---
    fun onIdName(v: String) = _state.update { it.copy(idName = v) }
    fun onIdBirth(v: String) = _state.update { it.copy(idBirth = v.filter { it.isDigit() }.take(6)) }
    fun onIdType(v: String) = _state.update { it.copy(idType = v) }
    fun onIdNumber(v: String) = _state.update { it.copy(idNumber = v) }
    fun onIdIssued(v: String) = _state.update { it.copy(idIssued = v) }

    fun submitIdentity() {
        val s = _state.value
        if (s.stepBusy) return
        viewModelScope.launch {
            _state.update { it.copy(stepBusy = true, stepError = null) }
            runCatching { efinRepository.verifyIdentity(s.idName, s.idBirth, s.idType, s.idNumber, s.idIssued) }
                .onSuccess { r ->
                    if (r.verified) {
                        val pwSet = runCatching { efinRepository.paymentPasswordSet() }.getOrDefault(false)
                        _state.update { it.copy(stepBusy = false, step = if (pwSet) CardStep.FORM else CardStep.SET_PW) }
                    } else {
                        _state.update { it.copy(stepBusy = false, stepError = "실명확인에 실패했습니다. 입력 정보를 확인하세요.") }
                    }
                }
                .onFailure { t -> _state.update { it.copy(stepBusy = false, stepError = t.message) } }
        }
    }

    // --- 결제비번 최초 설정 단계 ---
    fun onNewPw(v: String) = _state.update { it.copy(newPw = v.filter { it.isDigit() }.take(4)) }
    fun onNewPw2(v: String) = _state.update { it.copy(newPw2 = v.filter { it.isDigit() }.take(4)) }

    fun submitSetPw() {
        val s = _state.value
        if (s.stepBusy) return
        if (s.newPw.length != 4 || s.newPw != s.newPw2) {
            _state.update { it.copy(stepError = "결제 비밀번호 4자리가 일치하지 않습니다.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(stepBusy = true, stepError = null) }
            runCatching { efinRepository.setupPaymentPassword(s.newPw) }
                .onSuccess { _state.update { it.copy(stepBusy = false, step = CardStep.FORM) } }
                .onFailure { t -> _state.update { it.copy(stepBusy = false, stepError = t.message) } }
        }
    }

    // --- 카드 폼 ---
    fun onCardNumber(v: String) = _state.update { it.copy(cardNumber = v.filter { it.isDigit() }.take(16)) }
    fun onExpiry(v: String) = _state.update { it.copy(expiry = v.filter { it.isDigit() }.take(4)) }
    fun onCvc(v: String) = _state.update { it.copy(cvc = v.filter { it.isDigit() }.take(3)) }
    fun onCardPassword2(v: String) = _state.update { it.copy(cardPassword2 = v.filter { it.isDigit() }.take(2)) }
    fun onBirthDate(v: String) = _state.update { it.copy(birthDate = v.filter { it.isDigit() }.take(6)) }
    fun onOwnerName(v: String) = _state.update { it.copy(ownerName = v) }
    fun onPaymentPw(v: String) = _state.update { it.copy(paymentPw = v.filter { it.isDigit() }.take(4)) }

    fun register() {
        val s = _state.value
        if (s.loading) return // 이미 등록 요청 중이면 무시(중복 제출로 멈춘 것처럼 보이던 것 방지)
        if (s.cardNumber.length != 16 || s.expiry.length < 4 || s.cvc.length < 3 ||
            s.cardPassword2.length < 2 || s.birthDate.length < 6 || s.ownerName.isBlank() ||
            s.paymentPw.length != 4) {
            _state.update { it.copy(error = "모든 정보를 올바르게 입력해 주세요(결제 비밀번호 4자리 포함).") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val formattedExpiry = "${s.expiry.take(2)}/${s.expiry.drop(2)}"
                cardRepository.register(
                    cardNumber = s.cardNumber,
                    expiry = formattedExpiry,
                    cvc = s.cvc,
                    cardPassword2 = s.cardPassword2,
                    birthDate = s.birthDate,
                    ownerName = s.ownerName,
                    paymentPassword = s.paymentPw,
                )
            }.onSuccess {
                // 민감 입력 즉시 초기화 + 위저드 종료.
                _state.update { it.copy(
                    loading = false, adding = false,
                    cardNumber = "", expiry = "", cvc = "", cardPassword2 = "", birthDate = "", ownerName = "",
                    paymentPw = "", idName = "", idBirth = "", idNumber = "", idIssued = "", newPw = "", newPw2 = "",
                    error = null, stepError = null,
                ) }
                load()
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun delete(cardId: String) {
        // 이미 삭제 요청 중이면 무시 — 무응답 카드를 연타하면 요청만 쌓였다.
        if (_state.value.deletingId != null) return
        viewModelScope.launch {
            _state.update { it.copy(deletingId = cardId, error = null) }
            runCatching { cardRepository.delete(cardId) }
                .onSuccess {
                    _state.update { it.copy(deletingId = null) }
                    load()
                }
                .onFailure { t ->
                    // 무응답(타임아웃)·서버 오류를 조용히 넘기지 않고 그대로 보여 준다.
                    _state.update { it.copy(deletingId = null, error = t.userMessage("카드를 삭제하지 못했어요")) }
                }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/**
 * 카드 관리.
 *
 * <p>카드 그림을 브랜드색 그라데이션으로 칠하고 있었다. 그러면 「이 앱이 발급한 카드」처럼
 * 보이는데, 실제로는 사용자가 등록한 남의 카드다. 먹색 한 장으로 둔다.
 *
 * <p>삭제가 <b>길게 누르기</b>였다. 알아낼 방법이 없어 카드 오른쪽 위 「더 보기」로 꺼냈다.
 */
@Composable
fun ParentCardsScreen(
    onBack: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
    viewModel: CardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    var deleteCardId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxWidth().background(colors.card)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("\u2039", color = colors.text, fontSize = 26.sp)
                }
                Text(
                    stringResource(R.string.parent_cards_title),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 18.sp,
                    color = colors.text,
                )
            }
            KidHeaderDivider()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.parent_cards_lead),
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 18.dp),
                color = colors.muted,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
            )

            if (state.loading && state.items.isEmpty()) {
                Box(Modifier.padding(horizontal = 22.dp)) {
                    ParentLoadingOrError(true, state.error, viewModel::load)
                }
            } else if (state.error != null && state.items.isEmpty()) {
                // 목록 자체를 못 불러온 경우 — 큰 재시도 블록.
                Box(Modifier.padding(horizontal = 22.dp)) {
                    ParentLoadingOrError(false, state.error, viewModel::load)
                }
            } else if (state.error != null) {
                // 목록은 있는데 삭제/등록이 실패한 경우 — 카드 위에 얇은 경고 배너.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.danger.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.error ?: "",
                        modifier = Modifier.weight(1f),
                        color = colors.danger,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            state.items.forEach { card ->
                CreditCard(card = card, onMore = { deleteCardId = card.cardId })
                Spacer(Modifier.height(12.dp))
            }

            if (state.adding) {
                AddCardForm(state = state, viewModel = viewModel)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .clickable { viewModel.toggleAdding(true) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_plus),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.muted,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(R.string.parent_cards_add),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 13.5.sp,
                    )
                }

                Text(
                    stringResource(R.string.parent_cards_group),
                    modifier = Modifier.padding(start = 22.dp, top = 30.dp, bottom = 8.dp),
                    color = colors.subtle,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
                KidSectionCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenWallet)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.parent_cards_go_wallet),
                            modifier = Modifier.weight(1f),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.5.sp,
                            letterSpacing = (-0.25).sp,
                            color = colors.text,
                        )
                        Text("\u203a", color = colors.border, fontSize = 17.sp)
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    deleteCardId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCardId = null },
            containerColor = colors.card,
            title = {
                Text(
                    stringResource(R.string.parent_cards_delete_title),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 17.sp,
                    color = colors.text,
                )
            },
            text = {
                Text(
                    stringResource(R.string.parent_cards_delete_message),
                    color = colors.muted,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton({ viewModel.delete(id); deleteCardId = null }) {
                    Text(
                        stringResource(R.string.parent_cards_delete),
                        color = colors.danger,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                    )
                }
            },
            dismissButton = {
                TextButton({ deleteCardId = null }) {
                    Text(
                        stringResource(R.string.parent_cards_cancel),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                    )
                }
            },
        )
    }
}

/** 등록된 카드 한 장. 번호는 뒤 네 자리만 서버가 준다. */
@Composable
private fun CreditCard(card: Card, onMore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardInk)
            .padding(start = 22.dp, end = 12.dp, top = 20.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ROOKIES PAY",
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 10.5.sp,
                letterSpacing = 1.6.sp,
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(R.string.parent_community_more),
                    modifier = Modifier.size(18.dp),
                    tint = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            text = "\u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  ${card.last4}",
            color = Color.White,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 17.sp,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().padding(end = 10.dp)) {
            Text(
                card.ownerName,
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 11.5.sp,
            )
            Text(
                card.expiry,
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 11.5.sp,
            )
        }
    }
}

/** 카드 그림 바탕. 브랜드 주황을 쓰면 앱이 발급한 카드처럼 보인다. */
private val CardInk = Color(0xFF1C2026)

/** 새 카드 등록 폼. */
@Composable
private fun ColumnScope.AddCardForm(state: CardUiState, viewModel: CardViewModel) {
    when {
        state.checking -> StepTitle("확인 중…")
        state.step == CardStep.IDENTITY -> IdentityStep(state, viewModel)
        state.step == CardStep.SET_PW -> SetPwStep(state, viewModel)
        else -> CardFormStep(state, viewModel)
    }
}

/** 접근매체 발급 1단계 — 신분증+실명 진위확인(회원당 1회). */
@Composable
private fun ColumnScope.IdentityStep(state: CardUiState, viewModel: CardViewModel) {
    val colors = LocalKidhackColors.current
    StepTitle("실명확인 (1/3)")
    Column(
        modifier = Modifier.padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "카드(접근매체) 발급 전 본인 명의를 확인합니다.",
            color = colors.muted, fontFamily = KidhackFontFamily, fontSize = 12.5.sp,
        )
        KidTextField(state.idName, viewModel::onIdName, "이름", placeholder = "홍길동")
        KidTextField(state.idBirth, viewModel::onIdBirth, "생년월일 6자리", placeholder = "900101",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdTypeChip("주민등록증", state.idType == "RESIDENT", Modifier.weight(1f)) { viewModel.onIdType("RESIDENT") }
            IdTypeChip("운전면허", state.idType == "DRIVER", Modifier.weight(1f)) { viewModel.onIdType("DRIVER") }
        }
        KidTextField(state.idNumber, viewModel::onIdNumber, "신분증 번호", placeholder = "900101-1234567",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        KidTextField(state.idIssued, viewModel::onIdIssued, "발급일자", placeholder = "2015-03-20",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (state.stepError != null) StepError(state.stepError)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidButton("취소", onClick = { viewModel.toggleAdding(false) }, modifier = Modifier.weight(1f),
                variant = KidButtonVariant.Secondary)
            KidButton("실명확인", onClick = viewModel::submitIdentity, modifier = Modifier.weight(2f),
                enabled = !state.stepBusy && state.idName.isNotBlank() && state.idBirth.length == 6 &&
                    state.idNumber.isNotBlank() && state.idIssued.isNotBlank())
        }
    }
}

/** 접근매체 발급 2단계 — 결제 비밀번호 4자리 최초 설정(직전 실명확인이 본인확인 대체). */
@Composable
private fun ColumnScope.SetPwStep(state: CardUiState, viewModel: CardViewModel) {
    val colors = LocalKidhackColors.current
    StepTitle("결제 비밀번호 설정 (2/3)")
    Column(
        modifier = Modifier.padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "결제 시 사용할 4자리 비밀번호를 만듭니다.",
            color = colors.muted, fontFamily = KidhackFontFamily, fontSize = 12.5.sp,
        )
        KidTextField(state.newPw, viewModel::onNewPw, "결제 비밀번호 4자리", placeholder = "숫자 4자리",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        KidTextField(state.newPw2, viewModel::onNewPw2, "한 번 더 입력", placeholder = "숫자 4자리",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (state.stepError != null) StepError(state.stepError)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidButton("취소", onClick = { viewModel.toggleAdding(false) }, modifier = Modifier.weight(1f),
                variant = KidButtonVariant.Secondary)
            KidButton("설정", onClick = viewModel::submitSetPw, modifier = Modifier.weight(2f),
                enabled = !state.stepBusy && state.newPw.length == 4 && state.newPw2.length == 4)
        }
    }
}

@Composable
private fun ColumnScope.StepTitle(text: String) {
    val colors = LocalKidhackColors.current
    Text(
        text,
        modifier = Modifier.padding(start = 22.dp, bottom = 6.dp),
        fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 17.sp, color = colors.text,
    )
}

@Composable
private fun StepError(text: String) {
    val colors = LocalKidhackColors.current
    Text(text, color = colors.danger, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 12.5.sp)
}

@Composable
private fun IdTypeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.brandLight else colors.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) colors.brandDark else colors.muted,
            fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 13.sp)
    }
}

/** 접근매체 발급 3단계 — 카드 정보 + 결제 비밀번호 확인. */
@Composable
private fun ColumnScope.CardFormStep(state: CardUiState, viewModel: CardViewModel) {
    val colors = LocalKidhackColors.current
    val numberOptions = KeyboardOptions(keyboardType = KeyboardType.Number)

    StepTitle(stringResource(R.string.parent_cards_new))
    Column(
        modifier = Modifier.padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KidTextField(
            state.cardNumber,
            viewModel::onCardNumber,
            stringResource(R.string.parent_cards_number),
            placeholder = stringResource(R.string.parent_cards_number_hint),
            keyboardOptions = numberOptions,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidTextField(
                state.expiry,
                viewModel::onExpiry,
                stringResource(R.string.parent_cards_expiry),
                placeholder = "MM/YY",
                modifier = Modifier.weight(1f),
                keyboardOptions = numberOptions,
            )
            KidTextField(
                state.cvc,
                viewModel::onCvc,
                "CVC",
                placeholder = stringResource(R.string.parent_cards_cvc_hint),
                modifier = Modifier.weight(1f),
                keyboardOptions = numberOptions,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidTextField(
                state.cardPassword2,
                viewModel::onCardPassword2,
                stringResource(R.string.parent_cards_password),
                placeholder = stringResource(R.string.parent_cards_password_hint),
                modifier = Modifier.weight(1f),
                keyboardOptions = numberOptions,
            )
            KidTextField(
                state.birthDate,
                viewModel::onBirthDate,
                stringResource(R.string.parent_cards_birth),
                placeholder = "YYMMDD",
                modifier = Modifier.weight(1f),
                keyboardOptions = numberOptions,
            )
        }
        KidTextField(
            state.ownerName,
            viewModel::onOwnerName,
            stringResource(R.string.parent_cards_owner),
            placeholder = stringResource(R.string.parent_cards_owner_hint),
        )
        KidTextField(
            state.paymentPw,
            viewModel::onPaymentPw,
            "결제 비밀번호 4자리",
            placeholder = "본인확인 · 숫자 4자리",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        if (state.error != null) StepError(state.error)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidButton(
                text = stringResource(R.string.parent_cards_cancel),
                onClick = { viewModel.toggleAdding(false) },
                modifier = Modifier.weight(1f),
                variant = KidButtonVariant.Secondary,
            )
            KidButton(
                text = stringResource(R.string.parent_cards_save),
                onClick = viewModel::register,
                modifier = Modifier.weight(2f),
                enabled = !state.loading && state.paymentPw.length == 4,
            )
        }
    }
}
