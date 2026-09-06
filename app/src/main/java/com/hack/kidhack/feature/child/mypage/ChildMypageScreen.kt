package com.hack.kidhack.feature.child.mypage

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidLabeledSection
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildTabScaffold
import org.koin.androidx.compose.koinViewModel

/**
 * 자녀 마이 — 프로필 한 장 + 「계정」·「앱 정보」 두 덩이.
 *
 * <p>메뉴 셋이 라벨 없이 나란히 있으니 화면이 비어 보였다. 성격이 다른 것끼리 묶고 이름을
 * 붙이면 개수는 그대로여도 화면이 짜여 보인다.
 *
 * <p>로그아웃은 카드 한 장을 통째로 먹는 빨간 블록이었다. 자주 누를 것도 아닌데 화면에서
 * 제일 붉은 자리를 차지했다 — 맨 아래 회색 글씨로 내린다.
 *
 * <p>「연결된 보호자」 값은 여기 없다. 전에 채팅방 유무로 판단하다가, 부모가 채팅을 한 번도
 * 열지 않으면 페어링이 돼 있어도 「없음」으로 보여서 걷어낸 값이다. 근거가 생기기 전에는
 * 메뉴만 두고 값은 적지 않는다.
 */
@Composable
fun ChildMypageScreen(
    onLinkParent: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onScreenshots: () -> Unit = {},
    onTerms: () -> Unit = {},
    onNotice: () -> Unit = {},
    viewModel: ChildMypageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ChildTabScaffold(title = stringResource(R.string.child_tab_mypage)) {
        KidSectionCard(
            modifier = Modifier.clickable(onClick = onEditProfile),
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.brandLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.name.trim().take(1).ifBlank { "?" },
                        color = colors.brandDark,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 19.sp,
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.name,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 16.sp,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        state.email,
                        color = colors.subtle,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("›", color = colors.subtle, fontSize = 20.sp)
            }
        }

        KidLabeledSection(
            label = stringResource(R.string.child_mypage_section_account),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            MyRow(R.drawable.ic_link, stringResource(R.string.child_mypage_link_parent), onLinkParent)
            HorizontalDivider(modifier = Modifier.padding(start = 46.dp), color = colors.line)
            MyRow(R.drawable.ic_image, stringResource(R.string.child_mypage_menu_screenshots), onScreenshots)
        }

        KidLabeledSection(
            label = stringResource(R.string.child_mypage_section_app),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            MyRow(R.drawable.ic_report, stringResource(R.string.child_mypage_notice), onNotice)
            HorizontalDivider(modifier = Modifier.padding(start = 46.dp), color = colors.line)
            MyRow(R.drawable.ic_file, stringResource(R.string.child_mypage_terms), onTerms)
        }

        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.child_mypage_logout),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = viewModel::logout)
                .padding(vertical = 14.dp),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MyRow(@DrawableRes icon: Int, title: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.line),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = colors.muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            color = colors.text,
        )
        Text("›", color = colors.subtle, fontSize = 18.sp)
    }
}
