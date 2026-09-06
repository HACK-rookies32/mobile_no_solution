package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.BuildConfig
import com.hack.kidhack.R
import com.hack.kidhack.background.alert.ServerAlertSync
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.hack.kidhack.domain.model.displayTitle
import com.hack.kidhack.domain.model.body

data class NotificationsUiState(
    val settings: NotificationSettings = NotificationSettings(true, true, false, true),
    val notifications: List<AppNotification> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** 테스트 버튼 결과처럼 오류는 아니지만 알려 줘야 하는 것. */
    val notice: String? = null,
)

class NotificationsViewModel(
    private val userRepository: UserRepository,
    private val alertSync: ServerAlertSync,
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            val alerts = alertSync.sync().alerts
            runCatching { userRepository.notificationSettings() }
                .onSuccess { s ->
                    _state.update { it.copy(loading = false, settings = s, notifications = alerts) }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, notifications = alerts, error = t.message) }
                }
            alertSync.markAllRead()
        }
    }

    /**
     * 알림 테스트 버튼(디버그 빌드 전용).
     *
     * 서버 폴링과 같은 경로([ServerAlertSync.emitLocal])를 타므로 「최근 알림에 영구 기록 +
     * 푸시 표시」가 실제로 되는지 그대로 확인할 수 있다. 목록을 다시 읽되 읽음 처리는 하지
     * 않는다 — 대시보드의 미확인 건수가 오르는 것까지 봐야 하기 때문이다.
     */
    fun sendTestAlert(label: String, type: String, title: String, body: String) {
        viewModelScope.launch {
            val child = runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children)
                children.firstOrNull { it.id == id }
            }.getOrNull()
            val pushed = alertSync.emitLocal(
                type = type,
                title = title,
                body = child?.let { it.name + " · " + body } ?: body,
                childId = child?.id,
            )
            val alerts = alertSync.cached()
            _state.update {
                it.copy(
                    notifications = alerts,
                    notice = if (pushed) {
                        label + " 알림을 " + (child?.name ?: "전체") + " 앞으로 보냈어요. 상단바를 확인해 주세요."
                    } else {
                        label + " 알림을 기록했어요. 수신 설정이나 알림 권한이 꺼져 있어 푸시는 뜨지 않았어요."
                    },
                )
            }
        }
    }

    fun patch(update: (NotificationSettings) -> NotificationSettings) {
        val oldSettings = _state.value.settings
        val next = update(oldSettings)

        // Optimistic update
        _state.update { it.copy(settings = next) }

        viewModelScope.launch {
            runCatching { userRepository.patchNotificationSettings(next) }
                .onSuccess { s ->
                    _state.update { it.copy(settings = s, error = null) }
                }
                .onFailure { t ->
                    // Rollback on failure
                    _state.update { it.copy(settings = oldSettings, error = t.message) }
                }
        }
    }
}

@Composable
fun ParentNotificationsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        // 헤더 — ‹ 알림
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = colors.text, fontSize = 26.sp) }
            } else {
                Spacer(Modifier.width(14.dp))
            }
            Text(
                stringResource(R.string.parent_notif_list_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (state.loading && state.notifications.isEmpty() || state.error != null) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                    ParentLoadingOrError(state.loading && state.notifications.isEmpty(), state.error, viewModel::load)
                }
            }

            if (!state.loading && state.notifications.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.parent_empty),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontSize = 14.sp,
                    )
                }
            }

            // 오늘 / 이전 으로 나눈다. 목록은 최신순이라 각 묶음 안의 순서는 그대로 둔다.
            val today = state.notifications.filter { isToday(it.createdAt) }
            val older = state.notifications.filterNot { isToday(it.createdAt) }

            if (today.isNotEmpty()) {
                NotifGroupLabel(stringResource(R.string.parent_notif_group_today))
                today.forEachIndexed { i, n ->
                    NotificationRow(n)
                    if (i < today.lastIndex) NotifDivider()
                }
            }
            if (older.isNotEmpty()) {
                NotifGroupLabel(stringResource(R.string.parent_notif_group_older))
                older.forEachIndexed { i, n ->
                    NotificationRow(n)
                    if (i < older.lastIndex) NotifDivider()
                }
            }

            // 개발 빌드 전용 알림 점검 도구. 릴리즈에는 들어가지 않는다.
            if (BuildConfig.DEBUG) {
                TestAlertSection(state.notice) { sample ->
                    viewModel.sendTestAlert(
                        label = context.getString(sample.labelRes),
                        type = sample.type,
                        title = context.getString(sample.titleRes),
                        body = context.getString(sample.bodyRes),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NotifGroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 4.dp),
        color = LocalKidhackColors.current.subtle,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

@Composable
private fun NotifDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = LocalKidhackColors.current.line)
}

/** 알림 종류. 색은 종류로만 가른다(무지개 대신 위험/긍정/위치/기본 네 갈래). */
private enum class NotifKind { Danger, Positive, Location, Neutral }

private fun notifKind(type: String): NotifKind = when (type) {
    "DEVICE_POWER_OFF", "HARMFUL_SITE", "SCREEN_TIME_LIMIT" -> NotifKind.Danger
    "PAYMENT_APPROVED", "ALLOWANCE_RECEIVED" -> NotifKind.Positive
    "LOCATION_ENTER", "LOCATION_ARRIVED", "LOCATION_EXIT" -> NotifKind.Location
    else -> NotifKind.Neutral
}

