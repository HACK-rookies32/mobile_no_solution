package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.common.recentDaysRange
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as TextStyleJava
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class UsageGroupBy(val apiValue: String, val label: String) {
    APP("APP", "앱별"),
    HOUR("HOUR", "시간대별"),
    DAY("DAY", "일별"),
}

/**
 * 볼 기간.
 *
 * <p>서버는 `from`·`to` 를 받는데 화면에 고를 자리가 없어 늘 기본값만 보고 있었다.
 * 날짜를 직접 고르게 하는 대신 셋 중 하나로 줄인다 — 부모가 묻는 것은 「오늘 얼마나」이지
 * 「9월 12일부터 9월 19일까지」가 아니다.
 *
 * @param days 오늘을 포함해 거슬러 올라가는 날 수 - 1. 오늘만이면 0.
 */
enum class UsagePeriod(val days: Long) {
    TODAY(0),
    WEEK(6),
    MONTH(29),
}

data class AppUsageDetailUiState(
    val items: List<AppUsageStat> = emptyList(),
    val groupBy: UsageGroupBy = UsageGroupBy.APP,
    val period: UsagePeriod = UsagePeriod.TODAY,
    /**
     * 같은 길이의 <b>직전</b> 기간 합계. 못 불러왔으면 null 이고, 그때는 증감을 적지 않는다.
     *
     * <p>「2시간 14분」만으로는 많은지 적은지 알 수 없다. 견줄 것이 있어야 판단이 된다.
     */
    val previousTotalSeconds: Int? = null,
    val loading: Boolean = true,
    val error: String? = null,
) {
    /** 이 기간에 쓴 시간 전부. 막대만 있으면 「많이 썼다」가 얼마인지 모른다. */
    val totalSeconds: Int get() = items.sumOf { it.durationSec }

    /** 직전 기간과의 차이(초). 견줄 값이 없으면 null. */
    val deltaSeconds: Int? get() = previousTotalSeconds?.let { totalSeconds - it }
}

class AppUsageDetailViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val appUsageRepository: AppUsageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUsageDetailUiState())
    val state: StateFlow<AppUsageDetailUiState> = _state.asStateFlow()

    init { load() }

    fun onGroupBy(g: UsageGroupBy) {
        if (g == _state.value.groupBy) return
        _state.update { it.copy(groupBy = g) }
        load()
    }

    fun onPeriod(p: UsagePeriod) {
        if (p == _state.value.period) return
        _state.update { it.copy(period = p) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val id = runCatching { selectedChildRepository.resolveId(childRepository.children()) }
                .getOrNull()
            if (id == null) {
                _state.update { it.copy(loading = false, items = emptyList(), previousTotalSeconds = null) }
                return@launch
            }

            val period = _state.value.period
            // 서버는 시각 포함 LocalDateTime 구간을 받는다. 날짜만 보내면 '오늘'은 from==to(0시==0시)라
            // 빈 구간이 돼 아무것도 안 나온다. 오늘 0시~지금을 포함하도록 헬퍼를 쓴다.
            val (from, to) = recentDaysRange(period.days.toInt() + 1)

            runCatching {
                appUsageRepository.list(
                    childId = id,
                    groupBy = _state.value.groupBy.apiValue,
                    from = from,
                    to = to,
                )
            }.onSuccess { items ->
                _state.update { it.copy(loading = false, items = items) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
                return@launch
            }

            // 직전 같은 길이의 구간 합계(증감 배지용). 현재 구간 시작 하루 전까지를 같은 길이로 잡는다.
            // 실패하면 증감만 빠지고 화면은 그대로 뜬다.
            val curStartDay = LocalDate.now().minusDays(period.days)
            val prevEndDay = curStartDay.minusDays(1)
            val prevStartDay = prevEndDay.minusDays(period.days)
            runCatching {
                appUsageRepository.list(
                    childId = id,
                    // 합계만 쓰므로 어느 묶음으로 불러도 같다. 가장 적게 쪼개지는 것으로 부른다.
                    groupBy = UsageGroupBy.DAY.apiValue,
                    from = prevStartDay.atStartOfDay().toString(),
                    // prevEndDay 하루를 통째로 포함하도록 다음날 0시를 끝으로 둔다.
                    to = prevEndDay.plusDays(1).atStartOfDay().toString(),
                ).sumOf { it.durationSec }
            }.onSuccess { total ->
                _state.update { it.copy(previousTotalSeconds = total) }
            }.onFailure {
                _state.update { it.copy(previousTotalSeconds = null) }
            }
        }
    }
}

