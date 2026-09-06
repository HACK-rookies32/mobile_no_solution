package com.hack.kidhack.background.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import java.util.Calendar

/**
 * 부모가 걸어 둔 **앱별** 통제 규칙을 기기에서 적용한다.
 *
 * <p>서버·부모 앱은 오래전부터 규칙을 만들고 저장하고 있었는데, 자녀 기기가 그 규칙을
 * 한 번도 읽지 않았다. 유튜브를 차단해 둬도 자녀 폰에서는 그냥 열렸다. 하루 총
 * 스크린타임([ScreenTimeWatcher])만 적용되고 있었다.
 *
 * <p>접근성 서비스는 앱을 열 때마다 이 클래스에 물어본다. 그 자리에서 네트워크를 탈 수
 * 없으므로 규칙은 캐시해 두고 [refreshRules] 로만 갱신한다. 사용량은 OS 가 들고 있어
 * 바로 읽을 수 있지만, 이벤트가 초당 수십 번 오므로 잠깐 캐시한다.
 */
class AppRuleWatcher(
    private val context: Context,
    private val appControlRepository: AppControlRepository,
    private val sessionRepository: SessionRepository,
) {

    /** 왜 막았는지. 자녀에게 보여 줄 말이 다르다. */
    sealed interface Block {
        /** 부모가 아예 못 쓰게 해 둔 앱. */
        data class Forbidden(val appName: String) : Block

        /** 오늘 쓸 수 있는 만큼 다 썼다. */
        data class LimitReached(val appName: String, val limitMinutes: Int) : Block
    }

    /** 패키지명 → 규칙. 마지막으로 서버에서 읽은 것. */
    @Volatile
    private var rules: Map<String, AppControlItem> = emptyMap()

    @Volatile
    private var rulesLoadedAt: Long = 0L

    /** 패키지명 → 오늘 앞으로 나와 있던 시간(분). OS 가 주는 값이라 짧게만 캐시한다. */
    @Volatile
    private var usageMinutes: Map<String, Int> = emptyMap()

    @Volatile
    private var usageReadAt: Long = 0L

    /**
     * 서버에서 규칙을 다시 읽는다. 실패하면 이전 캐시를 유지한다.
     *
     * <p>못 읽었다고 캐시를 비우면 **네트워크가 끊긴 동안 모든 앱이 풀린다.** 부모가 걸어 둔
     * 차단이 와이파이를 끊는 것만으로 무력해지면 안 된다.
     */
    suspend fun refreshRules() {
        val id = sessionRepository.childId().takeIf { it.isNotBlank() } ?: return
        runCatching { appControlRepository.items(id) }
            .onSuccess { items ->
                rules = items.associateBy { it.packageName }
                rulesLoadedAt = System.currentTimeMillis()
            }
    }

    /** 마지막으로 규칙을 읽은 지 오래됐는지. 접근성 서비스가 이 값을 보고 갱신을 예약한다. */
    fun rulesStale(): Boolean = System.currentTimeMillis() - rulesLoadedAt > RULES_TTL_MS

    /**
     * 이 앱을 지금 막아야 하는지.
     *
     * <p>규칙이 없거나 꺼져 있으면 막지 않는다 — 통제 목록에 없는 앱은 자유롭게 쓴다.
     */
    fun blockReasonFor(packageName: String): Block? {
        val rule = rules[packageName] ?: return null
        if (!rule.enabled) return null

        val limit = rule.dailyLimitMinutes ?: return null
        // 0 분은 「하루 0분만 쓸 수 있다」가 아니라 완전 차단이다(서버 규약).
        if (limit <= 0) return Block.Forbidden(rule.name)

        val used = usedMinutes(packageName)
        return if (used >= limit) Block.LimitReached(rule.name, limit) else null
    }

    /**
     * 오늘 이 앱을 몇 분 썼는지.
     *
     * <p>`queryAndAggregateUsageStats` 는 패키지별 합계를 한 번에 준다. 앱을 열 때마다
     * 부르면 비싸므로 잠깐 캐시한다 — 몇 초 늦게 걸리는 것은 문제가 되지 않는다.
     */
    private fun usedMinutes(packageName: String): Int {
        val now = System.currentTimeMillis()
        if (now - usageReadAt > USAGE_TTL_MS) {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            usageMinutes = manager
                ?.let { runCatching { it.queryAndAggregateUsageStats(startOfToday(), now) }.getOrNull() }
                ?.mapValues { (_, stat) -> (stat.totalTimeInForeground / 60_000L).toInt() }
                .orEmpty()
            usageReadAt = now
        }
        return usageMinutes[packageName] ?: 0
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        /** 부모가 방금 건 규칙도 이 안에 반영된다. */
        const val RULES_TTL_MS = 5 * 60 * 1000L

        /** 사용량 캐시. 한도에 닿는 순간을 몇 초 안에 잡으면 충분하다. */
        const val USAGE_TTL_MS = 30 * 1000L
    }
}
