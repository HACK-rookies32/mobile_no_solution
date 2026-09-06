package com.hack.kidhack.feature.child.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.BuildConfig
import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.data.repository.fake.FakeCommandRepository
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import com.hack.kidhack.feature.child.formatDurationSec
import com.hack.kidhack.feature.child.formatTimeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildServicesUiState(
    val loading: Boolean = true,
    val locationLabel: String = "",
    val locationUpdatedAt: String = "",
    val locationGranted: Boolean = false,
    val gpsEnabled: Boolean = false,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val places: List<Geofence> = emptyList(),
    val harmfulBlockEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val usages: List<AppUsage> = emptyList(),
    val totalLabel: String = formatDurationSec(0),
    /** true 면 이 기기에서 실측한 사용량, false 면 서버/더미 대체값. */
    val usageFromDevice: Boolean = false,
    val blockedToday: Int = 0,
    val pushConnected: Boolean = false,
    val error: String? = null,
    /** 목 모드에서만 요청 시뮬레이션 버튼을 노출한다. */
    val simulationAvailable: Boolean = BuildConfig.USE_MOCK,
)

/**
 * 서비스 현황 탭.
 *
 * 위치·앱 사용량은 서버에서, 「켜짐/꺼짐」 배지는 [ChildDeviceStatus] 가 읽은 실제 OS 상태에서,
 * 차단 건수는 유해 접속 알림 개수에서 가져온다.
 */
class ChildServicesViewModel(
    private val sessionRepository: SessionRepository,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val usageCollector: AppUsageCollector,
    private val notificationRepository: NotificationRepository,
    private val commandRepository: CommandRepository,
    private val deviceStatus: ChildDeviceStatus,
    private val alertStore: ChildAlertStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildServicesUiState())
    val state: StateFlow<ChildServicesUiState> = _state.asStateFlow()

    init {
        refresh()
        // 실시간 요청 채널 상태는 계속 바뀌므로 별도로 구독한다.
        viewModelScope.launch {
            commandRepository.connected.collect { connected ->
                _state.update { it.copy(pushConnected = connected) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val childId = sessionRepository.childId()

            val device = deviceStatus.snapshot()
            // 동선은 최신순이라 첫 항목이 마지막으로 확인된 위치다.
            val location = runCatching { locationRepository.history(childId).firstOrNull() }
            val usage = usageCollector.todayUsage()
            val notifications = runCatching { notificationRepository.list(childId) }
            // 현재 위치 좌표(문자열 → Double). 장소 조회는 실패해도 위치 표시엔 지장 없다.
            val currentLat = location.getOrNull()?.lat?.toDoubleOrNull()
            val currentLng = location.getOrNull()?.lng?.toDoubleOrNull()
            val places = runCatching { placeRepository.list(childId) }.getOrDefault(emptyList())

            val resolved = usage.usages
            // 이 기기에서 오늘 실제로 막은 횟수. 서버 알림은 도착이 늦거나 없을 수 있다.
            val blocked = alertStore.countToday(ChildAlertStore.TYPE_HARMFUL_BLOCK) +
                notifications.getOrDefault(emptyList()).count {
                    val type = it.type.uppercase()
                    type.contains("HARMFUL") || type.contains("BLOCK")
                }

            _state.update {
                it.copy(
                    loading = false,
                    locationLabel = location.getOrNull()?.label
                        ?.substringBefore("(")
                        ?.trim()
                        ?.ifBlank { null }
                        ?: "위치 정보 없음",
                    locationUpdatedAt = location.getOrNull()?.recordedAt?.let(::formatTimeLabel).orEmpty(),
                    locationGranted = device.locationGranted,
                    gpsEnabled = device.gpsEnabled,
                    currentLat = currentLat,
                    currentLng = currentLng,
                    places = places,
                    harmfulBlockEnabled = device.harmfulBlockEnabled,
                    usageAccessGranted = device.usageAccessGranted,
                    usages = resolved,
                    totalLabel = formatDurationSec(usage.totalSeconds),
                    usageFromDevice = usage.fromDevice,
                    blockedToday = blocked,
                    error = if (location.isFailure && resolved.isEmpty()) {
                        "서비스 현황을 불러오지 못했어요"
                    } else {
                        null
                    },
                )
            }
        }
    }

    /**
     * 서버 없이 부모 요청 → 알림 → 캡처/촬영 전체 흐름을 손으로 돌려 보기 위한 시뮬레이션.
     * 실서버(USE_MOCK=false)에서는 [FakeCommandRepository] 가 아니므로 아무 일도 하지 않는다.
     */
    fun simulateRequest(type: RemoteCommandType) {
        val fake = commandRepository as? FakeCommandRepository ?: return
        viewModelScope.launch {
            fake.emit(
                RemoteCommand(
                    requestId = "sim-" + System.currentTimeMillis(),
                    type = type,
                    childId = sessionRepository.childId(),
                    requestedBy = "엄마",
                ),
            )
        }
    }
}
