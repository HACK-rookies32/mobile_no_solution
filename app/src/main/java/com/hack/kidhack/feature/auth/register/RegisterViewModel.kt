package com.hack.kidhack.feature.auth.register

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.core.common.digitsOnlyPhone
import com.hack.kidhack.core.common.formatPhoneNumber
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.domain.repository.PairingRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val step: Int = 0,
    val role: Role? = null,
    val nickname: String = "",
    val loginId: String = "",
    val realName: String = "",
    val phoneNumber: String = "",
    /** 휴대폰 인증: 사용자가 입력한 6자리. */
    val phoneCodeInput: String = "",
    /** 휴대폰 인증 통과 여부(클라 전용 검증 결과). 가입 요청 body 로 그대로 나간다. */
    val phoneVerified: Boolean = false,
    /**
     * [VULN-MOBILE-PHONEVERIFY-1] 서버 send-code 응답이 실어 준 코드. 화면에는 절대 표시하지 않고
     * 사용자 입력과 대조하는 용도로만 메모리에 보관한다(패킷으로만 확인 가능).
     */
    val sentCode: String? = null,
    /** 발급 코드 만료 시각(epoch millis). 0 이면 미발급. */
    val phoneCodeExpiresAt: Long = 0L,
    /** 인증요청 진행 중(중복 요청 잠금). */
    val phoneCodeSending: Boolean = false,
    /** 「확인」(서버 검증) 진행 중. */
    val phoneVerifying: Boolean = false,
    /** 「확인」 결과 안내(불일치 등). 성공 시 null 이고 phoneVerified=true 로 표시한다. */
    val phoneVerifyMessage: String? = null,
    val email: String = "",
    val birthDate: String = "",
    val address: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val parentId: String = "",
    /** 아이디·이메일 중복을 확인하는 중. 「다음」이 두 번 눌리지 않게 잠근다. */
    val checkingDuplicate: Boolean = false,
    /** 자녀 가입 2단계 — 부모 앱의 6자리 페어링 코드. 계정 생성·로그인 후 connect 에 쓴다. 비우면 건너뜀. */
    val pairingCode: String = "",
    val agreedAge: Boolean = false,
    val agreedTerms: Boolean = false,
    val agreedPrivacy: Boolean = false,
    val agreedLocation: Boolean = false,
    val agreedPush: Boolean = false,
    val agreedMarketing: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val session: Session? = null,
) {
    val agreedAll: Boolean
        get() = agreedAge && agreedTerms && agreedPrivacy && agreedLocation && agreedPush && agreedMarketing

    val requiredAgreed: Boolean
        get() = agreedAge && agreedTerms && agreedPrivacy && agreedLocation && agreedPush

    val doneStep: Int
        get() = if (role == Role.CHILD) 5 else 3
}

