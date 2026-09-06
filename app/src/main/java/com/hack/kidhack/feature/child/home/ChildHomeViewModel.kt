package com.hack.kidhack.feature.child.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.background.usage.ScreenTimeWatcher
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.childId
import com.hack.kidhack.domain.repository.notificationRecipientId
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.feature.child.formatDurationMin
import com.hack.kidhack.feature.child.formatTimeLabel
import com.hack.kidhack.feature.child.remainingLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeServiceRow(
    val label: String,
    val desc: String,
    val emoji: String,
    val active: Boolean,
    /**
     * 꺼져 있을 때 눌러서 열 설정 화면. null 이면 앱에서 켤 방법이 없다는 뜻.
     *
     * 온보딩을 건너뛴 자녀도 여기서 바로 켤 수 있어야 한다.
     */
    val settingsAction: String? = null,
)

/**
 * 부모가 걸어 둔 앱 규칙 한 줄 — 「무엇이 어느 기준이고 얼마나 썼는지」.
 *
 * <p>서버 규약상 하루 한도 0분이 곧 완전 차단이다([blocked]). 규칙이 있는데 아직 안 쓴 앱도
 * 목록에 남긴다 — 규칙이 걸려 있다는 사실은 쓰기 전에 알아야 쓸모가 있다.
 */
data class RuledAppRow(
    val name: String,
    /** 기기에 깔린 앱 아이콘을 찾는 열쇠. 안 깔린 앱이면 못 찾는다. */
    val packageName: String,
    /** 부모가 정한 하루 한도(분). 0 이면 아예 못 쓰는 앱. */
    val limitMinutes: Int,
    val usedMinutes: Int,
    /** 사용 기록 권한이 꺼져 있어 실제로 얼마나 썼는지 못 읽은 상태. */
    val usageKnown: Boolean = true,
) {
    val blocked: Boolean get() = limitMinutes <= 0

    /** 한도를 채웠거나 넘겼는지. */
    val over: Boolean get() = !blocked && usageKnown && usedMinutes >= limitMinutes

    /** 막대 길이. 차단된 앱은 잴 것이 없다. */
    val fraction: Float
        get() = if (blocked || !usageKnown) 0f
        else (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)
}

data class HomeAlertRow(
    val message: String,
    val time: String,
    val emoji: String,
    val tint: AlertTint,
    val opensScreenshot: Boolean = false,
    val opensChat: Boolean = false,
    val opensCamera: Boolean = false,
)

enum class AlertTint { Brand, Danger, Warn, Info }

data class ChildHomeUiState(
    val loading: Boolean = true,
    val name: String = "",
    val alias: String = "",
    val usedMinutes: Int = 0,
    /** 부모가 서버에 정한 하루 총 사용 한도(분). 없으면 null 이라 「남은 시간」을 적지 않는다. */
    val limitMinutes: Int? = null,
    /** 오늘 앱별 사용 — 자녀가 「내가 뭘 얼마나 썼는지」 직접 본다. 많이 쓴 순. */
    val usages: List<AppUsage> = emptyList(),
    /** 부모가 한도·차단을 걸어 둔 앱. 규칙이 없으면 비어 있다. */
    val ruledApps: List<RuledAppRow> = emptyList(),
    val protectedNow: Boolean = false,
    val services: List<HomeServiceRow> = emptyList(),
    val alerts: List<HomeAlertRow> = emptyList(),
    /** 안 읽은 알림 수. 헤더 종 배지에 쓴다. 알림 화면을 열면 서버에서 0 으로 준다. */
    val unreadCount: Int = 0,
    val error: String? = null,
) {
    val usedLabel: String get() = formatDurationMin(usedMinutes)

    /** 한도까지 남은 분. 한도가 없거나 이미 넘겼으면 null / 0. */
    val remainingMinutes: Int? get() = limitMinutes?.let { (it - usedMinutes).coerceAtLeast(0) }

    /**
     * 꺼져 있는데 자녀가 켤 수 있는 보호 기능들.
     *
     * 접근성은 앱을 강제 중지하거나 업데이트하면 안드로이드가 꺼 버린다(앱이 막을 수 없다).
     * 그래서 앱을 열 때마다 확인해서 눈에 띄게 알려 준다.
     */
    val needsAttention: List<HomeServiceRow>
        get() = services.filter { !it.active && it.settingsAction != null }

    /**
     * 규칙이 걸리지 <b>않은</b> 앱의 사용량.
     *
     * <p>규칙 있는 앱은 위쪽 「약속한 앱」에서 한도와 함께 보여 주므로, 아래 「많이 쓴 앱」에
     * 다시 적으면 같은 앱이 한 화면에 두 번 나온다.
     */
    val otherUsages: List<AppUsage>
        get() {
            val ruled = ruledApps.map { it.name }.toSet()
            return usages.filterNot { it.appName in ruled }
        }
}

