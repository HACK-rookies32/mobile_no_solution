package com.hack.kidhack.feature.child.onboarding

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.permissionRequester
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Single-screen permission list. Runtime permissions use [PermissionRequester]
 * (Activity-registered) so the system dialog is shown. Deny → tap 「허용」 again.
 *
 * <p>로그인 바로 다음에 나오는 화면이라 로그인과 같은 껍데기를 쓴다 — 흰 배경에 왼쪽 정렬,
 * 오버라인 + 큰 제목. 전에는 주황 그라데이션 히어로 아래 겹치는 시트였는데, 그 규격은 앱
 * 나머지가 이미 버린 것이라 로그인에서 넘어오는 순간 다른 앱처럼 보였다.
 *
 * <p>상태는 <b>회색 → 주황</b> 한 갈래로만 말한다. 아직인 줄은 회색 아이콘, 허용된 줄은 주황
 * 아이콘에 주황 체크. 색이 하나라 "주황이 다 찼으면 끝"이 한눈에 읽히고, 위쪽 진행 바가 차는
 * 것과 같은 이야기를 한다. 색맹이어도 구분되도록 체크 <b>모양</b>과 「허용됨」 <b>글자</b>가
 * 함께 붙는다.
 *
 * <p>주황은 오로지 「됐다」는 뜻으로만 쓴다. 아직인 줄은 오른쪽 「허용 ›」·「설정 ↗」까지
 * 회색이다 — 색으로 재촉하지 않아도 목록에서 주황이 안 찬 자리가 곧 할 일이고, 진행 바가
 * 몇 개 남았는지 세어 준다. 누를 수 있다는 신호는 색이 아니라 <b>화살표 모양</b>과 눌렀을
 * 때의 물결이 맡는다.
 */
