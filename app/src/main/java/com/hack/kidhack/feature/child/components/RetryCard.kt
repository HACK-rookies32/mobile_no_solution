package com.hack.kidhack.feature.child.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/**
 * 조회 실패를 알리고 다시 시도하게 해 주는 카드.
 *
 * 예전에는 네트워크가 끊기면 화면이 그냥 비어 버려서, 탭을 나갔다 들어오는 것 말고는
 * 다시 부를 방법이 없었다.
 */
@Composable
fun RetryCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.warn.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("⚠️", fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Text(
                text = stringResource(R.string.child_retry_hint),
                color = colors.muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            KidButton(
                text = stringResource(R.string.child_retry),
                onClick = onRetry,
                variant = KidButtonVariant.Secondary,
            )
        }
    }
}
