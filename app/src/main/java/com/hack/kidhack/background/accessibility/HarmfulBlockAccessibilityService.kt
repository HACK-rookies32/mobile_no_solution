package com.hack.kidhack.background.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildAlertUploadWorker
import com.hack.kidhack.core.common.HarmfulKeywordMatcher
import com.hack.kidhack.background.usage.AppRuleWatcher
import com.hack.kidhack.background.usage.ScreenTimeWatcher
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.time.LocalDate

/**
 * 접근성 기반 유해 페이지·키워드 차단 (REQ-M-11).
 *
 * 판정 순서는 블랙리스트 적중 → 화이트리스트 예외다. 적중하면
 *  1. 홈으로 내보내 **실제로 페이지에서 빠져나오고**
 *  2. 오버레이(권한 없으면 토스트)로 자녀에게 알린 뒤
 *  3. `POST /harmful-access/report` 로 보호자에게 보고한다.
 *
 * 검사 대상은 이벤트 텍스트 + 화면 노드 트리다. 크롬 계열 브라우저는 주소창이
 * 별도 노드라 이벤트 텍스트만 봐서는 URL 을 놓친다.
 */
class HarmfulBlockAccessibilityService : AccessibilityService() {

    private val blocklistRepository: BlocklistRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val alertStore: ChildAlertStore by inject()
    private val screenTimeWatcher: ScreenTimeWatcher by inject()
    private val appRuleWatcher: AppRuleWatcher by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var items: List<BlocklistItem> = emptyList()

    @Volatile
    private var blocklistLoadedAt: Long = 0L

    private var lastMatch: String? = null
    private var lastMatchAt: Long = 0L

    @Volatile
    private var lastScanAt: Long = 0L

    /** 한도 차단 안내가 연달아 뜨지 않게. 홈으로 보낸 뒤에도 이벤트가 계속 들어온다. */
    @Volatile
    private var lastScreenTimeBlockAt: Long = 0L

    /** 앱별 차단도 같은 이유로 겹친다. 다른 앱을 열면 바로 알려야 하므로 앱까지 함께 본다. */
    @Volatile
    private var lastAppBlockPackage: String? = null

    @Volatile
    private var lastAppBlockAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            refreshBlocklist(force = true)
            appRuleWatcher.refreshRules()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 검사 대상이 아닌 앱은 즉시 버린다.
        // 특히 우리 앱은 차단 키워드 목록 자체를 화면에 보여 주므로(「성인·음란물 차단」 등)
        // 제외하지 않으면 자기 UI 를 유해 콘텐츠로 오탐해 스스로를 튕겨 낸다.
        val source = event.packageName?.toString().orEmpty()
        if (source.isBlank() || isExempt(source)) return

        // 스크린타임 한도에 닿았으면 다른 앱을 열지 못하게 홈으로 돌려보낸다.
        // 우리 앱·런처·설정은 isExempt 로 이미 걸러져 있다 — 자녀가 앱을 못 열면
        // 페어링·채팅까지 막혀 버리므로 예외가 반드시 필요하다.
        if (screenTimeWatcher.limitReached) {
            blockForScreenTime()
            return
        }

        // 부모가 이 앱 하나에 걸어 둔 규칙. 총 한도와 달리 앱마다 다르므로 지금 열린
        // 패키지를 그때그때 물어본다.
        if (appRuleWatcher.rulesStale()) {
            scope.launch { appRuleWatcher.refreshRules() }
        }
        appRuleWatcher.blockReasonFor(source)?.let { reason ->
            blockForAppRule(source, reason)
            return
        }

        // typeWindowContentChanged 는 초당 수십 번 올 수 있다. 전체 노드 트리 스캔은 비싸므로
        // 최소 간격을 둔다.
        val nowScan = System.currentTimeMillis()
        if (nowScan - lastScanAt < SCAN_INTERVAL_MS) return
        lastScanAt = nowScan

        // 보호자가 방금 추가한 키워드도 곧 반영되도록 주기적으로 다시 읽는다.
        if (System.currentTimeMillis() - blocklistLoadedAt > BLOCKLIST_TTL_MS) {
            scope.launch { refreshBlocklist(force = false) }
        }
        val snapshot = items
        if (snapshot.isEmpty()) return

