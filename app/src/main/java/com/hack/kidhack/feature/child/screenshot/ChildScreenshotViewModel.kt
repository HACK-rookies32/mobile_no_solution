package com.hack.kidhack.feature.child.screenshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.data.local.ChildCaptureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildScreenshotUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<Screenshot> = emptyList(),
    val bannerVisible: Boolean = false,
    /** 아직 처리하지 않은 캡처 요청. null 이면 대기 중인 요청이 없다. */
    val pendingRequestId: String? = null,
    val requestedBy: String? = null,
)

/**
 * 자녀 캡처 내역 화면.
 *
 * <p>목록은 서버가 아니라 [ChildCaptureStore] — 이 기기에 남은 캡처 파일 — 에서 읽는다.
 * 서버의 캡처 목록 조회는 부모(PARENT) 전용이라 자녀 토큰으로는 403 이다(명세 4.6). 자녀에게
 * 「캡처 내역」은 "내 폰에서 찍힌 내 화면"이므로 로컬이 곧 정답이다. 촬영 요청 배너는 여전히
 * 실시간 커맨드([CommandRepository])로 받는다.
 */
class ChildScreenshotViewModel(
    private val captureStore: ChildCaptureStore,
    private val commandRepository: CommandRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildScreenshotUiState())
    val state: StateFlow<ChildScreenshotUiState> = _state.asStateFlow()

    init {
        refresh()
        // 화면이 열려 있는 동안 들어온 캡처 요청은 알림뿐 아니라 배너로도 보여 준다.
        viewModelScope.launch {
            commandRepository.observe()
                .filter { it.type == RemoteCommandType.SCREEN_CAPTURE }
                .collect { command ->
                    _state.update {
                        it.copy(
                            bannerVisible = true,
                            pendingRequestId = command.requestId,
                            requestedBy = command.requestedBy,
                        )
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // 로컬 파일만 읽으므로 네트워크 오류가 없다. 파일이 없으면 그냥 빈 목록이다.
            val items = runCatching { captureStore.list() }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, items = items, error = null) }
        }
    }

    /** 동의 화면으로 넘어간 뒤에는 배너를 접는다. 결과는 목록 갱신으로 확인한다. */
    fun onCaptureLaunched() = _state.update {
        it.copy(bannerVisible = false, pendingRequestId = null)
    }

    fun dismissBanner() = _state.update { it.copy(bannerVisible = false) }
}
