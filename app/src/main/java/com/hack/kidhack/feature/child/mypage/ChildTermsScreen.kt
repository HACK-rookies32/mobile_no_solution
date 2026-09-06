package com.hack.kidhack.feature.child.mypage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hack.kidhack.R
import com.hack.kidhack.feature.child.components.ChildSubScaffold
import com.hack.kidhack.feature.common.TermsBody

/**
 * 마이페이지 › 이용약관 (자녀).
 *
 * 본문은 부모 화면과 공유한다([TermsBody]). 여기서는 자녀 스캐폴드만 씌운다.
 */
@Composable
fun ChildTermsScreen(onBack: () -> Unit = {}) {
    ChildSubScaffold(
        title = stringResource(R.string.child_terms_title),
        onBack = onBack,
    ) {
        TermsBody()
    }
}
