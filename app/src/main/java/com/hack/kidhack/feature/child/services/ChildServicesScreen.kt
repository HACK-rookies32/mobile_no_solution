package com.hack.kidhack.feature.child.services

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.hack.kidhack.R
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.UsageBar
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import com.hack.kidhack.feature.child.components.RetryCard
import com.hack.kidhack.feature.child.formatDurationSec
import org.koin.androidx.compose.koinViewModel

/** 등록 장소 표시색. 아이 위치(빨강)와 겹치지 않도록 파랑을 쓴다(부모 위치화면과 동일 기준). */
private val CHILD_PLACE_BLUE = Color(0xFF2563EB)

@Composable
fun ChildServicesScreen(
    viewModel: ChildServicesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    // 접근성·사용정보 접근은 일반 권한 다이얼로그로 못 받고 설정 화면으로 보내야 한다.
    val openSettings: (String) -> Unit = { action ->
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // 권한·GPS 는 설정에서 바뀔 수 있으므로 탭 복귀 때마다 실제 상태를 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // 지도 상태는 위치추적 카드와 전체화면 Dialog가 함께 쓰므로 화면 최상위에서 들고 있는다.
    var mapExpanded by remember { mutableStateOf(false) }
    // state 가 위임 프로퍼티라 그대로 쓰면 스마트 캐스트가 안 되므로 지역 변수로 받는다.
    val lat = state.currentLat
    val lng = state.currentLng
    val currentPos = if (lat != null && lng != null) LatLng(lat, lng) else null
    val defaultPos = LatLng(37.5665, 126.9780) // 서울 시청 기본값, 부모 화면과 동일 기준
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentPos ?: defaultPos, 15f)
    }
    LaunchedEffect(currentPos) {
        if (currentPos != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(currentPos, 16f)
        }
    }
    // 전체화면 지도는 별도 카메라 상태를 쓴다(하나를 두 GoogleMap이 공유하면 예외).
    val fullCameraState = rememberCameraPositionState()

    ChildScreenScaffold(
        header = {
            Text(
                text = stringResource(R.string.child_services_title),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
            Text(
                text = stringResource(R.string.child_services_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        },
    ) {
        val loadError = state.error
        if (loadError != null) {
            RetryCard(message = loadError, onRetry = viewModel::refresh)
        }

        KidCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔", fontSize = 20.sp)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.child_services_push_title),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(
                            if (state.pushConnected) {
                                R.string.child_services_push_on_desc
                            } else {
                                R.string.child_services_push_off_desc
                            },
                        ),
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = stringResource(
                        if (state.pushConnected) {
                            R.string.child_services_push_on
                        } else {
                            R.string.child_services_push_off
                        },
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (state.pushConnected) Color(0xFFF0FDF4) else colors.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (state.pushConnected) colors.safe else colors.warn,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
        }

        KidCard {
            ServiceHeader(
                emoji = "📍",
                title = stringResource(R.string.child_services_location),
                subtitle = stringResource(R.string.child_services_location_desc),
                active = state.locationGranted && state.gpsEnabled,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                ) {
                    ChildLocationMapContent(currentPos, state.places)
                }
                // 전체보기 버튼 (전용 문자열 리소스가 없어 하드코딩)
                Text(
                    text = "전체보기",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White)
                        .clickable {
                            fullCameraState.position = cameraPositionState.position
                            mapExpanded = true
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = colors.text,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.locationUpdatedAt.isBlank()) {
                        stringResource(R.string.child_services_updated_unknown)
                    } else {
                        stringResource(R.string.child_services_updated_at, state.locationUpdatedAt)
                    },
                    modifier = Modifier.weight(1f),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                val gpsOn = state.locationGranted && state.gpsEnabled
                Text(
                    text = stringResource(
                        when {
                            gpsOn -> R.string.child_services_gps_on
                            !state.locationGranted -> R.string.child_services_location_denied
                            else -> R.string.child_services_gps_off
                        },
                    ),
                    color = if (gpsOn) colors.safe else colors.warn,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
        }

        KidCard {
            // 취침 모드·스크린타임 제한은 기기에서 강제되지 않는다(GAP-12).
            // 「실행 중」이라고 표시하면 거짓말이 되므로 배지를 달지 않는다.
            ServiceHeader(
                emoji = "⏰",
                title = stringResource(R.string.child_services_limits),
                subtitle = stringResource(R.string.child_services_limits_desc),
            )
            // 「하루 3시간」은 서버 근거가 없는 상수였다. 앱 통제는 앱별 한도만 제공하고
            // 자녀 전체 스크린타임 한도라는 개념이 서버에 없다.
            LimitRow("🌙", stringResource(R.string.child_services_bedtime), stringResource(R.string.child_services_bedtime_value))
            LimitRow("✅", stringResource(R.string.child_services_status), stringResource(R.string.child_services_available), showDivider = false)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.child_services_bedtime_note),
                color = colors.muted,
                fontSize = 12.sp,
            )
        }

        KidCard {
            ServiceHeader(
                emoji = "🚫",
                title = stringResource(R.string.child_services_harmful),
                subtitle = stringResource(R.string.child_services_harmful_desc),
                active = state.harmfulBlockEnabled,
            )
            Spacer(Modifier.height(8.dp))
            val cats = listOf(
                R.string.child_services_cat_adult,
                R.string.child_services_cat_gambling,
                R.string.child_services_cat_violence,
                R.string.child_services_cat_drug,
            )
            cats.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { res ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (state.harmfulBlockEnabled) Color(0xFFF0FDF4) else colors.surface,
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (state.harmfulBlockEnabled) "✅" else "⚪", fontSize = 14.sp)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(res),
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontSemibold,
                                fontSize = 12.sp,
                                color = colors.text,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (state.harmfulBlockEnabled) {
                    stringResource(R.string.child_services_blocked_today, state.blockedToday)
                } else {
                    stringResource(R.string.child_services_harmful_off)
                },
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
            if (!state.harmfulBlockEnabled) {
                Spacer(Modifier.height(12.dp))
                KidButton(
                    text = stringResource(R.string.child_services_harmful_enable),
                    onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
            }
        }

        KidCard {
            ServiceHeader(
                emoji = "📊",
                title = stringResource(R.string.child_services_usage),
                subtitle = stringResource(R.string.child_services_usage_desc),
                active = state.usageAccessGranted,
            )
            Spacer(Modifier.height(8.dp))
            if (!state.usageAccessGranted) {
                Text(
                    text = stringResource(R.string.child_services_usage_no_access),
                    color = colors.warn,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                KidButton(
                    text = stringResource(R.string.child_services_usage_enable),
                    onClick = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (state.usages.isEmpty()) {
                Text(
                    text = stringResource(
                        if (state.loading) R.string.child_services_loading
                        else R.string.child_services_usage_empty,
                    ),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            val max = state.usages.maxOfOrNull { it.durationSec }?.coerceAtLeast(1) ?: 1
            state.usages.forEach { usage ->
                UsageBar(
                    name = usage.appName,
                    timeLabel = formatDurationSec(usage.durationSec),
                    progress = usage.durationSec / max.toFloat(),
                    barColor = usageBarColor(usage.appName, colors.info),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            HorizontalDivider(color = colors.border)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.child_services_total, state.totalLabel),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
            if (state.usages.isNotEmpty() && !state.usageFromDevice) {
                Text(
                    text = stringResource(R.string.child_services_usage_sample),
                    color = colors.warn,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
        }

        if (state.simulationAvailable) {
            KidCard {
                ServiceHeader(
                    emoji = "🧪",
                    title = stringResource(R.string.child_services_sim_title),
                    subtitle = stringResource(R.string.child_services_sim_desc),
                )
                Spacer(Modifier.height(12.dp))
                KidButton(
                    text = stringResource(R.string.child_services_sim_capture),
                    onClick = { viewModel.simulateRequest(RemoteCommandType.SCREEN_CAPTURE) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
                Spacer(Modifier.height(8.dp))
                KidButton(
                    text = stringResource(R.string.child_services_sim_camera),
                    onClick = { viewModel.simulateRequest(RemoteCommandType.CAMERA_SHOT) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (mapExpanded) {
        val closeFullscreen = {
            // 전체화면에서 옮겨둔 시야를 작은 지도에도 이어준다.
            cameraPositionState.position = fullCameraState.position
            mapExpanded = false
        }
        Dialog(
            onDismissRequest = closeFullscreen,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(colors.surface)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = fullCameraState,
                ) {
                    ChildLocationMapContent(currentPos, state.places)
                }
                Text(
                    text = "닫기",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.brand)
                        .clickable(onClick = closeFullscreen)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * 자녀 서비스 탭 지도 콘텐츠. 부모 LocationScreen의 LocationMapContent와 같은 역할이지만
 * 패키지가 달라 재사용이 안 되므로 이 파일에 독립적으로 둔다(부모 파일은 손대지 않음).
 */
@Composable
@com.google.maps.android.compose.GoogleMapComposable
private fun ChildLocationMapContent(
    currentPos: LatLng?,
    places: List<Geofence>,
) {
    if (currentPos != null) {
        Marker(
            // maps-compose 6.4.1엔 rememberUpdatedMarkerState가 없어 rememberMarkerState 사용.
            // 좌표를 key로 걸어 위치가 바뀌면 마커도 새로 만들어져 갱신되도록 한다.
            state = rememberMarkerState(
                key = "child_${currentPos.latitude}_${currentPos.longitude}",
                position = currentPos,
            ),
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
        )
    }
    places.forEach { place ->
        val lat = place.lat.toDoubleOrNull()
        val lng = place.lng.toDoubleOrNull()
        if (lat != null && lng != null) {
            val center = LatLng(lat, lng)
            Circle(
                center = center,
                radius = place.radius.toDouble(),
                strokeColor = CHILD_PLACE_BLUE,
                strokeWidth = 2f,
                fillColor = CHILD_PLACE_BLUE.copy(alpha = 0.15f),
            )
            Marker(
                state = rememberMarkerState(key = place.id, position = center),
                title = place.name,
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
            )
        }
    }
}

@Composable
private fun ServiceHeader(
    emoji: String,
    title: String,
    subtitle: String,
    /**
     * 실제 동작 여부. null 이면 배지를 그리지 않는다.
     *
     * 예전에는 이 배지가 하드코딩이라 접근성이 꺼져 있어도 「실행 중」으로 보였고,
     * 같은 카드 안에서 「차단 기능 켜기」 버튼과 모순됐다.
     */
    active: Boolean? = null,
) {
    val colors = LocalKidhackColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Text(text = subtitle, color = colors.muted, fontSize = 12.sp)
        }
        if (active != null) {
            Text(
                text = stringResource(
                    if (active) R.string.child_services_running else R.string.child_services_stopped,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Color(0xFFF0FDF4) else colors.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = if (active) colors.safe else colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun LimitRow(emoji: String, label: String, value: String, showDivider: Boolean = true) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 14.sp)
            Spacer(Modifier.size(12.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
            Text(
                text = value,
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
        }
        if (showDivider) HorizontalDivider(color = colors.border)
    }
}

private fun usageBarColor(name: String, fallback: Color): Color = when {
    name.contains("YouTube", ignoreCase = true) -> Color(0xFFF87171)
    name.contains("카카오") -> Color(0xFFFACC15)
    else -> fallback
}
