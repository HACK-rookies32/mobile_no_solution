package com.hack.kidhack.feature.child.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidLabeledSection
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildTabScaffold
import com.hack.kidhack.feature.child.components.RetryCard
import com.hack.kidhack.feature.child.formatDurationMin
import com.hack.kidhack.feature.child.remainingLabel
import org.koin.androidx.compose.koinViewModel

/**
 * 자녀 홈 — 「오늘 얼마나 썼나」가 주인공.
 *
 * <p>보호 상태는 카드에서 헤더 <b>오른쪽</b>으로 옮겼다. 아무 일도 없다는 말이 카드 한 장을
 * 차지하고 있었는데, 잘 되고 있다는 소식은 조용해도 된다. 제목 아래에 한 줄로 붙였더니 이번엔
 * 제목이 위로 밀려, 페이·마이 탭과 헤더 높이가 어긋났다 — 탭을 옮길 때마다 제목이 들썩인다.
 * 오른쪽은 원래 비어 있던 자리라 다른 탭과 제목 위치가 정확히 같아진다.
 *
 * <p>꺼진 보호가 있을 때만 캔버스 맨 위에 카드가 <b>나타난다</b> — 평소에 없던 것이 생기는
 * 편이 늘 있는 카드의 색이 바뀌는 것보다 눈에 띈다.
 *
 * <p>헤더의 종은 알림 화면으로 들어가는 문이다. 안 읽은 알림이 있으면 개수 배지가 붙고,
 * 알림 화면을 열면 전체 읽음 처리되어 배지가 사라진다.
 *
 * <p>앱 목록은 두 덩이다. 부모가 한도를 걸어 둔 앱은 「약속한 앱」에서 <b>기준과 함께</b>
 * 보여 준다(8분 / 10분). 전에는 이 규칙을 접근성 서비스만 읽어서, 아이는 무엇이 얼마로 묶여
 * 있는지 <b>막히고 나서야</b> 알았다. 규칙 없는 앱은 아래에서 시간만 적는다 — 막대는 한도가
 * 있는 줄에만 두어야 「한도 대비 얼마나」라는 뜻을 지킨다.
 */
