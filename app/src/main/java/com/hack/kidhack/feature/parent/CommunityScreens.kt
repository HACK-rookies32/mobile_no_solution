package com.hack.kidhack.feature.parent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.repository.CommunityRepository
import com.hack.kidhack.domain.repository.UserRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.hack.kidhack.core.designsystem.component.KidSearchBar
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

class CommunityListViewModel(
    private val communityRepository: CommunityRepository,
) : ViewModel() {

    /** 검색칸에 찍히고 있는 글자. 아직 서버로 나가지 않았다. */
    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    /**
     * 실제로 조회에 쓰는 말.
     *
     * <p>[_keyword] 와 나눠 둔 이유는, 한 글자 칠 때마다 서버에 요청이 나가지 않게 하기
     * 위해서다. 키보드 「검색」 키를 눌러야 이쪽이 바뀐다.
     */
    private val _query = MutableStateFlow("")

    // 게시글 목록은 Paging3 로 노출한다(loading/error 는 화면의 loadState 에서 파생).
    @OptIn(ExperimentalCoroutinesApi::class)
    val posts: Flow<PagingData<Post>> = _query
        .flatMapLatest { q -> communityRepository.pagedPosts(q.ifBlank { null }) }
        .cachedIn(viewModelScope)

    /** 목록 화면이 한 번이라도 그려졌는지. 글쓰기·상세에서 돌아오면 목록을 다시 읽기 위한 표시. */
    var shownOnce = false

    fun onKeyword(value: String) = _keyword.update { value }

    fun search() = _query.update { _keyword.value.trim() }
}

data class CommunityDetailUiState(
    val post: Post? = null,
    val comments: List<Comment> = emptyList(),
    val draft: String = "",
    /** 내 회원 id. 내 글·내 댓글에만 삭제 버튼을 보인다(남의 것은 서버가 막는다). */
    val myMemberId: String = "",
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val error: String? = null,
)

/**
 * 게시글 상세 (명세 20).
 *
 * 신고 기능은 서버에 없다. 예전에는 `/report` 를 부르며 404 를 받고도 "신고 접수" 를
 * 띄우고 있었다 — 화면과 함께 걷어냈다.
 */
