package com.hack.kidhack.feature.parent

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.domain.model.Activity
import com.hack.kidhack.domain.model.ActivityType
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

/**
 * 내 활동 내역 (`GET /users/me/activities`).
 *
 * 커뮤니티에서 내가 쓴 글 · 단 댓글 · 누른 좋아요를 종류별로 본다.
 *
 * 서버는 `type`(`POST` / `COMMENT` / `LIKE`)을 **필수**로 받는다. 미전달 시 400 이라
 * 「전체」 탭은 만들 수 없다 — 세 종류 중 하나를 항상 고른 상태로 둔다.
 */
class ActivityViewModel(
    userRepository: UserRepository,
) : ViewModel() {

    // 서버가 type 을 필수로 받아 「전체」 조회가 없다. 기본값은 작성한 글.
    private val _filter = MutableStateFlow(ActivityType.POST)
    val filter: StateFlow<ActivityType> = _filter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activities: Flow<PagingData<Activity>> =
        _filter
            .flatMapLatest { type -> userRepository.pagedActivities(type) }
            .cachedIn(viewModelScope)

    fun selectFilter(type: ActivityType) = _filter.update { type }
}

/**
 * 내 활동 본문 — 내가 쓴 글·댓글·좋아요.
 *
 * <p>커뮤니티 활동 기록이라 커뮤니티 탭 안에 있다. 계정 설정에 두면 「내 계정을 손보는
 * 일」과 섞인다.
 */
@Composable
internal fun ColumnScope.ActivityBody(
    filter: ActivityType,
    activities: LazyPagingItems<Activity>,
    onFilter: (ActivityType) -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(Modifier.weight(1f).fillMaxWidth().background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(stringResource(R.string.parent_activity_filter_post), filter == ActivityType.POST) {
                onFilter(ActivityType.POST)
            }
            FilterChip(stringResource(R.string.parent_activity_filter_comment), filter == ActivityType.COMMENT) {
                onFilter(ActivityType.COMMENT)
            }
            FilterChip(stringResource(R.string.parent_activity_filter_like), filter == ActivityType.LIKE) {
                onFilter(ActivityType.LIKE)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val refresh = activities.loadState.refresh
            when {
                refresh is LoadState.Loading && activities.itemCount == 0 ->
                    item { ParentLoadingOrError(true, null) }
                refresh is LoadState.Error ->
                    item {
                        ParentLoadingOrError(
                            false,
                            refresh.error.message.orEmpty(),
                            onRetry = { activities.retry() },
                        )
                    }
                refresh is LoadState.NotLoading && activities.itemCount == 0 ->
                    item {
                        Text(
                            stringResource(R.string.parent_activity_empty),
                            color = colors.muted,
                            fontSize = 13.sp,
                        )
                    }
            }

            items(
                count = activities.itemCount,
                key = activities.itemKey { it.id },
            ) { index ->
                activities[index]?.let { activity ->
                    ActivityRow(activity, onOpenPost)
                }
            }

            if (activities.loadState.append is LoadState.Loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.parent_activity_loading_more), color = colors.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


/**
 * 내 활동 한 줄.
 *
 * <p>이모지(📝❤️)를 아이콘으로 쓰고 있었다. 앱의 다른 화면은 벡터 아이콘을 쓰는데 여기만
 * 이모지라 붕 떴다. 종류별 색 타일에 벡터 아이콘을 담고, 오른쪽 「›」로 「눌러서 그 글로
 * 간다」는 것을 알린다 — 실제로 누르면 글로 가므로 화살표가 기능과 맞다.
 */
@Composable
private fun ActivityRow(activity: Activity, onOpenPost: (String) -> Unit) {
    val colors = LocalKidhackColors.current
    // 글이 지워졌으면 postId 가 없다. 그때는 눌러도 갈 곳이 없으므로 클릭을 막는다.
    val postId = activity.postId?.toString()
    val modifier = if (postId != null) Modifier.clickable { onOpenPost(postId) } else Modifier
    val tint = activity.type.tint(colors)

    KidSectionCard(modifier = modifier, contentPadding = PaddingValues(13.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(activity.type.iconRes()),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = tint,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activity.preview.ifBlank { stringResource(activity.type.labelRes()) },
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${stringResource(activity.type.labelRes())} · ${activity.createdAtLabel.toDateLabel()}",
                    color = colors.muted,
                    fontSize = 11.5.sp,
                )
            }
            // 글이 살아 있어 이동 가능한 행에만 화살표를 둔다.
            if (postId != null) {
                Text("›", color = colors.subtle, fontSize = 18.sp)
            }
        }
    }
}

/**
 * 필터 칩.
 *
 * <p>안 고른 칩 배경이 회색(surface)이었는데, 목록 캔버스도 회색이라 칩이 묻혀 글자만
 * 떠 보였다. 안 고른 칩을 흰 카드 + 테두리로 바꿔 「누를 수 있는 칩」으로 세운다.
 */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.brand else colors.card)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, colors.border, RoundedCornerShape(999.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Color.White else colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

private fun ActivityType.iconRes(): Int = when (this) {
    ActivityType.POST -> R.drawable.ic_pencil
    ActivityType.COMMENT -> R.drawable.ic_chat
    ActivityType.LIKE -> R.drawable.ic_heart
    ActivityType.UNKNOWN -> R.drawable.ic_pencil
}

/** 종류별 색: 글=브랜드, 댓글=정보(파랑), 좋아요=안전(초록). 타일은 이 색의 옅은 배경. */
private fun ActivityType.tint(colors: com.hack.kidhack.core.designsystem.theme.KidhackColors): Color = when (this) {
    ActivityType.POST -> colors.brand
    ActivityType.COMMENT -> colors.info
    ActivityType.LIKE -> colors.safe
    ActivityType.UNKNOWN -> colors.muted
}

private fun ActivityType.labelRes(): Int = when (this) {
    ActivityType.POST -> R.string.parent_activity_filter_post
    ActivityType.COMMENT -> R.string.parent_activity_filter_comment
    ActivityType.LIKE -> R.string.parent_activity_filter_like
    // 서버가 새 유형을 추가해도 목록이 깨지지 않도록 둔다. 필터로는 쓰이지 않는다.
    ActivityType.UNKNOWN -> R.string.parent_activity_type_unknown
}

/** 서버는 오프셋 없는 ISO LocalDateTime 을 준다. 목록에서는 날짜와 시:분까지만 보여 준다. */
private fun String.toDateLabel(): String {
    if (length < 16) return this
    return substring(0, 10) + " " + substring(11, 16)
}
