package com.hack.kidhack.feature.child.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.local.ChildPhotoStore
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.CommandAckStatus
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.PhotoRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

enum class UploadPhase { Idle, Uploading, Sent, Failed }

data class ChildCameraUiState(
    /** 부모 요청으로 열린 경우의 requestId. null 이면 자녀가 스스로 연 것. */
    val requestId: String? = null,
    val requestedBy: String? = null,
    val phase: UploadPhase = UploadPhase.Idle,
    /** 기기에 남아 있는 촬영본. 업로드 성공 여부와 무관하게 자녀가 볼 수 있다. */
    val gallery: List<ChildPhoto> = emptyList(),
    val galleryLoading: Boolean = true,
)

/**
 * 카메라 촬영 → 부모 계정 업로드.
 *
 * 부모 요청(`requestId`)으로 들어온 경우 촬영본을 올린 뒤 서버에 DONE ack 을 보낸다.
 * 자녀가 그냥 카메라 탭을 연 경우에도 촬영본은 같은 경로로 올라간다.
 */
class ChildCameraViewModel(
    private val photoRepository: PhotoRepository,
    private val commandRepository: CommandRepository,
    private val sessionRepository: SessionRepository,
    private val photoStore: ChildPhotoStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildCameraUiState())
    val state: StateFlow<ChildCameraUiState> = _state.asStateFlow()

    init {
        loadGallery()
    }

    fun loadGallery() {
        viewModelScope.launch {
            val photos = photoStore.list()
            _state.update { it.copy(gallery = photos, galleryLoading = false) }
        }
    }

    fun onRequest(requestId: String?) {
        if (requestId.isNullOrBlank() || requestId == _state.value.requestId) return
        _state.update { it.copy(requestId = requestId, phase = UploadPhase.Idle) }
    }

    /** 촬영 파일을 부모 계정으로 전송한다. 성공/실패와 무관하게 임시 파일은 지운다. */
    fun upload(file: File) {
        _state.update { it.copy(phase = UploadPhase.Uploading) }
        viewModelScope.launch {
            val childId = sessionRepository.current()?.user?.id.orEmpty()
            val requestId = _state.value.requestId

            // 업로드보다 먼저 기기에 저장한다. 서버가 죽어 있어도 찍은 사진은 남아야 한다.
            photoStore.save(
                source = file,
                requestId = requestId,
                requestedBy = _state.value.requestedBy,
            )
            loadGallery()

            val result = runCatching {
                photoRepository.upload(
                    childId = childId,
                    file = file,
                    capturedAt = Instant.now().toString(),
                    requestId = requestId,
                )
            }
            runCatching { file.delete() }

            if (!requestId.isNullOrBlank()) {
                runCatching {
                    commandRepository.ack(
                        childId = childId,
                        requestId = requestId,
                        status = if (result.isSuccess) CommandAckStatus.DONE else CommandAckStatus.FAILED,
                    )
                }
            }
            _state.update {
                it.copy(phase = if (result.isSuccess) UploadPhase.Sent else UploadPhase.Failed)
            }
        }
    }

    fun onCaptureFailed() = _state.update { it.copy(phase = UploadPhase.Failed) }

    /** 배너·토스트를 잠시 보여 준 뒤 되돌린다. 요청 건이면 요청 표시도 함께 해제한다. */
    fun clearPhase() = _state.update {
        if (it.phase == UploadPhase.Sent) {
            it.copy(phase = UploadPhase.Idle, requestId = null)
        } else {
            it.copy(phase = UploadPhase.Idle)
        }
    }
}
