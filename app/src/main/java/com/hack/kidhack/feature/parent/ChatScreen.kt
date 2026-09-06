package com.hack.kidhack.feature.parent

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.navigation.ImmersiveContent
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ParentChatUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val selectedRoom: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    /** 이 앞에 더 오래된 대화가 남아 있는지. 맨 위 「지난 대화 더 보기」를 띄울지 정한다. */
    val hasOlder: Boolean = false,
    /** 지금 들고 있는 가장 오래된 메시지의 시각. 다음 장을 부를 때 `before` 로 넘긴다. */
    private val oldestSentAt: String? = null,
    /** 지난 장을 읽는 중. 두 번 눌러 같은 장을 두 번 붙이지 않게 한다. */
    val loadingOlder: Boolean = false,
) {
    internal val cursor: String? get() = oldestSentAt
}

class ParentChatViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentChatUiState())
    val state: StateFlow<ParentChatUiState> = _state.asStateFlow()
    private var observeJob: Job? = null
    /** childId → room (세션 동안 재사용) */
    private val roomByChildId = mutableMapOf<String, ChatRoom>()

    fun onDraft(v: String) = _state.update { it.copy(draft = v) }

    /** 방 목록 화면에서 탭 이탈 시 rooms만 비운다. 방 상세 중에는 WS 구독을 유지한다. */
    fun clearRoomsForPause() {
        if (_state.value.selectedRoom != null) return
        observeJob?.cancel()
        roomByChildId.clear()
        _state.update {
            it.copy(
                rooms = emptyList(),
                loading = false,
                error = null,
            )
        }
    }

    /** 방 상세 재진입·resume 시 히스토리 refresh + STOMP 구독 재시작. */
    fun reloadCurrentRoom() {
        val room = _state.value.selectedRoom ?: return
        viewModelScope.launch {
            observeJob?.cancel()
            _state.update { it.copy(loading = true, error = null) }
            runCatching { chatRepository.messages(room.id) }.onSuccess { page ->
                _state.update {
                    it.copy(
                        loading = false,
                        messages = page.messages,
                        hasOlder = page.hasMore,
                        oldestSentAt = page.oldestSentAt,
                    )
                }
                observe(room.id)
                chatRepository.markRead(room.id, page.messages.lastOrNull()?.id)
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.userMessage("메시지를 불러오지 못했어요"))
                }
                observe(room.id)
            }
        }
    }

    fun loadRooms() {
        // launch 전에 동기로 비워, 재조회 중 이전 LazyColumn이 그려지지 않게 한다.
        roomByChildId.clear()
        _state.update {
            it.copy(
                loading = true,
                error = null,
                rooms = emptyList(),
                selectedRoom = null,
                messages = emptyList(),
            )
        }
        viewModelScope.launch {
            runCatching {
                val parentId = sessionRepository.current()?.user?.id?.toLongOrNull()
                    ?: error("로그인 정보가 없어요")
                val children = childRepository.children()
                if (children.isEmpty()) {
                    emptyList()
                } else {
                    // 최근 메시지·안읽음이 든 요약 목록. 방 생성 API(POST)는 이 값을 주지 않아
                    // 예전엔 목록 미리보기가 늘 비어 있었다. 요약(GET rooms)에서 자녀별로 가져온다.
                    val summaryByChildId = runCatching { chatRepository.rooms() }
                        .getOrDefault(emptyList())
                        .mapNotNull { r -> r.childId?.let { it.toString() to r } }
                        .toMap()
                    children.map { child ->
                        val childId = child.id.toLongOrNull()
                            ?: error("자녀 회원 번호가 올바르지 않아요")
                        // 방이 이미 있으면 요약(미리보기 포함)을, 없으면 생성해 목록에 띄운다.
                        val room = summaryByChildId[child.id]
                            ?: chatRepository.getOrCreateRoom(parentId, childId)
                        roomByChildId[child.id] = room
                        // 서버가 자녀 접속 여부를 주지 않는다(ChildSummaryResponse 에 없음).
                        // 방 자체의 상태를 그대로 쓰고 자녀 정보로는 이름만 덮는다.
                        room.copy(title = child.name)
                    }
                }
            }.onSuccess { rooms ->
                _state.update { it.copy(loading = false, rooms = rooms, selectedRoom = null, error = null) }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        loading = false,
                        rooms = emptyList(),
                        error = t.userMessage("채팅방을 준비하지 못했어요"),
                    )
                }
            }
        }
    }

    fun selectRoom(room: ChatRoom?) {
        observeJob?.cancel()
        if (room == null) {
            _state.update {
                it.copy(selectedRoom = null, messages = emptyList(), hasOlder = false, oldestSentAt = null)
            }
            loadRooms()
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, selectedRoom = room) }
            runCatching {
                val children = childRepository.children()
                children.find { it.name == room.title || roomByChildId[it.id]?.id == room.id }
                    ?.let { selectedChildRepository.select(it.id) }

                val page = chatRepository.messages(room.id)
                _state.update {
                    it.copy(
                        loading = false,
                        messages = page.messages,
                        hasOlder = page.hasMore,
                        oldestSentAt = page.oldestSentAt,
                    )
                }
                observe(room.id)
                chatRepository.markRead(room.id, page.messages.lastOrNull()?.id)
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.userMessage("메시지를 불러오지 못했어요"))
                }
            }
        }
    }

    /**
     * 지난 대화 한 장을 앞에 붙인다.
     *
     * <p>서버는 커서 페이징으로 내려 주는데 앱이 첫 장만 쓰고 있어, 최근 서른 개 뒤로는
     * 볼 방법이 없었다. 커서는 <b>시각</b>이므로 붙인 뒤 커서를 새로 받은 장의 것으로 민다.
     */
    fun loadOlder() {
        val current = _state.value
        val room = current.selectedRoom ?: return
        val before = current.cursor ?: return
        if (current.loadingOlder || !current.hasOlder) return

        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            runCatching { chatRepository.messages(room.id, before = before) }
                .onSuccess { page ->
                    _state.update { s ->
                        // 그 사이 방을 옮겼으면 남의 대화에 붙게 된다.
                        if (s.selectedRoom?.id != room.id) return@update s.copy(loadingOlder = false)
                        val known = s.messages.mapTo(mutableSetOf()) { it.id }
                        s.copy(
                            loadingOlder = false,
                            messages = page.messages.filterNot { it.id in known } + s.messages,
                            hasOlder = page.hasMore,
                            // 빈 장이 오면 커서를 그대로 두고 「더 보기」만 감춘다.
                            oldestSentAt = page.oldestSentAt ?: s.cursor,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(loadingOlder = false, error = t.userMessage("지난 대화를 불러오지 못했어요"))
                    }
                }
        }
    }

    fun send() {
        val text = _state.value.draft.trim()
        val room = _state.value.selectedRoom ?: return
        if (text.isBlank()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            runCatching { chatRepository.send(room.id, text) }
                .onSuccess { append(it) }
                .onFailure { t ->
                    _state.update {
                        it.copy(error = t.userMessage("메시지를 보내지 못했어요"), draft = text)
                    }
                }
        }
    }

    private fun observe(roomId: String?) {
        if (roomId == null) return
        observeJob = viewModelScope.launch {
            chatRepository.observeMessages(roomId).collect { append(it) }
        }
    }

    private fun append(msg: ChatMessage) {
        _state.update { s ->
            val selectedId = s.selectedRoom?.id
            if (selectedId != null && msg.roomId != selectedId) return@update s
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

/**
 * 부모 채팅.
 *
 * <p>방 목록과 대화가 한 화면 안에서 갈린다. 하단 탭 안에 있어 대화로 들어가도 탭이
 * 남아야 하고, 방마다 라우트를 파면 탭 복원과 STOMP 구독이 어긋난다.
 */
@Composable
fun ParentChatScreen(viewModel: ParentChatViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    val selectedRoom = state.selectedRoom

    // 탭 복원 시: 방 상세면 WS 재구독, 목록이면 rooms 재조회.
    LifecycleResumeEffect(Unit) {
        if (viewModel.state.value.selectedRoom != null) {
            viewModel.reloadCurrentRoom()
        } else {
            viewModel.loadRooms()
        }
        onPauseOrDispose { viewModel.clearRoomsForPause() }
    }

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // 대화에서 뒤로 가면 목록으로. 여기서 막지 않으면 탭이 통째로 빠진다.
    BackHandler(enabled = selectedRoom != null) { viewModel.selectRoom(null) }

    // 방에 들어가면 하단 탭 바를 접어 풀스크린 대화로 만든다 — 입력창이 탭 바 위에
    // 떠 있지 않고 화면 맨 아래에 앉는다. 화면을 떠나거나 목록으로 돌아오면 되돌린다.
    DisposableEffect(selectedRoom != null) {
        ImmersiveContent.active = selectedRoom != null
        onDispose { ImmersiveContent.active = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        if (selectedRoom == null) {
            RoomList(state = state, onOpen = viewModel::selectRoom, onRetry = viewModel::loadRooms)
        } else {
            RoomDetail(
                room = selectedRoom,
                state = state,
                onBack = { viewModel.selectRoom(null) },
                onDraft = viewModel::onDraft,
                onSend = viewModel::send,
                onLoadOlder = viewModel::loadOlder,
            )
        }
    }
}

/* ---- 방 목록 ---- */

@Composable
private fun ColumnScope.RoomList(
    state: ParentChatUiState,
    onOpen: (ChatRoom) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.parent_chat_title),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 21.sp,
            color = colors.text,
        )
    }
    // 마이페이지·자녀관리와 같은 헤더 구분선 — 제목과 방 목록 사이를 다른 탭과 똑같이 가른다.
    KidHeaderDivider()

    when {
        state.loading && state.rooms.isEmpty() ->
            Box(Modifier.fillMaxWidth().padding(20.dp)) {
                ParentLoadingOrError(true, state.error, onRetry)
            }
        state.rooms.isEmpty() ->
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.parent_chat_empty_rooms),
                    color = colors.muted,
                    fontSize = 13.sp,
                )
            }
        else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(state.rooms, key = { it.id }) { room -> RoomRow(room) { onOpen(room) } }
        }
    }
}

