package com.hack.kidhack.feature.parent

// TODO: PlaceRequest에 notifyOnArrival 필드가 생기면 UI 복구 (docs/spec-gaps.md 참고)

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.PlaceType
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class GeofenceUiState(
    val items: List<Geofence> = emptyList(),
    val name: String = "",
    val radius: String = DEFAULT_RADIUS.toString(),
    val notify: Boolean = true,
    /** 사용자가 지도에서 고른 저장 좌표. 고르기 전에는 null이고, null이면 저장을 막는다. */
    val pickedLat: Double? = null,
    val pickedLng: Double? = null,
    /** 지도 카메라 초기 위치 힌트(자녀 최근 좌표). 저장값이 아니다. */
    val hintLat: Double? = null,
    val hintLng: Double? = null,
    /** 만들 장소의 종류. 서버 `type`(GEOFENCE 진입·이탈 / DESTINATION 도착)에 그대로 간다. */
    val type: PlaceType = PlaceType.GEOFENCE,
    /** 추가 시트가 열려 있는지. */
    val adding: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

/** 서울시청. 자녀 동선이 아직 없을 때 등록 좌표 기본값. */
/** 기본 반경. GPS 오차가 실내에서 20~50m 라 이보다 작으면 진입·이탈 알림이 떨린다. */
private const val DEFAULT_RADIUS = 100

private const val DEFAULT_LAT = 37.5665
private const val DEFAULT_LNG = 126.9780

class GeofenceViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val placeRepository: PlaceRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeofenceUiState())
    val state: StateFlow<GeofenceUiState> = _state.asStateFlow()

    init { refresh() }

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onRadius(v: String) = _state.update { it.copy(radius = v.filter { ch -> ch.isDigit() }) }
    fun onType(t: PlaceType) = _state.update { it.copy(type = t) }
    fun onMapPicked(lat: Double, lng: Double) =
        _state.update { it.copy(pickedLat = lat, pickedLng = lng, error = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children())
                if (id == null) {
                    _state.update { it.copy(loading = false, items = emptyList()) }
                    return@launch
                }
                // 자녀 최근 좌표는 지도 카메라를 띄울 위치일 뿐, 등록되는 좌표가 아니다.
                val hint = runCatching { locationRepository.history(id).firstOrNull() }.getOrNull()
                val places = placeRepository.list(id)
                _state.update {
                    it.copy(
                        loading = false,
                        items = places,
                        hintLat = hint?.lat?.toDoubleOrNull(),
                        hintLng = hint?.lng?.toDoubleOrNull(),
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun add() {
        val name = _state.value.name.trim()
        val radius = _state.value.radius.toIntOrNull() ?: return
        if (name.isBlank()) return
        val lat = _state.value.pickedLat
        val lng = _state.value.pickedLng
        if (lat == null || lng == null) {
            _state.update { it.copy(error = "지도에서 위치를 선택해주세요") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children) ?: return@runCatching
                placeRepository.create(
                    id,
                    Geofence(
                        id = "",
                        name = name,
                        lat = lat.toString(),
                        lng = lng.toString(),
                        radius = radius,
                        // notifyOnArrival 은 서버 PlaceRequest 에 대응 필드가 없어 무시된다.
                        // 알림 종류는 type 이 정한다 — GEOFENCE 는 진입·이탈, DESTINATION 은 도착.
                        notifyOnArrival = true,
                        type = _state.value.type,
                    ),
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        name = "",
                        pickedLat = null,
                        pickedLng = null,
                        radius = DEFAULT_RADIUS.toString(),
                        type = PlaceType.GEOFENCE,
                        adding = false,
                    )
                }
                refresh()
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun openAdd() = _state.update {
        it.copy(adding = true, error = null, name = "", pickedLat = null, pickedLng = null, radius = DEFAULT_RADIUS.toString(), type = PlaceType.GEOFENCE)
    }

    fun closeAdd() = _state.update { it.copy(adding = false, error = null) }

    fun delete(geofenceId: String) {
        viewModelScope.launch {
            runCatching {
                val id = selectedChildRepository.current() ?: return@runCatching
                placeRepository.delete(id, geofenceId)
            }.onSuccess {
                _state.update { it.copy(error = null) }
                refresh()
            }.onFailure { t ->
                // 명세: 400 INVALID_PARAMETER - "본인 자녀의 장소만 삭제할 수 있습니다." 등
                _state.update { it.copy(error = t.message) }
            }
        }
    }
}

/** 고른 위치의 드래그 가능한 마커. 작은 지도와 전체화면이 같은 로직을 쓴다. */
@Composable
@GoogleMapComposable
private fun PickedPlaceMarker(picked: LatLng, onPicked: (Double, Double) -> Unit) {
    val markerState = remember { MarkerState(position = picked) }
    LaunchedEffect(picked) {
        if (markerState.position != picked) markerState.position = picked
    }
    // 드래그로 옮긴 위치도 같은 경로로 상태에 반영한다(첫 값은 이미 picked라 버린다).
    LaunchedEffect(markerState) {
        snapshotFlow { markerState.position }
            .drop(1)
            .collect { onPicked(it.latitude, it.longitude) }
    }
    // 이미 등록된 장소(파랑)와 섞이지 않도록 아직 저장 안 된 이 마커만 초록으로 둔다.
    Marker(
        state = markerState,
        draggable = true,
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
    )
}

/**
 * 지정 장소.
 *
 * <p>등록한 장소를 구분선 목록으로 보여 주고, 「장소 추가」를 누르면 지도가 든 시트가
 * 화면 위로 올라온다. 전에는 지도가 든 큰 입력 카드가 목록 아래 늘 펼쳐져 있어, 장소를
 * 보러 온 부모에게도 화면 절반이 입력 폼이었다.
 *
 * <p>가짜 「알림 받기」 토글을 걷고 <b>안전 구역 / 목적지</b> 선택으로 바꿨다. 둘은 알림이
 * 다르다 — 안전 구역(GEOFENCE)은 진입·이탈, 목적지(DESTINATION)는 도착. 서버 `type` 그대로다.
 */
@Composable
fun ParentGeofenceScreen(
    onBack: () -> Unit = {},
    viewModel: GeofenceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_geofence_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
        }

        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if ((state.loading && state.items.isEmpty()) || (state.error != null && state.items.isEmpty())) {
                    Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::refresh)
                    }
                }
                if (!state.loading && state.items.isEmpty()) {
                    Text(
                        stringResource(R.string.parent_geofence_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        color = colors.muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                state.items.forEachIndexed { index, item ->
                    PlaceRow(item, showDivider = index > 0) { viewModel.delete(item.id) }
                }
                Spacer(Modifier.height(90.dp))
            }

            // 추가는 목록 위에 늘 떠 있는 알약으로. 커뮤니티·앱 통제와 같은 자리다.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(colors.brand)
                    .clickable(onClick = viewModel::openAdd)
                    .padding(start = 15.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_plus),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.parent_geofence_add),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                )
            }
        }
    }

    if (state.adding) {
        AddPlaceSheet(state = state, viewModel = viewModel)
    }
}

