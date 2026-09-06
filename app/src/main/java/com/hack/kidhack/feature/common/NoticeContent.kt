package com.hack.kidhack.feature.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSearchBar
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Notice
import com.hack.kidhack.domain.model.NoticeDetail
import com.hack.kidhack.domain.repository.NoticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 공지 첨부·링크를 외부 브라우저로 연다.
 *
 * 공지 본문은 운영자가 넣는 값이라 앞뒤 공백이나 "https:// example.com" 처럼
 * 스킴 뒤에 공백이 낀 문자열이 들어올 수 있다. 그대로 [Uri.parse] 하면 처리할 앱을
 * 못 찾아 조용히 실패하므로 공백을 먼저 걷어낸다.
 *
 * 첨부는 상대 경로로 오므로 [RemoteUrl.absolute] 로 origin 을 붙인다.
 *
 * 브라우저가 없는 기기에서는 ActivityNotFoundException 이 나므로 삼킨다
 * ([ContactScreen] 의 메일·전화 인텐트와 같은 처리).
 */
private fun Context.openLink(rawUrl: String) {
    val url = RemoteUrl.absolute(rawUrl.filterNot { it.isWhitespace() })
    if (url.isBlank()) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** 서버는 ISO-8601 로만 준다. 날짜 부분만 잘라 보여 준다. */
private fun String.toDisplayDate(): String = take(10).replace('-', '.').ifBlank { this }

data class NoticeUiState(
    val items: List<Notice> = emptyList(),
    /** 펼친 공지의 상세. 목록 항목에는 본문이 없어 서버에서 따로 받아온다. */
    val opened: NoticeDetail? = null,
    val keyword: String = "",
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * 공지 목록·상세 (명세 23).
 *
 * 부모·자녀 화면이 같은 뷰모델을 쓴다. 목적지마다 별도 인스턴스를 받으므로 펼침 상태는
 * 섞이지 않는다. 조회에 로그인이 필요 없어 자녀 계정에서도 그대로 동작한다.
 *
 * 예전에는 이 파일에 공지 3건이 상수로 박혀 있었다. 서버에 목록 API 가 없던 때의
 * 잔재였고, 지금은 `GET /notices` 가 열려 있다.
 */
class NoticeViewModel(private val repository: NoticeRepository) : ViewModel() {

    private val _state = MutableStateFlow(NoticeUiState())
    val state: StateFlow<NoticeUiState> = _state.asStateFlow()

    init { refresh() }

    fun onKeyword(value: String) = _state.update { it.copy(keyword = value) }

    /** 첫 페이지부터 다시 읽는다. 검색어를 바꿨거나 실패 후 재시도할 때. */
    fun refresh() = load(page = 0, append = false)

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        load(page = current.page + 1, append = true)
    }

    private fun load(page: Int, append: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.list(_state.value.keyword, page) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            loading = false,
                            items = if (append) it.items + result.items else result.items,
                            page = result.page,
                            hasMore = result.hasMore,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message ?: "공지를 불러오지 못했어요") }
                }
        }
    }

    /** 목록 항목에는 본문이 없어서 누를 때 상세를 받아온다. */
    fun open(notice: Notice) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.get(notice.id) }
                .onSuccess { detail -> _state.update { it.copy(loading = false, opened = detail) } }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message ?: "공지를 불러오지 못했어요") }
                }
        }
    }

    fun close() = _state.update { it.copy(opened = null) }
}

/**
 * 공지 목록 또는 펼친 공지 하나를 그린다.
 *
 * 호출부의 스캐폴드 안에 그대로 펼쳐지도록 [ColumnScope] 확장으로 둔다.
 */
