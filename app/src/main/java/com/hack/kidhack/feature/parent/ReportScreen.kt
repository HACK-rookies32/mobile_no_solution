package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.background.alert.ServerAlertSync
import com.hack.kidhack.core.common.recentDaysRange
import com.hack.kidhack.core.common.todayRange
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 주간 보고서가 훑는 날 수. */
private const val WEEK_DAYS = 7

data class ReportUiState(
    val childId: String? = null,
    val childName: String = "",
    /** 알림 보관함 전체. 화면에 그리는 건 [timeline] 로 걸러 낸 것이다. */
    val allAlerts: List<AppNotification> = emptyList(),
    /** 오늘 앱별 사용 시간. key = 앱 이름. */
    val todayApps: List<AppUsageStat> = emptyList(),
    /** 최근 7일 날짜별 사용 시간. key = yyyy-MM-dd. */
    val weekDays: List<AppUsageStat> = emptyList(),
    /** 최근 7일 앱별 사용 시간. */
    val weekApps: List<AppUsageStat> = emptyList(),
    /** 하루 약속 시간(분). null 이면 약속을 안 걸어 둔 것이다. */
    val dailyLimitMinutes: Int? = null,
    val loading: Boolean = true,
    /** 당겨서 새로고침 중. 첫 진입 로딩([loading])과 구분해야 표시가 겹치지 않는다. */
    val refreshing: Boolean = false,
    /** 마지막으로 서버에서 받아 온 시각(HH:mm). 자녀 기기가 꺼져 있으면 값이 안 바뀌므로 필요하다. */
    val loadedAt: String? = null,
    val error: String? = null,
) {
    val todaySeconds: Int get() = todayApps.sumOf { it.durationSec }
    val weekSeconds: Int get() = weekDays.sumOf { it.durationSec }

    /** 최근 7일 하루 평균. 기록이 없는 날도 0 으로 세야 "평균"이 부풀지 않는다. */
    val weekAverageSeconds: Int get() = weekSeconds / WEEK_DAYS

    /**
     * 오늘 하루 있었던 일. 이른 시각이 위로 온다.
     *
     * <p>어느 자녀 것인지 모르는 알림(서버가 childId 를 안 준 경우)은 감추지 않는다.
     * 잘못 숨기면 보호자가 사건 자체를 놓친다.
     *
     * <p>보관함과 자녀 선택이 따로 도착하므로 저장해 두지 않고 그때그때 걸러 낸다.
     * 먼저 온 쪽 기준으로 한 번만 계산하면 나중에 온 값이 반영되지 않는다.
     */
    val timeline: List<AppNotification>
        get() {
            val today = LocalDate.now().toString()
            return allAlerts
                .filter { it.createdAt.startsWith(today) }
                .filter { it.childId == null || it.childId == childId }
                .sortedBy { it.createdAt }
        }
}

/**
 * 사용 리포트.
 *
 * <p>전에는 자녀 카드·통계·알림이 한 화면에 쌓인 대시보드였다. 자녀 고르기가 앞으로
 * 나오면서 이 화면이 할 일은 하나로 좁혀졌다 — 얼마나 썼는지 보여 주는 것.
 *
 * <p>서버는 같은 엔드포인트를 `groupBy` 로 나눠 준다. 오늘 앱별(APP), 최근 7일
 * 날짜별(DAY), 최근 7일 앱별(APP)을 각각 받아 두 탭이 나눠 쓴다.
 */
