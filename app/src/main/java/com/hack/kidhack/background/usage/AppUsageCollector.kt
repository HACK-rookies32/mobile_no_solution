package com.hack.kidhack.background.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.hack.kidhack.core.common.todayRange
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 오늘 하루의 앱 사용량을 **기기에서 직접** 읽는다.
 *
 * 홈·마이페이지의 스크린타임과 서비스 현황의 앱 사용 모니터링이 서로 다른 값을 보이면 안 되므로,
 * 세 화면 모두 이 클래스 하나만 호출한다. 스크린타임 = [todayUsage] 합계라는 정의다.
 *
 * 「사용정보 접근」 권한이 없으면 OS 가 빈 목록을 주므로, 그때만 서버(목 모드에서는 더미) 값으로
 * 대체해 화면이 텅 비지 않게 한다. 어느 쪽을 쓰고 있는지는 [Result.fromDevice] 로 알려 준다.
 */
class AppUsageCollector(
    private val context: Context,
    private val appUsageRepository: AppUsageRepository,
    private val sessionRepository: SessionRepository,
) {
    data class Result(
        val usages: List<AppUsage> = emptyList(),
        /** true 면 이 기기에서 실측한 값, false 면 서버/더미 대체값. */
        val fromDevice: Boolean = false,
    ) {
        val totalSeconds: Int get() = usages.sumOf { it.durationSec }
        val totalMinutes: Int get() = totalSeconds / 60
    }

    suspend fun todayUsage(limit: Int = MAX_APPS): Result = withContext(Dispatchers.IO) {
        val local = readDeviceUsage(limit)
        if (local.isNotEmpty()) return@withContext Result(local, fromDevice = true)

        val fallback = runCatching {
            // list()가 이제 AppUsageStat(key=앱이름, durationSec)을 주므로 AppUsage로 매핑한다.
            val (from, to) = todayRange()
            appUsageRepository.list(sessionRepository.childId(), "APP", from, to)
                .map { AppUsage(appName = it.key, durationSec = it.durationSec, category = null) }
        }.getOrDefault(emptyList())
        Result(fallback.sortedByDescending { it.durationSec }.take(limit), fromDevice = false)
    }

    /**
     * `queryAndAggregateUsageStats` 는 패키지별 합계를 준다.
     * 런처에서 열 수 있는 앱만 남겨 시스템 서비스·오버레이 패키지를 걸러 낸다.
     */
    private fun readDeviceUsage(limit: Int): List<AppUsage> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val packageManager = context.packageManager
        val end = System.currentTimeMillis()
        val stats = runCatching {
            manager.queryAndAggregateUsageStats(startOfToday(), end)
        }.getOrNull().orEmpty()

        // 우리 앱·홈 런처·GMS 는 뺀다 — 예전엔 자녀 스크린타임(부모 대시보드 보고 포함)에 우리 앱 사용시간이 섞였다.
        val excluded = excludedPackages()
        return stats.values
            .asSequence()
            .filter { it.totalTimeInForeground >= MIN_FOREGROUND_MS }
            .filter { it.packageName !in excluded }
            .mapNotNull { stat ->
                val info = packageManager.launchableAppInfo(stat.packageName) ?: return@mapNotNull null
                AppUsage(
                    appName = packageManager.getApplicationLabel(info).toString(),
                    durationSec = (stat.totalTimeInForeground / 1000L).toInt(),
                    category = info.categoryLabel(),
                    packageName = stat.packageName,
                )
            }
            .sortedByDescending { it.durationSec }
            .take(limit)
            .toList()
    }

    /** 사용량 집계에서 뺄 패키지: 우리 앱, 홈 런처, GMS·권한 컨트롤러·시스템 UI. */
    private fun excludedPackages(): Set<String> {
        val launchers = runCatching {
            val home = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        return buildSet {
            add(context.packageName)
            addAll(launchers)
            addAll(SYSTEM_PACKAGES)
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun PackageManager.launchableAppInfo(packageName: String): ApplicationInfo? {
        if (getLaunchIntentForPackage(packageName) == null) return null
        return runCatching { getApplicationInfo(packageName, 0) }.getOrNull()
    }

    private fun ApplicationInfo.categoryLabel(): String? =
        ApplicationInfo.getCategoryTitle(context, category)?.toString()

    private companion object {
        const val MAX_APPS = 8

        /** 1초도 안 쓴 앱까지 나열하면 목록이 지저분해진다. */
        const val MIN_FOREGROUND_MS = 1_000L

        val SYSTEM_PACKAGES = setOf(
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.systemui",
            "com.android.vending",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
        )
    }
}
