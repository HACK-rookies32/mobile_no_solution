package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.data.remote.userMessage
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.common.formatPhoneNumber
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.component.ListRow
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.core.common.RemoteUrl

enum class PasswordError { EMPTY, MISMATCH, NO_CODE }

data class MypageUiState(
    val user: User? = null,
    val children: List<ChildProfile> = emptyList(),
    val selectedChildId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val changingPassword: Boolean = false,
    /** 비밀번호 변경은 서버 `reset-password` 2단계(인증코드 → 재설정)를 그대로 쓴다(`PATCH users/me/password` 없음). */
    val passwordCodeSent: Boolean = false,
    val passwordCode: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordError: PasswordError? = null,
    val showPasswordConfirm: Boolean = false,
    val passwordChanged: Boolean = false,
    val passwordInfo: String? = null,
)

class MypageViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MypageUiState())
    val state: StateFlow<MypageUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            selectedChildRepository.selectedChildId.collect { id ->
                _state.update { it.copy(selectedChildId = id) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val user = userRepository.me()
                val children = childRepository.children()
                val selectedId = selectedChildRepository.resolveId(children)
                Triple(user, children, selectedId)
            }.onSuccess { (user, children, selectedId) ->
                _state.update {
                    it.copy(
                        loading = false,
                        user = user,
                        children = children,
                        selectedChildId = selectedId,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun selectChild(id: String) {
        viewModelScope.launch { selectedChildRepository.select(id) }
    }

    fun togglePasswordChange() = _state.update {
        val opening = !it.changingPassword
        it.copy(
            changingPassword = opening,
            passwordCodeSent = false,
            passwordCode = "",
            newPassword = "",
            confirmPassword = "",
            passwordError = null,
            passwordChanged = false,
            passwordInfo = null,
        )
    }

    fun onNewPassword(v: String) = _state.update { it.copy(newPassword = v, passwordError = null) }
    fun onConfirmPassword(v: String) = _state.update { it.copy(confirmPassword = v, passwordError = null) }
    fun onPasswordCode(v: String) = _state.update { it.copy(passwordCode = v.filter { c -> c.isDigit() }, passwordError = null) }

    /**
     * 1단계 — 내 아이디·이메일로 인증코드 발송(`POST auth/reset-password/send-code`).
     * 서버에 `PATCH users/me/password` 가 없어 비밀번호 재설정 흐름을 그대로 쓴다.
     * 서버가 코드를 응답에 실어 주면(`[VULN-AUTH-MAIL]`) 입력칸에 미리 채운다.
     */
    fun sendPasswordCode() {
        val user = _state.value.user ?: return
        val email = user.email
        if (email.isNullOrBlank()) {
            _state.update { it.copy(error = "가입 이메일이 없어 비밀번호를 바꿀 수 없어요. 프로필에서 이메일을 먼저 등록해 주세요.") }
            return
        }
        viewModelScope.launch {
            runCatching { authRepository.sendResetPasswordCode(user.loginId, email) }
                .onSuccess { sent ->
                    _state.update {
                        it.copy(
                            passwordCodeSent = true,
                            passwordCode = sent.code.orEmpty(),
                            passwordInfo = sent.message?.takeIf { m -> m.isNotBlank() } ?: "인증코드를 $email 로 보냈어요",
                            error = null,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = t.userMessage("인증코드를 보내지 못했어요")) } }
        }
    }

    fun requestPasswordChange() {
        val s = _state.value
        val error = when {
            !s.passwordCodeSent || s.passwordCode.isBlank() -> PasswordError.NO_CODE
            s.newPassword.isBlank() -> PasswordError.EMPTY
            s.newPassword != s.confirmPassword -> PasswordError.MISMATCH
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(passwordError = error) }
        } else {
            _state.update { it.copy(showPasswordConfirm = true) }
        }
    }

    fun dismissPasswordConfirm() = _state.update { it.copy(showPasswordConfirm = false) }

    /** 2단계 — 코드 + 새 비밀번호로 재설정(`POST auth/reset-password`). */
    fun confirmPasswordChange() {
        val s = _state.value
        val email = s.user?.email ?: return
        viewModelScope.launch {
            runCatching { authRepository.resetPassword(email, s.passwordCode.trim(), s.newPassword) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            showPasswordConfirm = false,
                            changingPassword = false,
                            passwordCodeSent = false,
                            passwordCode = "",
                            newPassword = "",
                            confirmPassword = "",
                            passwordError = null,
                            passwordChanged = true,
                            passwordInfo = null,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(showPasswordConfirm = false, error = t.userMessage("비밀번호를 바꾸지 못했어요")) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { runCatching { authRepository.logout() } }
    }
}

/**
 * 부모 마이페이지.
 *
 * <p>주황 그라데이션 헤더를 걷어내고 자녀관리 화면과 같은 연주황→흰색 바탕을 쓴다(#43 규칙).
 * 인사말 바로 아래에 지갑 잔액을 두어, 충전·송금은 화면을 옮기지 않고 미니 시트에서 끝내고
 * 나머지 지갑 기능은 잔액 칸을 눌러 [ParentWalletScreen] 으로 들어간다.
 */
@Composable
fun ParentMypageScreen(
    onOpenAccountSettings: () -> Unit = {},
    onOpenPairing: () -> Unit = {},
    onOpenContact: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
    onOpenCards: () -> Unit = {},
    onOpenTerms: () -> Unit = {},
    viewModel: MypageViewModel = koinViewModel(),
    walletViewModel: WalletViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 잔액 칸과 미니 시트는 지갑 화면과 같은 뷰모델을 쓴다. 두 화면이 같은 일을 두 번 적지
    // 않게 하려는 것이고, 각자 제 화면에 매인 인스턴스라 서로의 상태를 건드리지 않는다.
    val walletState by walletViewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val user = state.user
    val childCount = state.children.size.takeIf { it > 0 }?.let { "${'$'}it명" }.orEmpty()

    // 지갑 화면에서 충전·송금하고 돌아오면 잔액이 달라져 있다.
    LifecycleResumeRefresh { walletViewModel.load() }
    // 내 정보 수정에서 사진·닉네임을 바꾸고 돌아오면 즉시 반영되도록 프로필도 다시 읽는다.
    LifecycleResumeRefresh { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
      Column(Modifier.fillMaxWidth().background(colors.card)) {
        // 화면 이름을 먼저 둔다. 자녀관리·사용 리포트·커뮤니티가 모두 이 자리에 제목을
        // 갖고 있어서, 마이페이지만 없으면 탭을 옮길 때 한 칸이 비어 보인다. 이름표가
        // 위를 받쳐 주면 프로필도 상태바에 붙지 않는다.
        // 제목 줄 높이를 40dp 로 맞춘다. 자녀관리는 제목 옆에 40dp 종 버튼이 있어 줄이
        // 그만큼 높고, 21sp 글씨가 그 안에서 가운데로 내려앉는다. 버튼이 없는 화면은 줄이
        // 글씨 높이만큼만이라 제목이 상태바에 바짝 붙어 보였다 — 같은 높이를 직접 준다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.parent_mypage_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 21.sp,
                color = colors.text,
            )
        }
        KidHeaderDivider()
      }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            // 프로필 줄 전체가 계정 설정으로 가는 문이다. 톱니만 눌리게 두면 손가락이
            // 19dp 짜리 과녁을 맞혀야 한다 — 톱니는 「여기를 누르면 설정」이라는 표시로만 둔다.
            KidSectionCard {
              Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAccountSettings)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(colors.brandLight),
                    contentAlignment = Alignment.Center,
                ) {
                    val photo = user?.profileImageUrl
                    if (photo.isNullOrBlank()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = colors.brandDark,
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(RemoteUrl.absolute(photo)).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.parent_mypage_greeting, user?.name ?: ""),
                        color = colors.text,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        user?.email ?: user?.loginId.orEmpty(),
                        color = colors.muted,
                        fontSize = 12.5.sp,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_gear),
                    contentDescription = stringResource(R.string.parent_account_settings),
                    modifier = Modifier.size(19.dp),
                    tint = colors.muted,
                )
              }
            }
            Spacer(Modifier.height(12.dp))

            WalletBalanceCard(
                wallet = walletState.wallet,
                onCharge = { walletViewModel.openSheet(WalletSheet.Charge) },
                onSend = { walletViewModel.openSheet(WalletSheet.Send) },
                sendEnabled = walletState.children.isNotEmpty(),
                onDetail = onOpenWallet,
            )
            WalletBanner(error = walletState.error, messageRes = walletState.messageRes)

            ParentLoadingOrError(state.loading && user == null, state.error, viewModel::load)

            // 묶음 이름은 카드 밖 작은 회색 글씨로. 계정 설정·알림 설정·자녀관리가 모두
            // 이 모양이라, 카드 안에 이름이 들어가 있던 여기만 층이 하나 더 깊어 보였다.
            // 줄마다 아이콘은 달지 않는다 — 이름만으로 뜻이 분명해 눈만 피로해진다.
            GroupLabel(stringResource(R.string.parent_mypage_service))
            KidSectionCard {
                ListRow(stringResource(R.string.parent_mypage_pairing), trailing = childCount, onClick = onOpenPairing)
                ListRow(stringResource(R.string.parent_mypage_cards), onClick = onOpenCards, showDivider = false)
            }
            // 커뮤니티·공지사항·활동 내역은 하단 탭 「커뮤니티」로 옮겼다. 여기 목록에 두면
            // 글 하나 읽는 데 마이페이지를 거쳐야 한다.
            GroupLabel(stringResource(R.string.parent_mypage_other))
            KidSectionCard {
                ListRow(stringResource(R.string.parent_mypage_contact), onClick = onOpenContact)
                ListRow(stringResource(R.string.parent_mypage_terms), onClick = onOpenTerms)
                ListRow(stringResource(R.string.parent_mypage_version), trailing = "", onClick = null, showDivider = false)
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    // 충전·송금 미니 시트. 지갑 화면과 같은 것을 쓴다.
    // 거래 승인(계좌비번+OTP) 다이얼로그는 앱 루트(TxAuthHost)에 하나만 있어 여기서 띄우지 않는다.
    WalletSheetHost(state = walletState, viewModel = walletViewModel, onRegisterCard = onOpenCards)
}