/** 휴대폰 인증코드 유효시간(3분). */
private const val PHONE_CODE_TTL_MS = 180_000L

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val pairingRepository: PairingRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val onboardingStore: ChildOnboardingStore,
    private val platformController: ChildPlatformController,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun selectRole(role: Role) = _state.update { it.copy(role = role, step = 1, error = null) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), error = null) }
    fun onNickname(v: String) = _state.update { it.copy(nickname = v) }
    fun onLoginId(v: String) = _state.update { it.copy(loginId = v) }
    fun onRealName(v: String) = _state.update { it.copy(realName = v) }
    fun onPhoneNumber(v: String) = _state.update {
        // 번호를 바꾸면 이전 인증은 무효 — 코드·인증상태를 초기화한다.
        it.copy(
            phoneNumber = digitsOnlyPhone(v),
            phoneVerified = false,
            sentCode = null,
            phoneCodeInput = "",
            phoneCodeExpiresAt = 0L,
            phoneVerifyMessage = null,
        )
    }

    /**
     * 휴대폰 인증코드 발송 요청. 서버가 코드를 응답에 실어 주지만 화면에는 표시하지 않고
     * [RegisterUiState.sentCode] 에만 담는다. 유효시간 3분.
     *
     * 정상 사용자는 본인 번호로 온 코드를 (실서버라면 SMS 로) 받아 입력해야 하지만,
     * 이 랩에서는 코드가 응답 패킷에만 실린다([VULN-MOBILE-PHONEVERIFY-1]).
     */
    fun requestPhoneCode() {
        val s = _state.value
        if (s.phoneCodeSending) return
        if (s.phoneNumber.isBlank()) {
            _state.update { it.copy(error = "휴대폰 번호를 입력해 주세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(phoneCodeSending = true, error = null) }
            runCatching { authRepository.sendPhoneCode(formatPhoneNumber(s.phoneNumber)) }
                .onSuccess { code ->
                    _state.update {
                        it.copy(
                            phoneCodeSending = false,
                            sentCode = code,
                            phoneCodeInput = "",
                            phoneVerified = false,
                            phoneVerifyMessage = null,
                            phoneCodeExpiresAt = System.currentTimeMillis() + PHONE_CODE_TTL_MS,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(phoneCodeSending = false, error = t.userMessage("인증번호 발송에 실패했어요"))
                    }
                }
        }
    }

    /**
     * 인증코드 입력. 입력이 발급 코드와 일치하고 만료 전이면 [RegisterUiState.phoneVerified] 를 세운다.
     *
     * [VULN-MOBILE-PHONEVERIFY-1] 코드 일치 검증이 클라이언트에만 있다. 서버는 코드를 검증하지 않고
     * 가입 요청의 phoneVerified 불리언만 신뢰하므로, 패킷에서 그 값만 true 로 바꿔도 우회된다.
     */
    fun onPhoneCodeInput(v: String) = _state.update {
        // 입력이 바뀌면 이전 인증 결과는 무효 — 「확인」을 다시 눌러야 한다.
        it.copy(
            phoneCodeInput = v.filter { ch -> ch.isDigit() }.take(6),
            phoneVerified = false,
            phoneVerifyMessage = null,
            error = null,
        )
    }

    /**
     * 「확인」 — 입력한 6자리를 발급 코드와 대조한다. 일치·미만료면 phoneVerified=true("인증되었습니다"),
     * 아니면 안내 문구를 남긴다.
     *
     * [VULN-MOBILE-PHONEVERIFY-1] 이 대조가 클라이언트에만 있다. 서버는 코드를 검증하지 않고
     * 가입 요청의 phoneVerified 불리언만 신뢰하므로, 패킷에서 그 값만 true 로 바꿔도 우회된다.
     */
    fun confirmPhoneCode() {
        val s = _state.value
        if (s.phoneVerifying) return
        when {
            s.sentCode == null -> {
                _state.update { it.copy(phoneVerifyMessage = "먼저 인증요청을 눌러주세요") }
                return
            }
            s.phoneCodeInput.length < 6 -> {
                _state.update { it.copy(phoneVerifyMessage = "인증번호 6자리를 입력해주세요") }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(phoneVerifying = true, phoneVerifyMessage = null) }
            runCatching { authRepository.verifyPhoneCode(formatPhoneNumber(s.phoneNumber), s.phoneCodeInput) }
                .onSuccess { valid ->
                    _state.update {
                        if (valid) it.copy(phoneVerifying = false, phoneVerified = true, phoneVerifyMessage = null)
                        else it.copy(phoneVerifying = false, phoneVerified = false, phoneVerifyMessage = "인증번호가 올바르지 않아요")
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(phoneVerifying = false, phoneVerified = false, phoneVerifyMessage = t.userMessage("인증 확인에 실패했어요"))
                    }
                }
        }
    }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onBirthDate(v: String) = _state.update { it.copy(birthDate = v) }
    fun onAddress(v: String) = _state.update { it.copy(address = v) }
    fun onPassword(v: String) = _state.update { it.copy(password = v) }
    fun onPasswordConfirm(v: String) = _state.update { it.copy(passwordConfirm = v) }
    fun onParentId(v: String) = _state.update { it.copy(parentId = v.filter { ch -> ch.isDigit() }) }
    fun onPairingCode(v: String) = _state.update { it.copy(pairingCode = v.filter { ch -> ch.isDigit() }.take(6), error = null) }
    fun onAgreedAge(v: Boolean) = _state.update { it.copy(agreedAge = v) }
    fun onAgreedTerms(v: Boolean) = _state.update { it.copy(agreedTerms = v) }
    fun onAgreedPrivacy(v: Boolean) = _state.update { it.copy(agreedPrivacy = v) }
    fun onAgreedLocation(v: Boolean) = _state.update { it.copy(agreedLocation = v) }
    fun onAgreedPush(v: Boolean) = _state.update { it.copy(agreedPush = v) }
    fun onAgreedMarketing(v: Boolean) = _state.update { it.copy(agreedMarketing = v) }
    fun onAgreedAll(v: Boolean) = _state.update {
        it.copy(
            agreedAge = v,
            agreedTerms = v,
            agreedPrivacy = v,
            agreedLocation = v,
            agreedPush = v,
            agreedMarketing = v,
        )
    }

    fun nextFromInfo() {
        val s = _state.value
        val role = s.role ?: return
        val birthOk = s.birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))
        when {
            s.loginId.isBlank() -> _state.update { it.copy(error = "아이디를 입력해 주세요") }
            s.realName.isBlank() -> _state.update { it.copy(error = "이름을 입력해 주세요") }
            s.nickname.isBlank() -> _state.update { it.copy(error = "닉네임을 입력해 주세요") }
            s.phoneNumber.isBlank() -> _state.update { it.copy(error = "휴대폰 번호를 입력해 주세요") }
            // 정직한 사용자용 UX 게이트 — 인증 완료해야 다음으로. (서버 게이트는 signup 의 phoneVerified)
            !s.phoneVerified -> _state.update { it.copy(error = "휴대폰 인증을 완료해 주세요") }
            role != Role.CHILD && s.email.isBlank() ->
                _state.update { it.copy(error = "이메일을 입력해 주세요") }
            !birthOk -> _state.update { it.copy(error = "생년월일을 선택해 주세요") }
            // 자녀도 parentId 없이 가입한다. 부모 연결은 가입 후 마이 → 「부모님 연결」에서 6자리 페어링 코드로 한다.
            s.password.length < 8 -> _state.update { it.copy(error = "비밀번호는 8자 이상이어야 해요") }
            s.password != s.passwordConfirm -> _state.update { it.copy(error = "비밀번호가 일치하지 않아요") }
            else -> checkDuplicateThenNext(s)
        }
    }

    /**
     * 아이디·이메일이 이미 쓰이는지 확인한 뒤 다음 단계로 넘어간다.
     *
     * <p>예전에는 마지막 「가입 완료하기」에서야 409 를 받았다. 그때는 부모 연결·약관·권한을
     * 다 지나온 뒤라 첫 화면으로 되돌아가 고치고 같은 길을 다시 걸어야 했고, 오류 문구도
     * 정작 입력칸이 없는 권한 화면에 떴다.
     *
     * <p>확인에 실패하면(네트워크 등) <b>막지 않고 넘어간다.</b> 가입에서 한 번 더 걸러지므로
     * 잘못된 계정이 만들어지지는 않는다 — 여기서 막으면 서버가 잠깐 안 될 때 가입 자체가
     * 불가능해진다.
     */
    private fun checkDuplicateThenNext(s: RegisterUiState) {
        if (s.checkingDuplicate) return
        viewModelScope.launch {
            _state.update { it.copy(checkingDuplicate = true, error = null) }
            val result = runCatching {
                authRepository.checkAvailability(s.loginId.trim(), s.email.trim().ifBlank { null })
            }.getOrNull()

            val message = when {
                result?.loginIdAvailable == false -> "이미 사용 중인 아이디예요"
                result?.emailAvailable == false -> "이미 사용 중인 이메일이에요"
                else -> null
            }
            _state.update {
                it.copy(
                    checkingDuplicate = false,
                    error = message,
                    step = if (message == null) 2 else it.step,
                )
            }
        }
    }

    /**
     * 자녀 2단계(페어링). 코드는 선택이다 — 부모가 아직 코드를 발급하지 않았을 수 있으므로 건너뛰고
     * 나중에 마이 → 「부모님 연결」에서 넣어도 된다. 코드가 있으면 6자리인지만 여기서 확인하고,
     * 실제 연결은 계정 생성·로그인 후 [startApp] 에서 한다(connect 는 로그인 상태에서만 된다).
     */
    fun nextFromPairing() {
        val s = _state.value
        if (s.pairingCode.isNotBlank() && s.pairingCode.length != 6) {
            _state.update { it.copy(error = "코드는 6자리예요") }
            return
        }
        _state.update { it.copy(step = 3, error = null) }
    }

    /** 페어링 단계 건너뛰기 — 코드 없이 다음(약관)으로. */
    fun skipPairing() = _state.update { it.copy(pairingCode = "", step = 3, error = null) }

    /** After terms: child goes to OS permissions; parent submits immediately. */
    fun nextFromTerms() {
        val s = _state.value
        val role = s.role ?: return
        when {
            !s.requiredAgreed -> _state.update { it.copy(error = "필수 약관에 모두 동의해 주세요") }
            role == Role.CHILD -> _state.update { it.copy(step = 4, error = null) }
            else -> submit()
        }
    }

    fun submit() {
        val s = _state.value
        val role = s.role ?: return
        when {
            !s.requiredAgreed -> _state.update { it.copy(error = "필수 약관에 모두 동의해 주세요") }
            role == Role.CHILD && !KidhackPermissions.hasAllRequiredForChildControl(appContext) ->
                _state.update {
                    it.copy(error = "자녀 보호에 필요한 권한을 모두 허용해 주세요")
                }
            else -> viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                runCatching {
                    authRepository.signup(
                        loginId = s.loginId.trim(),
                        username = s.realName.trim(),
                        password = s.password,
                        nickname = s.nickname.trim(),
                        email = s.email.trim().ifBlank { null },
                        phoneNumber = formatPhoneNumber(s.phoneNumber),
                        birthDate = s.birthDate.trim(),
                        address = s.address.trim().ifBlank { null },
                        role = role,
                        parentId = s.parentId.toLongOrNull()?.takeIf { role == Role.CHILD },
                        phoneVerified = s.phoneVerified,
                    )
                }.onSuccess { session ->
                    if (role == Role.CHILD) {
                        onboardingStore.markDone()
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            step = it.doneStep,
                            session = session,
                        )
                    }
                }.onFailure { t ->
                    _state.update {
                        it.copy(loading = false, error = t.userMessage("가입에 실패했어요"))
                    }
                }
            }
        }
    }

    /**
     * 연결 직후의 세션을 서버 기준으로 다시 맞춘다.
     *
     * <p>`login()` 으로 받은 세션은 **연결 이전** 상태라 `parentId` 가 비어 있다. 그대로
     * 넘기면 홈으로 가는 길목(`goAuthed`)이 "아직 부모가 없는 자녀"로 보고 「부모님 연결」
     * 화면으로 또 보낸다 — 방금 연결했는데 다시 묻는 꼴이 된다.
     *
     * <p>서버 조회가 실패하면 원래 세션을 그대로 쓴다. 연결은 이미 됐으므로 최악이라도
     * 페어링 화면이 한 번 더 뜰 뿐, 진행이 막히지는 않는다.
     */
    private suspend fun pairedSession(session: Session): Session {
        val parentId = runCatching { userRepository.me().parentId }.getOrNull() ?: return session
        val updated = session.copy(user = session.user.copy(parentId = parentId))
        runCatching { sessionRepository.save(updated) }
        return updated
    }

    /** 가입 응답에는 토큰이 없다. 앱 시작하기에서 로그인해서 토큰을 받은 뒤 홈으로 보낸다. */
    fun startApp(onAuthed: (Session) -> Unit) {
        val s = _state.value
        val role = s.role ?: return
        if (s.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                authRepository.login(s.loginId.trim(), s.password, preferredRole = role)
            }.onSuccess { session ->
                // 가입 2단계에서 받은 코드로 부모와 연결한다(로그인 직후라야 childId 로 인증됨).
                // 코드가 틀리거나 만료됐어도 앱 진입은 막지 않는다 — 마이 → 「부모님 연결」에서 다시 넣을 수 있다.
                var authed = session
                if (role == Role.CHILD && s.pairingCode.length == 6) {
                    runCatching { pairingRepository.connect(s.pairingCode) }
                        .onSuccess { authed = pairedSession(session) }
                }
                if (role == Role.CHILD) {
                    platformController.reevaluate()
                }
                _state.update { it.copy(loading = false, session = authed) }
                onAuthed(authed)
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.userMessage("로그인에 실패했어요"))
                }
            }
        }
    }
}