class CommunityDetailViewModel(
    private val postId: String,
    private val communityRepository: CommunityRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityDetailUiState())
    val state: StateFlow<CommunityDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            runCatching { userRepository.me() }.getOrNull()?.let { user ->
                _state.update { it.copy(myMemberId = user.id) }
            }
        }
    }

    fun onDraft(v: String) = _state.update { it.copy(draft = v) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { communityRepository.post(postId) }
                .onSuccess { post -> _state.update { it.copy(loading = false, post = post, comments = post.comments) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    /** 서버가 토글 결과를 준다. 예전에는 글을 다시 읽고 앱이 카운트를 ±1 했다. */
    fun like() {
        viewModelScope.launch {
            runCatching { communityRepository.like(postId) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(post = it.post?.copy(liked = result.liked, likeCount = result.likeCount))
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    /** 서버가 돌려준 댓글을 그대로 붙인다. 예전에는 가짜 댓글을 만들어 넣었다. */
    fun comment() {
        val text = _state.value.draft.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { communityRepository.comment(postId, text) }
                .onSuccess { created ->
                    _state.update { it.copy(draft = "", comments = it.comments + created) }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            runCatching { communityRepository.deleteComment(postId, commentId) }
                .onSuccess {
                    _state.update { it.copy(comments = it.comments.filterNot { c -> c.id == commentId }) }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun deletePost() {
        viewModelScope.launch {
            runCatching { communityRepository.delete(postId) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }
}

data class CommunityWriteUiState(
    /** 수정 모드. 제목·버튼 문구와 호출할 API 가 달라진다. */
    val editing: Boolean = false,
    val title: String = "",
    val content: String = "",
    /** 업로드를 마친 첨부의 **공개 경로**. 갤러리 URI 가 아니다. */
    val attachments: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val showLinkDialog: Boolean = false,
    val uploading: Boolean = false,
    val loading: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * 글쓰기 (명세 20).
 *
 * 첨부는 `POST /community/attachments` 로 **먼저 올려** 공개 경로를 받고 그 경로만
 * 글에 실어 보낸다. 예전에는 갤러리 `content://` URI 를 그대로 보냈다(서버에 없는 파일).
 */
class CommunityWriteViewModel(
    private val communityRepository: CommunityRepository,
    /** 수정할 글 id. 비어 있으면 새 글이다. */
    private val postId: String = "",
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityWriteUiState(editing = postId.isNotBlank()))
    val state: StateFlow<CommunityWriteUiState> = _state.asStateFlow()

    init {
        if (postId.isNotBlank()) loadForEdit()
    }

    /** 목록에는 본문·첨부가 없어서 수정 폼을 채우려면 상세를 받아와야 한다. */
    private fun loadForEdit() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { communityRepository.post(postId) }
                .onSuccess { post ->
                    _state.update {
                        it.copy(
                            loading = false,
                            title = post.title,
                            content = post.content,
                            attachments = post.attachments,
                            links = post.links,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onContent(v: String) = _state.update { it.copy(content = v) }

    fun addAttachment(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            runCatching { communityRepository.uploadAttachment(uri.toString()) }
                .onSuccess { url -> _state.update { it.copy(attachments = it.attachments + url, uploading = false) } }
                .onFailure { t ->
                    _state.update { it.copy(uploading = false, error = t.message ?: "첨부 업로드에 실패했어요") }
                }
        }
    }

    fun removeAttachment(url: String) = _state.update { it.copy(attachments = it.attachments - url) }

    fun toggleLinkDialog(show: Boolean) = _state.update { it.copy(showLinkDialog = show) }

    fun addLink(url: String) {
        if (url.isBlank()) return
        _state.update { it.copy(links = it.links + url, showLinkDialog = false) }
    }

    fun removeLink(url: String) = _state.update { it.copy(links = it.links - url) }

    fun submit() {
        val title = _state.value.title.trim()
        val content = _state.value.content.trim()
        if (title.isBlank() || content.isBlank()) {
            _state.update { it.copy(error = "제목과 내용을 입력해 주세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                if (postId.isBlank()) {
                    communityRepository.create(
                        title = title,
                        content = content,
                        attachments = _state.value.attachments,
                        links = _state.value.links,
                    )
                } else {
                    communityRepository.update(
                        postId = postId,
                        title = title,
                        content = content,
                        attachments = _state.value.attachments,
                        links = _state.value.links,
                    )
                }
            }
                .onSuccess { _state.update { it.copy(loading = false, done = true) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }
}

/**
 * 커뮤니티 글 목록 본문.
 *
 * <p>커뮤니티 탭이 공지·내 활동과 나란히 이 본문을 그린다. 화면 껍데기(헤더·배경)는
 * 탭 화면이 한 번만 씌운다 — 탭마다 제 헤더를 들고 있으면 탭을 옮길 때 제목이 두 번
 * 바뀌는 것처럼 보인다.
 *
 * <p>글 한 줄을 카드로 감싸지 않는다. 목록 응답에는 본문 미리보기도 댓글 수도 없어
 * 제목·작성자·날짜·좋아요뿐인데, 이것을 카드에 담으면 속이 빈 상자가 줄줄이 늘어선다.
 * 구분선으로만 나누면 제목이 이어서 읽힌다.
 */
@Composable
internal fun ColumnScope.CommunityListBody(
    posts: LazyPagingItems<Post>,
    keyword: String,
    onKeyword: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenPost: (String) -> Unit,
    onWrite: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(Modifier.weight(1f).fillMaxWidth().background(colors.surface)) {
      // 공지사항과 같은 검색바를 쓴다. 서버는 제목과 작성자 닉네임에서 찾는다.
      KidSearchBar(
        value = keyword,
        onValueChange = onKeyword,
        onSearch = onSearch,
        hint = stringResource(R.string.parent_community_search_hint),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
      )
      Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 아래 여백은 동그란 글쓰기 버튼이 마지막 글을 가리지 않도록 비워 둔다.
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            val refresh = posts.loadState.refresh
            when {
                refresh is LoadState.Loading && posts.itemCount == 0 ->
                    item { Box(Modifier.padding(16.dp)) { ParentLoadingOrError(true, null) } }
                refresh is LoadState.Error ->
                    item {
                        Box(Modifier.padding(16.dp)) {
                            ParentLoadingOrError(false, refresh.error.message.orEmpty(), onRetry = { posts.retry() })
                        }
                    }
                refresh is LoadState.NotLoading && posts.itemCount == 0 ->
                    item {
                        Text(
                            text = if (keyword.isBlank()) {
                                stringResource(R.string.parent_community_empty)
                            } else {
                                stringResource(R.string.parent_community_search_empty, keyword)
                            },
                            modifier = Modifier.fillMaxWidth().padding(20.dp, 36.dp),
                            color = colors.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
            }

            items(
                count = posts.itemCount,
                key = posts.itemKey { it.id },
            ) { index ->
                posts[index]?.let { post -> PostRow(post = post, onClick = { onOpenPost(post.id) }) }
            }

            when (posts.loadState.append) {
                is LoadState.Loading ->
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = colors.brand, strokeWidth = 2.dp)
                        }
                    }
                is LoadState.Error ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.parent_community_load_more_failed),
                                color = colors.danger,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            KidButton(
                                text = stringResource(R.string.parent_retry),
                                onClick = { posts.retry() },
                                modifier = Modifier.fillMaxWidth(),
                                variant = KidButtonVariant.Secondary,
                            )
                        }
                    }
                else -> Unit
            }
        }

        // 글쓰기를 목록 맨 위 폭 전체 버튼으로 두면 들어오자마자 첫 글을 가린다. 커뮤니티에
        // 들어온 사람이 먼저 볼 것은 남이 쓴 글이고, 글쓰기는 늘 같은 자리에 떠 있으면 된다.
        //
        // 이름을 붙여 알약으로 둔다. 연필만 그려 두면 무엇이 열리는지 눌러 봐야 안다.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(colors.brand)
                .clickable(onClick = onWrite)
                .padding(start = 15.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pencil),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(R.string.parent_community_write),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                letterSpacing = (-0.2).sp,
            )
        }
      }
    }
}

/**
 * 글 한 줄.
 *
 * <p>제목 → 본문 한 줄 → 곁가지(작성자·시간·좋아요) 순으로 내려간다. 커뮤니티에 들어온
 * 사람이 먼저 정하는 것은 「이 글을 열까」이고, 그 판단은 제목과 첫 문장이 한다.
 *
 * <p>작성자와 시간은 맨 아래에서 [KidhackColors.subtle] 로 눕는다. 본문과 같은 회색을
 * 쓰면 곁가지가 제목과 나란히 읽혀 한 줄이 세 덩어리로 흩어진다.
 */
@Composable
private fun PostRow(post: Post, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    KidSectionCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
      Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = post.title,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.4).sp,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
            )
            // 서버가 본문 앞부분을 주지 않던 시절 글은 미리보기가 비어 있다. 그때는 줄을
            // 비워 두지 않고 아예 빼서, 빈 자리가 잘린 글처럼 보이지 않게 한다.
            if (post.preview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = post.preview,
                    color = colors.subtle,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaText(post.author)
                MetaDot()
                MetaText(post.createdAtLabel.toRelativeTime())
                if (post.likeCount > 0) {
                    MetaDot()
                    MetaText(stringResource(R.string.parent_community_like_count, post.likeCount))
                }
            }
        }
        post.thumbnail?.takeIf { it.isNotBlank() }?.let { path ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(RemoteUrl.absolute(path))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
      }
    }
}

/** 곁가지 글씨. 작성자·시간·좋아요가 모두 같은 크기·같은 색으로 한 줄에 눕는다. */
@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        color = LocalKidhackColors.current.subtle,
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 11.5.sp,
    )
}

/** 곁가지 사이의 점. 「·」 글자를 쓰면 폰트마다 높이가 달라 줄이 흔들린다. */
@Composable
private fun MetaDot() {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .size(2.5.dp)
            .clip(CircleShape)
            .background(LocalKidhackColors.current.border),
    )
}

