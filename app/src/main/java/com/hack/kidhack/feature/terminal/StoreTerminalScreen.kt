package com.hack.kidhack.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.StoreTerminalInfo
import com.hack.kidhack.domain.repository.StoreTerminalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class StoreTerminalUiState(
    val terminals: List<StoreTerminalInfo> = emptyList(),
    val selectedToken: String? = null,
    val storeNameInput: String = "GS25 테스트점",
    val qrTokenInput: String = "",
    val amountInput: String = "",
    val busy: Boolean = false,
    val resultMessage: String? = null,
    val error: String? = null,
)

/**
 * 매장 단말(태그 16·17) 테스트/시연 전용 ViewModel.
 *
 * 회원 앱 정상 흐름과 **분리된** 진입점에서만 쓴다. 자녀 앱의 옛 자가승인과 달리, 결제 승인은
 * 여기서 발급받은 `X-Store-Terminal-Token` 으로만 이뤄진다([StoreTerminalRepository.approve]).
 */
class StoreTerminalViewModel(
    private val repository: StoreTerminalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StoreTerminalUiState())
    val state: StateFlow<StoreTerminalUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.terminals() }
                .onSuccess { list ->
                    _state.update { s ->
                        val kept = s.selectedToken?.takeIf { t -> list.any { it.terminalToken == t } }
                        s.copy(terminals = list, selectedToken = kept ?: list.firstOrNull()?.terminalToken)
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = t.userMessage()) } }
        }
    }

    fun onStoreName(v: String) = _state.update { it.copy(storeNameInput = v) }
    fun onQrToken(v: String) = _state.update { it.copy(qrTokenInput = v.trim()) }
    fun onAmount(v: String) = _state.update { it.copy(amountInput = v.filter { c -> c.isDigit() }) }
    fun selectToken(token: String) = _state.update { it.copy(selectedToken = token) }

    fun issueTerminal() {
        val name = _state.value.storeNameInput.ifBlank { "테스트 매장" }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, resultMessage = null) }
            runCatching { repository.issueTerminal(name) }
                .onSuccess { terminal ->
                    _state.update {
                        it.copy(
                            busy = false,
                            selectedToken = terminal.terminalToken,
                            resultMessage = "단말 발급: ${terminal.storeName} (토큰 ${terminal.terminalToken.take(12)}…)",
                        )
                    }
                    refresh()
                }
                .onFailure { t -> _state.update { it.copy(busy = false, error = t.userMessage("단말 발급 실패")) } }
        }
    }

    fun approve() {
        val token = _state.value.selectedToken
        val qrToken = _state.value.qrTokenInput
        val amount = _state.value.amountInput.toIntOrNull() ?: 0
        if (token.isNullOrBlank() || qrToken.isBlank() || amount <= 0) {
            _state.update { it.copy(error = "단말 토큰·QR 토큰·금액을 확인하세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, resultMessage = null) }
            runCatching { repository.approve(token, qrToken, amount) }
                .onSuccess { paid ->
                    _state.update {
                        it.copy(
                            busy = false,
                            qrTokenInput = "",
                            amountInput = "",
                            resultMessage = "승인 완료: ${paid.storeName} · ${paid.amount}원 · ${paid.status}",
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(busy = false, error = t.userMessage("승인 실패")) } }
        }
    }
}

/**
 * 매장 단말 테스트 화면 — 회원 앱 정상 내비게이션에 노출하지 않는 숨은 라우트(디버그/시연 전용).
 *
 * 흐름: ① 단말 토큰 발급 → ② 자녀 앱이 띄운 QR 토큰과 금액 입력 → ③ 승인. 승인되면 자녀 앱의
 * 상태 폴링이 PAID 를 감지해 잔액이 차감된다. 폰 두 대(자녀·단말) 시연이 가능하다.
 */
@Composable
fun StoreTerminalScreen(
    onBack: () -> Unit = {},
    viewModel: StoreTerminalViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "← 뒤로",
            modifier = Modifier.clickable(onClick = onBack),
            color = colors.brand,
            fontWeight = FontBold,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("매장 단말 (테스트 전용)", color = colors.text, fontWeight = FontBlack, fontSize = 22.sp)
        Text(
            "회원 앱 정상 화면이 아닙니다. QR 결제 승인(태그 16) 시연·검증용.",
            color = colors.muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))

        KidCard {
            Text("1) 단말 토큰 발급", fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Spacer(Modifier.height(8.dp))
            KidTextField(state.storeNameInput, viewModel::onStoreName, "매장 이름", placeholder = "GS25 테스트점")
            Spacer(Modifier.height(8.dp))
            KidButton("단말 발급", viewModel::issueTerminal, Modifier.fillMaxWidth(), enabled = !state.busy)
        }

        Spacer(Modifier.height(12.dp))
        KidCard {
            Text("발급된 단말 (토큰 선택)", fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Spacer(Modifier.height(8.dp))
            if (state.terminals.isEmpty()) {
                Text("아직 발급된 단말이 없어요.", color = colors.muted, fontSize = 12.sp)
            }
            state.terminals.forEach { terminal ->
                val selected = state.selectedToken == terminal.terminalToken
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.brandLight else colors.surface)
                        .border(
                            width = 1.5.dp,
                            color = if (selected) colors.brand else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { viewModel.selectToken(terminal.terminalToken) }
                        .padding(10.dp),
                ) {
                    Text(terminal.storeName, fontWeight = FontBold, fontSize = 13.sp, color = colors.text)
                    Text(terminal.terminalToken, color = colors.muted, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        KidCard {
            Text("2) QR 스캔 결제 승인", fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Spacer(Modifier.height(8.dp))
            KidTextField(state.qrTokenInput, viewModel::onQrToken, "자녀 QR 토큰", placeholder = "qr_...")
            Spacer(Modifier.height(8.dp))
            KidTextField(
                state.amountInput,
                viewModel::onAmount,
                "결제 금액",
                placeholder = "3000",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(12.dp))
            KidButton("결제 승인", viewModel::approve, Modifier.fillMaxWidth(), enabled = !state.busy)
        }

        val message = state.resultMessage ?: state.error
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            KidCard(containerColor = (if (state.error != null) colors.danger else colors.safe).copy(alpha = 0.1f)) {
                Text(
                    message,
                    color = if (state.error != null) colors.danger else colors.safe,
                    fontWeight = FontBold,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}