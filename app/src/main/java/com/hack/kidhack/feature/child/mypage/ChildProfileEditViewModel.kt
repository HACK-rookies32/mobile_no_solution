package com.hack.kidhack.feature.child.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildProfileEditUiState(
    val loading: Boolean = true,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
) {
    val nameError: String?
        get() = if (!loading && name.isBlank()) "이름을 입력해 주세요" else null

    /** 이메일은 선택 입력이라 비어 있으면 통과시키고, 적었을 때만 형식을 본다. */
    val emailError: String?
        get() = if (email.isNotBlank() && !EMAIL_PATTERN.matches(email)) {
            "이메일 형식이 올바르지 않아요"
        } else {
            null
        }

    val canSave: Boolean
        get() = !loading && !saving && nameError == null && emailError == null

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/**
 * 마이페이지 › 프로필 수정.
 *
 * `PATCH /users/me` 로 저장한 뒤 **세션에도 반영**한다. 세션을 갱신하지 않으면
 * 홈 인사말이나 마이페이지 헤더가 옛 이름을 그대로 보여 준다.
 * 프로필 사진은 명세의 `UserUpdateRequest` 에 필드가 없어 다루지 않는다.
 */
class ChildProfileEditViewModel(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildProfileEditUiState())
    val state: StateFlow<ChildProfileEditUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val user = runCatching { userRepository.me() }.getOrNull()
                ?: sessionRepository.current()?.user
            _state.update {
                it.copy(
                    loading = false,
                    name = user?.name.orEmpty(),
                    phone = user?.phone.orEmpty(),
                    email = user?.email.orEmpty(),
                )
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, message = null) }

    /** 숫자와 하이픈만 남긴다. 서버가 어떤 형식을 받는지 명세에 없어 입력을 좁혀 둔다. */
    fun onPhone(value: String) = _state.update {
        it.copy(phone = value.filter { ch -> ch.isDigit() || ch == '-' }.take(MAX_PHONE), message = null)
    }

    fun onEmail(value: String) = _state.update { it.copy(email = value.trim(), message = null) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            runCatching {
                userRepository.updateMe(
                    nickname = null,
                    phoneNumber = current.phone.trim(),
                    email = current.email.takeIf { it.isNotBlank() },
                    address = null,
                    profileImageUrl = null,
                )
            }.onSuccess { updated ->
                // 저장된 값을 세션에 밀어 넣어야 다른 화면들이 바로 새 이름을 쓴다.
                sessionRepository.current()?.let { session ->
                    // 서버 users/me 응답엔 role 이 없어 매퍼가 PARENT 로 기본 처리한다 — 그대로 저장하면
                    // 자녀 계정이 다음 실행부터 부모 화면으로 들어가던 버그. 세션의 role/parentId 를 보존한다.
                    runCatching {
                        sessionRepository.save(
                            session.copy(
                                user = updated.copy(
                                    role = session.user.role,
                                    parentId = updated.parentId ?: session.user.parentId,
                                ),
                            ),
                        )
                    }
                }
                _state.update {
                    it.copy(
                        saving = false,
                        saved = true,
                        name = updated.name,
                        phone = updated.phone,
                        email = updated.email.orEmpty(),
                        message = "프로필을 저장했어요",
                    )
                }
            }.onFailure {
                _state.update { it.copy(saving = false, message = "저장에 실패했어요. 잠시 후 다시 시도해 주세요") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val MAX_PHONE = 13
    }
}