@Composable
fun ChildPermissionWizard(
    onFinished: () -> Unit,
    @StringRes titleRes: Int = R.string.child_onboarding_title,
    @StringRes subtitleRes: Int = R.string.child_onboarding_subtitle,
    @StringRes continueRes: Int = R.string.child_onboarding_done,
    persistDone: Boolean = true,
    viewModel: ChildOnboardingViewModel = koinViewModel { parametersOf(persistDone) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalKidhackColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = permissionRequester()

    var grantTick by remember { mutableIntStateOf(0) }
    /** Simple one-button alert (e.g. need foreground location first). */
    var alertMessage by remember { mutableStateOf<String?>(null) }
    /** Confirm then open background-location settings / permission page. */
    var pendingBackgroundSettings by remember { mutableStateOf(false) }

    fun refreshGrants() {
        grantTick++
    }

    @Suppress("UNUSED_EXPRESSION")
    grantTick

    fun isGranted(item: OnboardingStep): Boolean = when (item.kind) {
        OnboardingPermissionKind.RuntimeMulti -> KidhackPermissions.hasLocation(context)
        OnboardingPermissionKind.RuntimeSingle -> {
            val permission = item.runtimePermission ?: return true
            KidhackPermissions.isGranted(context, permission)
        }
        OnboardingPermissionKind.SettingsUsage -> KidhackPermissions.hasUsageStats(context)
        OnboardingPermissionKind.SettingsOverlay -> KidhackPermissions.hasOverlay(context)
        OnboardingPermissionKind.SettingsAccessibility -> KidhackPermissions.hasAccessibility(context)
        OnboardingPermissionKind.MediaProjection -> KidhackPermissions.hasScreenCaptureConsent(context)
        OnboardingPermissionKind.SettingsAppDetails -> false
    }

    fun afterRuntimeAttempt(item: OnboardingStep) {
        refreshGrants()
        if (isGranted(item)) {
            when (item.kind) {
                OnboardingPermissionKind.RuntimeMulti -> {
                    KidhackPermissions.clearDenyHistory(
                        context,
                        KidhackPermissions.locationCoarse,
                        KidhackPermissions.locationFine,
                    )
                }
                OnboardingPermissionKind.RuntimeSingle -> {
                    item.runtimePermission?.let {
                        KidhackPermissions.clearDenyHistory(context, it)
                    }
                }
                else -> Unit
            }
            viewModel.clearError()
        } else {
            when (item.kind) {
                OnboardingPermissionKind.RuntimeMulti -> {
                    KidhackPermissions.recordDeny(
                        context,
                        KidhackPermissions.locationCoarse,
                        KidhackPermissions.locationFine,
                    )
                }
                OnboardingPermissionKind.RuntimeSingle -> {
                    item.runtimePermission?.let {
                        KidhackPermissions.recordDeny(context, it)
                    }
                }
                else -> Unit
            }
            if (item.required) viewModel.denyOrMissingRequired(item.key)
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshGrants()
        viewModel.clearError()
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        KidhackPermissions.markScreenCaptureConsent(
            context,
            result.resultCode == Activity.RESULT_OK,
        )
        refreshGrants()
        state.items.firstOrNull { it.kind == OnboardingPermissionKind.MediaProjection }
            ?.let { afterRuntimeAttempt(it) }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshGrants()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allRequiredGranted = KidhackPermissions.hasAllRequiredForChildControl(context)
    val activity = KidhackPermissions.findActivity(context)

    fun openPermissionSettings(key: String?) {
        viewModel.notifyPermissionBlocked(key)
        runCatching {
            settingsLauncher.launch(KidhackPermissions.appDetailsSettingsIntent(context))
        }
    }

    fun requestForegroundLocation(item: OnboardingStep) {
        if (KidhackPermissions.hasLocation(context)) {
            refreshGrants()
            return
        }
        viewModel.clearError()
        val act = activity
        val needed = KidhackPermissions.locationPermissionsToRequest(context)
        if (act != null &&
            needed.isNotEmpty() &&
            KidhackPermissions.shouldOpenSettingsForAny(act, *needed)
        ) {
            openPermissionSettings(item.key)
            return
        }
        permissions.request(
            KidhackPermissions.locationCoarse,
            KidhackPermissions.locationFine,
        ) { result ->
            val fineOk = result[KidhackPermissions.locationFine] == true ||
                KidhackPermissions.hasLocation(context)
            if (fineOk) {
                afterRuntimeAttempt(item)
            } else {
                val coarseOk = result[KidhackPermissions.locationCoarse] == true ||
                    KidhackPermissions.isGranted(context, KidhackPermissions.locationCoarse)
                if (coarseOk && !KidhackPermissions.hasLocation(context)) {
                    if (act != null &&
                        KidhackPermissions.shouldOpenSettingsForPermission(
                            act,
                            KidhackPermissions.locationFine,
                        )
                    ) {
                        openPermissionSettings(item.key)
                    } else {
                        permissions.requestOne(KidhackPermissions.locationFine) {
                            afterRuntimeAttempt(item)
                        }
                    }
                } else {
                    afterRuntimeAttempt(item)
                }
            }
        }
    }

    fun requestItem(item: OnboardingStep) {
        viewModel.clearError()
        when (item.kind) {
            OnboardingPermissionKind.RuntimeMulti -> requestForegroundLocation(item)
            OnboardingPermissionKind.RuntimeSingle -> {
                val permission = item.runtimePermission
                if (permission.isNullOrBlank()) return
                if (KidhackPermissions.isGranted(context, permission)) {
                    refreshGrants()
                    return
                }
                if (permission == KidhackPermissions.backgroundLocation &&
                    !KidhackPermissions.hasLocation(context)
                ) {
                    alertMessage =
                        context.getString(R.string.child_onboarding_bg_need_location_body)
                    return
                }
                // API 30+: requestPermissions(BACKGROUND) opens this app's Location page.
                // Show a dialog first so the tap always gives visible feedback.
                if (permission == KidhackPermissions.backgroundLocation) {
                    pendingBackgroundSettings = true
                    return
                }
                val act = activity
                if (act != null &&
                    KidhackPermissions.shouldOpenSettingsForPermission(act, permission)
                ) {
                    openPermissionSettings(item.key)
                    return
                }
                permissions.requestOne(permission) {
                    afterRuntimeAttempt(item)
                }
            }
            OnboardingPermissionKind.SettingsUsage -> {
                runCatching {
                    settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }.onFailure { viewModel.denyOrMissingRequired(item.key) }
            }
            OnboardingPermissionKind.SettingsOverlay -> {
                runCatching {
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }.onFailure { viewModel.denyOrMissingRequired(item.key) }
            }
            OnboardingPermissionKind.SettingsAccessibility -> {
                runCatching {
                    settingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }.onFailure { viewModel.denyOrMissingRequired(item.key) }
            }
            OnboardingPermissionKind.SettingsAppDetails -> {
                viewModel.clearError()
                runCatching {
                    settingsLauncher.launch(KidhackPermissions.appDetailsSettingsIntent(context))
                }.onFailure { viewModel.denyOrMissingRequired(item.key) }
            }
            OnboardingPermissionKind.MediaProjection -> {
                if (KidhackPermissions.hasScreenCaptureConsent(context)) {
                    refreshGrants()
                    return
                }
                val projectionManager =
                    context.getSystemService(MediaProjectionManager::class.java)
                if (projectionManager == null) {
                    viewModel.denyOrMissingRequired(item.key)
                    return
                }
                runCatching {
                    mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                }.onFailure { viewModel.denyOrMissingRequired(item.key) }
            }
        }
    }

    val foregroundLocationGranted = KidhackPermissions.hasLocation(context)
    val backgroundItem = state.items.firstOrNull {
        it.runtimePermission == KidhackPermissions.backgroundLocation
    }

    fun openBackgroundLocationPage() {
        val permission = KidhackPermissions.backgroundLocation ?: return
        val item = backgroundItem ?: return
        KidhackPermissions.markAsked(context, permission)
        runCatching {
            permissions.requestOne(permission) { granted ->
                afterRuntimeAttempt(item)
                if (!granted && !KidhackPermissions.hasBackgroundLocation(context)) {
                    alertMessage =
                        context.getString(R.string.child_onboarding_bg_open_settings_body)
                }
            }
        }.onFailure {
            runCatching {
                settingsLauncher.launch(KidhackPermissions.appDetailsSettingsIntent(context))
            }
        }
    }

    if (alertMessage != null) {
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            confirmButton = {
                TextButton(onClick = { alertMessage = null }) {
                    Text(stringResource(R.string.child_onboarding_alert_confirm))
                }
            },
            title = { Text(stringResource(R.string.child_onboarding_bg_need_location_title)) },
            text = { Text(alertMessage.orEmpty()) },
        )
    }

    if (pendingBackgroundSettings) {
        AlertDialog(
            onDismissRequest = { pendingBackgroundSettings = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingBackgroundSettings = false
                        openBackgroundLocationPage()
                    },
                ) {
                    Text(stringResource(R.string.child_onboarding_bg_open_settings_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackgroundSettings = false }) {
                    Text(stringResource(R.string.child_onboarding_alert_cancel))
                }
            },
            title = { Text(stringResource(R.string.child_onboarding_bg_open_settings_title)) },
            text = { Text(stringResource(R.string.child_onboarding_bg_open_settings_body)) },
        )
    }

    val totalCount = state.items.size
    val grantedCount = state.items.count { isGranted(it) }
    val requiredRemaining = state.items.count { it.required && !isGranted(it) }
    val progress by animateFloatAsState(
        targetValue = if (totalCount == 0) 0f else grantedCount.toFloat() / totalCount,
        animationSpec = tween(durationMillis = 320),
        label = "onboardingProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            // 상태 표시줄과 제스처 바를 한 번에 피한다. 하단 버튼이 고정이라 특히 아래쪽이 중요하다.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 14.dp),
        ) {
            Text(
                text = "ROOKIES",
                color = colors.brand,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(titleRes),
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(subtitleRes, totalCount),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.line),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(colors.brand),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        R.string.child_onboarding_progress,
                        grantedCount,
                        totalCount,
                    ),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 11.sp,
                )
            }
        }
        KidHeaderDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            OnboardingGroup.entries.forEach { group ->
                val rows = state.items.filter { it.group == group }
                if (rows.isEmpty()) return@forEach
                PermissionGroup(titleRes = group.titleRes) {
                    rows.forEachIndexed { index, item ->
                        val isBackgroundLocation =
                            item.runtimePermission == KidhackPermissions.backgroundLocation
                        val granted = isGranted(item)
                        val needsForegroundFirst =
                            isBackgroundLocation && !foregroundLocationGranted && !granted
                        PermissionRow(
                            item = item,
                            granted = granted,
                            showDivider = index != rows.lastIndex,
                            hint = when {
                                needsForegroundFirst ->
                                    stringResource(R.string.child_onboarding_bg_need_location_hint)
                                state.errorKey == item.key -> state.error
                                else -> null
                            },
                            onAllow = { requestItem(item) },
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().background(colors.card)) {
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            Column(modifier = Modifier.padding(16.dp)) {
                // 어느 줄 이야기인지 못 가리는 오류만 여기 남는다. 나머지는 그 줄 아래 붙는다.
                if (state.error != null && state.errorKey == null) {
                    Text(
                        text = state.error.orEmpty(),
                        color = colors.danger,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                KidButton(
                    // 꺼진 버튼이 왜 꺼졌는지 스스로 말한다. 「시작하기」만 회색으로 두면
                    // 무엇이 모자란지 화면 어디에도 적혀 있지 않다.
                    text = if (allRequiredGranted) {
                        stringResource(continueRes)
                    } else {
                        stringResource(
                            R.string.child_onboarding_remaining,
                            requiredRemaining.coerceAtLeast(1),
                        )
                    },
                    onClick = {
                        if (KidhackPermissions.hasAllRequiredForChildControl(context)) {
                            viewModel.finish()
                        } else {
                            viewModel.denyOrMissingRequired()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = allRequiredGranted,
                )
            }
        }
    }
}

/** 묶음 제목 + 그 아래 줄들을 담은 흰 카드. */
@Composable
private fun PermissionGroup(
    @StringRes titleRes: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column {
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
        )
        // 줄이 카드 폭을 다 쓰도록 카드 안쪽 여백을 없앤다. 여백은 줄이 스스로 갖는다.
        KidSectionCard(contentPadding = PaddingValues(0.dp), content = content)
    }
}

/** 아이콘 · 이름 · 상태 한 줄. 줄 전체가 누를 수 있는 자리다. */
@Composable
private fun PermissionRow(
    item: OnboardingStep,
    granted: Boolean,
    showDivider: Boolean,
    onAllow: () -> Unit,
    hint: String? = null,
) {
    val colors = LocalKidhackColors.current
    val opensSettingsApp = when (item.kind) {
        OnboardingPermissionKind.SettingsUsage,
        OnboardingPermissionKind.SettingsOverlay,
        OnboardingPermissionKind.SettingsAccessibility,
        OnboardingPermissionKind.SettingsAppDetails,
        -> true
        else -> false
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 허용된 줄은 더 할 일이 없다. 아직인 줄은 잠긴 것처럼 보여도 눌러야
                // 「먼저 위치부터」 안내가 뜨므로 계속 눌린다.
                .clickable(enabled = !granted, onClick = onAllow)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (granted) colors.brandLight else colors.line),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(item.iconRes),
                    // 바로 옆에 이름이 적혀 있다. 읽어 주면 같은 말을 두 번 하게 된다.
                    contentDescription = null,
                    tint = if (granted) colors.brand else colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(item.titleRes),
                        color = colors.text,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                    )
                    // 「필수」는 거의 모든 줄에 붙어 아무것도 못 가린다. 예외만 말한다.
                    if (!item.required) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.child_onboarding_optional_badge),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.line)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = colors.subtle,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 10.sp,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (granted) {
                        stringResource(R.string.child_onboarding_granted)
                    } else {
                        stringResource(item.bodyRes)
                    },
                    color = if (granted) colors.subtle else colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            } else {
                // 아직인 줄은 오른쪽 글자까지 회색이다. 주황은 오로지 「됐다」는 뜻으로만 쓴다.
                // 누를 수 있다는 신호는 색이 아니라 화살표 모양과 눌렀을 때의 물결이 맡는다.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (opensSettingsApp) {
                            stringResource(R.string.child_onboarding_settings)
                        } else {
                            stringResource(R.string.child_onboarding_allow)
                        },
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(3.dp))
                    // 설정 앱으로 나간다 vs 이 화면에 팝업이 뜬다 — 화살표가 그 차이를 말한다.
                    Text(
                        text = if (opensSettingsApp) "↗" else "›",
                        color = colors.subtle,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                modifier = Modifier.padding(start = 64.dp, end = 16.dp, bottom = 10.dp),
                color = colors.danger,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
        }
        if (showDivider) {
            // 아이콘(36) + 사이(12) + 카드 여백(16) — 글자 시작점에 맞춰 들여 긋는다.
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                thickness = 1.dp,
                color = colors.line,
            )
        }
    }
}
