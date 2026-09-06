package com.hack.kidhack.feature.auth.register

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidDateField
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.common.PhoneNumberVisualTransformation
import com.hack.kidhack.core.designsystem.component.StepProgressBar
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.feature.auth.AuthScaffold
import com.hack.kidhack.feature.child.onboarding.ChildOnboardingViewModel
import com.hack.kidhack.feature.child.onboarding.ChildPermissionWizard
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RegisterScreen(
    onRegistered: (Session) -> Unit,
    onLogin: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val role = state.role
    val permissionVm: ChildOnboardingViewModel = koinViewModel { parametersOf(false) }

    val onPermissionsStep = role == Role.CHILD && state.step == 4 && state.session == null
    val onDoneStep = state.session != null && state.step == state.doneStep

    LaunchedEffect(state.error, state.session, state.step) {
        if (onPermissionsStep && state.error != null && !state.loading) {
            permissionVm.resetFinished()
        }
    }

    if (onPermissionsStep) {
        Box(modifier = Modifier.fillMaxSize()) {
            ChildPermissionWizard(
                onFinished = { viewModel.submit() },
                titleRes = R.string.child_register_permissions_title,
                subtitleRes = R.string.child_register_permissions_subtitle,
                continueRes = R.string.child_register_permissions_continue,
                persistDone = false,
                viewModel = permissionVm,
            )
            if (state.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (state.error != null) {
                Text(
                    text = state.error.orEmpty(),
                    color = colors.danger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
        return
    }

    AuthScaffold(
        header = {
            // 역할을 고르기 전에는 몇 단계인지 알 수 없다(부모 3, 자녀 5). 칸 수가 도중에
            // 바뀌면 진행이 되감긴 것처럼 보이므로, 역할이 정해진 뒤의 칸 수를 쓴다.
            val labels = stepLabels(role)
            val current = state.step.coerceIn(0, labels.lastIndex)
            StepProgressBar(totalSteps = labels.size, currentStep = current)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${current + 1} / ${labels.size} · ${labels[current]}",
                color = colors.brandDark,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = headerTitle(role, state.step, onDoneStep),
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 23.sp,
            )
            Spacer(Modifier.height(10.dp))
        },
    ) {
        when {
            state.step == 0 -> RoleStep(
                onParent = { viewModel.selectRole(Role.PARENT) },
                onChild = { viewModel.selectRole(Role.CHILD) },
                onLogin = onLogin,
            )
            state.step == 1 && role != null -> InfoStep(
                badge = if (role == Role.PARENT) "🛡️ 부모님으로 가입" else "😊 자녀로 가입",
                nicknameLabel = "닉네임",
                nicknamePlaceholder = if (role == Role.PARENT) "홍길동" else "나의 이름",
                emailRequired = role != Role.CHILD,
                state = state,
                viewModel = viewModel,
            )
            state.step == 2 && role == Role.CHILD -> PairingStep(state = state, viewModel = viewModel)
            state.step == 2 -> TermsStep(state = state, viewModel = viewModel)
            state.step == 3 && role == Role.CHILD -> TermsStep(state = state, viewModel = viewModel)
            onDoneStep -> DoneStep(
                session = state.session!!,
                loading = state.loading,
                onStart = { viewModel.startApp(onRegistered) },
            )
        }
        // 에러는 각 단계의 버튼 바로 위(StepError)에서 보여 준다.
        // 예전에는 여기, 즉 버튼보다 아래에 그려서 폼이 길면 화면 밖으로 밀려 보이지 않았다.
    }
}

@Composable
private fun RoleStep(onParent: () -> Unit, onChild: () -> Unit, onLogin: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text("역할에 따라 쓸 수 있는 기능이 달라요.", color = colors.muted, fontSize = 13.sp)
    RoleCard("부모", "부모님", "자녀 위치와 사용 시간을 보고\n앱·사이트를 관리해요", onParent)
    RoleCard("자녀", "자녀", "부모님과 채팅하고\n내 보호 서비스를 확인해요", onChild)
    Spacer(Modifier.height(8.dp))
    Text(
        "이미 계정이 있으신가요? 로그인",
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLogin).padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
        color = colors.muted,
        fontSize = 13.sp,
    )
}

/**
 * 진행 바 칸마다 붙는 이름.
 *
 * <p>두 역할 모두 **역할 선택을 첫 칸으로 센다.** 자녀 쪽에서 그 칸을 빼면 역할을 고른
 * 순간 「1 / 3 · 역할 선택」이 「1 / 5 · 정보 입력」으로 바뀌어, 한 걸음 나아갔는데 번호가
 * 1로 되돌아간 것처럼 보인다.
 */
private fun stepLabels(role: Role?): List<String> = when (role) {
    Role.CHILD -> listOf("역할 선택", "정보 입력", "부모님 연결", "약관 동의", "권한 허용")
    else -> listOf("역할 선택", "정보 입력", "약관 동의")
}

/** 화면 제목. 단계마다 지금 할 일을 그대로 말한다. */
private fun headerTitle(role: Role?, step: Int, done: Boolean): String = when {
    done -> "가입이 끝났어요"
    role == null -> "어떤 분이세요?"
    step == 1 && role == Role.PARENT -> "부모님 계정을 만들어요"
    step == 1 -> "자녀 계정을 만들어요"
    step == 2 && role == Role.CHILD -> "부모님과 연결해요"
    else -> "약관에 동의해 주세요"
}

@Composable
private fun RoleCard(badge: String, title: String, desc: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 이모지 대신 글자 표식. 이모지는 기기마다 크기·여백이 달라 카드 높이가 흔들린다.
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
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
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 15.sp, color = colors.text)
            Spacer(Modifier.height(3.dp))
            Text(desc, color = colors.muted, fontSize = 11.5.sp, lineHeight = 16.sp)
        }
        Text("›", color = colors.muted, fontSize = 19.sp)
    }
}

