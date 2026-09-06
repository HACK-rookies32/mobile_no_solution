package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun StatCard(
    emoji: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    KidCard(modifier = modifier) {
        Text(text = emoji, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
            color = colors.muted,
        )
        Text(
            text = value,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 18.sp,
            color = colors.text,
        )
    }
}

@Composable
fun UsageBar(
    name: String,
    timeLabel: String,
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Text(
                text = timeLabel,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
                color = colors.muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.border),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(barColor),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatCardPreview() {
    KidhackTheme {
        StatCard(
            emoji = "⏱️",
            label = "오늘 스크린타임",
            value = "3h 24m",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UsageBarPreview() {
    KidhackTheme {
        UsageBar(
            name = "YouTube",
            timeLabel = "1h 20m",
            progress = 0.75f,
            barColor = Color(0xFFF87171),
            modifier = Modifier.padding(16.dp),
        )
    }
}
