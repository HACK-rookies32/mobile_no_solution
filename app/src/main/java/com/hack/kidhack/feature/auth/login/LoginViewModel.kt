package com.hack.kidhack.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.data.remote.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loginId: String = "",
    val password: String = "",
    val role: Role = Role.PARENT,
    val loading: Boolean = false,
    val error: String? = null,
    val session: Session? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onLoginId(value: String) = _state.update { it.copy(loginId = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onRole(role: Role) = _state.update { it.copy(role = role) }

    fun login() {
        val current = _state.value
        if (current.loginId.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "아이디와 비밀번호를 입력해 주세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                authRepository.login(
                    loginId = current.loginId.trim(),
                    password = current.password,
                    preferredRole = current.role,
                )
            }.onSuccess { session ->
                _state.update { it.copy(loading = false, session = session) }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = t.userMessage("로그인에 실패했어요"),
                    )
                }
            }
        }
    }
}