/**
 * 「3시간 전」.
 *
 * <p>`2026.10.02 14:20` 은 기록처럼 읽힌다. 커뮤니티에서 알고 싶은 것은 몇 시에 썼는지가
 * 아니라 **얼마나 새 글인지**다. 일주일이 넘으면 그때부터는 날짜가 더 쓸모 있다.
 */
private fun String.toRelativeTime(): String {
    val written = runCatching { LocalDateTime.parse(take(19)) }.getOrNull()
        ?: return toDisplayDateTime()
    val minutes = ChronoUnit.MINUTES.between(written, LocalDateTime.now())
    return when {
        minutes < 1 -> "방금"
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        minutes < 60 * 24 * 2 -> "어제"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}일 전"
        else -> written.format(DateTimeFormatter.ofPattern("M.d"))
    }
}

/**
 * 글 하나를 읽는 화면.
 *
 * <p>글 전체를 카드 하나로 감싸지 않는다. 화면이 이미 「글 한 편」이라 상자가 한 겹 더
 * 필요하지 않고, 카드 안에 갇히면 첨부 사진이 좌우 여백만큼 좁아진다.
 *
 * <p>헤더에는 제목을 적지 않는다. 글 제목이 바로 아래에 있어 같은 자리에 이름이 둘이
 * 된다. 뒤로 가기와 「더 보기」만 둔다.
 */
