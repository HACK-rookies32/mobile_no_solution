package com.hack.kidhack.feature.efin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.repository.EfinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 결제 비밀번호 변경 화면 — #59.
 *
 * 결제용 4자리 PIN 은 첫 카드 등록에서 설정하고, 이 화면은 **변경만** 담당한다(계정설정 진입).
 * 기존 결제 비밀번호 + 앱 로그인 비밀번호로 본인확인하고, 과거에 쓴 비밀번호로는 바꿀 수 없다.
 * 저장 결함은 서버측이다([VULN-EFIN-5/6]).
 */
data class PaymentPwChangeUiState(
    val current: String = "",
    val newPassword: String = "",
    /** 본인확인용 앱 로그인 비밀번호. */
    val loginPassword: String = "",
    val working: Boolean = false,
    val error: String? = null,
    val done: String? = null,
)

class PaymentPasswordViewModel(
    private val efinRepository: EfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PaymentPwChangeUiState())
    val state: StateFlow<PaymentPwChangeUiState> = _state.asStateFlow()

    fun onCurrent(v: String) = _state.update { it.copy(current = digits(v)) }
    fun onNew(v: String) = _state.update { it.copy(newPassword = digits(v)) }
    fun onLoginPassword(v: String) = _state.update { it.copy(loginPassword = v) }

    fun submit() {
        val s = _state.value
        if (s.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null, done = null) }
            runCatching { efinRepository.changePaymentPassword(s.current, s.newPassword, s.loginPassword) }
                .onSuccess {
                    _state.update {
                        it.copy(working = false, current = "", newPassword = "", loginPassword = "",
                            done = "결제 비밀번호를 변경했습니다.")
                    }
                }
                .onFailure { t -> _state.update { it.copy(working = false, error = t.userMessage()) } }
        }
    }

    private fun digits(v: String) = v.filter { it.isDigit() }.take(4)
}

@Composable
fun PaymentPasswordScreen(
    onBack: () -> Unit = {},
    viewModel: PaymentPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val ready = state.current.length == 4 && state.newPassword.length == 4 && state.loginPassword.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        EfinHeader(title = "결제 비밀번호 변경", onBack = onBack)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FieldLabel("기존 결제 비밀번호")
            PinField(state.current, viewModel::onCurrent, "기존 4자리")
            Spacer(Modifier.height(2.dp))
            FieldLabel("새 결제 비밀번호")
            PinField(state.newPassword, viewModel::onNew, "새 4자리")
            Spacer(Modifier.height(2.dp))
            FieldLabel("본인확인 · 앱 로그인 비밀번호")
            LoginPwField(state.loginPassword, viewModel::onLoginPassword)

            EfinPrimaryButton(text = "변경", enabled = !state.working && ready, onClick = viewModel::submit)
            EfinBanner(error = state.error, done = state.done)
        }
    }
}

/** 앱 로그인 비밀번호 입력(마스킹, 길이 제한 없음). */
@Composable
internal fun LoginPwField(value: String, onChange: (String) -> Unit) {
    val colors = LocalKidhackColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text("로그인 비밀번호", color = colors.muted, fontFamily = KidhackFontFamily, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = colors.text, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            cursorBrush = SolidColor(colors.brand),
        )
    }
}

/** 결제 비밀번호(마스킹) 4자리 입력 필드. 카드 위저드와 변경 화면이 공용. */
@Composable
internal fun PinField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val colors = LocalKidhackColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = colors.muted, fontFamily = KidhackFontFamily, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = colors.text, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            cursorBrush = SolidColor(colors.brand),
        )
    }
}
