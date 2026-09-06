package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/**
 * 흰 헤더와 그 아래 콘텐츠를 가르는 구분.
 *
 * <p>주황 그라데이션 헤더를 흰색으로 바꾸면서 헤더가 콘텐츠와 톤이 같아져 경계가 사라졌다.
 * 옛날식 두꺼운 회색 줄 대신 <b>얇은 실선 + 아래로 은은한 그림자</b>로, 헤더가 콘텐츠 위에
 * 얹힌 느낌만 준다. 헤더 Row 바로 아래(스크롤 콘텐츠 위)에 놓는다.
 */
@Composable
fun KidHeaderDivider() {
    val colors = LocalKidhackColors.current
    HorizontalDivider(thickness = 1.dp, color = colors.border)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Transparent))),
    )
}

/**
 * 연회색 캔버스 위에 뜬 흰 카드(그룹 묶음). 메뉴·설정·그룹 목록에서 쓴다.
 *
 * <p>예전에는 흰 배경 위에 테두리만 두른 흰 카드라 대비가 약해 밋밋했다. 배경을 surface
 * (연회색)로 깔고 이 카드를 <b>테두리 대신 은은한 그림자</b>로 띄우면 카드가 확실히 구분된다.
 * 안쪽 줄이 제 여백을 갖도록 좌우 16dp 패딩을 준다(구분선은 그 안에서 폭을 다 쓴다).
 */
@Composable
fun KidSectionCard(
    modifier: Modifier = Modifier,
    // 줄이 스스로 좌우 여백을 갖는 목록(구분선을 카드 폭 끝까지)이면 PaddingValues(0) 을 넘긴다.
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    // 요청 배너처럼 색을 얹어야 하는 카드면 브랜드 연색을 넘긴다. null 이면 흰 카드.
    containerColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor ?: colors.card,
        // 은은하게만 띄운다 — 3dp 는 버튼처럼 그림자가 세 보였다.
        shadowElevation = 1.5.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * 회색 캔버스 위 「라벨 → 카드」 한 덩이.
 *
 * <p>섹션 제목을 카드 <b>안</b>에 넣으면 카드가 어디서 시작하는지 경계가 흐려져, 화면을
 * 훑을 때마다 눈이 다시 판단해야 한다. 제목을 카드 밖 작은 회색 글씨로 빼면 화면이
 * 「라벨 → 카드」 리듬으로 읽힌다. 자녀·부모 탭이 모두 이 규칙을 쓴다.
 */
@Composable
fun KidLabeledSection(
    label: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(modifier) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
        )
        KidSectionCard(contentPadding = contentPadding, content = content)
    }
}