/** 등록한 장소 한 줄. 왼쪽 아이콘 색과 배지로 안전 구역·목적지를 가른다. */
@Composable
private fun PlaceRow(item: Geofence, showDivider: Boolean, onDelete: () -> Unit) {
    val colors = LocalKidhackColors.current
    val destination = item.type == PlaceType.DESTINATION
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = colors.line)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (destination) DestTint else colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (destination) R.drawable.ic_flag else R.drawable.ic_place),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = if (destination) DestFg else colors.brandDark,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.5.sp,
                letterSpacing = (-0.2).sp,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.parent_geofence_radius_unit, item.radius),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 11.5.sp,
            )
        }
        Text(
            text = stringResource(if (destination) R.string.parent_geofence_type_dest else R.string.parent_geofence_type_geo),
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (destination) DestTint else colors.brandLight)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = if (destination) DestFg else colors.brandDark,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 10.5.sp,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.parent_geofence_delete),
                modifier = Modifier.size(13.dp),
                tint = colors.subtle,
            )
        }
    }
}

private val DestTint = Color(0xFFEAF1FB)
private val DestFg = Color(0xFF4B6A93)

/**
 * 장소 추가 시트.
 *
 * <p>ModalBottomSheet 안에 지도를 넣으면 maps-compose 가 재측정에서 예외를 던지는 경우가
 * 있어, 시트를 화면을 덮는 in-flow 오버레이로 직접 만든다. 지도·전체화면·마커 로직은 그대로
 * 쓴다.
 */
