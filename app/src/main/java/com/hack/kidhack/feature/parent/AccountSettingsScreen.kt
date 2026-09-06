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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import org.koin.androidx.compose.koinViewModel

/**
 * 계정 설정.
 *
 * <p>마이페이지 목록 맨 위를 차지하던 계정 항목 넷을 이름 옆 톱니 안으로 옮긴 화면이다.
 * 「내 계정을 손보는 일」과 「서비스를 쓰는 일」이 한 목록에 섞여 있으면, 자녀를 연결하러
 * 들어온 부모가 비밀번호 변경을 지나쳐 읽어야 한다.
 *
 * <p>목록이 세 줄뿐이라 화면이 비어 보인다. 프로필을 위에 크게 세워 이 화면이 **누구의**
 * 계정인지부터 말하게 했다.
 *
 * <p>[MypageViewModel] 을 함께 쓴다. 사용자 정보와 비밀번호 재설정 흐름이 이미 거기 있고,
 * 화면마다 인스턴스가 따로 생기므로 마이페이지의 상태를 건드리지 않는다.
 */
@Composable
fun ParentAccountSettingsScreen(
    onBack: () -> Unit = {},
    onOpenEditProfile: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onWithdraw: () -> Unit = {},
    onOpenPaymentPassword: () -> Unit = {},
    viewModel: MypageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val user = state.user

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
                    text = stringResource(R.string.parent_account_settings),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 18.sp,
                    color = colors.text,
                )
            }
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
            // 프로필을 화면 주인공으로. 사진은 옆의 카메라로 바로 바꾼다.
            KidSectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(colors.brandLight),
                            contentAlignment = Alignment.Center,
                        ) {
                            val photo = user?.profileImageUrl
                            if (photo.isNullOrBlank()) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_person),
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = colors.brandDark,
                                )
                            } else {
                                AsyncImage(
                                    model = RemoteUrl.absolute(photo),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colors.card)
                                .border(1.dp, colors.border, CircleShape)
                                .clickable(onClick = onOpenEditProfile),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera),
                                contentDescription = stringResource(R.string.parent_account_change_photo),
                                modifier = Modifier.size(15.dp),
                                tint = colors.muted,
                            )
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Text(
                        text = user?.name.orEmpty(),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 17.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = user?.email ?: user?.loginId.orEmpty(),
                        color = colors.muted,
                        fontSize = 12.5.sp,
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = stringResource(R.string.parent_account_role_parent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(colors.brandLight)
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        color = colors.brandDark,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 11.5.sp,
                    )
                }
            }

            // 비밀번호 재설정 폼은 열었을 때만 나타난다. 접혀 있을 땐 줄 하나만 차지한다.
            PasswordChangeSection(state = state, viewModel = viewModel)

            AccountGroup(stringResource(R.string.parent_mypage_account)) {
                AccountRow(
                    title = stringResource(R.string.parent_mypage_edit_profile),
                    trailing = stringResource(R.string.parent_account_profile_hint),
                    onClick = onOpenEditProfile,
                )
                AccountRow(
                    title = stringResource(R.string.parent_mypage_change_password),
                    onClick = viewModel::togglePasswordChange,
                )
                AccountRow(
                    title = stringResource(R.string.parent_mypage_notif_settings),
                    onClick = onOpenNotificationSettings,
                    last = true,
                )
            }

            // 전자금융 거래 보안(#59) — 결제 비밀번호 변경만.
            //   최초 설정과 실명확인은 접근매체(카드) 발급 흐름에서 한 번에 처리하므로 여기서 별도 메뉴로 두지 않는다.
            AccountGroup("전자금융") {
                AccountRow(
                    title = "결제 비밀번호",
                    trailing = "변경",
                    onClick = onOpenPaymentPassword,
                    last = true,
                )
            }

            AccountGroup(stringResource(R.string.parent_account_manage)) {
                AccountRow(
                    title = stringResource(R.string.parent_mypage_logout),
                    onClick = viewModel::logout,
                )
                AccountRow(
                    title = stringResource(R.string.parent_mypage_withdraw),
                    onClick = onWithdraw,
                    danger = true,
                    last = true,
                )
            }
        }
    }
}

/** 작은 회색 라벨 + 그림자로 뜬 흰 카드. 줄이 스스로 좌우 여백을 가져 구분선이 카드 폭을 다 쓴다. */
@Composable
private fun AccountGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
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
 * 설정 한 줄.
 *
 * <p>아이콘을 달지 않는다. 이름만으로 뜻이 분명한 항목이라 아이콘이 정보를 더하지 않고,
 * 색칸만 세로로 늘어서 눈이 피로해진다.
 */
@Composable
private fun AccountRow(
    title: String,
    onClick: () -> Unit,
    trailing: String? = null,
    danger: Boolean = false,
    last: Boolean = false,
) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.5.sp,
                color = if (danger) colors.danger else colors.text,
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    modifier = Modifier.padding(end = 8.dp),
                    color = colors.muted,
                    fontSize = 12.5.sp,
                )
            }
            Text(text = "›", color = colors.muted, fontSize = 18.sp)
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