class ReportViewModel(
    private val appUsageRepository: AppUsageRepository,
    private val appControlRepository: AppControlRepository,
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val alertSync: ServerAlertSync,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        load()
        // 보관함을 구독한다. 워커가 새 알림을 받아 넣으면 화면을 다시 열지 않아도 늘어난다.
        viewModelScope.launch {
            alertSync.alerts.collect { alerts -> _state.update { it.copy(allAlerts = alerts) } }
        }
        viewModelScope.launch { runCatching { alertSync.sync() } }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val childId = selectedChildRepository.resolveId(children) ?: return@runCatching null
                val name = children.firstOrNull { it.id == childId }?.name.orEmpty()
                val (todayFrom, todayTo) = todayRange()
                val (weekFrom, weekTo) = recentDaysRange(WEEK_DAYS)
                LoadedReport(
                    childId = childId,
                    childName = name,
                    todayApps = appUsageRepository.list(childId, "APP", todayFrom, todayTo),
                    weekDays = appUsageRepository.list(childId, "DAY", weekFrom, weekTo),
                    weekApps = appUsageRepository.list(childId, "APP", weekFrom, weekTo),
                    // 약속은 부가 정보다. 못 받아 와도 사용 시간은 그대로 보여 준다.
                    dailyLimitMinutes = runCatching {
                        appControlRepository.screenTimeLimit(childId)
                    }.getOrNull(),
                )
            }.onSuccess { loaded ->
                // 알림 보관함은 따로 흘러 들어오므로 상태를 통째로 갈아 끼우지 않는다.
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadedAt = LocalTime.now().format(CLOCK),
                        childId = loaded?.childId,
                        childName = loaded?.childName.orEmpty(),
                        todayApps = loaded?.todayApps.orEmpty(),
                        weekDays = loaded?.weekDays.orEmpty(),
                        weekApps = loaded?.weekApps.orEmpty(),
                        dailyLimitMinutes = loaded?.dailyLimitMinutes,
                    )
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, refreshing = false, error = t.message ?: "불러오지 못했어요")
                }
            }
        }
    }

    /**
     * 당겨서 새로고침.
     *
     * <p>서버 값만 다시 읽으면 자녀가 마지막에 올린 값이 그대로 나온다. 자녀 앱은 1시간
     * 주기로만 올리기 때문이다. 그래서 먼저 자녀 기기에 「지금 올려라」를 보내고, 올라올
     * 짬을 준 뒤에 읽는다.
     *
     * <p>자녀 기기가 꺼져 있거나 네트워크가 없으면 요청이 닿지 않는다. 그때는 예전 값이
     * 그대로 나오므로 화면에 받아 온 시각을 함께 적는다.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val childId = _state.value.childId
            if (childId != null) {
                runCatching { appUsageRepository.requestReport(childId) }
            }
            // 자녀 기기가 올릴 때까지의 짬. 푸시가 닿고 업로드가 끝나기까지 대략 이 정도다.
            delay(UPLOAD_WAIT_MS)
            load()
        }
    }
}

/** 새로고침 요청 뒤 자녀 기기의 업로드를 기다리는 시간. */
private const val UPLOAD_WAIT_MS = 1_500L

/** 받아 온 시각 표기. */
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 한 번의 조회로 받아 오는 것들. 상태를 통째로 바꾸지 않으려고 따로 담는다. */
private data class LoadedReport(
    val childId: String,
    val childName: String,
    val todayApps: List<AppUsageStat>,
    val weekDays: List<AppUsageStat>,
    val weekApps: List<AppUsageStat>,
    val dailyLimitMinutes: Int?,
)

