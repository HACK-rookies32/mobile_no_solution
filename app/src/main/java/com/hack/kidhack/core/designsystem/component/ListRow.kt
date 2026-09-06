package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: String? = null,
    subtitle: String? = null,
    trailing: String = "›",
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalKidhackColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Text(text = leading, fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                        color = colors.muted,
                    )
                }
            }
            Text(
                text = trailing,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 16.sp,
                color = colors.border,
            )
        }
        if (showDivider) {
            HorizontalDivider(color = colors.border)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListRowPreview() {
    KidhackTheme {
        Column(Modifier.padding(16.dp)) {
            ListRow(leading = "💳", title = "루키즈 페이", subtitle = "잔액 ₩45,000", onClick = {})
            ListRow(leading = "🔔", title = "알림 설정", onClick = {}, showDivider = false)
        }
    }
}
