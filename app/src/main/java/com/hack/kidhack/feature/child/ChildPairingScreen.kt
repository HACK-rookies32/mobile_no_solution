package com.hack.kidhack.feature.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidLabeledSection
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.repository.PairingRepository
import com.hack.kidhack.feature.child.components.ChildSubScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ChildPairingUiState(
    val code: String = "",
    val loading: Boolean = false,
    val linked: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = code.isNotBlank() && !loading

    companion object {
        const val CODE_LENGTH = 6
    }
}

/**
 * 자녀가 부모의 6자리 페어링 코드를 입력해 계정을 연결한다.
 *
 * 예전에는 부모 앱만 코드를 **발급**하고 자녀 앱에는 그 코드를 **입력할 화면이 없었다**.
 * 그래서 회원가입 때 부모 회원번호를 넣지 않은 자녀는 사후에 연결할 방법이 아예 없었고,
 * 연결이 안 되니 부모의 캡처·촬영 요청이 자녀 기기로 갈 대상 자체가 없었다.
 */
class ChildPairingViewModel(
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildPairingUiState())
    val state: StateFlow<ChildPairingUiState> = _state.asStateFlow()

    // 입력을 손대지 않고 그대로 담는다. 숫자·자릿수 제약은 입력단이 아니라 서버가 판단한다.
    fun onCode(value: String) = _state.update { it.copy(code = value, error = null) }

    fun connect() {
        val code = _state.value.code
        if (code.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { pairingRepository.connect(code) }
                .onSuccess { _state.update { it.copy(loading = false, linked = true) } }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.userMessage("연결에 실패했어요. 코드를 다시 확인해 주세요.")) }
                }
        }
    }

    fun reset() = _state.update { ChildPairingUiState() }
}

/**
 * 부모님 연결 화면.
 *
 * <p>코드는 자유 입력칸으로 받는다. 칸 여섯 개짜리 PIN 입력은 숫자 6자리만 담을 수 있어,
 * 그 틀을 벗어나는 값을 넣어 볼 수 없다. 서버가 이 값을 어떻게 다루는지 시험하려면 입력단이
 * 미리 걸러 내지 않아야 한다.
 *
 * <p>연결에 성공하면 🎉 대신 초록 체크를 둔다. 이모지는 기기 폰트마다 다르게 그려져, 정작
 * 「됐다」는 신호가 기기마다 다른 얼굴로 나온다.
 */
@Composable
fun ChildPairingScreen(
    onBack: () -> Unit = {},
    viewModel: ChildPairingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    if (state.linked) {
        ChildSubScaffold(
            title = stringResource(R.string.child_pairing_title),
            onBack = onBack,
            bottomBar = {
                KidButton(
                    text = stringResource(R.string.child_pairing_done_back),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            KidSectionCard(contentPadding = PaddingValues(vertical = 28.dp, horizontal = 20.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.safe),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.child_pairing_done_title),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 18.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.child_pairing_done_desc),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    ChildSubScaffold(
        title = stringResource(R.string.child_pairing_title),
        onBack = onBack,
        bottomBar = {
            KidButton(
                text = stringResource(
                    if (state.loading) R.string.child_pairing_connecting else R.string.child_pairing_connect,
                ),
                onClick = viewModel::connect,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
            )
        },
    ) {
        KidSectionCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                stringResource(R.string.child_pairing_desc),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            KidTextField(
                value = state.code,
                onValueChange = viewModel::onCode,
                label = stringResource(R.string.child_pairing_code_label),
                placeholder = stringResource(R.string.child_pairing_code_placeholder),
                isError = state.error != null,
                supportingText = state.error,
            )
        }

        KidLabeledSection(
            label = stringResource(R.string.child_pairing_guide_label),
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(R.drawable.ic_people),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.brandDark,
                )
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        stringResource(R.string.child_pairing_guide_title),
                        color = colors.text,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(R.string.child_pairing_guide_desc),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Text(
            stringResource(R.string.child_pairing_hint),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}
