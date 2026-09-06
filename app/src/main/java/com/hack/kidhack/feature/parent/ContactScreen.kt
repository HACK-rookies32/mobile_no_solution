package com.hack.kidhack.feature.parent

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryDetail
import com.hack.kidhack.domain.repository.InquiryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 화면 상태.
 *
 * 예전에는 이메일·전화·FAQ 타일이 깔린 시작 화면(GRID)이 있었는데, 이메일 주소·전화번호가
 * 가짜였고 FAQ 도 상수 두 줄이었다. 실제로 서버와 오가는 건 1:1 문의뿐이라 목록을
 * 첫 화면으로 둔다.
 */
enum class ContactPane { INQUIRY_LIST, INQUIRY_CREATE, INQUIRY_DETAIL }

data class ContactUiState(
    val pane: ContactPane = ContactPane.INQUIRY_LIST,
    val inquiries: List<Inquiry> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    /** 펼친 문의. 목록 항목에는 본문·첨부·답변이 없어 서버에서 따로 받아온다. */
    val detail: InquiryDetail? = null,
    val title: String = "",
    val content: String = "",
    /** 업로드를 마친 첨부의 **공개 경로**. 갤러리 URI 가 아니다. */
    val attachments: List<String> = emptyList(),
    /** 서버는 링크를 하나만 받는다. */
    val linkUrl: String = "",
    /** 수정 중인 문의 id. null 이면 새 문의다. */
    val editingId: String? = null,
    val uploading: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * 고객센터 — 1:1 문의 (명세 21).
 *
 * 첨부는 `POST /inquiries/upload` 로 **먼저 올려** 공개 경로를 받고, 그 경로만 문의
 * 본문에 실어 보낸다. 예전에는 갤러리에서 고른 `content://` URI 를 그대로 보냈는데
 * 서버에는 그런 파일이 없다.
 *
 * 답변이 달린 문의는 서버가 수정·삭제를 400 으로 막으므로 버튼도 감춘다.
 */
class ContactViewModel(
    private val inquiryRepository: InquiryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ContactUiState())
    val state: StateFlow<ContactUiState> = _state.asStateFlow()

    init { loadInquiries(page = 0, append = false) }

    fun open(pane: ContactPane) {
        _state.update { it.copy(pane = pane, error = null) }
        if (pane == ContactPane.INQUIRY_LIST) loadInquiries(page = 0, append = false)
    }

    /** 목록 항목을 누르면 본문을 받으러 간다. */
    fun openDetail(inquiry: Inquiry) {
        _state.update { it.copy(pane = ContactPane.INQUIRY_DETAIL, detail = null, loading = true, error = null) }
        viewModelScope.launch {
            runCatching { inquiryRepository.get(inquiry.id) }
                .onSuccess { detail -> _state.update { it.copy(detail = detail, loading = false) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        loadInquiries(page = current.page + 1, append = true)
    }

    private fun loadInquiries(page: Int, append: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { inquiryRepository.list(page) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            inquiries = if (append) it.inquiries + result.items else result.items,
                            page = result.page,
                            hasMore = result.hasMore,
                            loading = false,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message, loading = false) } }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onContent(v: String) = _state.update { it.copy(content = v) }
    fun onLinkUrl(v: String) = _state.update { it.copy(linkUrl = v) }

    /** 새 문의 작성 화면. 이전에 쓰던 내용이 남지 않도록 폼을 비운다. */
    fun startCreate() {
        _state.update {
            it.copy(
                pane = ContactPane.INQUIRY_CREATE,
                editingId = null,
                title = "",
                content = "",
                attachments = emptyList(),
                linkUrl = "",
                error = null,
            )
        }
    }

    /** 상세에서 수정으로. 답변 전에만 부른다. */
    fun startEdit() {
        val detail = _state.value.detail ?: return
        _state.update {
            it.copy(
                pane = ContactPane.INQUIRY_CREATE,
                editingId = detail.id,
                title = detail.title,
                content = detail.content,
                attachments = detail.attachments,
                linkUrl = detail.linkUrl.orEmpty(),
                error = null,
            )
        }
    }

    /** 고른 파일을 곧바로 업로드하고 공개 경로만 들고 있는다. */
    fun addAttachment(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            runCatching { inquiryRepository.uploadAttachment(uri.toString()) }
                .onSuccess { url -> _state.update { it.copy(attachments = it.attachments + url, uploading = false) } }
                .onFailure { t ->
                    _state.update { it.copy(uploading = false, error = t.message ?: "첨부 업로드에 실패했어요") }
                }
        }
    }

    /** 목록에서만 뺀다. 서버의 실물은 문의를 저장할 때 정리된다. */
    fun removeAttachment(url: String) = _state.update { it.copy(attachments = it.attachments - url) }

    fun submit() {
        val current = _state.value
        if (current.title.isBlank() || current.content.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val id = current.editingId
                if (id == null) {
                    inquiryRepository.create(current.title, current.content, current.attachments, current.linkUrl)
                } else {
                    inquiryRepository.update(id, current.title, current.content, current.attachments, current.linkUrl)
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        loading = false,
                        title = "",
                        content = "",
                        attachments = emptyList(),
                        linkUrl = "",
                        editingId = null,
                    )
                }
                open(ContactPane.INQUIRY_LIST)
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun delete() {
        val id = _state.value.detail?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { inquiryRepository.delete(id) }
                .onSuccess {
                    _state.update { it.copy(loading = false, detail = null) }
                    open(ContactPane.INQUIRY_LIST)
                }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }
}

/**
 * 1:1 문의.
 *
 * <p>목록·작성·상세가 한 화면 안에서 갈린다(`pane`). 헤더 왼쪽이 <b>✕</b> 였는데, 마이페이지
 * 에서 들어온 화면이라 닫기 표시는 어디로 돌아가는지 알려 주지 않는다. 뒤로 가기로 바꿨다.
 */
@Composable
fun ParentContactScreen(
    onBack: () -> Unit = {},
    viewModel: ContactViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.addAttachment(uri)
    }

    // 안쪽 화면에서 뒤로 가면 목록으로. 여기서 막지 않으면 문의를 쓰다 말고 화면이 닫힌다.
    BackHandler(enabled = state.pane != ContactPane.INQUIRY_LIST) {
        viewModel.open(ContactPane.INQUIRY_LIST)
    }
    val leaveInner = { viewModel.open(ContactPane.INQUIRY_LIST) }

    // 작성 화면은 「글 쓰는 종이」라 흰 배경, 목록·상세는 회색 캔버스.
    val creating = state.pane == ContactPane.INQUIRY_CREATE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (creating) colors.card else colors.surface)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxWidth().background(colors.card)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { if (state.pane == ContactPane.INQUIRY_LIST) onBack() else leaveInner() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.pane == ContactPane.INQUIRY_CREATE) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.parent_back),
                            modifier = Modifier.size(19.dp),
                            tint = colors.text,
                        )
                    } else {
                        Text("‹", color = colors.text, fontSize = 26.sp)
                    }
                }
                // 헤더에 제목이 늘 보이게 한다 — 전에는 목록 화면에서 제목이 본문 안(구분선 아래)에 있어
                // 헤더가 비어 보였다.
                Text(
                    text = when (state.pane) {
                        ContactPane.INQUIRY_CREATE -> stringResource(
                            if (state.editingId == null) R.string.parent_contact_create
                            else R.string.parent_contact_edit,
                        )
                        else -> stringResource(R.string.parent_contact_title)
                    },
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 18.sp,
                    color = colors.text,
                )
                Spacer(Modifier.weight(1f))
                when (state.pane) {
                    // 폭 전체 「+ 새로운 문의하기」 버튼이 목록 위를 가로막고 있었다.
                    ContactPane.INQUIRY_LIST -> HeaderAction(
                        label = stringResource(R.string.parent_contact_create),
                        enabled = true,
                        onClick = viewModel::startCreate,
                    )
                    ContactPane.INQUIRY_CREATE -> HeaderAction(
                        label = stringResource(R.string.parent_contact_submit),
                        enabled = state.title.isNotBlank() && state.content.isNotBlank() &&
                            !state.loading && !state.uploading,
                        onClick = viewModel::submit,
                    )
                    ContactPane.INQUIRY_DETAIL -> Unit
                }
            }
            KidHeaderDivider()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            if (state.error != null) {
                Text(
                    state.error.orEmpty(),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = colors.danger,
                    fontSize = 13.sp,
                )
            }
            when (state.pane) {
                ContactPane.INQUIRY_LIST -> InquiryList(state, viewModel)
                ContactPane.INQUIRY_CREATE -> InquiryForm(state, viewModel, context)
                ContactPane.INQUIRY_DETAIL -> InquiryDetailBody(state, viewModel, context)
            }
            Spacer(Modifier.height(24.dp))
        }

        // 키보드 바로 위에 붙는 툴바(작성 화면만). 사진은 여기서 넣는다 — 커뮤니티 글쓰기와 같은 포맷.
        if (creating) {
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
                    fileLauncher.launch("image/*")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.parent_contact_photo_count, state.attachments.size),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 11.5.sp,
                )
            }
        }
    }
}

