package com.hack.kidhack.feature.child.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.feature.child.components.ChildSubScaffold
import com.hack.kidhack.feature.common.NoticeBody
import com.hack.kidhack.feature.common.NoticeViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 마이페이지 › 공지사항 (자녀).
 *
 * 본문은 부모 화면과 공유한다([NoticeBody]). 여기서는 자녀 스캐폴드만 씌운다.
 */
@Composable
fun ChildNoticeScreen(
    onBack: () -> Unit = {},
    viewModel: NoticeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChildSubScaffold(
        title = stringResource(R.string.child_notice_title),
        onBack = onBack,
    ) {
        NoticeBody(
            state = state,
            onKeyword = viewModel::onKeyword,
            onSearch = viewModel::refresh,
            onOpen = viewModel::open,
            onClose = viewModel::close,
            onLoadMore = viewModel::loadMore,
        )
    }
}
