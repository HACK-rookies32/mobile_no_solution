package com.hack.kidhack.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.component.BrandGradientHeader
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

@Composable
fun PlaceholderScreen(
    title: String,
    emoji: String,
    description: String = "다음 단계에서 구현될 화면이에요",
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        BrandGradientHeader {
            Text(title, color = Color.White, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 24.sp)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = (-16).dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KidCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(emoji, fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(title, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 18.sp, color = colors.text)
                    Text(description, color = colors.muted, fontSize = 13.sp)
                }
            }
        }
    }
}