/**
 * 앱 사용 상세.
 *
 * <p>전에는 세 묶음을 <b>모두 같은 가로 막대 목록</b>으로 그렸다. 셋은 묻는 것이 다르다 —
 * 앱별은 순위, 시간대는 하루의 흐름, 일별은 추세다. 특히 시간대는 가로 막대가 24줄이 되어
 * 하루를 보려고 스물네 번 스크롤해야 했다.
 *
 * <p>흐름과 추세는 <b>세로 막대</b>로 눕힌다. 한 화면에 다 들어와야 「저녁에 몰린다」가
 * 그림 하나로 읽힌다.
 */
@Composable
fun ParentAppUsageDetailScreen(
    onBack: () -> Unit = {},
    viewModel: AppUsageDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2039", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_usage_detail_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
            Spacer(Modifier.weight(1f))
            // 기간을 헤더로 올려 조작 장치 한 줄을 없앤다. 셋뿐이라 세그먼트가 맞다.
            PeriodSegment(selected = state.period, onSelect = viewModel::onPeriod)
        }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            UsageHeadline(state)

            // 묶음은 밑줄 탭으로. 커뮤니티·사용 리포트와 같은 모양이라 앱 안에서 「탭」이
            // 늘 같은 뜻으로 읽힌다.
            GroupTabs(selected = state.groupBy, onSelect = viewModel::onGroupBy)

            if (state.error != null) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                    ParentLoadingOrError(false, state.error, viewModel::load)
                }
            } else if (!state.loading && state.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.parent_usage_detail_empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    color = colors.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                when (state.groupBy) {
                    UsageGroupBy.APP -> AppRanking(state)
                    UsageGroupBy.HOUR -> HourChart(state)
                    UsageGroupBy.DAY -> DayChart(state)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/* ---- 머리말 ---- */

/** 기간 이름 · 합계 · 직전 기간 대비. 합계를 상자에서 꺼내 화면에서 가장 큰 글씨로 둔다. */
@Composable
private fun UsageHeadline(state: AppUsageDetailUiState) {
    val colors = LocalKidhackColors.current
    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp)) {
        Text(
            text = periodLabel(state.period),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (state.loading && state.items.isEmpty()) {
                stringResource(R.string.parent_usage_detail_loading)
            } else {
                formatDuration(state.totalSeconds)
            },
            color = colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 32.sp,
            letterSpacing = (-1.4).sp,
        )
        // 견줄 값을 못 불러왔거나 차이가 1분이 안 되면 적지 않는다. 「0분 많음」은 말이 아니다.
        val delta = state.deltaSeconds
        if (delta != null && abs(delta) >= 60) {
            Spacer(Modifier.height(9.dp))
            val up = delta > 0
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (up) colors.brandLight else DeltaDownBg)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(if (up) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down),
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = if (up) colors.brandDark else DeltaDownFg,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (up) state.period.moreRes() else state.period.lessRes(),
                        formatDuration(abs(delta)),
                    ),
                    color = if (up) colors.brandDark else DeltaDownFg,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 11.5.sp,
                )
            }
        }
    }
}

/** 줄어든 쪽은 초록. 늘어난 쪽만 브랜드색이라 「늘었다」가 먼저 눈에 든다. */
private val DeltaDownBg = Color(0xFFE7F4EC)
private val DeltaDownFg = Color(0xFF2E6B4F)

/* ---- 조작 ---- */