/** 헤더 오른쪽 글자 버튼. 쓸 수 없을 때는 눌리지 않고 색만 가라앉는다. */
@Composable
private fun HeaderAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        color = if (enabled) colors.brandDark else colors.border,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 14.sp,
    )
}

/* ---- 목록 ---- */

@Composable
private fun ColumnScope.InquiryList(state: ContactUiState, viewModel: ContactViewModel) {
    val colors = LocalKidhackColors.current

    if (state.inquiries.isEmpty() && !state.loading) {
        Text(
            stringResource(R.string.parent_contact_empty),
            modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp),
            color = colors.muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }

    // 문의 하나 = 카드 하나. 예전엔 구분선으로만 나눠 목록이 밋밋했다.
    state.inquiries.forEach { inquiry ->
        KidSectionCard(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.openDetail(inquiry) }
                    .padding(vertical = 15.dp),
            ) {
                // 상태를 제목 위로 올린다. 목록을 훑을 때 먼저 알고 싶은 것이 「답이 왔나」다.
                AnswerState(answered = inquiry.answered)
                Spacer(Modifier.height(7.dp))
                Text(
                    inquiry.title,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    letterSpacing = (-0.3).sp,
                    color = colors.text,
                    style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
                )
                Spacer(Modifier.height(6.dp))
                Text(inquiry.createdAt.toDisplayDate(), color = colors.subtle, fontSize = 11.5.sp, fontWeight = FontSemibold)
            }
        }
    }

    if (state.hasMore) {
        Text(
            text = stringResource(R.string.parent_contact_load_more),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::loadMore)
                .padding(vertical = 16.dp),
            color = colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** 답변 상태 — 점 하나와 글자. 알약보다 가벼워 제목을 누르지 않는다. */
@Composable
private fun AnswerState(answered: Boolean) {
    val colors = LocalKidhackColors.current
    val tint = if (answered) colors.safe else colors.subtle
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (answered) R.string.parent_contact_answered else R.string.parent_contact_waiting),
            color = tint,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 11.5.sp,
        )
    }
}