/**
 * 자녀 홈 탭.
 *
 * 스크린타임은 실제 앱 사용량 합계, 「루키즈가 지키고 있어요」 배지는 실제 권한·서비스 상태,
 * 최근 알림은 알림 API 에서 가져온다. 하드코딩 문구는 남기지 않는다.
 */
class ChildHomeViewModel(
    private val sessionRepository: SessionRepository,
    private val usageCollector: AppUsageCollector,
    private val appControlRepository: AppControlRepository,
    private val notificationRepository: NotificationRepository,
    private val deviceStatus: ChildDeviceStatus,
    private val alertStore: ChildAlertStore,
    private val screenTimeWatcher: ScreenTimeWatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildHomeUiState())
    val state: StateFlow<ChildHomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val user = sessionRepository.current()?.user
            val notifications = runCatching { notificationRepository.list(sessionRepository.notificationRecipientId()) }

            // 스크린타임은 서비스 현황 탭의 앱 사용 모니터링 합계와 반드시 같아야 하므로
            // 같은 수집기를 쓴다.
            val usage = usageCollector.todayUsage()
            val device = deviceStatus.snapshot()
            val usedMinutes = usage.totalMinutes

            // 하루 총 한도(부모가 서버에 설정)를 한 번 읽어 화면에 남은 시간을 보여 준다.
            val limitMinutes = runCatching { screenTimeWatcher.refreshLimit() }.getOrNull()

            // 앱별 규칙. 지금까지 이 값을 읽는 곳은 접근성 서비스뿐이라, 아이는 무엇이 얼마로
            // 묶여 있는지 <b>막히고 나서야</b> 알았다. 홈에서 미리 보여 준다.
            val ruledApps = buildRuledApps(usage)

            // 한도 도달을 여기서도 확인한다(시간당 워커만 믿으면 최대 1시간 늦게 뜬다).
            screenTimeWatcher.check(usedMinutes)

            // 기기에서 실제로 일어난 사건(차단·한도 도달)을 서버 알림보다 앞에 둔다.
            val localAlerts = alertStore.recent()
            val usageTracked = device.usageAccessGranted
            val locationOn = device.locationGranted && device.gpsEnabled
            val harmfulOn = device.harmfulBlockEnabled
            val alertsOn = device.notificationsGranted

            _state.update {
                it.copy(
                    loading = false,
                    name = user?.name.orEmpty(),
                    alias = user?.name?.aliasOrSelf().orEmpty(),
                    usedMinutes = usedMinutes,
                    limitMinutes = limitMinutes,
                    usages = usage.usages,
                    ruledApps = ruledApps,
                    protectedNow = locationOn || harmfulOn,
                    services = listOf(
                        HomeServiceRow(
                            label = "위치 추적",
                            desc = if (locationOn) "부모님이 위치를 확인 중" else "위치 권한이나 GPS 가 꺼져 있어요",
                            emoji = "📍",
                            active = locationOn,
                        ),
                        HomeServiceRow(
                            label = "유해 콘텐츠 차단",
                            desc = if (harmfulOn) "안전한 인터넷 사용 중" else "눌러서 켜 주세요",
                            emoji = "🚫",
                            active = harmfulOn,
                            settingsAction = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
                        ),
                        HomeServiceRow(
                            label = "앱 사용 모니터링",
                            desc = usageDesc(usageTracked, usage.usages.size),
                            emoji = "📊",
                            active = usageTracked,
                            settingsAction = android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        ),
                        HomeServiceRow(
                            label = "요청 알림",
                            desc = if (alertsOn) "캡처·촬영 요청을 바로 받아요" else "알림 권한이 꺼져 있어요",
                            emoji = "🔔",
                            active = alertsOn,
                        ),
                    ),
                    unreadCount = notifications.getOrDefault(emptyList()).count { n -> !n.read },
                    alerts = (localAlerts + notifications.getOrDefault(emptyList()))
                        // 기기에 남긴 기록과 서버가 돌려준 같은 사건이 나란히 뜨지 않게 한다.
                        .distinctBy { it.message }
                        .take(MAX_ALERTS)
                        .map { alert -> alert.toRow() },
                    error = if (usage.usages.isEmpty() && notifications.isFailure) {
                        "정보를 불러오지 못했어요"
                    } else {
                        null
                    },
                )
            }
        }
    }

    /**
     * 규칙 × 오늘 사용량을 잇는다.
     *
     * <p>기준은 패키지명이다. 다만 「사용정보 접근」이 꺼져 있으면 기기에서 못 읽어 서버
     * 대체값이 오는데 거기엔 패키지명이 없다 — 그때만 앱 이름으로 맞춰 보고, 그것도 없으면
     * 사용량을 <b>모른다</b>고 표시한다(0분으로 적으면 다 쓴 앱을 안 쓴 것처럼 보여 준다).
     */
    private suspend fun buildRuledApps(usage: AppUsageCollector.Result): List<RuledAppRow> {
        val id = sessionRepository.childId().takeIf { it.isNotBlank() } ?: return emptyList()
        val rules = runCatching { appControlRepository.items(id) }.getOrDefault(emptyList())
            // dailyLimitMinutes 가 null 이면 「제한 없음」이라 보여 줄 기준이 없다.
            .filter { it.enabled && it.dailyLimitMinutes != null }
        if (rules.isEmpty()) return emptyList()

        val byPackage = usage.usages
            .mapNotNull { u -> u.packageName?.let { it to u.durationSec / 60 } }
            .toMap()
        val byName = usage.usages.associate { it.appName to it.durationSec / 60 }

        return rules
            .map { rule ->
                val minutes = byPackage[rule.packageName] ?: byName[rule.name]
                RuledAppRow(
                    name = rule.name,
                    packageName = rule.packageName,
                    limitMinutes = rule.dailyLimitMinutes ?: 0,
                    usedMinutes = minutes ?: 0,
                    // 기기에서 실측했다면 목록에 없는 앱은 진짜로 0분이다.
                    usageKnown = usage.fromDevice || minutes != null,
                )
            }
            // 아예 못 쓰는 앱은 잴 것이 없으니 맨 아래로, 나머지는 많이 쓴 순으로.
            .sortedWith(compareBy({ it.blocked }, { -it.usedMinutes }))
    }

    private fun usageDesc(tracked: Boolean, count: Int): String = when {
        !tracked -> "눌러서 사용 기록을 켜 주세요"
        count == 0 -> "오늘 기록된 앱이 없어요"
        else -> "오늘 ${count}개 앱 사용 중"
    }

    /**
     * 성 한 글자를 떼어 친근한 호칭으로("김민수" → "민수").
     * 한글 2~4자 이름일 때만 적용한다 — 영문 등 다른 이름은 그대로 쓴다("ChildFour" → "hildFour" 오류 방지).
     */
    private fun String.aliasOrSelf(): String {
        val t = trim()
        val allHangul = t.isNotEmpty() && t.all { it in '가'..'힣' }
        return if (allHangul && t.length in 2..4) t.drop(1) else t
    }

    private fun AppNotification.toRow(): HomeAlertRow {
        val upper = type.uppercase()
        val isCameraRequest = upper.contains("CAMERA")
        val isCapture = !isCameraRequest &&
            (upper.contains("CAPTURE") || upper.contains("SCREENSHOT"))
        val isScheduled = upper.contains("SCHEDULED")
        val isChat = upper.contains("CHAT") || upper.contains("MESSAGE")
        val isHarmful = upper.contains("HARMFUL") || upper.contains("BLOCK")
        val isScreenTime = upper.contains("SCREEN_TIME") || upper.contains("LIMIT")
        return HomeAlertRow(
            message = message,
            time = formatTimeLabel(createdAt),
            emoji = when {
                isCameraRequest -> "📷"
                isCapture -> "📸"
                isHarmful -> "🚫"
                isScreenTime -> "⏰"
                isChat -> "💬"
                else -> "🔔"
            },
            tint = when {
                isHarmful -> AlertTint.Danger
                isScreenTime -> AlertTint.Warn
                isChat -> AlertTint.Info
                isScheduled -> AlertTint.Info
                else -> AlertTint.Brand
            },
            opensScreenshot = isCapture,
            opensChat = isChat,
            opensCamera = isCameraRequest,
        )
    }

    private companion object {
        const val MAX_ALERTS = 3
    }
}