@Composable
private fun InfoStep(
    badge: String,
    nicknameLabel: String,
    nicknamePlaceholder: String,
    emailRequired: Boolean,
    state: RegisterUiState,
    viewModel: RegisterViewModel,
) {
    val colors = LocalKidhackColors.current
    KidTextField(state.loginId, viewModel::onLoginId, "아이디", placeholder = "로그인에 사용할 아이디")
    KidTextField(state.realName, viewModel::onRealName, "이름", placeholder = "이름을 입력하세요")
    KidTextField(
        state.password,
        viewModel::onPassword,
        "비밀번호",
        placeholder = "8자 이상",
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    KidTextField(
        state.passwordConfirm,
        viewModel::onPasswordConfirm,
        "비밀번호 확인",
        placeholder = "비밀번호를 다시 입력하세요",
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    KidTextField(state.nickname, viewModel::onNickname, nicknameLabel, placeholder = nicknamePlaceholder)
    KidTextField(
        state.phoneNumber,
        viewModel::onPhoneNumber,
        "휴대폰 번호",
        placeholder = "010-0000-0000",
        visualTransformation = PhoneNumberVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    KidTextField(
        state.email,
        viewModel::onEmail,
        if (emailRequired) "이메일" else "이메일 (선택)",
        placeholder = "example@email.com",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    KidDateField(
        value = state.birthDate,
        onValueChange = viewModel::onBirthDate,
        label = "생년월일",
        placeholder = "달력에서 선택하세요",
    )
    KidTextField(state.address, viewModel::onAddress, "주소 (선택)", placeholder = "서울시 강남구")
    // 부모 연결은 가입이 아니라 로그인 후 페어링 코드로 한다(POST /pairing/connect).
    // 예전에는 여기서 「부모님 회원 번호」를 필수로 받았는데, 그 값은 부모 계정의 DB id 라
    // 자녀가 알 방법이 없었다. 서버도 signup 의 parentId 는 선택으로 두고 검증하지 않는다.
    StepError(state.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidButton("← 이전", viewModel::back, Modifier.weight(1f), KidButtonVariant.Ghost)
        // 중복 확인이 오가는 동안 잠근다. 두 번 누르면 요청도 두 번 나간다.
        KidButton(
            text = if (state.checkingDuplicate) "확인 중…" else "다음 →",
            onClick = viewModel::nextFromInfo,
            modifier = Modifier.weight(1f),
            enabled = !state.checkingDuplicate,
        )
    }
}

@Composable
private fun TermsStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    val colors = LocalKidhackColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("약관 동의", fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp, color = colors.text)
        Text("가입을 완료하려면 약관에 동의해 주세요", color = colors.muted, fontSize = 14.sp)
    }
    KidCard {
        TermsCheckRow(
            checked = state.agreedAll,
            label = "모든 약관에 동의합니다",
            emphasized = true,
            onCheckedChange = viewModel::onAgreedAll,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.border)
        TermsCheckRow(
            checked = state.agreedAge,
            label = "[필수] 만 14세 이상입니다.",
            onCheckedChange = viewModel::onAgreedAge,
        )
        TermsCheckRow(
            checked = state.agreedTerms,
            label = "[필수] 서비스 이용약관 동의",
            onCheckedChange = viewModel::onAgreedTerms,
        )
        TermsCheckRow(
            checked = state.agreedPrivacy,
            label = "[필수] 개인정보 수집 및 이용 동의",
            onCheckedChange = viewModel::onAgreedPrivacy,
        )
        TermsCheckRow(
            checked = state.agreedLocation,
            label = "[필수] 위치기반 서비스 이용약관 동의",
            onCheckedChange = viewModel::onAgreedLocation,
        )
        TermsCheckRow(
            checked = state.agreedPush,
            label = "[필수] PUSH 알림 수신 동의",
            onCheckedChange = viewModel::onAgreedPush,
        )
        TermsCheckRow(
            checked = state.agreedMarketing,
            label = "[선택] 마케팅 활용 동의",
            onCheckedChange = viewModel::onAgreedMarketing,
        )
    }
    val isChild = state.role == Role.CHILD
    StepError(state.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidButton("← 이전", viewModel::back, Modifier.weight(1f), KidButtonVariant.Ghost)
        KidButton(
            text = when {
                state.loading -> "처리 중..."
                isChild -> "다음 →"
                else -> "가입하기"
            },
            onClick = viewModel::nextFromTerms,
            modifier = Modifier.weight(1f),
            enabled = !state.loading,
        )
    }
}

@Composable
private fun TermsCheckRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    emphasized: Boolean = false,
) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.brand),
        )
        Text(
            text = label,
            color = if (emphasized) colors.text else colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = if (emphasized) FontBold else FontSemibold,
            fontSize = if (emphasized) 15.sp else 12.sp,
        )
    }
}