@Composable
private fun FormHeader(title: String, onClose: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            color = colors.text,
        )
        Text(
            "✕",
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = colors.muted,
            fontSize = 16.sp,
            fontWeight = FontBold,
        )
    }
    Spacer(Modifier.height(8.dp))
}

/** 묶음 위의 작은 회색 이름. 계정 설정·알림 설정과 같은 모양이다. */
@Composable
private fun GroupLabel(title: String) {
    val colors = LocalKidhackColors.current
    Text(
        text = title,
        modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp),
        color = colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

/** 테두리만 두른 흰 카드. 안쪽 줄이 제 여백을 갖고 구분선은 카드 폭을 다 쓴다. */
@Composable
private fun OutlinedGroup(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.border.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        content = content,
    )
}

/**
 * 비밀번호 변경 폼.
 *
 * <p>서버에 「현재 비밀번호로 바꾸기」 API 가 없어 가입 이메일 인증코드를 받아 재설정하는
 * 2단계(`/auth/reset-password/send-code` → `/auth/reset-password`)를 그대로 쓴다.
 *
 * <p>계정 설정 화면이 부른다. 마이페이지에 있던 것을 옮겼다 — 비밀번호는 내 계정을
 * 손보는 일이라 서비스 목록과 같은 자리에 있을 이유가 없다.
 */
