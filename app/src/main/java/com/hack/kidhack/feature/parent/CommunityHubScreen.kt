package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.common.NoticeBody
import com.hack.kidhack.feature.common.NoticeViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 커뮤니티 탭.
 *
 * <p>하단 탭에서 알림이 빠진 자리를 받았다. 전에는 커뮤니티·공지사항·활동 내역이 마이페이지
 * 목록 세 줄로 흩어져 있어, 글을 읽으러 들어가는 데 두 단계가 걸렸다.
 *
 * <p>셋을 한 화면의 갈래로 묶는다. 「읽을거리」라는 점에서 같은 성격이고, 활동 내역은 내가
 * 쓴 글·댓글·좋아요라 커뮤니티 안에 있어야 맥락이 이어진다.
 *
 * <p>탭마다 뷰모델이 따로 있고 **고른 탭만 그린다.** 셋을 한꺼번에 띄우면 들어오자마자
 * 요청이 세 번 나간다.
 */
@Composable
fun ParentCommunityHubScreen(
    onOpenPost: (String) -> Unit = {},
    onWrite: () -> Unit = {},
    communityViewModel: CommunityListViewModel = koinViewModel(),
    noticeViewModel: NoticeViewModel = koinViewModel(),
    activityViewModel: ActivityViewModel = koinViewModel(),
) {
    val colors = LocalKidhackColors.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        // 제목 줄 높이를 40dp 로 맞춘다. 자녀관리는 제목 옆에 40dp 종 버튼이 있어 줄이
        // 그만큼 높고, 21sp 글씨가 그 안에서 가운데로 내려앉는다. 버튼이 없는 화면은 줄이
        // 글씨 높이만큼만이라 제목이 상태바에 바짝 붙어 보였다 — 같은 높이를 직접 준다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.parent_community_hub_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 21.sp,
                color = colors.text,
            )
        }
        HubTabs(
            titles = listOf(
                stringResource(R.string.parent_community_tab_board),
                stringResource(R.string.parent_community_tab_notice),
                stringResource(R.string.parent_community_tab_activity),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        when (tab) {
            0 -> {
                val posts = communityViewModel.posts.collectAsLazyPagingItems()
                // 글을 쓰거나 상세에서 돌아오면 목록을 다시 읽는다(첫 진입은 페이징이 알아서).
                LaunchedEffect(Unit) {
                    if (communityViewModel.shownOnce) posts.refresh()
                    communityViewModel.shownOnce = true
                }
                val keyword by communityViewModel.keyword.collectAsStateWithLifecycle()
                CommunityListBody(
                    posts = posts,
                    keyword = keyword,
                    onKeyword = communityViewModel::onKeyword,
                    onSearch = communityViewModel::search,
                    onOpenPost = onOpenPost,
                    onWrite = onWrite,
                )
            }
            1 -> {
                val state by noticeViewModel.state.collectAsStateWithLifecycle()
                // 공지 본문은 스캐폴드가 대 주던 여백·스크롤을 그대로 기대한다(자녀 화면과 공유).
                // 검색바 위치를 게시판 탭과 똑같이 맞춘다 — 게시판은 검색바에 가로 20·위 12 를
                // 주는데, 공지 탭은 여기 Column 패딩에 기대므로 그 값을 그대로 맞춰 준다.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.surface)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NoticeBody(
                        state = state,
                        onKeyword = noticeViewModel::onKeyword,
                        onSearch = noticeViewModel::refresh,
                        onOpen = noticeViewModel::open,
                        onClose = noticeViewModel::close,
                        onLoadMore = noticeViewModel::loadMore,
                    )
                }
            }
            else -> {
                val filter by activityViewModel.filter.collectAsStateWithLifecycle()
                val activities = activityViewModel.activities.collectAsLazyPagingItems()
                ActivityBody(
                    filter = filter,
                    activities = activities,
                    onFilter = activityViewModel::selectFilter,
                    onOpenPost = onOpenPost,
                )
            }
        }
    }
}

/**
 * 세 갈래 탭.
 *
 * <p>글자 폭만큼만 차지하고 왼쪽에 붙는다. 자녀관리·사용 리포트와 같은 모양이라 앱 안에서
 * 「탭」이 늘 같은 뜻으로 읽힌다.
 */
@Composable
private fun HubTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            titles.forEachIndexed { index, title ->
                val active = index == selected
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(vertical = 9.dp),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 15.sp,
                        color = if (active) colors.text else colors.muted,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (active) colors.brand else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}