/** 방 한 줄. 이름 첫 글자가 아니라 **프로필 사진**을 그린다. */
@Composable
private fun RoomRow(room: ChatRoom, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfilePhoto(url = room.counterpartProfileImageUrl, size = 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    room.title,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.25).sp,
                    color = colors.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    room.lastMessage.ifBlank { stringResource(R.string.parent_chat_no_message) },
                    color = colors.subtle,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                // 서버가 ISO 로 준다. 그대로 찍으면 2026-09-02T14:20:00 이 목록에 뜬다.
                Text(formatChatSentAt(room.lastAt), color = colors.subtle, fontSize = 11.sp, fontWeight = FontSemibold)
                if (room.unreadCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .heightIn(min = 20.dp)
                            .widthIn(min = 20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.brand)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 빨강은 이 앱에서 위험(이탈·유해 접속)을 뜻한다. 안 읽음에 쓰면 뜻이 섞인다.
                        Text(
                            room.unreadCount.coerceAtMost(99).toString(),
                            color = Color.White,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 20.dp), color = colors.line)
    }
}

/**
 * 프로필 사진.
 *
 * <p>서버가 `counterpartProfileImageUrl` 을 주는데 화면이 쓰지 않고 이름 첫 글자를 그리고
 * 있었다. 사진이 없으면 글자 대신 사람 아이콘을 둔다 — 이름 글자는 사진처럼 보이지 않는다.
 */
