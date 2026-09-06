package com.hack.kidhack.feature.auth.forgot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.auth.AuthScaffold
import org.koin.androidx.compose.koinViewModel

/** 이 화면이 하는 두 가지. 고르기 전에는 둘 다 아니다(null). */
private enum class ForgotMode { FIND_ID, RESET_PASSWORD }

/**
 * 계정 찾기 화면.
 *
 * <p>전에는 아이디 찾기 폼과 비밀번호 재설정 폼이 한 화면에 세로로 쌓여 있었다. 입력칸
 * 넷과 「인증코드 받기」 버튼 둘이 한꺼번에 보여서, 어느 쪽을 채워야 하는지 읽어야 알 수
 * 있었다. 무엇을 찾는지 **먼저 고르게** 하고 그다음 화면에는 그 폼만 남긴다.
 *
 * <p>서버가 이메일 인증코드 2단계(발송 → 검증)를 요구하므로 두 갈래 모두 같은 구조다.
 * 코드를 받기 전에는 검증 입력이 보이지 않는다.
 */
@Composable
fun ForgotScreen(
    onLogin: () -> Unit,
    viewModel: ForgotViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf<ForgotMode?>(null) }

    // 갈래를 고른 뒤의 뒤로가기는 로그인이 아니라 고르기 화면으로 돌아간다. 여기서 앱을
    // 나가 버리면 잘못 고른 사람이 처음부터 다시 들어와야 한다.
    BackHandler(enabled = mode != null) { mode = null }

    AuthScaffold(
        header = {
            BackRow(onClick = { if (mode == null) onLogin() else mode = null })
            Spacer(Modifier.height(10.dp))
            Text(
                text = when (mode) {
                    null -> "무엇을 찾으시나요?"
                    ForgotMode.FIND_ID -> "아이디 찾기"
                    ForgotMode.RESET_PASSWORD -> "비밀번호 재설정"
                },
                color = LocalKidhackColors.current.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 23.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = when (mode) {
                    null -> "가입할 때 쓰신 이메일로 인증코드를 보내 드려요."
                    ForgotMode.FIND_ID -> "이름과 이메일로 본인을 확인해요."
                    ForgotMode.RESET_PASSWORD -> "아이디와 이메일로 본인을 확인해요."
                },
                color = LocalKidhackColors.current.muted,
                fontSize = 13.sp,
            )
            if (mode != null) {
                Spacer(Modifier.height(18.dp))
                val done = when (mode) {
                    ForgotMode.FIND_ID -> state.findCodeSent
                    else -> state.resetCodeSent
                }
                StepHint(step = if (done) 2 else 1, label = if (done) "코드 확인" else "본인 확인")
            }
            Spacer(Modifier.height(6.dp))
        },
    ) {
        when (mode) {
            null -> {
                PickRow("ID", "아이디 찾기", "이름과 이메일로 확인해요") { mode = ForgotMode.FIND_ID }
                PickRow("PW", "비밀번호 재설정", "아이디와 이메일로 확인해요") {
                    mode = ForgotMode.RESET_PASSWORD
                }
                Spacer(Modifier.height(20.dp))
                BackToLogin(onLogin)
            }
            ForgotMode.FIND_ID -> FindIdForm(state, viewModel, onLogin)
            ForgotMode.RESET_PASSWORD -> ResetPasswordForm(state, viewModel, onLogin)
        }
    }
}

