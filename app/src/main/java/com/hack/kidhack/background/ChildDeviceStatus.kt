package com.hack.kidhack.background

import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.background.accessibility.HarmfulBlockAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 자녀 기기에서 보호 기능이 **실제로** 켜져 있는지 읽어 온다.
 *
 * 홈/서비스 현황 화면이 「켜짐/꺼짐」 배지를 하드코딩하지 않고 이 값을 쓴다.
 * 전부 OS 가 공개한 플래그만 읽으며, 강제로 켜지는 않는다(GAP-12).
 */
class ChildDeviceStatus(private val context: Context) {

    /** 한 화면이 필요한 상태를 한 번에 담는다. */
    data class Snapshot(
        val locationGranted: Boolean = false,
        val gpsEnabled: Boolean = false,
        val cameraGranted: Boolean = false,
        val notificationsGranted: Boolean = false,
        val harmfulBlockEnabled: Boolean = false,
        val usageAccessGranted: Boolean = false,
        val overlayGranted: Boolean = false,
    )

    /**
     * 설정 조회는 전부 바인더 호출이라 IO 스레드에서 한 번에 읽는다.
     * (`queryUsageStats`·`Settings.Secure` 를 메인 스레드에서 부르면 StrictMode 에 걸린다.)
     */
    suspend fun snapshot(): Snapshot = withContext(Dispatchers.IO) {
        Snapshot(
            locationGranted = KidhackPermissions.location.all { granted(it) },
            gpsEnabled = gpsEnabled(),
            cameraGranted = granted(KidhackPermissions.camera),
            notificationsGranted = KidhackPermissions.notifications?.let { granted(it) } ?: true,
            harmfulBlockEnabled = harmfulBlockEnabled(),
            usageAccessGranted = usageAccessGranted(),
            overlayGranted = Settings.canDrawOverlays(context),
        )
    }

    private fun gpsEnabled(): Boolean = runCatching {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    /** 유해 콘텐츠 차단은 접근성 서비스가 켜져 있어야 동작한다. */
    private fun harmfulBlockEnabled(): Boolean = runCatching {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val target = ComponentName(context, HarmfulBlockAccessibilityService::class.java)
        enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
    }.getOrDefault(false)

    /**
     * 앱 사용 모니터링은 「사용정보 접근」 특별 권한이 있어야 수집된다.
     *
     * AppOps 조회 API 는 전부 deprecated 라, AppUsageWorker 와 같은 방식으로
     * 실제 질의 결과가 비어 있는지로 판정한다(권한이 없으면 항상 빈 목록이 온다).
     */
    private fun usageAccessGranted(): Boolean = runCatching {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@runCatching false
        val end = System.currentTimeMillis()
        manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - USAGE_PROBE_WINDOW_MS, end)
            .orEmpty()
            .isNotEmpty()
    }.getOrDefault(false)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        /** 하루치면 권한 여부를 가르기에 충분하다. */
        const val USAGE_PROBE_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}
