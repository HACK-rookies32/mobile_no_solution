package com.hack.kidhack.feature.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.view.MotionEvent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.LocationEvent
import com.hack.kidhack.domain.model.LocationEventType
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.LocationEventRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 위치 탭 재진입 시 이 간격 안에 이미 읽었으면 다시 읽지 않는다. */
private const val REFRESH_MIN_INTERVAL_MS = 5_000L

data class LocationUiState(
    val history: List<LocationPoint> = emptyList(),
    /** 지도에 반경 원으로 겹쳐 그릴 등록 장소들. */
    val places: List<Geofence> = emptyList(),
    /** 등록 장소 진입/이탈 기록. 화면 맨 아래 "최근 출입 기록"에 쓴다. */
    val events: List<LocationEvent> = emptyList(),
    val realtimeEnabled: Boolean = false,
    /** "오늘 이동 경로 확인" 토글. 켜면 지도에 동선을 선으로 잇는다. */
    val showRoute: Boolean = false,
    val hasChildren: Boolean = true,
    /** 상단 자녀 선택 칩에 쓸 목록. 2명 이상일 때만 칩이 보인다. */
    val children: List<ChildProfile> = emptyList(),
    /** 지금 보고 있는 자녀. 지도·토글·기록이 모두 이 자녀 기준이다. */
    val selectedChildId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
) {
    /** 동선은 최신순이라 첫 항목이 현재 위치다(실시간 ON이면 방금 좌표). */
    val current: LocationPoint? get() = history.firstOrNull()

    /** 선택된 자녀. 이름·프로필을 여기서 읽는다. */
    val selectedChild: ChildProfile? get() = children.firstOrNull { it.id == selectedChildId }
}

class LocationViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val locationEventRepository: LocationEventRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    /** 실시간 토글에 쓸 현재 선택된 자녀. refresh()에서만 갱신한다. */
    private var childId: String? = null

    private var lastLoadedAt = 0L

    init {
        viewModelScope.launch {
            selectedChildRepository.selectedChildId.collect { refresh() }
        }
    }

    /**
     * 탭 재진입용. 뷰모델이 살아 있으면 init 의 조회가 다시 돌지 않아 자녀 위치가 갱신되지 않던 문제 —
     * 최근 [REFRESH_MIN_INTERVAL_MS] 안에 조회했으면 건너뛴다.
     */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastLoadedAt < REFRESH_MIN_INTERVAL_MS) return
        refresh()
    }

    fun refresh() {
        lastLoadedAt = System.currentTimeMillis()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children)
                childId = id
                if (id == null) {
                    _state.update { it.copy(loading = false, hasChildren = false, children = children) }
                    return@runCatching
                }
                val history = locationRepository.history(id)
                // 장소·출입 기록은 부가 정보라 실패해도 위치 표시는 그대로 간다.
                val places = runCatching { placeRepository.list(id) }.getOrDefault(emptyList())
                val events = runCatching { locationEventRepository.events(id) }.getOrDefault(emptyList())
                // 스위치 초기값을 서버에서 가져온다. 예전에는 false 로 시작해서, 실제로
                // 추적이 켜져 있어도 화면에서는 꺼진 것처럼 보였다.
                val realtime = runCatching { locationRepository.realtimeEnabled(id) }
                    .getOrDefault(_state.value.realtimeEnabled)
                _state.update {
                    it.copy(
                        loading = false,
                        hasChildren = true,
                        children = children,
                        selectedChildId = id,
                        history = history,
                        places = places,
                        events = events,
                        realtimeEnabled = realtime,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    /** 상단 칩에서 다른 자녀를 고른다. selectedChildId 흐름이 바뀌면 init 의 collect 가 refresh() 한다. */
    fun selectChild(id: String) {
        if (id == _state.value.selectedChildId) return
        // 경로 토글은 자녀가 바뀌면 초기화한다(다른 자녀의 동선을 이어 그리면 안 된다).
        _state.update { it.copy(selectedChildId = id, showRoute = false) }
        viewModelScope.launch { selectedChildRepository.select(id) }
    }

    fun toggleRoute() = _state.update { it.copy(showRoute = !it.showRoute) }

    fun setRealtime(enabled: Boolean) {
        val id = childId ?: return
        viewModelScope.launch {
            runCatching { locationRepository.toggleRealtime(id, enabled) }
                .onSuccess { applied -> _state.update { it.copy(realtimeEnabled = applied, error = null) } }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }
}

/** 자녀별 마커 색. 이름이 본체고 색은 보조라, 색만으로 자녀를 가르지 않는다. */
private val CHILD_MARKER_COLORS = listOf(
    Color(0xFFF58220), Color(0xFF2563EB), Color(0xFF16A34A),
    Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFF0891B2),
)

/** id 해시로 자녀마다 고정 색. 목록 순서가 바뀌어도 같은 자녀는 같은 색이다. */
private fun childColor(id: String?): Color {
    id ?: return CHILD_MARKER_COLORS.first()
    val n = CHILD_MARKER_COLORS.size
    return CHILD_MARKER_COLORS[((id.hashCode() % n) + n) % n]
}

/** 작은 지도와 전체화면 지도가 같은 내용을 그리도록 한 곳에 모아둔다. */
@Composable
@GoogleMapComposable
private fun LocationMapContent(
    childPos: LatLng?,
    childName: String,
    markerColor: Color,
    places: List<Geofence>,
    routePoints: List<LatLng>,
) {
    val colors = LocalKidhackColors.current
    if (childPos != null) {
        // 빨간 점 하나로는 누구인지 알 수 없다 — 이름표를 단 자녀별 색 핀으로 그린다.
        MarkerComposable(
            childName, markerColor, childPos.latitude, childPos.longitude,
            state = rememberMarkerState(key = "child_${childPos.latitude}_${childPos.longitude}", position = childPos),
            title = childName,
        ) {
            ChildPinLabel(childName, markerColor)
        }
    }
    SavedPlacesOverlay(places)
    if (routePoints.size >= 2) {
        Polyline(points = routePoints, color = markerColor, width = 6f)
    }
}

/** 지도 위 자녀 마커 — 자녀별 색 알약에 이름을 얹고, 아래로 뾰족한 꼬리가 위치를 가리킨다. */
@Composable
private fun ChildPinLabel(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = name,
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(color)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            color = Color.White,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
        )
        Canvas(modifier = Modifier.size(width = 12.dp, height = 7.dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, color)
        }
    }
}

