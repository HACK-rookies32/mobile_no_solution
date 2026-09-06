package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

@Composable
fun BrandGradientHeader(
    modifier: Modifier = Modifier,
    // 히어로 헤더는 아래 여백을 넉넉히(40dp) 두지만, 채팅처럼 본문 세로 공간이 중요한
    // 화면은 슬림하게 줄여 쓸 수 있게 열어 둔다. 기본값은 기존 동작 그대로.
    contentPadding: PaddingValues = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(colors.brand, colors.brandDark)))
            .statusBarsPadding()
            .padding(contentPadding),
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun BrandGradientHeaderPreview() {
    KidhackTheme {
        BrandGradientHeader {
            Text(
                text = "ROOKIES",
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "안녕하세요 👋",
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 30.sp,
            )
        }
    }
}
