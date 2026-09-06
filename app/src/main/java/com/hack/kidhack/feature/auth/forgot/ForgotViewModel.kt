package com.hack.kidhack.feature.auth.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.BuildConfig
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.SentCode
import com.hack.kidhack.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 계정 찾기 화면 상태.
 *
 * 서버 인증 API 가 **이메일로 코드를 보내고 코드로 검증하는 2단계**라,
 * 두 섹션 모두 「코드 발송 → 코드 입력」 순서를 갖는다.
 * 예전 화면은 휴대폰 번호 한 번으로 아이디를 찾는 1단계였는데, 그런 엔드포인트는 서버에 없다.
 */
data class ForgotUiState(
    // 아이디 찾기
    val findName: String = "",
    val findEmail: String = "",
    val findCode: String = "",
    val findCodeSent: Boolean = false,
    val foundLoginId: String? = null,
    val findSendLoading: Boolean = false,
    val findLoading: Boolean = false,
    val findError: String? = null,
    val findInfo: String? = null,

    // 비밀번호 재설정
    val resetLoginId: String = "",
    val resetEmail: String = "",
    val verifyCode: String = "",
    val resetCodeSent: Boolean = false,
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val resetSendLoading: Boolean = false,
    val resetLoading: Boolean = false,
    val resetError: String? = null,
    val resetInfo: String? = null,
)

class ForgotViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    fun onFindName(v: String) = _state.update { it.copy(findName = v, findError = null) }
    fun onFindEmail(v: String) = _state.update { it.copy(findEmail = v.trim(), findError = null) }
    fun onFindCode(v: String) = _state.update { it.copy(findCode = v.trim(), findError = null) }

    fun onResetLoginId(v: String) = _state.update { it.copy(resetLoginId = v, resetError = null) }
    fun onResetEmail(v: String) = _state.update { it.copy(resetEmail = v.trim(), resetError = null) }
    fun onVerifyCode(v: String) = _state.update { it.copy(verifyCode = v.trim(), resetError = null) }
    fun onNewPassword(v: String) = _state.update { it.copy(newPassword = v, resetError = null) }
    fun onNewPasswordConfirm(v: String) =
        _state.update { it.copy(newPasswordConfirm = v, resetError = null) }

    /** 아이디 찾기 1단계 — 실명 + 이메일로 인증코드 발송. */
    fun sendFindIdCode() {
        val s = _state.value
        if (s.findName.isBlank() || s.findEmail.isBlank()) {
            _state.update { it.copy(findError = "이름과 이메일을 입력해 주세요", findInfo = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(findSendLoading = true, findError = null, findInfo = null) }
            runCatching { authRepository.sendFindIdCode(s.findName.trim(), s.findEmail) }
                .onSuccess { sent ->
                    _state.update {
                        it.copy(
                            findSendLoading = false,
                            findCodeSent = true,
                            findInfo = sent.userFacingMessage("인증코드를 보냈어요. 메일함을 확인해 주세요"),
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            findSendLoading = false,
                            findError = t.userMessage("인증코드를 보내지 못했어요"),
                        )
                    }
                }
        }
    }

    /** 아이디 찾기 2단계 — 이메일 + 코드 검증. */
    fun verifyFindId() {
        val s = _state.value
        if (s.findCode.isBlank()) {
            _state.update { it.copy(findError = "인증 코드를 입력해 주세요", findInfo = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(findLoading = true, findError = null, findInfo = null) }
            runCatching { authRepository.verifyFindId(s.findEmail, s.findCode) }
                .onSuccess { id ->
                    _state.update {
                        it.copy(
                            findLoading = false,
                            foundLoginId = id,
                            // 재설정 섹션도 같은 계정을 쓸 확률이 높아 미리 채워 준다.
                            resetLoginId = it.resetLoginId.ifBlank { id },
                            resetEmail = it.resetEmail.ifBlank { s.findEmail },
                            findInfo = "아이디는 $id 입니다",
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            findLoading = false,
                            findError = t.userMessage("아이디를 찾지 못했어요"),
                        )
                    }
                }
        }
    }

    /** 비밀번호 재설정 1단계 — 아이디 + 이메일로 인증코드 발송. */
    fun sendResetPasswordCode() {
        val s = _state.value
        if (s.resetLoginId.isBlank() || s.resetEmail.isBlank()) {
            _state.update { it.copy(resetError = "아이디와 이메일을 입력해 주세요", resetInfo = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(resetSendLoading = true, resetError = null, resetInfo = null) }
            runCatching { authRepository.sendResetPasswordCode(s.resetLoginId.trim(), s.resetEmail) }
                .onSuccess { sent ->
                    _state.update {
                        it.copy(
                            resetSendLoading = false,
                            resetCodeSent = true,
                            resetInfo = sent.userFacingMessage("인증코드를 보냈어요. 메일함을 확인해 주세요"),
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            resetSendLoading = false,
                            resetError = t.userMessage("인증코드를 보내지 못했어요"),
                        )
                    }
                }
        }
    }

    /** 비밀번호 재설정 2단계 — 이메일 + 코드 + 새 비밀번호. */
    fun resetPassword() {
        val s = _state.value
        when {
            !s.resetCodeSent ->
                _state.update { it.copy(resetError = "먼저 인증코드를 받아 주세요", resetInfo = null) }
            s.verifyCode.isBlank() ->
                _state.update { it.copy(resetError = "인증 코드를 입력해 주세요", resetInfo = null) }
            s.newPassword.length < 8 ->
                _state.update { it.copy(resetError = "비밀번호는 8자 이상이어야 해요", resetInfo = null) }
            s.newPassword != s.newPasswordConfirm ->
                _state.update { it.copy(resetError = "비밀번호가 일치하지 않아요", resetInfo = null) }
            else -> viewModelScope.launch {
                _state.update { it.copy(resetLoading = true, resetError = null, resetInfo = null) }
                runCatching {
                    authRepository.resetPassword(s.resetEmail, s.verifyCode, s.newPassword)
                }.onSuccess {
                    _state.update {
                        it.copy(
                            resetLoading = false,
                            resetInfo = "비밀번호가 재설정되었어요. 로그인해 주세요",
                        )
                    }
                }.onFailure { t ->
                    _state.update {
                        it.copy(
                            resetLoading = false,
                            resetError = t.userMessage("재설정에 실패했어요"),
                        )
                    }
                }
            }
        }
    }

    /**
     * 서버는 mock 메일러를 쓰기 때문에 6자리 코드를 응답 본문에 그대로 담아 준다.
     * 실기기 테스트를 하려면 그 값을 봐야 하지만, 운영 빌드에 노출되면 이메일 인증이 무의미해진다.
     * 그래서 **디버그 빌드에서만** 화면에 덧붙인다.
     */
    private fun SentCode.userFacingMessage(fallback: String): String {
        val base = message?.takeIf { it.isNotBlank() } ?: fallback
        val dev = code?.takeIf { it.isNotBlank() && BuildConfig.DEBUG } ?: return base
        return "$base (개발용 코드: $dev)"
    }
}
