package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import org.koin.androidx.compose.koinViewModel

/**
 * 알림 설정 — 어떤 알림을 받을지만.
 *
 * <p>전에는 알림 목록 화면 맨 위에 토글이 얹혀 있었다. 마이페이지의 「알림 설정」과 하단
 * 탭의 「알림」이 **같은 화면으로 갔고**, 이름이 설정이라고 말하는데 목록이 통째로 열렸다.
 *
 * <p>목록은 「무슨 일이 있었나」에, 이 화면은 「무엇을 받을까」에 답한다. 물음이 다르므로
 * 화면을 나눈다. 여기는 계정 설정 안으로 들어간다.
 *
 * <p>[NotificationsViewModel] 을 목록 화면과 함께 쓴다. 설정을 읽고 쓰는 코드가 이미 거기
 * 있고, 화면마다 인스턴스가 따로 생겨 서로의 상태를 건드리지 않는다.
 */
@Composable
fun ParentNotificationSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val s = state.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxWidth().background(colors.card)) {
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
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
                    text = stringResource(R.string.parent_notif_settings_title),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 18.sp,
                    color = colors.text,
                )
            }
            Text(
                text = stringResource(R.string.parent_notif_settings_lead),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                color = colors.muted,
                fontSize = 12.5.sp,
            )
            KidHeaderDivider()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsGroup(stringResource(R.string.parent_notif_group_safety)) {
                ToggleRow(
                    title = stringResource(R.string.parent_notif_location),
                    desc = stringResource(R.string.parent_notif_location_desc),
                    checked = s.location,
                ) { checked -> viewModel.patch { it.copy(location = checked) } }
                ToggleRow(
                    title = stringResource(R.string.parent_notif_harmful),
                    checked = s.harmfulAccess,
                    last = true,
                ) { checked -> viewModel.patch { it.copy(harmfulAccess = checked) } }
            }

            SettingsGroup(stringResource(R.string.parent_notif_group_usage)) {
                ToggleRow(
                    title = stringResource(R.string.parent_notif_app_usage),
                    desc = stringResource(R.string.parent_notif_app_usage_desc),
                    checked = s.appUsage,
                    last = true,
                ) { checked -> viewModel.patch { it.copy(appUsage = checked) } }
            }

            SettingsGroup(stringResource(R.string.parent_notif_group_etc)) {
                ToggleRow(
                    title = stringResource(R.string.parent_notif_notice),
                    checked = s.notice,
                    last = true,
                ) { checked -> viewModel.patch { it.copy(notice = checked) } }
            }

            state.error?.let {
                Text(it, color = colors.danger, fontSize = 12.5.sp)
            }
        }
    }
}

/** 작은 회색 라벨 + 흰 카드. 계정 설정·자녀관리와 같은 모양이다. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalKidhackColors.current
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 8.dp),
            color = colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
        )
        KidSectionCard(contentPadding = PaddingValues(0.dp), content = content)
    }
}

/**
 * 토글 한 줄.
 *
 * <p>줄 전체가 눌린다. 스위치만 누를 수 있게 두면 손가락이 24dp 짜리 과녁을 맞혀야 한다.
 */
@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    desc: String? = null,
    last: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.5.sp,
                    color = colors.text,
                )
                if (desc != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(text = desc, color = colors.muted, fontSize = 11.5.sp, lineHeight = 16.sp)
                }
            }
            KidSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (!last) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border.copy(alpha = 0.7f)),
            )
        }
    }
}