@Composable
fun ParentCommunityDetailScreen(
    postId: String,
    onBack: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    viewModel: CommunityDetailViewModel = koinViewModel(parameters = { parametersOf(postId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    val post = state.post
    var menuOpen by remember { mutableStateOf(false) }

    // 글이 지워졌으면 상세에 머물 이유가 없다.
    if (state.deleted) {
        LaunchedEffect(Unit) { onBack() }
    }

    // 수정 화면에서 돌아오면 뷰모델이 그대로 살아 있어 옛 내용이 남는다. 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    val mine = post != null && post.authorId.isNotBlank() && post.authorId == state.myMemberId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackTapTarget(onBack)
            Text(
                stringResource(R.string.parent_community_post_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
            Spacer(Modifier.weight(1f))
            // 수정·삭제는 내 글에만, 그리고 「더 보기」 안에 넣는다. 좋아요 줄에 작은 글씨로
            // 붙여 두면 읽는 도중 지우기 버튼이 늘 시야에 있다.
            if (mine) {
                Box {
                    IconTapTarget(R.drawable.ic_more, R.string.parent_community_more) { menuOpen = true }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = colors.card,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.parent_community_edit),
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 14.sp,
                                    color = colors.text,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                post?.let { onEdit(it.id) }
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.parent_community_delete),
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 14.sp,
                                    color = colors.danger,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.deletePost()
                            },
                        )
                    }
                }
            }
        }
        KidHeaderDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if ((state.loading && post == null) || state.error != null) {
                Box(Modifier.padding(20.dp)) {
                    ParentLoadingOrError(state.loading && post == null, state.error, viewModel::load)
                }
            }
            if (post != null) {
                Text(
                    text = post.title,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    color = colors.text,
                    style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
                )
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InitialAvatar(post.author, size = 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            post.author,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 13.sp,
                            color = colors.text,
                        )
                        Text(
                            post.createdAtLabel.toRelativeTime(),
                            color = colors.subtle,
                            fontSize = 11.5.sp,
                        )
                    }
                }

                Text(
                    text = post.content,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = colors.text,
                    fontSize = 14.5.sp,
                    lineHeight = 26.sp,
                    style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
                )

                // 첨부 사진은 폭을 다 쓴다. 상대 경로라 origin 을 붙여야 뜬다.
                post.attachments.forEach { path ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(RemoteUrl.absolute(path))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }

                post.links.forEach { link ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .clickable { context.openExternal(link) }
                            .padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_link),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = colors.muted,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = link,
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 화면 폭을 반반 나눈 액션 바는 2015년 무렵 앱의 문법이다. 알약 두 개를
                // 왼쪽에 붙이고, 누른 좋아요는 알약이 통째로 물들게 한다 — 숫자 색만
                // 바뀌는 것보다 눌렀다는 사실이 분명하다.
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionChip(
                        icon = if (post.liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                        label = "${post.likeCount}",
                        active = post.liked,
                        onClick = viewModel::like,
                    )
                    ActionChip(
                        icon = R.drawable.ic_chat,
                        label = "${state.comments.size}",
                        active = false,
                        onClick = null,
                    )
                }

                // 글 본문·액션과 댓글을 실선 한 줄로 확실히 가른다 — 어디까지가 글이고
                // 어디부터 댓글인지 스크롤 중에 헷갈리지 않게.
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = colors.border)

                Text(
                    text = stringResource(R.string.parent_community_comment_head, state.comments.size),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 2.dp),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.sp,
                    color = colors.text,
                )

                // 댓글끼리도 얇은 선으로 나눈다. 예전엔 아바타 색만으로 갈라 두 댓글이
                // 이어 읽혔다 — 첫 댓글 위에는 선을 두지 않아 머리글과 붙여 둔다.
                state.comments.forEachIndexed { index, c ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = colors.line,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    CommentRow(
                        comment = c,
                        mine = c.memberId.isNotBlank() && c.memberId == state.myMemberId,
                        onDelete = { viewModel.deleteComment(c.id) },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        CommentComposer(
            draft = state.draft,
            onDraft = viewModel::onDraft,
            onSend = viewModel::comment,
        )
    }
}