@Composable
internal fun ColumnScope.PasswordChangeSection(
    state: MypageUiState,
    viewModel: MypageViewModel,
) {
    val colors = LocalKidhackColors.current
    val user = state.user
        if (state.changingPassword) {
            KidCard {
                FormHeader(stringResource(R.string.parent_mypage_change_password), viewModel::togglePasswordChange)
                Text(
                    "가입 이메일(${user?.email ?: "없음"})로 인증코드를 받은 뒤 새 비밀번호를 설정해요",
                    color = colors.muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (!state.passwordCodeSent) {
                    KidButton("인증코드 받기", viewModel::sendPasswordCode, Modifier.fillMaxWidth(), KidButtonVariant.Secondary)
                } else {
                    KidTextField(
                        state.passwordCode,
                        viewModel::onPasswordCode,
                        "인증 코드",
                        placeholder = "6자리",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.passwordError == PasswordError.NO_CODE,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                state.passwordInfo?.let { Text(it, color = colors.safe, fontSize = 12.sp) }
                KidTextField(
                    state.newPassword,
                    viewModel::onNewPassword,
                    stringResource(R.string.parent_mypage_new_password),
                    placeholder = stringResource(R.string.parent_mypage_password_placeholder),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = state.passwordError == PasswordError.EMPTY,
                )
                Spacer(Modifier.height(8.dp))
                KidTextField(
                    state.confirmPassword,
                    viewModel::onConfirmPassword,
                    stringResource(R.string.parent_mypage_new_password_confirm),
                    placeholder = stringResource(R.string.parent_mypage_password_confirm_placeholder),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = state.passwordError == PasswordError.MISMATCH,
                )
                state.passwordError?.let { err ->
                    val msg = when (err) {
                        PasswordError.EMPTY -> stringResource(R.string.parent_mypage_password_error_empty)
                        PasswordError.MISMATCH -> stringResource(R.string.parent_mypage_password_error_mismatch)
                        PasswordError.NO_CODE -> "먼저 인증코드를 받아 주세요"
                    }
                    Text(msg, color = colors.danger, fontSize = 12.sp, fontWeight = FontBold)
                }
                Spacer(Modifier.height(8.dp))
                KidButton(
                    stringResource(R.string.parent_mypage_password_change),
                    viewModel::requestPasswordChange,
                    Modifier.fillMaxWidth(),
                    enabled = state.passwordCodeSent,
                )
            }
        }
        if (state.passwordChanged) {
            Text(stringResource(R.string.parent_mypage_password_changed), color = colors.safe, fontSize = 12.sp, fontWeight = FontBold)
        }
        if (state.showPasswordConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPasswordConfirm,
                title = { Text(stringResource(R.string.parent_mypage_password_confirm_title), fontWeight = FontBold) },
                text = { Text(stringResource(R.string.parent_mypage_password_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmPasswordChange) {
                        Text(stringResource(R.string.parent_mypage_password_confirm_ok), color = colors.brand, fontWeight = FontBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPasswordConfirm) {
                        Text(stringResource(R.string.parent_mypage_password_confirm_cancel), color = colors.muted)
                    }
                },
            )
        }
}