package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/**
 * 자녀를 고르는 칩 줄.
 *
 * <p>부모 화면의 거의 모든 설정이 **자녀 한 명 기준**이다(앱 통제·스크린타임·캡처·사용량).
 * 어느 자녀를 보고 있는지 화면마다 다르게 보이면 부모가 다른 아이 설정을 건드린다.
 * 대시보드·마이페이지·앱 통제가 같은 줄을 쓴다.
 *
 * <p>자녀가 하나뿐이면 고를 것이 없으므로 그리지 않는다.
 *
 * @param onDark 헤더처럼 브랜드 색 위에 얹을 때. 안 고른 칩의 대비를 바꾼다.
 */
@Composable
fun ChildChips(
    children: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    if (children.size < 2) return
    val colors = LocalKidhackColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        children.forEach { (id, name) ->
            val selected = id == selectedId
            val background = when {
                selected && onDark -> Color.White
                selected -> colors.brand
                onDark -> Color.White.copy(alpha = 0.2f)
                else -> colors.surface
            }
            val textColor = when {
                selected && onDark -> colors.brand
                selected -> Color.White
                onDark -> Color.White
                else -> colors.text
            }
            Text(
                text = name,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(background)
                    .then(
                        // 안 고른 칩은 배경색이 옅어서 회색 화면 위에서는 칩인지 글자인지
                        // 구분되지 않는다. 테두리를 둘러 어느 배경에서든 눌리는 것으로 보이게 한다.
                        if (selected || onDark) Modifier
                        else Modifier.border(1.dp, colors.border, RoundedCornerShape(99.dp))
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = textColor,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
        }
    }
}