@Composable
fun ChildHomeScreen(
    onNotifications: () -> Unit = {},
    viewModel: ChildHomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    // 권한·GPS 는 설정 화면에서 바뀔 수 있으니 탭으로 돌아올 때마다 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val attention = state.needsAttention.firstOrNull()

    ChildTabScaffold(
        title = if (state.alias.isBlank()) {
            stringResource(R.string.child_home_hello)
        } else {
            stringResource(R.string.child_home_hello_named, KoreanParticles.vocative(state.alias))
        },
        action = {
            ProtectionLine(protectedNow = state.protectedNow && state.needsAttention.isEmpty())
            Spacer(Modifier.width(6.dp))
            NotificationBell(unread = state.unreadCount, onClick = onNotifications)
        },
    ) {
        state.error?.let { RetryCard(message = it, onRetry = viewModel::refresh) }

        // 꺼진 보호가 있을 때만 나타난다. 안전할 때는 이 자리에 아무것도 없다.
        if (attention?.settingsAction != null) {
            AttentionCard(
                label = attention.label,
                onTurnOn = {
                    runCatching {
                        context.startActivity(
                            Intent(attention.settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }

        KidLabeledSection(
            label = stringResource(R.string.child_home_section_today),
            contentPadding = PaddingValues(16.dp),
        ) {
            Text(
                stringResource(R.string.child_home_used_label),
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
                color = colors.muted,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                state.usedLabel,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 32.sp,
                color = colors.text,
            )
            // 한도가 있을 때만 막대와 문장을 붙인다. 근거 없는 기준을 지어내지 않는다.
            state.limitMinutes?.takeIf { it > 0 }?.let { limit ->
                val over = state.usedMinutes >= limit
                Spacer(Modifier.height(14.dp))
                UsageBar(
                    fraction = state.usedMinutes.toFloat() / limit,
                    color = if (over) colors.danger else colors.brand,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (over) {
                        stringResource(
                            R.string.child_home_limit_over_sentence,
                            KoreanParticles.objective(formatDurationMin(limit)),
                            formatDurationMin(state.usedMinutes - limit),
                        )
                    } else {
                        stringResource(
                            R.string.child_home_limit_left_sentence,
                            formatDurationMin(limit),
                            remainingLabel(state.usedMinutes, limit),
                        )
                    },
                    color = if (over) colors.danger else colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
        }

        if (state.ruledApps.isNotEmpty()) {
            KidLabeledSection(
                label = stringResource(R.string.child_home_section_ruled),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                state.ruledApps.forEachIndexed { index, row ->
                    RuledAppItem(row)
                    if (index != state.ruledApps.lastIndex) HorizontalDivider(color = colors.line)
                }
            }
        }

        val others = state.otherUsages.take(5)
        if (others.isNotEmpty()) {
            KidLabeledSection(
                label = stringResource(R.string.child_home_section_top_apps),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                others.forEachIndexed { index, app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(packageName = app.packageName)
                        Spacer(Modifier.width(11.dp))
                        Text(
                            app.appName,
                            modifier = Modifier.weight(1f),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatDurationMin(app.durationSec / 60),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 13.sp,
                            color = colors.muted,
                        )
                    }
                    if (index != others.lastIndex) HorizontalDivider(color = colors.line)
                }
            }
        } else if (state.ruledApps.isEmpty()) {
            KidLabeledSection(
                label = stringResource(R.string.child_home_section_top_apps),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    stringResource(R.string.child_home_top_apps_empty),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 헤더 오른쪽 종. 안 읽은 알림이 있으면 개수 배지가 붙는다. */
@Composable
private fun NotificationBell(unread: Int, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bell),
            contentDescription = stringResource(R.string.child_notifications_title),
            modifier = Modifier.size(21.dp),
            tint = colors.text,
        )
        if (unread > 0) {
            Text(
                text = if (unread > 99) "99+" else unread.toString(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 3.dp)
                    .defaultMinSize(minWidth = 15.dp)
                    .clip(CircleShape)
                    .background(colors.danger)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 헤더 오른쪽에 앉는 보호 상태.
 *
 * <p>색은 점에만 얹고 글자는 먹색으로 둔다 — 경고색(#F59E0B)은 흰 배경에서 작은 글씨로
 * 쓰기엔 대비가 모자라고, 무슨 일인지는 어차피 글자가 말한다.
 *
 * <p>여기 들어갈 말은 짧아야 한다. 무엇이 왜 꺼졌는지는 아래 카드가 자세히 말하므로 이 자리는
 * 「꺼진 게 있다」까지만 알린다.
 */
@Composable
private fun ProtectionLine(protectedNow: Boolean) {
    val colors = LocalKidhackColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (protectedNow) colors.safe else colors.warn),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (protectedNow) {
                stringResource(R.string.child_home_protected)
            } else {
                stringResource(R.string.child_home_unprotected)
            },
            color = if (protectedNow) colors.muted else colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 11.sp,
            // 좁은 기기에서 이 말이 접히면 헤더가 두 줄이 된다. 접히느니 제목이 줄어야 한다.
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 꺼진 보호가 있을 때만 나타나는 카드. */
@Composable
private fun AttentionCard(label: String, onTurnOn: () -> Unit) {
    val colors = LocalKidhackColors.current
    KidSectionCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.warn,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.child_home_protection_off,
                        KoreanParticles.subject(label),
                    ),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.child_home_protection_off_hint),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        KidButton(
            text = stringResource(R.string.child_home_protection_turn_on),
            onClick = onTurnOn,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 「약속한 앱」 한 줄 — 무엇이 어느 기준이고 얼마나 썼는지.
 *
 * <p>아예 못 쓰는 앱(하루 0분)에는 막대를 두지 않는다. 잴 것이 없는데 빈 막대를 그리면
 * 「아직 안 썼다」로 읽힌다.
 */
@Composable
private fun RuledAppItem(row: RuledAppRow) {
    val colors = LocalKidhackColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 못 쓰는 앱은 아이콘을 흐리게 둔다 — 지금 이 앱이 죽어 있다는 것이 한눈에 읽힌다.
            // 색으로만 말하지 않도록 오른쪽에 「못 쓰는 앱」이라고 적는다.
            AppIcon(packageName = row.packageName, dimmed = row.blocked)
            Spacer(Modifier.width(11.dp))
            Text(
                row.name,
                modifier = Modifier.weight(1f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = if (row.blocked) colors.subtle else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            when {
                row.blocked -> StatusText(
                    stringResource(R.string.child_home_app_blocked),
                    colors.subtle,
                )
                !row.usageKnown -> StatusText(
                    stringResource(R.string.child_home_app_usage_unknown),
                    colors.muted,
                )
                row.over -> StatusText(
                    stringResource(R.string.child_home_app_over),
                    colors.danger,
                )
                row.usedMinutes == 0 -> StatusText(
                    stringResource(R.string.child_home_app_unused),
                    colors.muted,
                )
                // 쓴 만큼만 브랜드색으로 세워, 기준(/ 10분)과 한눈에 대비된다.
                else -> Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.brand)) {
                            append(formatDurationMin(row.usedMinutes))
                        }
                        append(" / ")
                        append(formatDurationMin(row.limitMinutes))
                    },
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.sp,
                )
            }
        }
        if (!row.blocked && row.usageKnown) {
            Spacer(Modifier.height(8.dp))
            UsageBar(
                fraction = row.fraction,
                color = if (row.over) colors.danger else colors.brand,
            )
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 13.sp,
    )
}

/**
 * 기기에 깔린 진짜 앱 아이콘.
 *
 * <p>이름 첫 글자를 동그라미에 넣어 두면 어느 앱인지 <b>글자를 읽어야</b> 안다. 아이는 유튜브를
 * 「유」가 아니라 빨간 재생 버튼으로 안다. 매니페스트가 런처에서 열 수 있는 앱을 조회하도록
 * 열어 두었으므로, 사용 목록·규칙에 오르는 앱은 대개 아이콘을 찾을 수 있다.
 *
 * <p>못 찾을 때(안 깔린 앱, 서버 대체값이라 패키지명이 없는 경우)만 중립 아이콘으로 떨어진다.
 */
@Composable
private fun AppIcon(packageName: String?, dimmed: Boolean = false) {
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(packageName) {
        packageName
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }
            ?.toImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (icon == null) colors.line else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null) {
            Icon(
                painter = painterResource(R.drawable.ic_apps),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = colors.muted,
            )
        } else {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(if (dimmed) 0.4f else 1f),
            )
        }
    }
}

/**
 * 런처 아이콘을 Compose 가 그릴 수 있는 형태로.
 *
 * <p>안드로이드 8 부터는 대부분이 적응형 아이콘(전경·배경 두 겹)이라 [BitmapDrawable] 이
 * 아니다. 그런 것은 캔버스에 한 번 그려서 받아 낸다.
 */
private fun Drawable.toImageBitmap(): ImageBitmap {
    (this as? BitmapDrawable)?.bitmap?.let { return it.asImageBitmap() }
    val size = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(MAX_ICON_PX) else MAX_ICON_PX
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

/** 34dp 자리에 그리는 아이콘이라 이보다 크게 뜰 이유가 없다. */
private const val MAX_ICON_PX = 192

/** 사용 비율 막대. 0 이면 정말로 비워 둔다 — 안 쓴 것과 조금 쓴 것은 다른 말이다. */
@Composable
private fun UsageBar(fraction: Float, color: Color) {
    val colors = LocalKidhackColors.current
    val safe = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(colors.line),
    ) {
        if (safe > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safe.coerceAtLeast(0.03f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color),
            )
        }
    }
}