@Composable
private fun ColumnScope.FindIdForm(
    state: ForgotUiState,
    viewModel: ForgotViewModel,
    onLogin: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    KidTextField(state.findName, viewModel::onFindName, "이름", placeholder = "홍길동")
    KidTextField(
        state.findEmail,
        viewModel::onFindEmail,
        "이메일",
        placeholder = "parent01@test.com",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    Text(
        text = "자녀 계정은 부모님 이메일을 넣어 주세요.",
        color = colors.muted,
        fontSize = 11.5.sp,
    )
    KidButton(
        text = if (state.findSendLoading) "보내는 중..." else "인증코드 받기",
        onClick = viewModel::sendFindIdCode,
        modifier = Modifier.fillMaxWidth(),
        variant = KidButtonVariant.Secondary,
        enabled = !state.findSendLoading,
    )
    if (state.findCodeSent) {
        KidTextField(
            state.findCode,
            viewModel::onFindCode,
            "인증 코드",
            placeholder = "6자리 숫자",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        KidButton(
            text = if (state.findLoading) "확인 중..." else "아이디 찾기",
            onClick = viewModel::verifyFindId,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.findLoading,
        )
    }
    Result(info = state.findInfo, error = state.findError)
    Spacer(Modifier.height(12.dp))
    BackToLogin(onLogin)
}

@Composable
private fun ColumnScope.ResetPasswordForm(
    state: ForgotUiState,
    viewModel: ForgotViewModel,
    onLogin: () -> Unit,
) {
    KidTextField(state.resetLoginId, viewModel::onResetLoginId, "아이디", placeholder = "아이디를 입력하세요")
    KidTextField(
        state.resetEmail,
        viewModel::onResetEmail,
        "이메일",
        placeholder = "parent01@test.com",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    KidButton(
        text = if (state.resetSendLoading) "보내는 중..." else "인증코드 받기",
        onClick = viewModel::sendResetPasswordCode,
        modifier = Modifier.fillMaxWidth(),
        variant = KidButtonVariant.Secondary,
        enabled = !state.resetSendLoading,
    )
    if (state.resetCodeSent) {
        KidTextField(
            state.verifyCode,
            viewModel::onVerifyCode,
            "인증 코드",
            placeholder = "6자리 숫자",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        KidTextField(
            state.newPassword,
            viewModel::onNewPassword,
            "새 비밀번호",
            placeholder = "8자 이상",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        KidTextField(
            state.newPasswordConfirm,
            viewModel::onNewPasswordConfirm,
            "새 비밀번호 확인",
            placeholder = "비밀번호를 다시 입력하세요",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        KidButton(
            text = if (state.resetLoading) "처리 중..." else "비밀번호 재설정",
            onClick = viewModel::resetPassword,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.resetLoading,
        )
    }
    Result(info = state.resetInfo, error = state.resetError)
    Spacer(Modifier.height(12.dp))
    BackToLogin(onLogin)
}

/** 무엇을 찾을지 고르는 줄. */
@Composable
private fun PickRow(badge: String, title: String, desc: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badge,
                color = colors.brandDark,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(Modifier.padding(start = 7.dp)) {
            Text(
                text = title,
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.5.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(text = desc, color = colors.muted, fontSize = 11.5.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(text = "›", color = colors.muted, fontSize = 19.sp)
    }
}

/** 「1 / 2 · 본인 확인」. 코드를 받으면 2단계로 넘어간다. */
@Composable
private fun ColumnScope.StepHint(step: Int, label: String) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(TOTAL_STEPS) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (index < step) colors.brand else colors.border),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = "$step / $TOTAL_STEPS · $label",
        color = colors.brandDark,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 11.5.sp,
    )
}

private const val TOTAL_STEPS = 2

/** 성공·실패 문구. 둘 다 폼 바로 아래에 붙어 어느 폼의 결과인지 헷갈리지 않는다. */
@Composable
private fun ColumnScope.Result(info: String?, error: String?) {
    val colors = LocalKidhackColors.current
    if (info != null) Text(info, color = colors.safe, fontSize = 13.sp)
    if (error != null) Text(error, color = colors.danger, fontSize = 13.sp)
}

@Composable
private fun BackRow(onClick: () -> Unit) {
    Text(
        text = "‹",
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = LocalKidhackColors.current.text,
        fontSize = 24.sp,
    )
}

@Composable
private fun BackToLogin(onLogin: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = "로그인으로 돌아가기",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLogin)
            .padding(vertical = 10.dp),
        color = colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
}
