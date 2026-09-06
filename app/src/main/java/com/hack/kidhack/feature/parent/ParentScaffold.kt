package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.BrandGradientHeader
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.SelectedChildRepository
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ParentScreenScaffold(
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    overlap: Boolean = true,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        BrandGradientHeader(content = header)
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (overlap) Modifier.offset(y = (-16).dp) else Modifier)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.surface)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        bottomBar()
    }
}

@Composable
fun ParentHeaderBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("←", color = Color.White, fontSize = 14.sp, fontFamily = KidhackFontFamily, fontWeight = FontBold)
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 18.sp,
        )
        actions()
    }
}

@Composable
fun ParentLoadingOrError(
    loading: Boolean,
    error: String?,
    onRetry: (() -> Unit)? = null,
) {
    val colors = LocalKidhackColors.current
    if (loading) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.brand)
        }
    }
    if (error != null) {
        Text(error, color = colors.danger, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 13.sp)
        if (onRetry != null) {
            KidButton(stringResource(R.string.parent_retry), onRetry, Modifier.fillMaxWidth())
        }
    }
}

/**
 * 「1시간 22분」.
 *
 * <p>전에는 `2h 14m` 로 적었다. 앱의 다른 곳은 모두 한국어라 이 화면만 말이 달랐다.
 * 1분이 안 되면 초로 적는다 — 분으로 자르면 30초를 쓴 앱과 아예 안 쓴 앱이 같은 글자가 된다.
 */
internal fun formatDuration(sec: Int): String {
    if (sec < 60) return sec.toString() + "초"
    val minutes = sec / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> hours.toString() + "시간 " + rest + "분"
        hours > 0 -> hours.toString() + "시간"
        else -> minutes.toString() + "분"
    }
}

internal fun formatWon(amount: Int): String =
    NumberFormat.getIntegerInstance(Locale.KOREA).format(kotlin.math.abs(amount))

/**
 * 「12,000원」.
 *
 * <p>`₩ 12,000` 은 기호가 숫자 앞에 서서, 눈이 금액에 닿기까지 한 글자를 더 지나야 한다.
 * 한국에서 값을 읽는 순서와도 다르다. 지갑·페이의 모든 금액이 이 모양을 쓴다.
 */
internal fun formatWonText(amount: Int): String = formatWon(amount) + "원"

internal fun formatMmSs(totalSec: Int): String {
    val m = (totalSec / 60).coerceAtLeast(0)
    val s = (totalSec % 60).coerceAtLeast(0)
    return "%d:%02d".format(m, s)
}

/**
 * 말풍선 옆에 붙는 시각 — 「오후 2:32」.
 *
 * <p>대화는 날짜가 위 구분선으로 이미 갈려 있어, 풍선마다 날짜까지 적으면 같은 말이
 * 열 번 반복된다.
 */
internal fun formatChatTimeOnly(raw: String): String {
    if (raw.isBlank()) return ""
    if (raw.matches(Regex("""^\d{1,2}:\d{2}$"""))) return raw
    val dt = raw.toChatDateTime() ?: return raw
    return dt.format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA))
}

/**
 * 날짜 구분에 쓸 하루 이름. 오늘·어제는 말로, 그 앞은 날짜로.
 *
 * <p>시각을 못 읽으면 null 을 준다 — 그때는 구분선을 그리지 않는다. 「방금」처럼 서버가
 * 아닌 곳에서 만든 값이 섞이기 때문이다.
 */
internal fun String.toChatDay(): String? {
    val dt = toChatDateTime() ?: return null
    val today = LocalDate.now()
    return when (dt.toLocalDate()) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> dt.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREA))
    }
}

private fun String.toChatDateTime(): LocalDateTime? {
    if (isBlank()) return null
    val normalized = replace(Regex("""\.\d+"""), "")
    return runCatching { LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull()
}

/** ISO/ STOMP sentAt → 채팅 말풍선용. 오늘이면 HH:mm, 아니면 yyyy.MM.dd HH:mm */
internal fun formatChatSentAt(raw: String): String {
    if (raw.isBlank()) return ""
    if (raw.matches(Regex("""^\d{1,2}:\d{2}$"""))) return raw
    val normalized = raw.replace(Regex("""\.\d+"""), "")
    val dt = runCatching {
        LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.getOrElse { return raw }
    return if (dt.toLocalDate() == LocalDate.now()) {
        dt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    } else {
        dt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.getDefault()))
    }
}

internal suspend fun SelectedChildRepository.resolveId(children: List<ChildProfile>): String? {
    val current = current()
    if (current != null && children.any { it.id == current }) return current
    val first = children.firstOrNull()?.id
    if (first != null) select(first)
    return first
}

internal fun ChildProfile.avatarEmoji(): String = "👦"

/** 지도 위에 겹쳐 놓는 작은 버튼(전체화면 열기/닫기). */
@Composable
fun MapChipButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Text(
        label,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) colors.brand else colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        color = if (active) Color.White else colors.text,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}