/** 이탈만 이동으로 보고, 나머지(ENTER·과거 ARRIVED)는 도착으로 묶는다. */
private fun isExitEvent(type: LocationEventType): Boolean = type == LocationEventType.EXIT

private fun eventIcon(type: LocationEventType): Int =
    if (isExitEvent(type)) R.drawable.ic_exit else R.drawable.ic_place

/** "2026-08-25T17:05:00" -> "17:05". 파싱 실패하면 기존처럼 뒤 5글자. */
private fun hourMinute(raw: String): String =
    runCatching { java.time.LocalDateTime.parse(raw).toLocalTime().toString().take(5) }
        .getOrDefault(raw.takeLast(5))

/** "3\ubd84 \uc804" \ucc98\ub7fc \uc9c0\uae08\uc73c\ub85c\ubd80\ud130 \uc9c0\ub09c \uc2dc\uac04. \uba85\uc138\uc5d0\ub294 \uc88c\ud45c \uc2dc\uac01(at)\ub9cc \uc788\uc5b4 \uc774 \uac12\uc73c\ub85c \uc2e0\uc120\ub3c4\ub97c \uc54c\ub9b0\ub2e4. */
private fun relativeTime(raw: String?): String? {
    raw ?: return null
    return runCatching {
        val instant = java.time.LocalDateTime.parse(raw)
            .atZone(java.time.ZoneId.systemDefault()).toInstant()
        val min = java.time.Duration.between(instant, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
        when {
            min < 1 -> "\ubc29\uae08 \uc804"
            min < 60 -> min.toString() + "\ubd84 \uc804"
            min < 60 * 24 -> (min / 60).toString() + "\uc2dc\uac04 \uc804"
            else -> (min / 60 / 24).toString() + "\uc77c \uc804"
        }
    }.getOrNull()
}

@Composable
fun ParentLocationScreen(
    onOpenGeofence: () -> Unit = {},
    onOpenPairing: () -> Unit = {},
    viewModel: LocationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    // \ud0ed\uc744 \ub2e4\uc2dc \uc5f4 \ub54c \uc11c\ubc84 \uc704\uce58\ub97c \ub2e4\uc2dc \uc77d\ub294\ub2e4(\ubdf0\ubaa8\ub378\uc774 \uc720\uc9c0\ub3fc init \uc870\ud68c\uac00 \ub2e4\uc2dc \ub3cc\uc9c0 \uc54a\uc74c).
    LaunchedEffect(Unit) { viewModel.refreshIfStale() }

    // \uc11c\ubc84\uac00 \uc900 updatedAt\uc774 \uc2e4\uc81c \uac31\uc2e0 \uc2dc\uac01\uc774\ub2e4. \uc5c6\uc73c\uba74 recordedAt.
    val lastAt = state.current?.let { it.updatedAt ?: it.recordedAt }
    val isCurrentLive = lastAt?.let {
        runCatching {
            val instant = java.time.LocalDateTime.parse(it).atZone(java.time.ZoneId.systemDefault()).toInstant()
            java.time.Duration.between(instant, java.time.Instant.now()).toMinutes() < 10
        }.getOrDefault(true)
    } ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        // \ud5e4\ub354 \u2014 \uc81c\ubaa9 + \uc0c8\ub85c\uace0\uce68. \ub9c8\uc774\ud398\uc774\uc9c0\u00b7\ucc44\ud305\u00b7\uc790\ub140\uad00\ub9ac\uc640 \uac19\uc740 \uaddc\uaca9(\uc81c\ubaa9 21 Black,
        // \uc704 12\u00b7\uc544\ub798 4, \uc544\ub798 KidHeaderDivider)\uc73c\ub85c \ub9de\ucd98\ub2e4.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.parent_location_header),
                modifier = Modifier.weight(1f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 21.sp,
                color = colors.text,
            )
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable { viewModel.refresh() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.parent_retry),
                    modifier = Modifier.size(20.dp),
                    tint = colors.text,
                )
            }
        }
        KidHeaderDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .background(colors.card)
                .verticalScroll(rememberScrollState()),
        ) {
            // \uc790\ub140 \ud604\uc7ac \uc704\uce58(\uc88c\ud45c \ubb38\uc790\uc5f4 -> LatLng). \uc5c6\uc73c\uba74 \uc11c\uc6b8 \uc2dc\uccad \uae30\ubcf8\uac12.
            val defaultPos = LatLng(37.5665, 126.9780)
            val childPos = state.current?.let { c ->
                val lat = c.lat.toDoubleOrNull()
                val lng = c.lng.toDoubleOrNull()
                if (lat != null && lng != null) LatLng(lat, lng) else null
            }
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(childPos ?: defaultPos, 15f)
            }
            LaunchedEffect(childPos) {
                if (childPos != null) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(childPos, 16f)
                }
            }
            // \uc9c0\ub3c4 \ub9c8\ucee4 \u2014 \uc790\ub140\ubcc4 \uc0c9 + \uc774\ub984. \uc774\ub984\uc774 \uc5c6\uc73c\uba74(\uc790\ub140 \ubbf8\uc5f0\uacb0) "\ud604\uc7ac \uc704\uce58".
            val markerColor = childColor(state.selectedChildId)
            val childName = state.selectedChild?.name
            val markerName = childName ?: stringResource(R.string.parent_location_current)
            // history\ub294 \ucd5c\uc2e0\uc21c\uc774\ub77c \ub4a4\uc9d1\uc5b4\uc57c \uacfc\uac70 -> \ud604\uc7ac \uc21c\uc11c\ub85c \uc120\uc774 \uadf8\ub824\uc9c4\ub2e4.
            val routePoints = if (state.showRoute) {
                state.history
                    .mapNotNull { pt ->
                        pt.lat.toDoubleOrNull()?.let { lat -> pt.lng.toDoubleOrNull()?.let { lng -> LatLng(lat, lng) } }
                    }
                    .reversed()
            } else {
                emptyList()
            }
            val routeEnabled = state.history.size >= 2
            var mapExpanded by remember { mutableStateOf(false) }
            val fullCameraState = rememberCameraPositionState()

            // 자녀 2명 이상이면 누구 기준인지 먼저 고른다(앱통제·리포트 탭과 같은 규칙).
            if (state.children.size >= 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
                ) {
                    state.children.forEach { child ->
                        ChildLocationChip(
                            name = child.name,
                            color = childColor(child.id),
                            selected = child.id == state.selectedChildId,
                            onClick = { viewModel.selectChild(child.id) },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                    .height(230.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(18.dp))
                    // \uc9c0\ub3c4 \uc704\uc5d0\uc11c \uc190\uac00\ub77d\uc744 \uc6c0\uc9c1\uc774\ub294 \ub3d9\uc548\uc5d0\ub294 \uc0c1\uc704 verticalScroll\uc774 \uc81c\uc2a4\ucc98\ub97c \uac00\uc838\uac00\uc9c0 \ubabb\ud558\uac8c \ub9c9\ub294\ub2e4.
                    .pointerInteropFilter { event -> event.action == MotionEvent.ACTION_MOVE },
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                ) {
                    LocationMapContent(childPos, markerName, markerColor, state.places, routePoints)
                }
                // \uc624\ub298 \uacbd\ub85c \uce69(\uc88c\uc0c1\ub2e8) \u2014 \ub9ac\uc2a4\ud2b8 \ud1a0\uae00\uacfc \uac19\uc740 showRoute \ub97c \uacf5\uc720\ud55c\ub2e4.
                if (routeEnabled) {
                    MapChipButton(
                        label = stringResource(R.string.parent_location_route),
                        modifier = Modifier.align(Alignment.TopStart),
                        active = state.showRoute,
                        onClick = viewModel::toggleRoute,
                    )
                }
                MapChipButton(
                    label = stringResource(R.string.parent_map_fullscreen),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    fullCameraState.position = cameraPositionState.position
                    mapExpanded = true
                }
            }
            if (mapExpanded) {
                val closeFullscreen = {
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
                            LocationMapContent(childPos, markerName, markerColor, state.places, routePoints)
                        }
                        MapChipButton(
                            label = stringResource(R.string.parent_map_close),
                            modifier = Modifier.align(Alignment.TopEnd),
                            onClick = closeFullscreen,
                        )
                    }
                }
            }

            Box(Modifier.padding(horizontal = 16.dp)) {
                ParentLoadingOrError(state.loading && state.current == null, state.error) { viewModel.refresh() }
            }

            if (!state.loading && !state.hasChildren) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.parent_dashboard_empty_child),
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        KidButton(
                            text = stringResource(R.string.parent_dashboard_empty_pair_action),
                            onClick = onOpenPairing,
                        )
                    }
                }
            } else if (state.hasChildren) {
                Spacer(Modifier.height(8.dp))

                // \ud604\uc7ac \uc704\uce58 \u2014 \uba85\uc138\uc5d0 \uc8fc\uc18c\uac00 \uc5c6\uc5b4 \uac31\uc2e0 \uc2dc\uac01\uc73c\ub85c \ubcf4\uc5ec \uc900\ub2e4.
                LocationRow(
                    icon = R.drawable.ic_place,
                    iconTint = if (isCurrentLive) colors.safe else colors.muted,
                    iconBg = if (isCurrentLive) Color(0xFFF0FDF4) else colors.surface,
                    title = childName?.let { stringResource(R.string.parent_location_current_named, it) }
                        ?: stringResource(R.string.parent_location_current),
                    desc = relativeTime(lastAt)?.let { stringResource(R.string.parent_location_updated, it) },
                    trailing = {
                        val live = isCurrentLive
                        Text(
                            if (live) stringResource(R.string.parent_location_live) else stringResource(R.string.parent_location_recent),
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (live) Color(0xFFF0FDF4) else colors.surface)
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            color = if (live) colors.safe else colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 11.sp,
                        )
                    },
                )
                LocationDivider()

                // \uc2e4\uc2dc\uac04 \ucd94\uc801 \ud1a0\uae00
                LocationRow(
                    icon = R.drawable.ic_target,
                    title = stringResource(R.string.parent_location_realtime_toggle),
                    desc = childName?.let { stringResource(R.string.parent_location_realtime_named, it) }
                        ?: stringResource(R.string.parent_location_realtime_desc),
                    trailing = { KidSwitch(state.realtimeEnabled, viewModel::setRealtime) },
                )
                LocationDivider()

                // \uc624\ub298 \uc774\ub3d9 \uacbd\ub85c \ud1a0\uae00(\uc9c0\ub3c4 \uce69\uacfc \uac19\uc740 \uc0c1\ud0dc \uacf5\uc720)
                LocationRow(
                    icon = R.drawable.ic_route,
                    title = stringResource(R.string.parent_location_route),
                    desc = stringResource(R.string.parent_location_route_desc),
                    dimmed = !routeEnabled,
                    trailing = {
                        KidSwitch(
                            state.showRoute && routeEnabled,
                            { viewModel.toggleRoute() },
                            enabled = routeEnabled,
                        )
                    },
                )
                LocationDivider()

                // \uc9c0\uc815 \uc7a5\uc18c \uad00\ub9ac \u2014 \uc774\ub3d9
                LocationRow(
                    icon = R.drawable.ic_place,
                    title = stringResource(R.string.parent_geofence_manage),
                    desc = stringResource(R.string.parent_geofence_manage_desc),
                    onClick = onOpenGeofence,
                    trailing = { Text("\u203A", color = colors.border, fontSize = 19.sp) },
                )

                // \ucd5c\uadfc \ucd9c\uc785 \uae30\ub85d
                if (state.events.isNotEmpty()) {
                    Text(
                        childName?.let { stringResource(R.string.parent_geofence_events_named, it) }
                            ?: stringResource(R.string.parent_geofence_events),
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 4.dp),
                        color = colors.subtle,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                    val shown = state.events.take(5)
                    shown.forEachIndexed { i, event ->
                        val placeName = state.places.find { it.id == event.placeId }?.name
                            ?: stringResource(R.string.parent_geofence_event_unknown_place)
                        val exit = isExitEvent(event.eventType)
                        LocationRow(
                            icon = eventIcon(event.eventType),
                            iconTint = if (exit) colors.muted else colors.safe,
                            iconBg = if (exit) colors.surface else Color(0xFFF0FDF4),
                            title = stringResource(
                                if (exit) R.string.parent_geofence_event_exit else R.string.parent_geofence_event_entered,
                                placeName,
                            ),
                            trailing = { Text(hourMinute(event.occurredAt), color = colors.muted, fontSize = 12.sp) },
                        )
                        if (i < shown.lastIndex) LocationDivider()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** \uc704\uce58 \ud0ed\uc758 \uad6c\ubd84\uc120 \ub9ac\uc2a4\ud2b8 \ud55c \uc904. \uc544\uc774\ucf58 \u00b7 \uc81c\ubaa9 \u00b7 \uc124\uba85 \u00b7 \ub05d \ucee8\ud2b8\ub864. */
@Composable
private fun LocationRow(
    icon: Int,
    title: String,
    desc: String? = null,
    iconTint: Color = LocalKidhackColors.current.muted,
    iconBg: Color = LocalKidhackColors.current.surface,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (dimmed) colors.border else iconTint,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.5.sp,
                letterSpacing = (-0.2).sp,
                color = if (dimmed) colors.subtle else colors.text,
            )
            if (desc != null) {
                Spacer(Modifier.height(2.dp))
                Text(desc, color = colors.subtle, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun LocationDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 22.dp),
        color = LocalKidhackColors.current.line,
    )
}

/** 상단 자녀 선택 칩. 색 점이 지도 핀 색과 같아, 어느 칩이 어느 핀인지 이어진다. */
@Composable
private fun ChildLocationChip(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.brandLight else colors.surface)
            .then(
                if (selected) Modifier.border(1.dp, colors.brand, RoundedCornerShape(20.dp)) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(
            name,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
            color = if (selected) colors.brandDark else colors.muted,
        )
    }
}