/** 뒤로 가기. 다른 화면 헤더와 같은 「‹」 글자를 쓴다. */
@Composable
private fun BackTapTarget(onBack: () -> Unit) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", color = colors.text, fontSize = 26.sp)
    }
}

/** 헤더의 아이콘 버튼. 손가락이 닿는 자리를 40dp 로 잡는다. */
@Composable
private fun IconTapTarget(
    @DrawableRes icon: Int,
    @StringRes desc: Int,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(desc),
            modifier = Modifier.size(20.dp),
            tint = colors.text,
        )
    }
}

/**
 * 좋아요·댓글 알약.
 *
 * <p>높이 36dp 는 손가락 최소 과녁(44dp)보다 작아 보이지만, 좌우 여백까지 눌리는 자리라
 * 실제로 닿는 면적은 그보다 넓다.
 */
@Composable
private fun ActionChip(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    onClick: (() -> Unit)?,
) {
    val colors = LocalKidhackColors.current
    val tint = if (active) colors.danger else colors.muted
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) colors.brandLight else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) colors.brandLight else colors.border,
                shape = RoundedCornerShape(18.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 13.sp)
    }
}

/**
 * 이름 첫 글자로 만드는 아바타.
 *
 * <p>프로필 사진이 없어도 사람이 구분된다. 색은 이름에서 뽑으므로 같은 사람은 어느
 * 화면에서든 같은 색이다 — 무작위로 고르면 스크롤할 때마다 색이 바뀐다.
 */
