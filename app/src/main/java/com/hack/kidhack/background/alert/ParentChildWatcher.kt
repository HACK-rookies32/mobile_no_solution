package com.hack.kidhack.background.alert

import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.data.local.ParentWatchStore
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.childAlertTypeToParent
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.ChildAlertRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.LocationRepository
import java.text.SimpleDateFormat
import com.hack.kidhack.core.common.todayRange
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 자녀 한 명의 「지금」. [ParentChildWatcher.evaluateAll] 판정의 부산물이다.
 *
 * <p>화면이 따로 계산하지 않고 이 값을 그대로 그린다. 그래야 알림과 화면이 언제나 같은
 * 근거를 말한다 — 알림은 「안전 구역 이탈」인데 타일은 「집」이라고 하는 일이 없다.
 * 판정에 드는 요청은 [ParentChildWatcher] 가 이미 보내므로 화면이 더 부를 것은 없다.
 */
data class ChildWatchStatus(
    val childId: String,
    /** 지금 들어와 있는 안전 구역 이름. 밖이거나 등록된 구역이 없으면 null. */
    val placeName: String? = null,
    /** 등록된 안전 구역이 있는데 그 어디에도 없다. 좌표를 못 읽어 판정을 건너뛴 경우는 false. */
    val outsideFence: Boolean = false,
    /** 마지막 위치를 받은 지 몇 분 됐는지. 위치를 한 번도 못 받았으면 null. */
    val ageMinutes: Long? = null,
    /** 위치 갱신이 30분 넘게 끊겼다. */
    val stale: Boolean = false,
    /**
     * 등록된 안전 구역 수. 좌표를 못 읽어 구역을 조회하지 못했으면 null(모름)이다.
     *
     * <p>판정하느라 어차피 부르는 값이라 화면이 따로 물을 필요가 없다. 「모름」과 「없음」을
     * 나눠 두는 이유는, 못 불러온 것을 「하나도 등록 안 함」으로 적으면 멀쩡히 등록해 둔
     * 부모에게 거짓말이 되기 때문이다.
     */
    val fenceCount: Int? = null,
) {
    /** 위치가 지금도 들어오고 있는지. 타일의 연결 점이 이 값으로 켜지고 꺼진다. */
    val connected: Boolean = ageMinutes != null && !stale
}

/**
 * 보호자 앱이 **스스로** 자녀 상태를 판정해 알림을 만든다.
 *
 * 자녀 기기와 보호자 기기는 서로를 직접 볼 수 없다. 원래대로라면 자녀가 보고한 사건을
 * 서버가 받아 보호자 알림으로 바꿔 줘야 하는데, 그 생성 로직이 서버에 없으면 보호자 화면에는
 * 아무것도 뜨지 않는다.
 *
 * 다행히 아래 세 가지는 **보호자가 읽을 수 있는 API 만으로** 판정할 수 있다.
 *
 * | 사건 | 근거 |
 * |---|---|
 * | 안전 구역 이탈·도착 | `GET /children/{id}/location/current` + `GET /children/{id}/geofences` |
 * | 앱 사용량 한도 도달 | `GET /children/{id}/app-usage` |
 * | 위치 갱신 끊김(GPS 꺼짐 추정) | `location/current` 의 `recordedAt` 이 오래됐는지 |
 *
 * 유해 사이트 차단은 여기에 낄 수 없다. 자녀가 `POST /harmful-access/report` 로 보고만 하고
 * 보호자가 그 사건을 **읽을 수 있는 경로가 명세에 없다**. 그건 서버가 알림을 만들어 줘야 한다.
 *
 * 서버가 나중에 알림을 제대로 만들어 주더라도 중복되지 않는다.
 * [ServerAlertStore][com.hack.kidhack.data.local.ServerAlertStore] 가 id 로 합치고,
 * 여기서 만든 알림은 상태가 **바뀐 순간에만** 나오기 때문이다.
 */