private fun notifIcon(type: String): Int = when (type) {
    "LOCATION_ENTER", "LOCATION_ARRIVED", "LOCATION_EXIT" -> R.drawable.ic_place
    "DEVICE_POWER_OFF" -> R.drawable.ic_warning
    "HARMFUL_SITE" -> R.drawable.ic_shield
    "PAYMENT_APPROVED", "PAYMENT_CANCELED", "ALLOWANCE_RECEIVED", "ALLOWANCE_RECLAIMED" -> R.drawable.ic_card
    "CHAT_MESSAGE", "INQUIRY_ANSWERED" -> R.drawable.ic_chat
    "SCREEN_TIME_LIMIT" -> R.drawable.ic_clock
    "APP_USAGE" -> R.drawable.ic_apps
    "NOTICE" -> R.drawable.ic_bell
    else -> R.drawable.ic_bell
}

@Composable
private fun NotificationRow(n: AppNotification) {
    val colors = LocalKidhackColors.current
    val tint = when (notifKind(n.type)) {
        NotifKind.Danger -> colors.danger
        NotifKind.Positive -> colors.safe
        NotifKind.Location -> colors.brand
        NotifKind.Neutral -> colors.muted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 안 읽은 알림은 옅은 주황 바탕으로 눈에 띄게 한다.
            .background(if (!n.read) colors.brandLight.copy(alpha = 0.5f) else colors.card)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.card)
                .border(1.dp, colors.line, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(notifIcon(n.type)),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = tint,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = n.displayTitle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                letterSpacing = (-0.2).sp,
                color = colors.text,
            )
            if (n.body.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = n.body,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = colors.muted,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(relativeNotifTime(n.createdAt), fontSize = 11.sp, color = colors.subtle)
            if (!n.read) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.brand))
            }
        }
    }
}

/** 오늘 자정 이후에 온 알림인지. 파싱 실패하면 「이전」으로 본다. */
private fun isToday(raw: String): Boolean = runCatching {
    java.time.LocalDateTime.parse(raw).toLocalDate() == java.time.LocalDate.now()
}.getOrDefault(false)

/** "방금 전 / N분 전 / N시간 전 / 어제 / M월 D일". 파싱 실패하면 원문 뒤 5글자. */
private fun relativeNotifTime(raw: String): String = runCatching {
    val dt = java.time.LocalDateTime.parse(raw)
    val instant = dt.atZone(java.time.ZoneId.systemDefault()).toInstant()
    val min = java.time.Duration.between(instant, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
    val today = java.time.LocalDate.now()
    when {
        min < 1 -> "방금 전"
        min < 60 -> min.toString() + "분 전"
        dt.toLocalDate() == today -> (min / 60).toString() + "시간 전"
        dt.toLocalDate() == today.minusDays(1) -> "어제"
        else -> dt.monthValue.toString() + "월 " + dt.dayOfMonth + "일"
    }
}.getOrDefault(raw.takeLast(5))

/**
 * 알림 종류별 테스트 샘플.
 *
 * [type] 은 서버가 내려 주는 값과 같은 문자열이라 아이콘 매핑과 수신 설정(알림 탭 토글)
 * 필터를 그대로 탄다. 즉 버튼으로 띄운 알림은 실제 알림과 같은 경로를 지난다.
 */
private data class AlertSample(
    val type: String,
    val labelRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
)

private val TestAlertSamples = listOf(
    AlertSample("GPS_OFF", R.string.parent_notif_test_gps, R.string.parent_notif_test_gps_title, R.string.parent_notif_test_gps_body),
    AlertSample("LOCATION", R.string.parent_notif_test_geofence, R.string.parent_notif_test_geofence_title, R.string.parent_notif_test_geofence_body),
    AlertSample("HARMFUL", R.string.parent_notif_test_harmful, R.string.parent_notif_test_harmful_title, R.string.parent_notif_test_harmful_body),
    AlertSample("APP_USAGE", R.string.parent_notif_test_usage, R.string.parent_notif_test_usage_title, R.string.parent_notif_test_usage_body),
    AlertSample("NOTICE", R.string.parent_notif_test_notice, R.string.parent_notif_test_notice_title, R.string.parent_notif_test_notice_body),
)

/** 개발 빌드 전용. 목록 맨 아래에 옅게 둔다 — 릴리즈에는 없다. */
@Composable
private fun TestAlertSection(notice: String?, onSend: (AlertSample) -> Unit) {
    val colors = LocalKidhackColors.current
    NotifGroupLabel(stringResource(R.string.parent_notif_test_section))
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            stringResource(R.string.parent_notif_test_desc),
            fontSize = 12.sp,
            color = colors.subtle,
            lineHeight = 17.sp,
        )
        TestAlertSamples.forEach { sample ->
            KidButton(
                text = stringResource(sample.labelRes),
                onClick = { onSend(sample) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                variant = KidButtonVariant.Secondary,
            )
        }
        if (notice != null) {
            Spacer(Modifier.height(8.dp))
            Text(notice, fontSize = 12.sp, color = colors.muted, lineHeight = 17.sp)
        }
    }
}
