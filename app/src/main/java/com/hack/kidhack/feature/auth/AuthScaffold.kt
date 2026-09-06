package com.hack.kidhack.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/**
 * 로그인·계정 찾기·회원가입이 함께 쓰는 흰 화면.
 *
 * <p>전에는 주황 그라데이션 헤더 아래 회색 시트였다. 색 띠가 화면의 3분의 1을 먹는데
 * 거기 담긴 것은 인사말뿐이라, 정작 할 일(입력)이 아래로 밀려 있었다. 색을 걷어내면
 * 제목이 화면의 첫 목소리가 되고, 앱 안쪽 화면들과도 같은 언어가 된다.
 *
 * <p>[header] 는 제목 묶음, [content] 는 그 아래 폼이다. 둘 다 같은 24dp 여백에 서고
 * 왼쪽으로 정렬된다 — 가운데 정렬은 읽는 눈이 줄마다 시작점을 다시 찾게 만든다.
 */
@Composable
fun AuthScaffold(
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            // 상태 표시줄·내비게이션 바·키보드를 한 번에 피한다.
            //
            // 앱이 edge-to-edge 라 창이 키보드에 맞춰 줄지 않는다. 이 여백이 없으면 아래쪽
            // 입력칸을 누를 때 키보드가 그 칸을 덮은 채로 타자를 치게 되고, 「다음」 버튼은
            // 갤럭시의 제스처 바 아래에 깔려 눌리지 않는다.
            //
            // 스크롤보다 **먼저** 걸어야 한다. 그래야 키보드가 올라올 때 스크롤 영역 자체가
            // 줄어들어, 초점이 간 칸이 보이는 자리로 따라 올라온다.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp),
            content = header,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/**
 * 왼쪽에 붙는 두세 갈래 탭.
 *
 * <p>알약 토글이 아니라 밑줄 탭인 이유: 알약은 켜고 끄는 스위치로 읽히는데, 여기서
 * 고르는 것은 **아래 폼이 무엇에 대한 것인지**다. 밑줄이 탭과 폼을 한 덩어리로 묶는다.
 */
@Composable
fun AuthTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    Column(modifier) {
        // 화면을 똑같이 나눠 갖는다. 글자 폭에 맞추면 「부모님」과 「자녀」의 누를 수 있는
        // 넓이가 서로 달라져, 짧은 쪽이 누르기 어려워진다.
        Row(Modifier.fillMaxWidth()) {
            titles.forEachIndexed { index, title ->
                val active = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(vertical = 11.dp),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 15.sp,
                        color = if (active) colors.text else colors.muted,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (active) colors.brand else Color.Transparent),
                    )
                }
            }
        }
        // 고르지 않은 탭 아래에도 선이 이어져야 탭 줄이 어디까지인지 보인다.
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}