@Composable
private fun AddPlaceSheet(state: GeofenceUiState, viewModel: GeofenceViewModel) {
    val colors = LocalKidhackColors.current
    val picked = state.pickedLat?.let { lat -> state.pickedLng?.let { lng -> LatLng(lat, lng) } }
    val camera = state.hintLat?.let { lat -> state.hintLng?.let { lng -> LatLng(lat, lng) } }
        ?: state.items.firstNotNullOfOrNull { it.latLngOrNull() }
        ?: LatLng(DEFAULT_LAT, DEFAULT_LNG)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(camera, 15f)
    }
    LaunchedEffect(camera) {
        if (picked == null) cameraPositionState.position = CameraPosition.fromLatLngZoom(camera, 15f)
    }
    var mapExpanded by remember { mutableStateOf(false) }
    val fullCameraState = rememberCameraPositionState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = viewModel::closeAdd),
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 60.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(colors.card)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.parent_geofence_add),
                    modifier = Modifier.weight(1f),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 17.sp,
                    color = colors.text,
                )
                val canSave = state.name.isNotBlank() && state.pickedLat != null &&
                    (state.radius.toIntOrNull() ?: 0) > 0
                Text(
                    stringResource(R.string.parent_geofence_save),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (canSave) Modifier.clickable(onClick = viewModel::add) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    color = if (canSave) colors.brandDark else colors.border,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInteropFilter { event -> event.action == MotionEvent.ACTION_MOVE },
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng -> viewModel.onMapPicked(latLng.latitude, latLng.longitude) },
                ) {
                    SavedPlacesOverlay(state.items)
                    if (picked != null) PickedPlaceMarker(picked, viewModel::onMapPicked)
                }
                MapChipButton(
                    label = stringResource(R.string.parent_map_fullscreen),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    fullCameraState.position = cameraPositionState.position
                    mapExpanded = true
                }
            }
            Text(
                stringResource(R.string.parent_geofence_pick_hint),
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontSize = 11.5.sp,
            )

            SheetLabel(stringResource(R.string.parent_geofence_type_label))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TypeOption(
                    title = stringResource(R.string.parent_geofence_type_geo),
                    desc = stringResource(R.string.parent_geofence_type_geo_desc),
                    selected = state.type == PlaceType.GEOFENCE,
                    modifier = Modifier.weight(1f),
                ) { viewModel.onType(PlaceType.GEOFENCE) }
                TypeOption(
                    title = stringResource(R.string.parent_geofence_type_dest),
                    desc = stringResource(R.string.parent_geofence_type_dest_desc),
                    selected = state.type == PlaceType.DESTINATION,
                    modifier = Modifier.weight(1f),
                ) { viewModel.onType(PlaceType.DESTINATION) }
            }

            SheetLabel(stringResource(R.string.parent_geofence_name))
            KidTextField(
                state.name,
                viewModel::onName,
                label = "",
                placeholder = stringResource(R.string.parent_geofence_name_hint),
                modifier = Modifier.padding(horizontal = 22.dp),
            )

            SheetLabel(stringResource(R.string.parent_geofence_radius))
            val presets = listOf(50, 100, 200)
            val radiusNow = state.radius.toIntOrNull()
            val custom = radiusNow == null || radiusNow !in presets
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { m ->
                    RadiusChip(m.toString() + "m", state.radius == m.toString() && !custom, Modifier.weight(1f)) {
                        viewModel.onRadius(m.toString())
                    }
                }
                RadiusChip(stringResource(R.string.parent_geofence_radius_custom), custom, Modifier.weight(1f)) {
                    viewModel.onRadius("")
                }
            }
            if (custom) {
                Spacer(Modifier.height(10.dp))
                KidTextField(
                    state.radius,
                    viewModel::onRadius,
                    label = "",
                    placeholder = stringResource(R.string.parent_geofence_radius_hint),
                    modifier = Modifier.padding(horizontal = 22.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            state.error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp),
                    color = colors.danger,
                    fontSize = 12.5.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (mapExpanded) {
        val close = {
            cameraPositionState.position = fullCameraState.position
            mapExpanded = false
        }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(colors.surface)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = fullCameraState,
                    onMapClick = { latLng -> viewModel.onMapPicked(latLng.latitude, latLng.longitude) },
                ) {
                    SavedPlacesOverlay(state.items)
                    if (picked != null) PickedPlaceMarker(picked, viewModel::onMapPicked)
                }
                MapChipButton(
                    label = stringResource(R.string.parent_map_close),
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = close,
                )
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 7.dp),
        color = LocalKidhackColors.current.subtle,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

/** 안전 구역 / 목적지 중 하나. 라디오 대신 테두리·바탕으로 고른 쪽을 말한다. */
@Composable
private fun TypeOption(
    title: String,
    desc: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.brandLight else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.5.sp,
            color = if (selected) colors.brandDark else colors.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(desc, color = colors.subtle, fontSize = 10.5.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun RadiusChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.brandLight else colors.card)
            .then(
                if (selected) Modifier else Modifier.border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        color = if (selected) colors.brandDark else colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )
}
