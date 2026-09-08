package com.hack.kidhack.feature.child.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.MessageType
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildChatUiState(
    val loading: Boolean = true,
    val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val error: String? = null,
) {
    val canSend: Boolean get() = room != null && draft.isNotBlank()
}

/**
 * 자녀 ↔ 부모 채팅.
 *
 * 채팅 탭 첫 로드 시 [ChatRepository.rooms] 에서 자기 방을 찾는다. 방 생성은 부모만 할 수
 * 있으므로(서버 제약) 부모가 먼저 대화를 열기 전까지는 방이 없다.
 * 계정 전환 후에도 이전 room을 쓰지 않도록 [boundChildUserId]로 세션을 검증한다.
 */
class ChildChatViewModel(
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildChatUiState())
    val state: StateFlow<ChildChatUiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var cachedRoom: ChatRoom? = null
    /** load() 성공 시 바인딩한 자녀 user id. 다른 계정으로 바뀌면 load()를 강제한다. */
    private var boundChildUserId: String? = null

    /** 채팅 탭 resume: 세션과 room 소유자가 다르면 무조건 재로드. */
    fun onScreenResume() {
        viewModelScope.launch {
            val userId = sessionRepository.current()?.user?.id
            if (userId.isNullOrBlank() ||
                boundChildUserId != userId ||
                _state.value.room == null
            ) {
                load()
            } else {
                reloadCurrentRoom()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            observeJob?.cancel()
            cachedRoom = null
            boundChildUserId = null
            _state.update { it.copy(loading = true, error = null, room = null, messages = emptyList()) }
            runCatching {
                val user = sessionRepository.current()?.user
                    ?: error("로그인 정보가 없어요")
                val childId = user.id.toLongOrNull()
                    ?: error("회원 번호가 올바르지 않아요")
                // 방 생성(POST /chat/rooms)은 서버에서 PARENT 전용이라 자녀가 부르면 403 이다.
                // 자녀는 목록(GET /chat/rooms)에서 자기 방을 찾는다. 세션의 parentId 에 기대지도
                // 않는다 — 로그인 시점에 저장된 값이라 그 뒤에 페어링하면 계속 null 로 남는다.
                val room = chatRepository.rooms()
                    .firstOrNull { it.childId == null || it.childId == childId }
                    ?: error("부모님이 아직 대화를 시작하지 않았어요")
                cachedRoom = room
                boundChildUserId = user.id
                val messages = chatRepository.messages(room.id).messages
                room to messages
            }.onSuccess { (room, messages) ->
                _state.update {
                    it.copy(loading = false, room = room, messages = messages, error = null)
                }
                observe(room.id)
                runCatching { chatRepository.markRead(room.id, messages.lastOrNull()?.id) }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = t.userMessage("채팅방을 준비하지 못했어요"),
                    )
                }
            }
        }
    }

    fun onDraft(value: String) = _state.update { it.copy(draft = value) }

    fun send() {
        val text = _state.value.draft.trim()
        val room = _state.value.room ?: return
        if (text.isEmpty()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            val sessionChildId = sessionRepository.current()?.user?.id
            if (sessionChildId == null || boundChildUserId != sessionChildId) {
                _state.update { it.copy(draft = text) }
                load()
                return@launch
            }
            runCatching { chatRepository.send(room.id, text) }
                .onSuccess { append(it) }
                .onFailure {
                    _state.update { s ->
                        s.copy(draft = text, error = it.userMessage("메시지를 보내지 못했어요"))
                    }
                }
        }
    }

    fun sendImage(localUri: String) = sendMedia(localUri, "image/jpeg", MessageType.IMAGE)

    fun sendAudio(localUri: String) = sendMedia(localUri, "audio/mp4", MessageType.AUDIO)

    private fun sendMedia(localUri: String, mimeType: String, type: MessageType) {
        val room = _state.value.room ?: return
        viewModelScope.launch {
            val sessionChildId = sessionRepository.current()?.user?.id
            if (sessionChildId == null || boundChildUserId != sessionChildId) {
                load()
                return@launch
            }
            runCatching {
                val url = chatRepository.uploadMedia(localUri, mimeType)
                chatRepository.sendMedia(room.id, type, url)
            }.onSuccess { append(it) }
                .onFailure {
                    _state.update { s -> s.copy(error = it.userMessage("첨부를 보내지 못했어요")) }
                }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** 채팅 탭 resume 시 히스토리 refresh + STOMP 구독 재시작. */
    fun reloadCurrentRoom() {
        viewModelScope.launch {
            val sessionChildId = sessionRepository.current()?.user?.id
            if (sessionChildId == null || boundChildUserId != sessionChildId) {
                load()
                return@launch
            }
            val room = _state.value.room ?: cachedRoom
            if (room == null) {
                load()
                return@launch
            }
            val roomChildId = room.childId
            if (roomChildId != null && roomChildId.toString() != sessionChildId) {
                load()
                return@launch
            }
            observeJob?.cancel()
            _state.update { it.copy(loading = true, error = null, room = room) }
            runCatching {
                chatRepository.messages(room.id).messages
            }.onSuccess { messages ->
                _state.update {
                    it.copy(loading = false, room = room, messages = messages, error = null)
                }
                observe(room.id)
                runCatching { chatRepository.markRead(room.id, messages.lastOrNull()?.id) }
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.userMessage("메시지를 불러오지 못했어요"))
                }
                observe(room.id)
            }
        }
    }

    private fun observe(roomId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            chatRepository.observeMessages(roomId).collect { message ->
                append(message)
                runCatching { chatRepository.markRead(roomId, message.id) }
            }
        }
    }

    private fun append(msg: ChatMessage) {
        _state.update { s ->
            if (s.room != null && msg.roomId != s.room.id) return@update s
            if (s.messages.any { it.id == msg.id }) return@update s
            val last = s.messages.lastOrNull()
            val isEchoOfLocal = last != null &&
                (last.id.startsWith("local-") || last.id.startsWith("fake-")) &&
                last.text == msg.text &&
                last.fromRole == msg.fromRole
            if (isEchoOfLocal) {
                s.copy(messages = s.messages.dropLast(1) + msg)
            } else {
                s.copy(messages = s.messages + msg)
            }
        }
    }
}
