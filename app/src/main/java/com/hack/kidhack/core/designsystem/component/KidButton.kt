package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

enum class KidButtonVariant { Primary, Secondary, Ghost, Danger }

@Composable
fun KidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KidButtonVariant = KidButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val colors = LocalKidhackColors.current
    val shape = RoundedCornerShape(12.dp)
    val buttonColors = when (variant) {
        KidButtonVariant.Primary -> ButtonDefaults.buttonColors(
            containerColor = colors.brand,
            contentColor = androidx.compose.ui.graphics.Color.White,
            disabledContainerColor = colors.border,
            disabledContentColor = colors.muted,
        )
        KidButtonVariant.Secondary -> ButtonDefaults.buttonColors(
            containerColor = colors.brandLight,
            contentColor = colors.brand,
        )
        KidButtonVariant.Ghost -> ButtonDefaults.buttonColors(
            containerColor = colors.surface,
            contentColor = colors.text,
        )
        KidButtonVariant.Danger -> ButtonDefaults.buttonColors(
            containerColor = colors.danger.copy(alpha = 0.08f),
            contentColor = colors.danger,
        )
    }
    val content: @Composable () -> Unit = {
        Text(
            text = text,
            // 색을 직접 넘긴다. 테마의 labelLarge 에 진한 글자색이 박혀 있어서, 그냥 두면
            // 버튼이 정한 contentColor(주황 배경 위 흰색)를 덮어써 어두운 글자가 나온다.
            color = LocalContentColor.current,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
        )
    }
    if (variant == KidButtonVariant.Ghost) {
        TextButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.text),
        ) { content() }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            enabled = enabled,
            shape = shape,
            colors = buttonColors,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) { content() }
    }
}

@Preview(showBackground = true)
@Composable
private fun KidButtonPreview() {
    KidhackTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KidButton("로그인", onClick = {}, modifier = Modifier.fillMaxWidth())
            KidButton("인증번호 받기", onClick = {}, modifier = Modifier.fillMaxWidth(), variant = KidButtonVariant.Secondary)
            KidButton("← 이전", onClick = {}, modifier = Modifier.fillMaxWidth(), variant = KidButtonVariant.Ghost)
            KidButton("삭제", onClick = {}, modifier = Modifier.fillMaxWidth(), variant = KidButtonVariant.Danger)
        }
    }
}
