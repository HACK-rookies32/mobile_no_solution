package com.hack.kidhack.feature.child.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.hack.kidhack.R
import com.hack.kidhack.background.capture.MediaProjectionRequestActivity
import com.hack.kidhack.background.capture.ScreenCaptureService
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidLabeledSection
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.feature.child.components.ChildSubScaffold
import com.hack.kidhack.feature.child.components.RetryCard
import org.koin.androidx.compose.koinViewModel
import java.io.File

/**
 * 마이페이지·홈 종 → 화면 캡처 내역 (자녀).
 *
 * <p>주황 히어로 + 이모지(📸🖼️📭) + 테두리 카드를 걷어내고 다른 자녀 화면과 같은 껍데기를
 * 쓴다. 요청 배너는 브랜드 카드로, 안내는 접히는 회색 카드로, 목록은 「최근 캡처」 라벨 아래
 * 흰 카드 안의 줄로 정리한다. 이모지는 전부 벡터 아이콘으로 갈았다.
 */
@Composable
fun ChildScreenshotScreen(
    onBack: () -> Unit = {},
    viewModel: ChildScreenshotViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    // 동의 → 캡처 → 업로드를 마치고 돌아오면 목록이 최신이어야 한다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ChildSubScaffold(
        title = stringResource(R.string.child_screenshot_title),
        onBack = onBack,
    ) {
        if (state.bannerVisible) {
            KidSectionCard(
                modifier = Modifier,
                containerColor = colors.brandLight,
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painter = painterResource(R.drawable.ic_capture),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = colors.brandDark,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.child_screenshot_banner_title),
                            color = colors.brandDark,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                R.string.child_screenshot_banner_body_from,
                                KoreanParticles.subject(
                                    state.requestedBy?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.child_request_default_sender),
                                ),
                            ),
                            color = colors.brandDark.copy(alpha = 0.8f),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        KidButton(
                            text = stringResource(R.string.child_screenshot_allow),
                            onClick = {
                                context.startActivity(
                                    MediaProjectionRequestActivity.intent(
                                        context = context,
                                        requestId = state.pendingRequestId.orEmpty(),
                                        trigger = ScreenCaptureService.TRIGGER_MANUAL,
                                    ),
                                )
                                viewModel.onCaptureLaunched()
                            },
                        )
                    }
                    Text(
                        text = stringResource(R.string.child_screenshot_dismiss),
                        modifier = Modifier
                            .clickable(onClick = viewModel::dismissBanner)
                            .padding(4.dp),
                        color = colors.brandDark,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        // 안내 — 잔잔한 회색 카드. 아이콘 + 한 줄 설명이면 충분하다.
        KidSectionCard(contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.brandLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_image),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.brandDark,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.child_screenshot_guide_title),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.child_screenshot_guide_body),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        val loadError = state.error
        if (loadError != null) {
            RetryCard(message = loadError, onRetry = viewModel::refresh)
        }

        if (loadError == null && state.items.isEmpty() && !state.loading) {
            KidLabeledSection(
                label = stringResource(R.string.child_screenshot_recent),
                contentPadding = PaddingValues(vertical = 28.dp, horizontal = 16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_image),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = colors.border,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.child_screenshot_empty),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.child_screenshot_empty_hint),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                    )
                }
            }
        } else if (state.items.isNotEmpty()) {
            KidLabeledSection(
                label = stringResource(R.string.child_screenshot_recent),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                state.items.forEachIndexed { index, item ->
                    ShotRow(item)
                    if (index != state.items.lastIndex) HorizontalDivider(color = colors.line)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ShotRow(item: Screenshot) {
    val colors = LocalKidhackColors.current
    val manual = item.trigger.equals("MANUAL", ignoreCase = true)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            // 기기에 남은 캡처본이면 실제 이미지를, 없으면 자리표시자 아이콘을 그린다.
            val localFile = item.imageUrl?.let(::File)?.takeIf { it.exists() }
            if (localFile != null) {
                AsyncImage(
                    model = localFile,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_image),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.subtle,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (manual) R.string.child_screenshot_mom else R.string.child_screenshot_scheduled,
                ),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = formatCapturedAt(item.capturedAt),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * 촬영 시각 ISO(`2026-09-04T14:22:31Z`) → 「오늘 14:22」·「9월 4일 14:22」.
 *
 * <p>로컬 저장소가 시각을 ISO 로만 넘기므로 여기서 사람이 읽을 형태로 바꾼다. 형식을 못 읽으면
 * 원문을 그대로 둔다 — 틀린 시각을 지어내지 않는다.
 */
private fun formatCapturedAt(raw: String): String {
    val instant = runCatching { java.time.Instant.parse(raw) }.getOrNull() ?: return raw
    val dt = instant.atZone(java.time.ZoneId.systemDefault())
    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    val time = dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    return if (dt.toLocalDate() == today) {
        "오늘 $time"
    } else {
        "${dt.monthValue}월 ${dt.dayOfMonth}일 $time"
    }
}
