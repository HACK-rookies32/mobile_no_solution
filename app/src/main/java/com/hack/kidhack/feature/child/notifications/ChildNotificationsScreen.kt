package com.hack.kidhack.feature.child.notifications

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.body
import com.hack.kidhack.domain.model.displayTitle
import com.hack.kidhack.feature.child.components.ChildSubScaffold
import com.hack.kidhack.feature.child.formatTimeLabel
import org.koin.androidx.compose.koinViewModel

/**
 * 자녀 알림 목록 — 홈 헤더의 종에서 들어온다.
 *
 * <p>안 읽은 알림은 연주황 배경 + 주황 점으로 강조하되, 색 하나에만 기대지 않도록 점(모양)도
 * 함께 둔다. 화면이 열리면 뷰모델이 전체 읽음 처리하므로 다음 방문부터는 강조가 사라진다.
 */
@Composable
fun ChildNotificationsScreen(
    onBack: () -> Unit = {},
    viewModel: ChildNotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    ChildSubScaffold(
        title = stringResource(R.string.child_notifications_title),
        onBack = onBack,
    ) {
        when {
            state.error != null -> EmptyBlock(
                icon = R.drawable.ic_bell,
                title = stringResource(R.string.child_notifications_load_failed),
                hint = null,
            )
            !state.loading && state.items.isEmpty() -> EmptyBlock(
                icon = R.drawable.ic_bell,
                title = stringResource(R.string.child_notifications_empty),
                hint = stringResource(R.string.child_notifications_empty_hint),
            )
            else -> state.items.forEach { item -> NotificationRow(item) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NotificationRow(item: AppNotification) {
    val colors = LocalKidhackColors.current
    val visual = item.type.toVisual()
    KidSectionCard(
        // 안 읽은 것만 연주황으로 띄운다. 읽은 것은 흰 카드 그대로.
        containerColor = if (item.read) colors.card else colors.brandLight,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(visual.container(colors)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(visual.icon),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = visual.tint(colors),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle,
                    color = colors.text,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                )
                val body = item.body
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = body,
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = formatTimeLabel(item.createdAt),
                    color = colors.subtle,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 11.sp,
                )
            }
            // 안 읽음 점. 색을 못 보는 경우에도 점의 유무로 구분된다.
            if (!item.read) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.brand),
                )
            }
        }
    }
}

@Composable
private fun EmptyBlock(@DrawableRes icon: Int, title: String, hint: String?) {
    val colors = LocalKidhackColors.current
    KidSectionCard(contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = colors.border,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
            )
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    hint,
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** 알림 유형 → 아이콘·색. 홈의 알림 분류와 같은 기준을 쓴다. */
private enum class NotiRole { Harmful, Camera, Capture, ScreenTime, Chat, Location, Generic }

private data class NotiVisual(@DrawableRes val icon: Int, val role: NotiRole) {
    fun tint(c: com.hack.kidhack.core.designsystem.theme.KidhackColors): Color = when (role) {
        NotiRole.Harmful -> c.danger
        NotiRole.ScreenTime -> c.warn
        NotiRole.Chat -> c.safe
        NotiRole.Location -> c.info
        else -> c.brandDark
    }

    fun container(c: com.hack.kidhack.core.designsystem.theme.KidhackColors): Color = when (role) {
        NotiRole.Harmful -> c.danger.copy(alpha = 0.10f)
        NotiRole.ScreenTime -> c.warn.copy(alpha = 0.14f)
        NotiRole.Chat -> c.safe.copy(alpha = 0.12f)
        NotiRole.Location -> c.info.copy(alpha = 0.12f)
        else -> c.brandLight
    }
}

private fun String.toVisual(): NotiVisual {
    val u = uppercase()
    val camera = u.contains("CAMERA")
    return when {
        u.contains("HARMFUL") || u.contains("BLOCK") -> NotiVisual(R.drawable.ic_shield, NotiRole.Harmful)
        camera -> NotiVisual(R.drawable.ic_camera, NotiRole.Camera)
        u.contains("CAPTURE") || u.contains("SCREENSHOT") -> NotiVisual(R.drawable.ic_capture, NotiRole.Capture)
        u.contains("SCREEN_TIME") || u.contains("LIMIT") -> NotiVisual(R.drawable.ic_clock, NotiRole.ScreenTime)
        u.contains("CHAT") || u.contains("MESSAGE") -> NotiVisual(R.drawable.ic_chat, NotiRole.Chat)
        u.contains("GEOFENCE") || u.contains("LOCATION") || u.contains("GPS") -> NotiVisual(R.drawable.ic_place, NotiRole.Location)
        else -> NotiVisual(R.drawable.ic_bell, NotiRole.Generic)
    }
}
