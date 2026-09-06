package com.hack.kidhack.feature.efin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/** 전자금융 화면 공통 헤더 — 뒤로가기 + 제목. AccountSettings 헤더와 같은 모양. */
@Composable
internal fun EfinHeader(title: String, onBack: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            color = colors.text,
            fontSize = 26.sp,
        )
        Text(
            text = title,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 21.sp,
            color = colors.text,
        )
    }
}

@Composable
internal fun FieldLabel(text: String) {
    val colors = LocalKidhackColors.current
    Text(
        text = text,
        color = colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.5.sp,
    )
}

@Composable
internal fun EfinPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) colors.brand else colors.border)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) colors.card else colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 15.sp,
        )
    }
}

/** 성공(done)·실패(error) 한 줄 배너. */
@Composable
internal fun EfinBanner(error: String?, done: String?) {
    val colors = LocalKidhackColors.current
    when {
        error != null -> Text(
            error,
            modifier = Modifier.fillMaxWidth(),
            color = colors.danger,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
        done != null -> Text(
            done,
            modifier = Modifier.fillMaxWidth(),
            color = colors.safe,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
    }
}