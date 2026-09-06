package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.common.TermsArticleTitles
import com.hack.kidhack.feature.common.TermsBody
import kotlinx.coroutines.launch

/**
 * 마이페이지 › 이용약관 (부모).
 *
 * <p>본문은 자녀 화면과 공유한다([TermsBody]). 여기서는 껍데기와 <b>조 이동 칩</b>을 얹는다.
 *
 * <p>칩은 스크롤 밖에 고정한다. 안에 두면 조금만 내려도 사라져, 조를 건너뛰려는 사람이
 * 다시 맨 위로 올라가야 한다.
 */
@Composable
fun ParentTermsScreen(onBack: () -> Unit = {}) {
    val colors = LocalKidhackColors.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // 조가 놓인 세로 위치. 본문이 그려질 때 채워진다.
    val articleTops = remember { mutableStateMapOf<Int, Int>() }
    var current by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            // \uce74\ub4dc\uad00\ub9ac \ub4f1 \ub2e4\ub978 \uc11c\ube0c \ud398\uc774\uc9c0\uc640 \uac19\uc740 \ud5e4\ub354 \uaddc\uaca9(\uc704 12\u00b7\uc544\ub798 4).
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2039", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_terms_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
        }
        KidHeaderDivider()

        // 법무 문서에서 가장 먼저 확인하는 값이라 맨 위에 한 줄로 둔다.
        Text(
            text = stringResource(R.string.terms_revision),
            modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 14.dp),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TermsArticleTitles.forEachIndexed { index, title ->
                val on = index == current
                Text(
                    text = title,
                    modifier = Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (on) colors.text else colors.surface)
                        .clickable {
                            current = index
                            scope.launch { articleTops[index]?.let { scrollState.animateScrollTo(it) } }
                        }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                    color = if (on) Color.White else colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp),
        ) {
            TermsBody { index, y -> articleTops[index] = y }
            Spacer(Modifier.height(28.dp))
        }
    }
}