/**
 * 사용 리포트 화면. 일일·주간 두 탭.
 *
 * <p>일일은 "오늘 약속을 지키고 있나", 주간은 "요즘 늘고 있나"에 답한다. 물음이 다르니
 * 같은 숫자라도 보여 주는 방식이 다르다 — 일일은 약속 대비 막대 하나, 주간은 날짜별 막대다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentReportScreen(
    onBack: () -> Unit = {},
    viewModel: ReportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // 화면으로 돌아올 때마다 다시 읽는다. 자녀가 그 사이 더 썼을 수 있다.
    // 첫 진입은 ViewModel 이 이미 읽었으므로 건너뛴다.
    var firstResume by rememberSaveable { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) firstResume = false else viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        ReportTitleBar(title = stringResource(R.string.parent_report_title), onBack = onBack)

        ReportTabs(
            titles = listOf(
                stringResource(R.string.parent_report_tab_daily),
                stringResource(R.string.parent_report_tab_weekly),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                state.error?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = colors.danger,
                        fontSize = 13.sp,
                    )
                }
                if (tab == 0) DailyReport(state) else WeeklyReport(state)
                state.loadedAt?.let { at ->
                    // 자녀 기기가 꺼져 있으면 새로고침해도 값이 그대로다. 언제 것인지 적어
                    // 두지 않으면 부모가 "지금 값"으로 읽는다.
                    Text(
                        text = stringResource(R.string.parent_report_loaded_at, at),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        color = colors.muted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DailyReport(state: ReportUiState) {
    val colors = LocalKidhackColors.current

    ReportCard {
        Text(
            text = stringResource(R.string.parent_report_today_used),
            color = colors.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatKoreanDuration(state.todaySeconds),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 28.sp,
            color = colors.text,
        )
        Spacer(Modifier.height(14.dp))
        PromiseBar(usedSeconds = state.todaySeconds, limitMinutes = state.dailyLimitMinutes)
    }

    Spacer(Modifier.height(12.dp))
    TopAppsCard(
        titleRes = R.string.parent_report_top_apps_today,
        apps = state.todayApps,
        empty = state.loading,
    )

    Spacer(Modifier.height(12.dp))
    // 하루 요약은 따로 들어가는 화면이었는데, 리포트를 보러 온 사람이 곧바로 이어서
    // 궁금해하는 것이라 같은 화면에서 내려 보게 두었다.
    ReportCard {
        Text(
            text = stringResource(R.string.parent_report_day_summary),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 15.sp,
            color = colors.text,
        )
        Spacer(Modifier.height(14.dp))
        if (state.timeline.isEmpty()) {
            Text(
                text = stringResource(
                    if (state.loading) R.string.parent_report_loading
                    else R.string.parent_day_summary_empty
                ),
                color = colors.muted,
                fontSize = 13.sp,
            )
            return@ReportCard
        }
        state.timeline.forEachIndexed { index, item ->
            TimelineRow(
                time = timeLabel(item.createdAt),
                title = item.title ?: item.message,
                message = if (item.title != null) item.message else null,
                last = index == state.timeline.lastIndex,
            )
        }
    }
}

/** 타임라인 한 칸. 점과 선은 이 칸이 직접 그린다 — 줄 높이가 내용마다 달라서다. */
@Composable
private fun TimelineRow(time: String, title: String, message: String?, last: Boolean) {
    val colors = LocalKidhackColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = time,
            modifier = Modifier.width(42.dp).padding(top = 2.dp),
            color = colors.muted,
            fontSize = 12.sp,
        )
        Column(
            modifier = Modifier.width(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(9.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.brand),
            )
            if (!last) {
                // 마지막 칸에는 선을 잇지 않는다. 아래로 뻗은 선은 "더 있다"는 뜻이 된다.
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .width(2.dp)
                        .height(TIMELINE_LINE_HEIGHT)
                        .background(colors.border),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            message?.let {
                Text(text = it, color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

/** 점 아래로 잇는 선의 길이. 한 칸의 최소 높이를 이 값이 정한다. */
private val TIMELINE_LINE_HEIGHT = 40.dp

/**
 * ISO 시각에서 앞의 HH:mm 만 떼어 낸다.
 *
 * <p>형식이 다르면 자르지 않고 그대로 보여 준다. 서버 표기가 바뀌었을 때 엉뚱한 두
 * 글자를 시각처럼 내보이는 것보다, 낯선 값을 그대로 두는 편이 알아채기 쉽다.
 */
private fun timeLabel(createdAt: String): String {
    val time = createdAt.substringAfter("T", "")
    return if (time.length >= 5) time.take(5) else createdAt
}

@Composable
private fun WeeklyReport(state: ReportUiState) {
    val colors = LocalKidhackColors.current

    ReportCard {
        Text(
            text = stringResource(R.string.parent_report_week_average),
            color = colors.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatKoreanDuration(state.weekAverageSeconds),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 28.sp,
            color = colors.text,
        )
        state.dailyLimitMinutes?.let { limit ->
            Spacer(Modifier.height(6.dp))
            val diffMinutes = state.weekAverageSeconds / 60 - limit
            Text(
                text = if (diffMinutes > 0) {
                    stringResource(R.string.parent_report_week_over, formatMinutesLabel(diffMinutes))
                } else {
                    stringResource(R.string.parent_report_week_under, formatMinutesLabel(-diffMinutes))
                },
                color = if (diffMinutes > 0) colors.danger else colors.safe,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        WeekBars(state.weekDays, state.dailyLimitMinutes)
    }

    Spacer(Modifier.height(12.dp))
    TopAppsCard(
        titleRes = R.string.parent_report_top_apps_week,
        apps = state.weekApps,
        empty = state.loading,
    )
}

/**
 * 최근 7일 막대.
 *
 * <p>기록이 없는 날은 서버 응답에 아예 빠져 온다. 빠진 날을 건너뛰고 그리면 이틀 쓴
 * 주와 이레 쓴 주가 똑같이 일곱 칸으로 보이므로, 날짜를 만들어 0 으로 채운다.
 */
@Composable
private fun WeekBars(weekDays: List<AppUsageStat>, limitMinutes: Int?) {
    val colors = LocalKidhackColors.current
    val today = LocalDate.now()
    val byDate = weekDays.associate { it.key to it.durationSec }
    val days = (0 until WEEK_DAYS).map { offset ->
        val date = today.minusDays((WEEK_DAYS - 1 - offset).toLong())
        date to (byDate[date.toString()] ?: 0)
    }
    // 막대 높이의 기준. 약속 시간이 있으면 그것에 맞춘다 — 그래야 "약속을 넘긴 날"이
    // 눈에 띄고, 20 초 쓴 날이 가장 높은 막대로 서지 않는다. 약속이 없으면 가장 긴 날에
    // 맞추되, 기준이 너무 작아 몇 초짜리가 꽉 차 보이지 않게 최소 30 분은 둔다.
    val max = maxOf(
        days.maxOf { it.second },
        limitMinutes?.times(60) ?: 0,
        MIN_CHART_SCALE_SEC,
    )

    // 막대와 요일 라벨을 <b>한 Column 에 쌓지 않는다.</b> 전에는 [값·막대·요일]을 한 칸에
    // 세로로 쌓고 Row 를 120dp 로 묶었는데, 가장 높은 막대(80dp)에 값·요일 글자와 간격까지
    // 더하면 120dp 를 넘겼다. 넘친 만큼 아래로 밀려 막대가 요일 글자를 덮었다.
    //
    // 막대는 <b>고정 높이 플롯</b>(값 라벨 + 막대)에서 바닥 정렬로만 자라고, 요일은 그 아래
    // 별도 줄에 둔다. 두 줄이 나뉘어 있으니 막대가 아무리 높아도 요일을 덮지 못한다.
    val plotHeight = 108.dp
    val barMax = 84.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(plotHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { (date, seconds) ->
            val isToday = date == today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    // 1 분이 안 되는 날에 "0" 을 적으면 안 쓴 날과 구별되지 않는다.
                    text = if (seconds >= 60) (seconds / 60).toString() else "",
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 0 인 날도 자리를 남겨 둔다. 아예 안 그리면 막대가 사라져
                        // 그날 기록이 없는 것인지 화면이 잘린 것인지 알 수 없다.
                        .height((barMax * seconds / max).coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isToday) colors.brand else colors.brandLight),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { (date, _) ->
            val isToday = date == today
            Text(
                text = WEEKDAY_LABELS[date.dayOfWeek.value - 1],
                modifier = Modifier.weight(1f),
                color = if (isToday) colors.text else colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 막대 높이 기준의 최소값(초). 30 분. */
private const val MIN_CHART_SCALE_SEC = 30 * 60

/** 월요일부터. `DayOfWeek.value` 가 월=1 이라 그대로 인덱스로 쓴다. */
private val WEEKDAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

/** 많이 쓴 앱 세 개. 넷째부터는 순위 다툼이라기보다 꼬리라서 자른다. */
@Composable
private fun TopAppsCard(titleRes: Int, apps: List<AppUsageStat>, empty: Boolean) {
    val colors = LocalKidhackColors.current
    val top = apps.sortedByDescending { it.durationSec }.take(3)

    ReportCard {
        Text(
            text = stringResource(titleRes),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 15.sp,
            color = colors.text,
        )
        Spacer(Modifier.height(12.dp))
        if (top.isEmpty()) {
            Text(
                text = stringResource(
                    if (empty) R.string.parent_report_loading else R.string.parent_report_no_usage
                ),
                color = colors.muted,
                fontSize = 13.sp,
            )
            return@ReportCard
        }
        top.forEachIndexed { index, app ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RANK_TINTS[index]),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                        color = colors.text,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = app.key,
                    modifier = Modifier.weight(1f),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatKoreanDuration(app.durationSec),
                    color = colors.muted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** 1·2·3 등 색. 금·은·동을 옅게 옮긴 것이라 순위가 색만으로도 읽힌다. */
private val RANK_TINTS = listOf(
    Color(0xFFFFE08A),
    Color(0xFFE2E5EA),
    Color(0xFFF3D3B8),
)

/**
 * 약속 대비 오늘 사용량.
 *
 * <p>약속을 안 걸어 뒀으면 비율을 그릴 기준이 없다. 막대를 비워 두는 대신 안 걸어
 * 뒀다고 적는다 — 빈 막대는 "0분 썼다"로 잘못 읽힌다.
 */
@Composable
private fun PromiseBar(usedSeconds: Int, limitMinutes: Int?) {
    val colors = LocalKidhackColors.current
    if (limitMinutes == null || limitMinutes <= 0) {
        Text(
            text = stringResource(R.string.parent_report_no_promise),
            color = colors.muted,
            fontSize = 13.sp,
        )
        return
    }
    val usedMinutes = usedSeconds / 60
    val ratio = (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)
    val over = usedMinutes >= limitMinutes

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(colors.brandLight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio)
                .height(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (over) colors.danger else colors.brand),
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (over) {
            stringResource(R.string.parent_report_promise_over)
        } else {
            stringResource(
                R.string.parent_report_promise_left,
                formatMinutesLabel(limitMinutes),
                formatMinutesLabel(limitMinutes - usedMinutes),
            )
        },
        color = if (over) colors.danger else colors.muted,
        fontSize = 13.sp,
    )
}

/** 화면 맨 위 제목 줄. */
/** 카드관리 등 다른 서브 페이지와 같은 헤더 규격 — 40dp ‹ 상자 + 좌측 제목 18 Bold, 위 12·아래 4. */
@Composable
internal fun ReportTitleBar(title: String, onBack: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", color = colors.text, fontSize = 26.sp)
        }
        Text(
            text = title,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 18.sp,
            color = colors.text,
        )
    }
}

/** 자녀관리 화면과 같은 모양의 탭. 왼쪽에 붙고 글자 폭만큼만 밑줄이 깔린다. */
@Composable
private fun ReportTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                    color = if (active) colors.text else colors.muted,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (active) colors.brand else Color.Transparent),
                )
            }
        }
    }
}

/** 리포트의 카드 한 장. */
@Composable
private fun ReportCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/** "1시간 20분". 같은 패키지의 [formatDuration] 은 "1h 20m" 이라 리포트에는 안 맞는다. */
private fun formatKoreanDuration(totalSeconds: Int): String {
    // 1 분이 안 되는 값을 분으로 자르면 전부 "0분"이 된다. 실제로 30 초를 쓴 앱과 아예
    // 쓰지 않은 앱이 같은 글자로 보여서, 목록에 앱이 여섯 줄인데 합계는 0분인 화면이 나온다.
    if (totalSeconds < SECONDS_PER_MINUTE) return totalSeconds.toString() + "초"
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    // 한 시간이 넘어가면 초는 의미가 없다. "1시간 20분 37초"는 읽기만 번거롭다.
    if (minutes >= MINUTES_PER_HOUR || seconds == 0) return formatMinutesLabel(minutes)
    return minutes.toString() + "분 " + seconds + "초"
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

private fun formatMinutesLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> h.toString() + "시간 " + m + "분"
        h > 0 -> h.toString() + "시간"
        else -> m.toString() + "분"
    }
}