@Composable
private fun PeriodSegment(selected: UsagePeriod, onSelect: (UsagePeriod) -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        UsagePeriod.entries.forEach { p ->
            val on = p == selected
            Text(
                text = stringResource(p.shortLabelRes()),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) colors.card else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = if (on) colors.text else colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun GroupTabs(selected: UsageGroupBy, onSelect: (UsageGroupBy) -> Unit) {
    val colors = LocalKidhackColors.current
    Column(Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            UsageGroupBy.entries.forEach { g ->
                val on = g == selected
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable { onSelect(g) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = g.label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = if (on) colors.text else colors.subtle,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(if (on) colors.text else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.line)
    }
}

/* ---- 앱별: 순위 ---- */

@Composable
private fun AppRanking(state: AppUsageDetailUiState) {
    val sorted = state.items.sortedByDescending { it.durationSec }
    val maxSec = sorted.firstOrNull()?.durationSec?.coerceAtLeast(1) ?: 1
    sorted.forEachIndexed { index, item ->
        AppRankRow(
            rank = index + 1,
            name = item.key,
            seconds = item.durationSec,
            ratioOfMax = item.durationSec.toFloat() / maxSec,
            share = if (state.totalSeconds > 0) item.durationSec.toFloat() / state.totalSeconds else 0f,
            // 상위 둘만 진하게. 색을 늘리는 대신 무게로 순위를 말한다.
            strong = index < 2,
            showDivider = index > 0,
        )
    }
}

/**
 * 앱 한 줄.
 *
 * <p>순위 번호를 붙인다. 막대 길이만으로는 3위와 4위가 붙어 있을 때 구분이 안 된다.
 *
 * <p>막대 길이는 <b>1위 대비</b>라 눈으로 견주기 좋고, 옆의 비율은 <b>전체 대비</b>다.
 * 둘은 다른 것을 말한다 — 길이는 순위를, 비율은 무게를 알려 준다.
 */
@Composable
private fun AppRankRow(
    rank: Int,
    name: String,
    seconds: Int,
    ratioOfMax: Float,
    share: Float,
    strong: Boolean,
    showDivider: Boolean,
) {
    val colors = LocalKidhackColors.current
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = rank.toString(),
                modifier = Modifier.width(16.dp),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 11.sp,
            )
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                letterSpacing = (-0.25).sp,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatDuration(seconds),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
                color = colors.text,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                // 1% 가 안 되는 값을 0% 로 적으면 「안 썼다」로 읽힌다.
                text = if (share > 0f && share < 0.01f) "<1%" else "${(share * 100).roundToInt()}%",
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 11.sp,
                color = colors.subtle,
            )
        }
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratioOfMax.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (strong) colors.brand else UsageBarDim),
            )
        }
    }
}

/* ---- 시간대: 하루의 흐름 ---- */

@Composable
private fun HourChart(state: AppUsageDetailUiState) {
    // 서버는 값이 있는 시간대만 준다. 빈 칸을 0 으로 메워야 하루가 끊기지 않는다.
    val byHour = state.items.associate { (it.key.toIntOrNull() ?: -1) to it.durationSec }
    val hours = (0..23).map { byHour[it] ?: 0 }
    val peak = hours.withIndex().maxByOrNull { it.value }

    BarChart(
        values = hours,
        // 가장 많이 쓴 때와 그 앞뒤 한 칸까지 진하게. 한 칸만 칠하면 눈에 잘 안 띈다.
        highlight = { index -> peak != null && peak.value > 0 && abs(index - peak.index) <= 0 },
        labels = listOf(0 to "0시", 6 to "6시", 12 to "12시", 18 to "18시", 23 to "23시"),
    )
    if (peak != null && peak.value > 0) {
        FootNote(
            label = stringResource(R.string.parent_usage_peak_hour),
            value = stringResource(R.string.parent_usage_peak_hour_value, peak.index, formatDuration(peak.value)),
        )
    }
}

/* ---- 일별: 추세 ---- */