@Composable
private fun InitialAvatar(name: String, size: Dp) {
    val colors = LocalKidhackColors.current
    val palette = listOf(
        colors.brandLight to colors.brandDark,
        Color(0xFFE8EDF5) to Color(0xFF4B6A93),
        Color(0xFFF0EAF7) to Color(0xFF6E51A0),
        Color(0xFFE4F1EC) to Color(0xFF3E7D68),
    )
    val (bg, fg) = palette[(name.hashCode().mod(palette.size))]
    Box(
        Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).ifBlank { "?" },
            color = fg,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

/** 댓글 한 줄. 카드를 씌우지 않는다 — 짧은 글 여럿이라 상자가 내용보다 커진다. */
@Composable
private fun CommentRow(comment: Comment, mine: Boolean, onDelete: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InitialAvatar(comment.author, size = 30.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.5.sp,
                    color = colors.text,
                )
                Spacer(Modifier.width(7.dp))
                Text(comment.createdAt.toRelativeTime(), color = colors.subtle, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                // 남의 댓글이면 서버가 403 이라 버튼 자체를 내지 않는다.
                if (mine) {
                    Text(
                        text = stringResource(R.string.parent_community_delete),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = colors.subtle,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = comment.text,
                color = colors.text,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
            )
        }
    }
}

/**
 * 댓글 입력줄.
 *
 * <p>전에는 입력칸에 바탕도 테두리도 없어 **어디를 눌러야 글자가 들어가는지 보이지 않았다.**
 * 회색 알약으로 칸을 만들고, 보내기는 글자 버튼 대신 동그란 아이콘으로 줄여 입력칸에
 * 폭을 내준다. 쓴 글자가 없으면 보내기가 꺼진 색으로 남는다.
 */
@Composable
private fun CommentComposer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    val colors = LocalKidhackColors.current
    val canSend = draft.isNotBlank()
    Column(Modifier.fillMaxWidth().background(colors.card)) {
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 제스처 바 여백은 탭 껍데기(Scaffold)가 이미 준다. 여기서 또 주면 두 번 쌓인다.
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(21.dp))
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                textStyle = TextStyle(
                    fontFamily = KidhackFontFamily,
                    fontSize = 13.5.sp,
                    color = colors.text,
                ),
                cursorBrush = SolidColor(colors.brand),
                maxLines = 4,
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            stringResource(R.string.parent_community_comment_hint),
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canSend) colors.brand else colors.line)
                    .then(if (canSend) Modifier.clickable(onClick = onSend) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.parent_community_comment_send),
                    modifier = Modifier.size(17.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * 글을 쓰거나 고치는 화면.
 *
 * <p>「등록」을 맨 아래 폭 전체 버튼으로 두면, 본문을 길게 쓴 사람은 다 쓰고도 버튼을
 * 찾아 스크롤해 내려가야 한다. 헤더 오른쪽으로 올려 늘 같은 자리에 둔다.
 *
 * <p>제목·내용에 테두리를 두르지 않는다. 글을 쓰는 화면이 글처럼 보이는 편이 낫고,
 * 칸이 둘뿐이라 어디에 무엇을 쓰는지는 남아 있는 안내 글씨로 충분하다.
 *
 * <p>사진·링크는 키보드 위 툴바로 내렸다. 본문 아래에 두면 글이 길어질수록 멀어진다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentCommunityWriteScreen(
    /** 비어 있으면 새 글, 값이 있으면 그 글을 수정한다. */
    postId: String = "",
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    viewModel: CommunityWriteViewModel = koinViewModel(parameters = { parametersOf(postId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.addAttachment(uri)
    }

    if (state.done) {
        LaunchedEffect(Unit) { onDone() }
    }

    // 제목과 내용이 모두 있어야 올릴 수 있다. 서버가 400 을 돌려주기 전에 버튼이 먼저 말한다.
    val canSubmit = state.title.isNotBlank() && state.content.isNotBlank() &&
        !state.loading && !state.uploading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.parent_back),
                    modifier = Modifier.size(19.dp),
                    tint = colors.text,
                )
            }
            Text(
                text = stringResource(
                    if (state.editing) R.string.parent_community_edit else R.string.parent_community_write,
                ),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 17.sp,
                color = colors.text,
            )
            Spacer(Modifier.weight(1f))
            // 글자만 두면 눌러야 할 곳인지 헷갈린다. 알약으로 채우고, 쓸 수 없을 땐
            // 알약째 회색으로 가라앉힌다.
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (canSubmit) colors.brand else colors.line)
                    .then(if (canSubmit) Modifier.clickable(onClick = viewModel::submit) else Modifier)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (state.editing) R.string.parent_community_edit_submit
                        else R.string.parent_community_write_submit,
                    ),
                    color = if (canSubmit) Color.White else colors.subtle,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.5.sp,
                )
            }
        }
        HorizontalDivider(color = colors.line)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading || state.error != null) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    ParentLoadingOrError(state.loading, state.error)
                }
            }

            PlainField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                hint = stringResource(R.string.parent_community_write_title),
                fontSize = 18.sp,
                lineHeight = 26.sp,
                weight = FontBlack,
                singleLine = true,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.55f))
            PlainField(
                value = state.content,
                onValueChange = viewModel::onContent,
                hint = stringResource(R.string.parent_community_write_body),
                fontSize = 14.5.sp,
                lineHeight = 25.sp,
                weight = FontSemibold,
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .padding(horizontal = 20.dp, vertical = 15.dp),
            )

            if (state.attachments.isNotEmpty() || state.uploading) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(state.attachments) { path ->
                        Box(Modifier.size(74.dp)) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(RemoteUrl.absolute(path))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { viewModel.removeAttachment(path) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.parent_community_attach_remove),
                                    modifier = Modifier.size(10.dp),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                    if (state.uploading) {
                        item {
                            Box(
                                Modifier
                                    .size(74.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(colors.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    color = colors.brand,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            state.links.forEach { link ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_link),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = colors.muted,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = link,
                        modifier = Modifier.weight(1f),
                        color = colors.text,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.removeLink(link) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.parent_community_attach_remove),
                            modifier = Modifier.size(12.dp),
                            tint = colors.muted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // 키보드 바로 위에 붙는 툴바. 본문 아래에 두면 글이 길어질수록 첨부가 멀어진다.
        HorizontalDivider(color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .imePadding()
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(R.drawable.ic_image, R.string.parent_community_attach_photo, enabled = !state.uploading) {
                imageLauncher.launch("image/*")
            }
            Spacer(Modifier.width(10.dp))
            ToolButton(R.drawable.ic_link, R.string.parent_community_attach_link) {
                viewModel.toggleLinkDialog(true)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.parent_community_attach_count,
                    state.attachments.size,
                    state.links.size,
                ),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 11.5.sp,
            )
        }
    }

    if (state.showLinkDialog) {
        LinkSheet(
            onDismiss = { viewModel.toggleLinkDialog(false) },
            onAdd = { url ->
                viewModel.addLink(url)
                viewModel.toggleLinkDialog(false)
            },
        )
    }
}

/**
 * 테두리 없는 입력칸.
 *
 * <p>[KidTextField] 는 라벨과 테두리를 함께 그린다. 글쓰기 화면에서는 그 상자가 글보다
 * 먼저 눈에 들어와, 두 칸을 안내 글씨만 남긴 맨 입력으로 둔다.
 */
@Composable
internal fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = TextStyle(
            fontFamily = KidhackFontFamily,
            fontWeight = weight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = colors.text,
            lineBreak = KidLineBreak,
        ),
        cursorBrush = SolidColor(colors.brand),
        singleLine = singleLine,
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = hint,
                    fontFamily = KidhackFontFamily,
                    fontWeight = weight,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = colors.border,
                )
            }
            inner()
        },
    )
}