/* ---- 작성 ---- */

@Composable
private fun ColumnScope.InquiryForm(
    state: ContactUiState,
    viewModel: ContactViewModel,
    context: android.content.Context,
) {
    val colors = LocalKidhackColors.current

    // 커뮤니티 글쓰기와 같은 에디토리얼 포맷 — 테두리 없는 제목·본문, 사진은 하단 툴바.
    PlainField(
        value = state.title,
        onValueChange = viewModel::onTitle,
        hint = stringResource(R.string.parent_contact_field_title_hint),
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
        hint = stringResource(R.string.parent_contact_field_content_hint),
        fontSize = 14.5.sp,
        lineHeight = 25.sp,
        weight = FontSemibold,
        singleLine = false,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .padding(horizontal = 20.dp, vertical = 15.dp),
    )

    // 첨부 사진 — 넣은 것만 가로줄로. 추가는 하단 툴바에서.
    if (state.attachments.isNotEmpty() || state.uploading) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(state.attachments) { path ->
                Box(Modifier.size(74.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
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
                        Modifier.size(74.dp).clip(RoundedCornerShape(11.dp)).background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(Modifier.size(18.dp), color = colors.brand, strokeWidth = 2.dp) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    // 링크(선택) — 본문 아래 얇은 인라인 줄. 서버는 링크를 하나만 받는다.
    HorizontalDivider(color = colors.line, modifier = Modifier.padding(top = 8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_link),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = colors.muted,
        )
        Spacer(Modifier.width(9.dp))
        PlainField(
            value = state.linkUrl,
            onValueChange = viewModel::onLinkUrl,
            hint = stringResource(R.string.parent_contact_field_link_hint),
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            weight = FontSemibold,
            singleLine = true,
            modifier = Modifier.weight(1f).padding(vertical = 2.dp),
        )
    }
}

/* ---- 상세 ---- */

@Composable
private fun ColumnScope.InquiryDetailBody(
    state: ContactUiState,
    viewModel: ContactViewModel,
    context: android.content.Context,
) {
    val colors = LocalKidhackColors.current
    val detail = state.detail
    if (detail == null) {
        Text(
            stringResource(if (state.loading) R.string.parent_contact_loading else R.string.parent_contact_load_failed),
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            color = colors.muted,
            fontSize = 13.sp,
        )
        return
    }

    // 질문 한 덩이 — 상태 배지 · 제목 · 접수일 · <구분선> · 본문 · 첨부를 흰 카드 하나에 담는다.
    // 전에는 이 모두가 회색 캔버스에 여백만으로 흘러, 제목(21 Black)과 본문(14.5)이 크기 차이로만
    // 갈렸다. 카드가 경계를 만들고, 제목과 본문 사이 얇은 실선이 「여기까지 제목」을 못박는다.
    KidSectionCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        StatusChip(answered = detail.answered)
        Spacer(Modifier.height(12.dp))
        // 제목은 21 Black -> 18 Bold 로 낮춘다. 21 은 헤더(화면 이름)만큼 커서, 본문으로 이어지는
        // 글의 머리치고는 과했다.
        Text(
            detail.title,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.4).sp,
            color = colors.text,
            style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
        )
        Spacer(Modifier.height(8.dp))
        // 자기 문의만 보므로 작성자는 적지 않는다. 기다리는 사람이 알고 싶은 건 「언제 접수됐나」다.
        Text(
            stringResource(R.string.parent_contact_received, detail.createdAt.toDisplayDate()),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = colors.line)
        Spacer(Modifier.height(16.dp))
        Text(
            detail.content,
            color = colors.text,
            fontSize = 14.5.sp,
            lineHeight = 25.sp,
            style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
        )

        if (detail.attachments.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detail.attachments.forEach { path ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(RemoteUrl.absolute(path))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }

        if (!detail.linkUrl.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .clickable {
                        val url = RemoteUrl.absolute(detail.linkUrl.filterNot { ch -> ch.isWhitespace() })
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.muted,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    detail.linkUrl,
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    // 답변이 달리면 서버가 수정/삭제를 막는다(400). 그때는 버튼을 감춘다.
    if (!detail.answered) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.parent_contact_edit),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::startEdit)
                    .padding(vertical = 4.dp),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
            Text(
                stringResource(R.string.parent_contact_delete),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::delete)
                    .padding(vertical = 4.dp),
                color = colors.danger,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
        }
    } else {
        Spacer(Modifier.height(12.dp))
    }

    val answer = detail.answer
    if (answer != null) {
        // 답변은 브랜드 연색 카드에 담아 내 질문(흰 카드)과 확실히 가른다. 고객센터 아바타 +
        // 이름이 붙어, 카드 제목을 읽지 않아도 누가 한 말인지 안다.
        KidSectionCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = colors.brandLight,
            contentPadding = PaddingValues(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chat),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.parent_contact_answer_from),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 13.sp,
                        color = colors.brandDark,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        stringResource(R.string.parent_contact_answered_at, answer.createdAt.toDisplayDate()),
                        color = colors.brandDark.copy(alpha = 0.7f),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                answer.content,
                color = colors.text,
                fontSize = 14.sp,
                lineHeight = 24.sp,
                style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * 답변 여부를 색 있는 알약으로. 색만으로 나누지 않도록 점 + 글자를 함께 둔다.
 * 대기 = 호박색, 완료 = 초록.
 */
@Composable
private fun StatusChip(answered: Boolean) {
    val colors = LocalKidhackColors.current
    val bg = if (answered) colors.safe.copy(alpha = 0.12f) else colors.warn.copy(alpha = 0.14f)
    val fg = if (answered) colors.safe else colors.warn
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (answered) R.string.parent_contact_answered else R.string.parent_contact_waiting),
            color = fg,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 11.5.sp,
        )
    }
}

/** 서버는 ISO-8601 로만 준다. 목록·상세 모두 날짜 부분만 보여 준다. */
private fun String.toDisplayDate(): String = take(10).replace('-', '.').ifBlank { this }