        val haystack = buildString {
            event.text.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it).append(' ') }
            append(collectWindowText())
        }
        if (haystack.isBlank()) return

        val matched = shouldBlock(haystack, snapshot) ?: return
        val now = System.currentTimeMillis()
        if (matched == lastMatch && now - lastMatchAt < THROTTLE_MS) return
        lastMatch = matched
        lastMatchAt = now

        // 알리기만 하면 자녀는 그대로 페이지에 머문다. 실제로 빠져나오게 한다.
        //
        // BACK 은 신뢰할 수 없다. 새 탭으로 연 페이지는 돌아갈 히스토리가 없고,
        // 열려 있는 다이얼로그(쿠키 배너 등)가 먼저 먹어 버리며, 히스토리에 따라
        // 같은 유해 페이지로 되돌아갈 수도 있다. HOME 은 상황과 무관하게 확실히 벗어난다.
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlock(matched)
        scope.launch {
            // 홈 탭 「최근 알림」에 뜨도록 기기에도 남긴다.
            // 서버 보고만 하면 서버가 알림을 되돌려 줄 때까지 자녀 화면에는 아무것도 안 보인다.
            alertStore.record(
                type = ChildAlertStore.TYPE_HARMFUL_BLOCK,
                title = "유해 사이트 접속이 차단됐어요",
                body = matched,
                dedupeKey = ChildAlertStore.TYPE_HARMFUL_BLOCK + "-" + matched,
            )
            // 서버에 접속 대상을 올린다. 서버는 자녀별 규칙에 더해 관리자 전역 유해
            // 키워드까지 보고 차단 여부를 판정한다(응답의 blocked). 기기에서는 이미
            // 로컬 규칙으로 막았으므로 여기서는 기록·동기화 목적이다.
            runCatching {
                val childId = sessionRepository.current()?.user?.id
                if (childId != null) blocklistRepository.reportVisit(childId, matched)
            }
            // 보호자가 다음 폴링에서 바로 받도록 방금 남긴 기록을 즉시 올린다.
            ChildAlertUploadWorker.enqueueNow(applicationContext)
        }
    }

    /**
     * 자기 자신과 시스템 셸은 검사하지 않는다.
     *
     * 런처·시스템 UI·설정 화면의 텍스트까지 훑으면 오탐이 쏟아지고,
     * 정작 막아야 할 웹/앱 콘텐츠와 무관하다.
     */
    private fun isExempt(packageName: String): Boolean =
        packageName == applicationContext.packageName ||
            packageName in EXEMPT_PACKAGES ||
            packageName in deviceExemptPackages()

    private var deviceExemptCache: Set<String> = emptySet()
    private var deviceExemptCachedAt = 0L

    /**
     * 기기마다 패키지가 다른 「항상 예외」 앱들 — 고정 목록 대신 시스템에 물어 캐시한다.
     *
     * <p>① 홈 런처: 삼성은 `com.sec.android.app.launcher`, 픽셀은 `nexuslauncher` 처럼 제조사마다
     * 다르다. 런처가 예외가 아니면 한도 초과 시 홈 화면에서 계속 HOME 이 발동하고, 그 여파로
     * 앱을 여는 전환까지 끊겨 우리 앱(예외)마저 열리다 튕긴다.
     * ② 기본 전화·문자 앱: 한도를 다 써도 아이가 연락은 할 수 있어야 한다.
     */
    private fun deviceExemptPackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - deviceExemptCachedAt < ESSENTIAL_TTL_MS && deviceExemptCache.isNotEmpty()) return deviceExemptCache
        val set = buildSet {
            // 홈 런처(들). AppUsageCollector 가 사용량 집계에서 빼는 것과 같은 방식.
            runCatching {
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                packageManager.queryIntentActivities(home, 0)
                    .forEach { it.activityInfo?.packageName?.takeIf { p -> p.isNotBlank() }?.let(::add) }
            }
            // 기본 전화 앱.
            runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { add(it) }
            // 기본 문자 앱.
            runCatching { Telephony.Sms.getDefaultSmsPackage(this@HarmfulBlockAccessibilityService) }
                .getOrNull()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        if (set.isNotEmpty()) {
            deviceExemptCache = set
            deviceExemptCachedAt = now
        }
        return set.ifEmpty { deviceExemptCache }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 현재 창의 노드 트리를 훑어 주소창·본문 텍스트를 모은다. */
    private fun collectWindowText(): String {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return ""
        val builder = StringBuilder()
        collectInto(root, builder, 0)
        return builder.toString()
    }

    private fun collectInto(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > MAX_NODE_DEPTH || out.length > MAX_TEXT_LENGTH) return
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            collectInto(node.getChild(i), out, depth + 1)
        }
    }

    private suspend fun refreshBlocklist(force: Boolean) {
        if (!force && System.currentTimeMillis() - blocklistLoadedAt <= BLOCKLIST_TTL_MS) return
        val childId = sessionRepository.childId()
        // 로그인 전(자녀 id 없음)에는 시각을 찍지 않는다 — 찍어 두면 로그인 후에도 TTL(5분)까지 규칙을 안 읽는다.
        if (childId.isBlank()) return
        blocklistLoadedAt = System.currentTimeMillis()
        // 서버 `GET filtering/rules/{childId}` — 자녀 본인 조회 허용(백엔드 feat/filtering-child-rules-fcm-data-push).
        runCatching { blocklistRepository.get(childId) }
            .onSuccess {
                items = it
                android.util.Log.d("HarmfulBlock", "blocklist loaded: ${it.size} rules")
            }
            .onFailure { android.util.Log.w("HarmfulBlock", "blocklist load failed: ${it.message}") }
    }

    /**
     * 한도 도달 상태에서 다른 앱을 열었을 때. 유해차단과 같은 방식으로 홈으로 보낸다.
     *
     * 안드로이드에서 일반 앱은 다른 앱 실행을 막거나 기기를 잠글 수 없다. 접근성 서비스로
     * 홈으로 돌려보내는 것이 앱이 할 수 있는 최대치다.
     */
    private fun blockForScreenTime() {
        val now = System.currentTimeMillis()
        if (now - lastScreenTimeBlockAt < SCREEN_TIME_BLOCK_THROTTLE_MS) return
        lastScreenTimeBlockAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        val message = getString(R.string.screen_time_block_toast)
        if (Settings.canDrawOverlays(this)) {
            showOverlay(message)
        } else {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 부모가 막아 둔 앱을 열었을 때.
     *
     * <p>스크린타임 차단과 같은 방식으로 홈으로 내보내되 <b>말이 다르다</b> — 「오늘 다 썼다」와
     * 「이 앱은 쓸 수 없다」는 자녀가 할 수 있는 일이 다르다.
     *
     * <p>같은 앱을 연달아 열면 안내가 겹쳐 뜨므로 앱마다 최소 간격을 둔다.
     */
    private fun blockForAppRule(packageName: String, reason: AppRuleWatcher.Block) {
        val now = System.currentTimeMillis()
        if (packageName == lastAppBlockPackage && now - lastAppBlockAt < APP_BLOCK_THROTTLE_MS) return
        lastAppBlockPackage = packageName
        lastAppBlockAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        val message = when (reason) {
            is AppRuleWatcher.Block.Forbidden ->
                getString(R.string.app_rule_block_forbidden, reason.appName)
            is AppRuleWatcher.Block.LimitReached ->
                getString(R.string.app_rule_block_limit, reason.appName, reason.limitMinutes)
        }
        if (Settings.canDrawOverlays(this)) showOverlay(message) else {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }

        scope.launch {
            // 자녀 홈 「최근 알림」에 남긴다. 앱마다 하루 한 번이면 충분하다 — 막힐 때마다
            // 쌓으면 목록이 같은 줄로 가득 찬다.
            val recorded = alertStore.record(
                type = ChildAlertStore.TYPE_APP_BLOCK,
                title = when (reason) {
                    is AppRuleWatcher.Block.Forbidden -> "사용할 수 없는 앱이에요"
                    is AppRuleWatcher.Block.LimitReached -> "오늘 쓸 수 있는 시간을 다 썼어요"
                },
                body = message,
                // 앱마다 하루 한 번. 막힐 때마다 쌓으면 목록이 같은 줄로 가득 찬다.
                dedupeKey = ChildAlertStore.TYPE_APP_BLOCK + "-" + packageName + "-" + LocalDate.now(),
                dedupeWindowMs = Long.MAX_VALUE,
            )
            // 보호자에게도 나른다(docs/child-push-contract.md 4절).
            if (recorded) ChildAlertUploadWorker.enqueueNow(this@HarmfulBlockAccessibilityService)
        }
    }

    private fun showBlock(keyword: String) {
        val message = getString(R.string.harmful_block_toast, keyword)
        if (Settings.canDrawOverlays(this)) {
            showOverlay(message)
        } else {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showOverlay(message: String) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        val view = TextView(this).apply {
            text = message
            textSize = 16f
            setPadding(32, 48, 32, 48)
            setBackgroundColor(0xCCDC2626.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        runCatching { wm.addView(view, params) }
        view.postDelayed({ runCatching { wm.removeView(view) } }, OVERLAY_MS)
    }

    companion object {
        private const val THROTTLE_MS = 10_000L
        private const val SCAN_INTERVAL_MS = 800L

        /** 홈으로 보낸 직후 같은 안내가 쏟아지지 않도록 두는 최소 간격. */
        private const val SCREEN_TIME_BLOCK_THROTTLE_MS = 3_000L

        /** 같은 앱을 연달아 열 때 안내가 겹치지 않게. 다른 앱이면 바로 알린다. */
        private const val APP_BLOCK_THROTTLE_MS = 3_000L

        private val EXEMPT_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.google.android.inputmethod.latin",
            "com.android.permissioncontroller",
        )
        /** 기본 전화/문자 앱 조회 캐시 수명. 기본 앱은 자주 바뀌지 않는다. */
        private const val ESSENTIAL_TTL_MS = 10 * 60 * 1000L
        private const val BLOCKLIST_TTL_MS = 5 * 60 * 1000L
        private const val OVERLAY_MS = 2_500L
        private const val MAX_NODE_DEPTH = 12
        private const val MAX_TEXT_LENGTH = 4_000

        /**
         * 차단 패턴에 걸리면 그 패턴을 돌려준다.
         *
         * 예전에는 블랙리스트 적중 → 화이트리스트 예외 순으로 봤는데, **서버에 허용 목록
         * 개념이 없어**(BlockRuleResponse 는 pattern 하나뿐) 예외 단계를 걷어냈다.
         *
         * 단순 `contains` 로 보면 영단어 키워드가 오탐을 낸다(`meth` → `method`).
         * 매칭 규칙은 [HarmfulKeywordMatcher] 가 갖고 있다.
         */
        fun shouldBlock(text: String, items: List<BlocklistItem>): String? =
            items.firstOrNull { HarmfulKeywordMatcher.matches(text, it.pattern) }?.pattern
    }
}