@Composable
fun ColumnScope.NoticeBody(
    state: NoticeUiState,
    onKeyword: (String) -> Unit,
    onSearch: () -> Unit,
    onOpen: (Notice) -> Unit,
    onClose: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    val opened = state.opened

    if (opened != null) {
        // 「목록」을 본문 아래 풀폭 버튼으로 두면 다른 상세 페이지와 어긋나고, 그냥 텍스트로
        // 두면 위에 붕 떠 보였다. 테두리 칩으로 만들어 위쪽에 딱 붙는 「누를 것」으로 세운다.
        //
        // 호출부 Column 이 spacedBy 를 걸면 칩·카드 사이에 그 간격이 중복으로 끼어 위쪽을
        // 크게 먹었다. 하나의 Column 으로 감싸 부모 spacedBy 간섭을 끊고 간격을 직접 준다.
        Column {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(99.dp))
                    .clickable(onClick = onClose)
                    .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", color = colors.muted, fontSize = 16.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.notice_back_to_list),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.5.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            NoticeDetailCard(opened)
        }
        return
    }

    KidSearchBar(
        value = state.keyword,
        onValueChange = onKeyword,
        onSearch = onSearch,
        hint = stringResource(R.string.notice_search_hint),
    )

    if (state.error != null) {
        KidCard {
            Text(state.error, color = colors.danger, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            KidButton(
                text = stringResource(R.string.notice_retry),
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth(),
                variant = KidButtonVariant.Secondary,
            )
        }
    }

    // 공지 하나하나를 흰 카드로 — 연회색 캔버스 위에서 떠 보이게(마이페이지 규격).
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.items.forEach { notice ->
            KidSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(notice) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            notice.title,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp,
                            letterSpacing = (-0.3).sp,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(notice.createdAt.toDisplayDate(), color = colors.subtle, fontSize = 11.5.sp)
                            // 첨부·링크는 목록에 개수만 온다. 실물은 상세에서 받는다.
                            val extras = buildList {
                                if (notice.attachmentCount > 0) {
                                    add(stringResource(R.string.notice_attachment_count, notice.attachmentCount))
                                }
                                if (notice.linkCount > 0) {
                                    add(stringResource(R.string.notice_link_count, notice.linkCount))
                                }
                            }
                            extras.forEach { label ->
                                Text(" · ", color = colors.subtle, fontSize = 11.5.sp)
                                Text(label, color = colors.subtle, fontSize = 11.5.sp)
                            }
                        }
                    }
                    Text("›", color = colors.border, fontSize = 17.sp)
                }
            }
        }
    }

    if (state.items.isEmpty() && !state.loading && state.error == null) {
        Text(
            stringResource(R.string.notice_empty),
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }

    if (state.loading) {
        Text(
            stringResource(R.string.notice_loading),
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    if (state.hasMore) {
        KidButton(
            text = stringResource(R.string.notice_load_more),
            onClick = onLoadMore,
            modifier = Modifier.fillMaxWidth(),
            variant = KidButtonVariant.Secondary,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NoticeDetailCard(notice: NoticeDetail) {
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    KidCard {
        // 「공지」 배지 — 게시글 상세의 배지 자리와 같은 위치·모양이라 두 상세가 한 가족처럼 읽힌다.
        Text(
            text = stringResource(R.string.notice_badge),
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(colors.brandLight)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            color = colors.brandDark,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            notice.title,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            color = colors.text,
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_clock),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = colors.subtle,
            )
            Spacer(Modifier.width(5.dp))
            Text(notice.createdAt.toDisplayDate(), color = colors.subtle, fontSize = 12.sp)
        }
        Spacer(Modifier.height(15.dp))
        HorizontalDivider(color = colors.line)
        Spacer(Modifier.height(15.dp))
        Text(notice.content, fontSize = 14.sp, lineHeight = 24.sp, color = colors.text)

        // 서버는 첨부가 이미지인지 알려주지 않는다. 확장자로 갈라 이미지만 미리보기로 띄운다.
        // 썸네일 줄이 아니라 본문 폭을 다 쓰는 한 장씩으로 — 공지 이미지는 대개 안내문 한 장이다.
        val images = notice.attachments.filter { RemoteUrl.isImage(it) }
        val files = notice.attachments - images.toSet()

        images.forEach { path ->
            Spacer(Modifier.height(14.dp))
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(RemoteUrl.absolute(path))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface),
            )
        }

        if (files.isNotEmpty() || notice.links.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            files.forEach { path ->
                AttachmentRow(R.drawable.ic_file, path.substringAfterLast('/')) { context.openLink(path) }
            }
            notice.links.forEach { link ->
                AttachmentRow(R.drawable.ic_link, link) { context.openLink(link) }
            }
        }
    }
}

/** 첨부·링크 한 줄. 이모지 대신 벡터 아이콘 + 테두리 행으로 「누르는 항목」임을 분명히 한다. */
@Composable
private fun AttachmentRow(icon: Int, label: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.brand,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 12.5.sp,
            color = colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(8.dp))
}