class ParentChildWatcher(
    private val childRepository: ChildRepository,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val appUsageRepository: AppUsageRepository,
    private val childAlertRepository: ChildAlertRepository,
    private val store: ParentWatchStore,
    private val alertSync: ServerAlertSync,
) {

    /**
     * 자녀 전원을 판정하고 **자녀 id → 지금 상태**를 돌려준다.
     *
     * 한 명이 실패해도 나머지는 판정한다. 실패한 자녀는 결과에서 빠진다.
     *
     * @param children 이미 불러 둔 자녀 목록. 넘기지 않으면 여기서 부른다(주기 워커).
     *   화면은 자기가 그리려고 이미 부른 목록을 넘겨서 같은 조회를 두 번 하지 않는다.
     */
    suspend fun evaluateAll(children: List<ChildProfile>? = null): Map<String, ChildWatchStatus> {
        val targets = children ?: runCatching { childRepository.children() }.getOrNull().orEmpty()
        return targets.mapNotNull { child ->
            runCatching { child.id to evaluate(child) }.getOrNull()
        }.toMap()
    }

    private suspend fun evaluate(child: ChildProfile): ChildWatchStatus {
        // 자녀 기기가 직접 올린 사건이 가장 정확하다. 파생 판정보다 먼저 처리해
        // 같은 사건이 두 경로로 두 번 울리지 않게 한다.
        relayChildAlerts(child)
        val location = runCatching { locationRepository.history(child.id).firstOrNull() }.getOrNull()
        var status = ChildWatchStatus(childId = child.id)
        if (location != null) {
            val fences = runCatching { placeRepository.list(child.id) }.getOrNull().orEmpty()
            val fence = checkGeofence(child, location, fences)
            val ageMinutes = parseInstant(location.recordedAt)
                ?.let { (System.currentTimeMillis() - it) / 60_000L }
            status = ChildWatchStatus(
                childId = child.id,
                placeName = (fence as? FenceResult.Inside)?.fence?.name,
                outsideFence = fence is FenceResult.Outside,
                ageMinutes = ageMinutes,
                stale = checkLocationFreshness(child, ageMinutes),
                fenceCount = fences.size,
            )
        }
        val (usageFrom, usageTo) = todayRange()
        val usages = runCatching { appUsageRepository.list(child.id, "APP", usageFrom, usageTo) }.getOrNull()
        if (usages != null) checkUsage(child, usages)
        return status
    }

    /**
     * 자녀 홈 「최근 알림」과 같은 데이터([ChildAlertRepository])를 보호자 알림으로 옮긴다.
     *
     * 유해 사이트 차단은 이 경로로만 보호자에게 전해진다(달리 읽을 API 가 없다).
     * 서버가 아직 이 경로를 구현하지 않았으면 빈 목록이 와서 조용히 건너뛰고,
     * 아래 파생 판정이 이탈·사용량·위치 끊김 3종을 대신 커버한다.
     *
     * id 를 자녀 사건 id 로 고정하므로 몇 번을 다시 가져와도 푸시는 한 번만 울린다.
     */
    private suspend fun relayChildAlerts(child: ChildProfile) {
        val events = runCatching { childAlertRepository.list(child.id) }.getOrNull().orEmpty()
        events.forEach { event ->
            val parentType = childAlertTypeToParent(event.type) ?: return@forEach
            // 파생 판정이 같은 사건을 또 만들지 않도록 상태 기억을 맞춰 둔다.
            when (event.type.uppercase()) {
                ChildAlertStore.TYPE_GEOFENCE_EXIT -> store.setOutside(child.id, true)
                ChildAlertStore.TYPE_GEOFENCE_ENTER -> store.setOutside(child.id, false)
                ChildAlertStore.TYPE_SCREEN_TIME ->
                    store.setUsageAlertedOn(child.id, DAY_FORMAT.format(Date()))
                ChildAlertStore.TYPE_GPS_OFF -> store.setStale(child.id, true)
            }
            alertSync.recordRemote(
                id = "child-" + child.id + "-" + event.id,
                type = parentType,
                title = event.title,
                body = child.name + " · " + event.body.ifBlank { event.title },
                childId = child.id,
            )
        }
    }

    /**
     * 안전 구역 판정 결과.
     *
     * <p>「밖」과 「판정 못 함」을 반드시 구분한다. 등록된 구역이 없거나 좌표를 읽지 못한
     * 것을 「밖」으로 뭉뚱그리면, 아무 잘못 없는 자녀 타일에 경고가 뜬다.
     */
    private sealed interface FenceResult {
        /** 등록된 구역이 없거나 좌표를 읽지 못했다. */
        data object Unknown : FenceResult

        /** 등록된 구역이 있는데 어디에도 들어 있지 않다. */
        data object Outside : FenceResult

        data class Inside(val fence: Geofence) : FenceResult
    }

    private suspend fun checkGeofence(
        child: ChildProfile,
        location: LocationPoint,
        fences: List<Geofence>,
    ): FenceResult {
        if (fences.isEmpty()) return FenceResult.Unknown
        val lat = location.lat.toDoubleOrNull() ?: return FenceResult.Unknown
        val lng = location.lng.toDoubleOrNull() ?: return FenceResult.Unknown

        val inside = fences.firstOrNull { fence ->
            val fLat = fence.lat.toDoubleOrNull()
            val fLng = fence.lng.toDoubleOrNull()
            fLat != null && fLng != null &&
                distanceMeters(lat, lng, fLat, fLng) <= fence.radius.toDouble()
        }
        val wasOutside = store.wasOutside(child.id)

        if (inside == null) {
            // 처음 판정하는 경우 직전 상태는 「안」으로 본다. 그래야 밖으로 나간 순간을 잡는다.
            if (!wasOutside) {
                val zone = fences.firstOrNull()?.name.orEmpty()
                alertSync.emitLocal(
                    type = "LOCATION",
                    title = "안전 구역 이탈",
                    body = KoreanParticles.subject(child.name) +
                        (if (zone.isBlank()) " 안전 구역을 벗어났어요." else " '" + zone + "' 구역을 벗어났어요."),
                    childId = child.id,
                )
            }
            store.setOutside(child.id, true)
            return FenceResult.Outside
        }

        if (wasOutside) {
            if (inside.notifyOnArrival) {
                alertSync.emitLocal(
                    type = "ARRIVAL",
                    title = "안전 구역 도착",
                    body = KoreanParticles.subject(child.name) + " '" + inside.name + "'에 도착했어요.",
                    childId = child.id,
                )
            }
            store.setOutside(child.id, false)
        }
        return FenceResult.Inside(inside)
    }

    /**
     * GPS 가 꺼졌는지는 보호자 쪽에서 **직접** 알 수 없다(`POST /devices/gps-status` 는 쓰기 전용).
     * 대신 위치가 한동안 갱신되지 않은 것을 보고 알린다. 그래서 문구도 「꺼졌다」가 아니라
     * 「갱신이 끊겼다」로 쓴다 — 실제로는 기기 꺼짐·네트워크 문제일 수도 있다.
     *
     * `recordedAt` 형식이 ISO-8601 이 아니면 판정을 건너뛴다(추측해서 틀린 알림을 보내지 않는다).
     *
     * @param ageMinutes 마지막 위치를 받은 지 몇 분 됐는지. 시각을 읽지 못했으면 null.
     * @return 지금 끊긴 상태인지. 판정하지 못했으면 false.
     */
    private suspend fun checkLocationFreshness(child: ChildProfile, ageMinutes: Long?): Boolean {
        if (ageMinutes == null) return false
        val stale = ageMinutes >= STALE_MINUTES
        if (stale == store.wasStale(child.id)) return stale
        if (stale) {
            alertSync.emitLocal(
                type = "GPS_OFF",
                title = "자녀 위치 업데이트가 끊겼어요",
                body = KoreanParticles.subject(child.name) + " " + ageMinutes +
                    "분째 위치를 보내지 않고 있어요. 기기의 위치 서비스가 꺼졌을 수 있어요.",
                childId = child.id,
            )
        }
        store.setStale(child.id, stale)
        return stale
    }

    private suspend fun checkUsage(child: ChildProfile, usages: List<AppUsageStat>) {
        val totalMinutes = usages.sumOf { it.durationSec } / 60
        if (totalMinutes < DAILY_LIMIT_MINUTES) return
        val today = DAY_FORMAT.format(Date())
        if (store.usageAlertedOn(child.id) == today) return
        alertSync.emitLocal(
            type = "APP_USAGE",
            title = "앱 사용 시간 한도 도달",
            body = child.name + " · 오늘 " + formatMinutes(totalMinutes) +
                " 사용 · 한도 " + formatMinutes(DAILY_LIMIT_MINUTES),
            childId = child.id,
        )
        store.setUsageAlertedOn(child.id, today)
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 -> hours.toString() + "시간 " + rest + "분"
            hours > 0 -> hours.toString() + "시간"
            else -> rest.toString() + "분"
        }
    }

    private fun parseInstant(raw: String): Long? =
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()

    companion object {
        /** 자녀 앱의 `ScreenTimeWatcher` 와 같은 값. 서버에 한도 필드가 생기면 함께 옮긴다(GAP-12). */
        const val DAILY_LIMIT_MINUTES = 180

        private const val STALE_MINUTES = 30L
        private const val EARTH_RADIUS_M = 6_371_000.0

        private val DAY_FORMAT = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        /** 두 좌표 사이 거리(m). 안전 구역 반경과 비교하는 데 쓴다. */
        fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(a).coerceIn(0.0, 1.0))
        }
    }
}