@Composable
private fun ProfilePhoto(url: String?, size: Dp) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = null,
                modifier = Modifier.size(size * 0.5f),
                tint = colors.border,
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(RemoteUrl.absolute(url))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/* ---- 대화 ---- */

@Composable
private fun ColumnScope.RoomDetail(
    room: ChatRoom,
    state: ParentChatUiState,
    onBack: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    val listState = rememberLazyListState()

    // 새 메시지가 붙으면 아래로. 지난 장을 앞에 붙일 때는 움직이지 않는다 — 읽던 자리가
    // 튀면 「더 보기」를 누른 사람이 어디를 보고 있었는지 잃는다. 개수가 아니라 「가장 최근
    // 메시지가 바뀌었는지」로 보면 앞에 붙이는 경우가 저절로 걸러진다.
    val newestId = state.messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null) listState.animateScrollToItem(0)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", color = colors.text, fontSize = 26.sp)
        }
        ProfilePhoto(url = room.counterpartProfileImageUrl, size = 30.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            room.title,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 16.sp,
            letterSpacing = (-0.3).sp,
            color = colors.text,
        )
    }
    HorizontalDivider(color = colors.line)

    // 뒤집어 쌓아 목록의 기준점을 <b>맨 아래</b>로 둔다. 키보드가 올라와 목록이 줄어들어도
    // 마지막 말이 입력창 바로 위에 그대로 붙어 있다. 「더 보기」는 위쪽이라 맨 뒤에 놓는다.
    val shown = remember(state.messages) { state.messages.asReversed() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(vertical = 8.dp),
        reverseLayout = true,
    ) {
        itemsIndexed(shown, key = { _, m -> m.id }) { index, m ->
            // 뒤집힌 목록이라 더 오래된 메시지가 한 칸 뒤에 있다.
            val olderDay = shown.getOrNull(index + 1)?.sentAt?.toChatDay()
            val day = m.sentAt.toChatDay()
            // Column 으로 묶는다 — 한 칸 안에 여러 개를 늘어놓으면 reverseLayout 이 그 안쪽
            // 순서까지 뒤집어, 날짜 머리가 말풍선 아래로 내려간다.
            Column {
                // 날짜가 바뀌는 자리에만 구분을 넣는다. 줄마다 넣으면 대화가 표가 된다.
                if (day != null && day != olderDay) {
                    Text(
                        text = day,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                        color = colors.subtle,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                MessageRow(message = m)
            }
        }
        if (state.hasOlder) {
            item(key = "older") {
                Text(
                    text = stringResource(
                        if (state.loadingOlder) R.string.parent_chat_loading_older
                        else R.string.parent_chat_load_older,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.loadingOlder, onClick = onLoadOlder)
                        .padding(vertical = 12.dp),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    ChatComposer(draft = state.draft, onDraft = onDraft, onSend = onSend)
}

/**
 * 말풍선 한 줄.
 *
 * <p>전에는 폭이 `fillMaxWidth(0.7f)` 로 **고정**이라 「넵」 한 글자도 화면 70% 를 차지했다.
 * 글자만큼만 차지하되 74% 를 넘지 않게 한다.
 *
 * <p>시각은 풍선 바깥 아래에 붙인다. 안에 넣으면 짧은 말일수록 풍선이 시각 폭에 맞춰 늘어난다.
 */
@Composable
private fun MessageRow(message: ChatMessage) {
    val colors = LocalKidhackColors.current
    val mine = message.fromRole == Role.PARENT
    val shape = if (mine) {
        RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (mine) {
            Stamp(message.sentAt)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = message.text,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clip(shape)
                .background(if (mine) colors.brand else colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (mine) Color.White else colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            letterSpacing = (-0.25).sp,
            style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
        )
        if (!mine) {
            Spacer(Modifier.width(6.dp))
            Stamp(message.sentAt)
        }
    }
}

@Composable
private fun Stamp(sentAt: String) {
    Text(
        text = formatChatTimeOnly(sentAt),
        modifier = Modifier.padding(bottom = 3.dp),
        color = LocalKidhackColors.current.subtle,
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 10.sp,
    )
}

/** 보내기 줄. 글자가 없으면 보내기가 꺼진 색으로 남는다. */
@Composable
private fun ChatComposer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    val colors = LocalKidhackColors.current
    val canSend = draft.isNotBlank()
    Column(Modifier.fillMaxWidth().background(colors.card)) {
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 탭 껍데기(TabShell)가 비워 둔 만큼을 소비했다고 알려 주므로, 여기서는
                // 모자란 만큼만 더하면 키보드 바로 위에 앉는다. 전에는 네비바를 직접 빼서
                // 맞췄는데, 그 식은 탭 바가 접혔을 때만 맞았다.
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 13.5.sp,
                    color = colors.text,
                ),
                cursorBrush = SolidColor(colors.brand),
                maxLines = 4,
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            stringResource(R.string.parent_chat_input_hint),
                            color = colors.subtle,
                            fontSize = 13.5.sp,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(9.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (canSend) colors.brand else colors.line)
                    .then(if (canSend) Modifier.clickable(onClick = onSend) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.parent_chat_send),
                    modifier = Modifier.size(17.dp),
                    tint = Color.White,
                )
            }
        }
    }
}
