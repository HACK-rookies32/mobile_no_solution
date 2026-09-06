package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class WithdrawUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val confirming: Boolean = false,
)

/**
 * 회원 탈퇴 (`DELETE /users/me`).
 *
 * 탈퇴가 성공하면 서버의 토큰은 무의미해지므로 로컬 세션도 함께 정리해 로그인 화면으로 돌려보낸다.
 * 서버 응답이 성공이든 아니든 세션을 남겨 두면 앱이 죽은 계정으로 계속 요청을 보낸다.
 */
class WithdrawViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WithdrawUiState())
    val state: StateFlow<WithdrawUiState> = _state.asStateFlow()

    fun ask() = _state.update { it.copy(confirming = true, error = null) }
    fun dismiss() = _state.update { it.copy(confirming = false) }

    fun withdraw() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, confirming = false, error = null) }
            runCatching { userRepository.deleteMe() }
                .onSuccess {
                    // 세션 정리는 실패하더라도 탈퇴 자체는 끝났으므로 로그아웃 흐름을 그대로 탄다.
                    runCatching { authRepository.logout() }
                    _state.update { it.copy(loading = false) }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.userMessage("탈퇴하지 못했어요")) }
                }
        }
    }
}

@Composable
fun ParentWithdrawScreen(
    onBack: () -> Unit = {},
    viewModel: WithdrawViewModel = koinViewModel(),
) {
    val colors = LocalKidhackColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = colors.text, fontSize = 26.sp) }
                Text(
                    stringResource(R.string.parent_withdraw_title),
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            KidSectionCard {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(
                        stringResource(R.string.parent_withdraw_heading),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 16.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.parent_withdraw_desc),
                        color = colors.muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = colors.danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            KidButton(
                text = if (state.loading) {
                    stringResource(R.string.parent_withdraw_processing)
                } else {
                    stringResource(R.string.parent_withdraw_action)
                },
                onClick = viewModel::ask,
                modifier = Modifier.fillMaxWidth(),
                variant = KidButtonVariant.Danger,
                enabled = !state.loading,
            )
        }
    }

    if (state.confirming) {
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.parent_withdraw_confirm_title), fontWeight = FontBold) },
            text = { Text(stringResource(R.string.parent_withdraw_confirm_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::withdraw) {
                    Text(stringResource(R.string.parent_withdraw_confirm_ok), color = colors.danger, fontWeight = FontBold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) {
                    Text(stringResource(R.string.parent_withdraw_confirm_cancel), color = colors.muted)
                }
            },
        )
    }
}