@Composable
private fun DayChart(state: AppUsageDetailUiState) {
    val today = LocalDate.now()
    val span = (state.period.days + 1).toInt()
    val byDay = state.items.associate { it.key.take(10) to it.durationSec }
    val days = (0 until span).map { offset ->
        val date = today.minusDays((span - 1 - offset).toLong())
        date to (byDay[date.toString()] ?: 0)
    }

    // 한 주는 요일을, 한 달은 5일 간격 날짜만 적는다. 서른 개를 다 적으면 글자가 겹친다.
    val labels = if (span <= 7) {
        days.mapIndexed { index, (date, _) ->
            index to date.dayOfWeek.getDisplayName(TextStyleJava.NARROW, Locale.KOREA)
        }
    } else {
        days.mapIndexedNotNull { index, (date, _) ->
            if (index == days.lastIndex || index % 5 == 0) index to date.dayOfMonth.toString() else null
        }
    }

    BarChart(
        values = days.map { it.second },
        highlight = { index -> index == days.lastIndex },
        labels = labels,
        gap = if (span <= 7) 8.dp else 3.dp,
        corner = if (span <= 7) 5.dp else 3.dp,
    )
    val active = days.count { it.second > 0 }
    if (active > 0) {
        FootNote(
            label = stringResource(R.string.parent_usage_daily_average),
            value = formatDuration(state.totalSeconds / span),
        )
    }
}

/* ---- 공용 ---- */

/**
 * 세로 막대 하나.
 *
 * <p>값이 0 인 칸도 자리를 지킨다. 빼 버리면 축이 늘어붙어 「그 시간에 안 썼다」가
 * 「그 시간이 없다」로 보인다.
 */
@Composable
private fun BarChart(
    values: List<Int>,
    highlight: (Int) -> Boolean,
    labels: List<Pair<Int, String>>,
    gap: Dp = 3.dp,
    corner: Dp = 3.dp,
) {
    val colors = LocalKidhackColors.current
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                // 값이 있는데 막대가 1dp 도 안 되면 「안 썼다」로 보인다. 최소 높이를 준다.
                val ratio = value.toFloat() / max
                val height = if (value > 0) (132 * ratio).coerceAtLeast(3f) else 0f
                Box(
                    Modifier
                        .weight(1f)
                        .height(height.dp)
                        .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
                        .background(if (highlight(index)) colors.brand else UsageBarDim),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 라벨은 막대 자리에 맞춰 같은 칸에 놓는다. 좌우로만 흩으면 칸과 어긋난다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            val labelAt = labels.toMap()
            values.indices.forEach { index ->
                Text(
                    text = labelAt[index].orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = colors.subtle,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 차트가 말하지 못하는 값 한 줄. 그림은 모양을 보여 주고 이 줄이 값을 말한다. */
@Composable
private fun FootNote(label: String, value: String) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(label, color = colors.subtle, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 11.5.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            color = colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 15.sp,
            letterSpacing = (-0.4).sp,
        )
    }
}

/** 아래쪽 막대. 브랜드색을 옅게 — 색을 바꾸지 않고 무게만 낮춘다. */
private val UsageBarDim = Color(0xFFF9C99A)

@Composable
private fun UsagePeriod.shortLabelRes(): Int = when (this) {
    UsagePeriod.TODAY -> R.string.parent_usage_period_today
    UsagePeriod.WEEK -> R.string.parent_usage_period_week_short
    UsagePeriod.MONTH -> R.string.parent_usage_period_month_short
}

private fun UsagePeriod.moreRes(): Int = when (this) {
    UsagePeriod.TODAY -> R.string.parent_usage_delta_more_day
    UsagePeriod.WEEK -> R.string.parent_usage_delta_more_week
    UsagePeriod.MONTH -> R.string.parent_usage_delta_more_month
}

private fun UsagePeriod.lessRes(): Int = when (this) {
    UsagePeriod.TODAY -> R.string.parent_usage_delta_less_day
    UsagePeriod.WEEK -> R.string.parent_usage_delta_less_week
    UsagePeriod.MONTH -> R.string.parent_usage_delta_less_month
}

/** 「10월 2일 · 전체 사용」처럼 무엇을 더한 값인지 말한다. */
@Composable
private fun periodLabel(period: UsagePeriod): String {
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA)
    return when (period) {
        UsagePeriod.TODAY -> stringResource(R.string.parent_usage_period_range_one, today.format(fmt))
        else -> stringResource(
            R.string.parent_usage_period_range,
            today.minusDays(period.days).format(fmt),
            today.format(fmt),
        )
    }
}
