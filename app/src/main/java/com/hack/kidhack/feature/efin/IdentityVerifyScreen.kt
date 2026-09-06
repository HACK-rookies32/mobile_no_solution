package com.hack.kidhack.feature.efin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * 접근매체(카드 등) 발급 전 실명확인 화면 — #59.
 *
 * 이름·생년월일·신분증 종류·번호·발급일자를 넣어 신분증 진위확인(mock)을 받는다. 결함은 서버측이다
 * ([VULN-EFIN-7] 실제 대조 없이 형식·자기일치만 확인 + 결과 응답 노출).
 */
data class IdentityUiState(
    val name: String = "",
    val birthDate: String = "",
    val idType: String = "RESIDENT",
    val idNumber: String = "",
    val issuedDate: String = "",
    val working: Boolean = false,
    val error: String? = null,
    val verified: Boolean? = null,
)

class IdentityVerifyViewModel(
    private val efinRepository: EfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IdentityUiState())
    val state: StateFlow<IdentityUiState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onBirth(v: String) = _state.update { it.copy(birthDate = v.filter { c -> c.isDigit() }.take(6)) }
    fun onIdType(v: String) = _state.update { it.copy(idType = v, verified = null) }
    fun onIdNumber(v: String) = _state.update { it.copy(idNumber = v) }
    fun onIssuedDate(v: String) = _state.update { it.copy(issuedDate = v) }

    fun submit() {
        val s = _state.value
        if (s.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null, verified = null) }
            runCatching {
                efinRepository.verifyIdentity(s.name, s.birthDate, s.idType, s.idNumber, s.issuedDate)
            }.onSuccess { r ->
                _state.update { it.copy(working = false, verified = r.verified) }
            }.onFailure { t ->
                _state.update { it.copy(working = false, error = t.userMessage()) }
            }
        }
    }

    fun ready(): Boolean = _state.value.let {
        it.name.isNotBlank() && it.birthDate.length == 6 && it.idNumber.isNotBlank() && it.issuedDate.isNotBlank()
    }
}

@Composable
fun IdentityVerifyScreen(
    onBack: () -> Unit = {},
    viewModel: IdentityVerifyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        EfinHeader(title = "실명확인", onBack = onBack)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldLabel("이름")
            EfinTextField(state.name, viewModel::onName, "홍길동", KeyboardType.Text)

            FieldLabel("생년월일 6자리")
            EfinTextField(state.birthDate, viewModel::onBirth, "900101", KeyboardType.Number)

            FieldLabel("신분증 종류")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdTypeChip("주민등록증", state.idType == "RESIDENT", Modifier.weight(1f)) { viewModel.onIdType("RESIDENT") }
                IdTypeChip("운전면허", state.idType == "DRIVER", Modifier.weight(1f)) { viewModel.onIdType("DRIVER") }
            }

            FieldLabel("신분증 번호")
            EfinTextField(state.idNumber, viewModel::onIdNumber, "900101-1234567", KeyboardType.Number)

            FieldLabel("발급일자")
            EfinTextField(state.issuedDate, viewModel::onIssuedDate, "2015-03-20", KeyboardType.Number)

            Spacer(Modifier.height(2.dp))
            EfinPrimaryButton(text = "실명확인", enabled = !state.working && viewModel.ready(), onClick = viewModel::submit)

            when (state.verified) {
                true -> EfinBanner(error = null, done = "실명확인이 완료되었습니다.")
                false -> EfinBanner(error = "실명확인에 실패했습니다. 입력 정보를 확인하세요.", done = null)
                null -> EfinBanner(error = state.error, done = null)
            }
        }
    }
}

@Composable
private fun IdTypeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.brandLight else colors.surface)
            .border(1.dp, if (selected) colors.brand else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) colors.brandDark else colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun EfinTextField(value: String, onChange: (String) -> Unit, placeholder: String, keyboard: KeyboardType) {
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
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            singleLine = true,
            cursorBrush = SolidColor(colors.brand),
        )
    }
}