/**
 * 툴바 버튼 하나.
 *
 * <p>이름표는 떼고 동그란 회색 자리만 남긴다. 무엇이 담겼는지는 툴바 오른쪽 끝의 개수가
 * 말해 주고, 아이콘 둘뿐이라 사진과 고리는 헷갈릴 여지가 없다. 읽어 주는 이름은
 * `contentDescription` 으로 남는다.
 */
@Composable
internal fun ToolButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.size(18.dp),
            tint = if (enabled) colors.muted else colors.border,
        )
    }
}

/**
 * 링크 추가 시트.
 *
 * <p>전에는 제목과 빈 칸 하나뿐이라 무엇을 붙여 넣어야 하는지 알려 주지 않았다. 한 줄
 * 안내를 넣고, 주소를 넣기 전까지 「추가」를 꺼 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val colors = LocalKidhackColors.current
    var url by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.parent_community_link_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 17.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.parent_community_link_lead),
                color = colors.muted,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
            )
            Spacer(Modifier.height(20.dp))
            KidTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.parent_community_link_label),
                placeholder = "https://",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            KidButton(
                text = stringResource(R.string.parent_community_link_add),
                onClick = { onAdd(url.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = url.isNotBlank(),
            )
        }
    }
}

/** 서버는 ISO-8601 로만 준다. 목록·상세 모두 분 단위까지만 보여 준다. */
private fun String.toDisplayDateTime(): String =
    if (length >= 16) substring(0, 10).replace('-', '.') + " " + substring(11, 16) else this

/**
 * 게시글 링크·첨부를 외부 브라우저로 연다.
 *
 * 첨부는 상대 경로라 origin 을 붙인다. 브라우저가 없는 기기에서는 예외가 나므로 삼킨다.
 */
private fun Context.openExternal(rawUrl: String) {
    val url = RemoteUrl.absolute(rawUrl.filterNot { it.isWhitespace() })
    if (url.isBlank()) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
