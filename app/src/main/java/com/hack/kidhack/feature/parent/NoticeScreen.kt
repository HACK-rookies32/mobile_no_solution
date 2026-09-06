package com.hack.kidhack.feature.parent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.feature.common.NoticeBody
import com.hack.kidhack.feature.common.NoticeViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 마이페이지 › 공지사항 (부모).
 *
 * 본문은 자녀 화면과 공유한다([NoticeBody]). 여기서는 부모 스캐폴드만 씌운다.
 */
@Composable
fun ParentNoticeScreen(
    onBack: () -> Unit = {},
    viewModel: NoticeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_notice_title), onBack) },
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
