package com.hack.kidhack.feature.child.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/**
 * 자녀 탭 화면 껍데기 — 부모쪽과 같은 규격.
 *
 * <p>예전 [ChildScreenScaffold] 는 주황 그라데이션 히어로 + 겹치는 흰 시트였다. 아이 화면이라
 * 화려했지만, 화면마다 색이 꽉 차 정보가 묻히고 부모쪽과 두 앱처럼 보였다. 여기서는 부모의
 * 규격을 그대로 따른다 — 흰 헤더(제목 21 Black) + 구분선, 그 아래 회색 캔버스에 흰 카드.
 * 아이다움은 색을 꽉 채우는 대신 홈 히어로의 마스코트·상태색 하나로만 준다.
 */
@Composable
fun ChildTabScaffold(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 21.sp,
                color = colors.text,
                // 오른쪽에 붙는 것이 많은 탭에서 제목이 두 줄로 접히면 헤더 높이가 탭마다
                // 달라진다. 탭을 옮길 때 제목이 들썩이는 것보다는 말줄임이 낫다.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            action?.invoke(this)
        }
        KidHeaderDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.surface)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * 자녀 서브 화면 껍데기 — 뒤로가기 있는 상세. 부모 서브 페이지와 같은 규격(‹ 40dp + 제목 18 Bold).
 */
@Composable
fun ChildSubScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
    /**
     * 화면 맨 아래에 붙박이로 앉는 줄. 「저장」·「연결하기」처럼 그 화면의 결론이 되는 버튼용이다.
     *
     * <p>스크롤 안에 두면 폼이 길어질 때 버튼이 화면 밖으로 사라져, 다 채우고도 어디를 눌러야
     * 하는지 찾아 내려가야 한다. 키보드가 올라오면 이 줄도 함께 올라온다.
     */
    bottomBar: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
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
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
            action?.invoke(this)
        }
        KidHeaderDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.surface)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (bottomBar != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card)
                    // 탭 껍데기가 이미 비워 둔 만큼은 소비됐으므로 모자란 만큼만 더한다.
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                Column(modifier = Modifier.padding(16.dp)) { bottomBar() }
            }
        }
    }
}