/**
 * 자녀 가입 2단계 — 부모 앱의 6자리 페어링 코드 입력.
 *
 * 코드는 선택이다(부모가 아직 발급 안 했을 수 있음). 「나중에 연결」로 건너뛰면 가입 후
 * 마이 → 「부모님 연결」에서 넣을 수 있다. 실제 연결은 가입·로그인 뒤 자동으로 이뤄진다
 * (`POST /pairing/connect` 는 로그인 상태라야 childId 로 인증된다).
 */
@Composable
private fun PairingStep(state: RegisterUiState, viewModel: RegisterViewModel) {
    val colors = LocalKidhackColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("부모님과 연결", fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp, color = colors.text)
        Text("부모님 앱에 표시된 6자리 코드를 입력해요", color = colors.muted, fontSize = 14.sp)
    }
    KidCard {
        Text("👪  부모님 앱에서 코드 받기", color = colors.brandDark, fontWeight = FontBold, fontSize = 14.sp)
        Text(
            "부모님 앱: 마이 → 「자녀 페어링」 → 「설치 완료」를 누르면 6자리 코드가 떠요. 코드는 5분 동안만 쓸 수 있어요.",
            color = colors.muted,
            fontSize = 12.sp,
        )
    }
    KidTextField(
        state.pairingCode,
        viewModel::onPairingCode,
        "연결 코드",
        placeholder = "6자리 숫자",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    StepError(state.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidButton("← 이전", viewModel::back, Modifier.weight(1f), KidButtonVariant.Ghost)
        KidButton("다음 →", viewModel::nextFromPairing, Modifier.weight(1f))
    }
    Text(
        text = "나중에 연결할게요",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = viewModel::skipPairing)
            .padding(vertical = 8.dp),
        color = colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 13.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun DoneStep(session: Session, loading: Boolean, onStart: () -> Unit) {
    val colors = LocalKidhackColors.current
    val parent = session.user.role == Role.PARENT
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(colors.brand, colors.brandDark))),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (parent) "🛡️" else "😊", fontSize = 40.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("가입 완료! 🎉", fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp)
        Text(
            if (parent) "루키즈 부모님 앱을 시작할게요" else "루키즈 자녀 앱을 시작할게요",
            color = colors.muted,
            fontSize = 14.sp,
        )
    }
    KidCard {
        Text(if (parent) "부모님 계정" else "자녀 계정", fontWeight = FontBold)
        Text("아이디  ${session.user.loginId}", color = colors.muted, fontSize = 12.sp)
        if (session.user.name.isNotBlank()) {
            Text("이름  ${session.user.name}", color = colors.muted, fontSize = 12.sp)
        }
    }
    KidButton(
        text = when {
            loading -> "로그인 중..."
            parent -> "부모님 앱 시작하기 →"
            else -> "자녀 앱 시작하기 →"
        },
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading,
    )
}

/** 단계별 에러 문구. 버튼 바로 위에 두어 스크롤 없이 보이게 한다. */
@Composable
private fun StepError(error: String?) {
    if (error == null) return
    Text(error, color = LocalKidhackColors.current.danger, fontSize = 13.sp)
}
