package com.hack.kidhack.feature.parent

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.hack.kidhack.domain.model.Geofence

/** 등록 장소 색. 자녀 실시간 위치(빨강)와 구분한다. */
internal val PLACE_BLUE = Color(0xFF2563EB)

/** 좌표가 문자열이라 숫자로 못 읽는 값이 섞일 수 있다. 그런 장소는 지도에 못 그린다. */
internal fun Geofence.latLngOrNull(): LatLng? {
    val lat = lat.toDoubleOrNull() ?: return null
    val lng = lng.toDoubleOrNull() ?: return null
    return LatLng(lat, lng)
}

/** 마커 위에 항상 떠 있는 이름표. 탭해야 뜨는 InfoWindow 대신 쓴다. */
@Composable
internal fun rememberPlaceLabelIcon(text: String): BitmapDescriptor {
    val density = LocalDensity.current.density
    return remember(text, density) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 11f * density
            typeface = Typeface.DEFAULT_BOLD
        }
        val padH = 6f * density
        val padV = 3f * density
        val metrics = textPaint.fontMetrics
        val width = (textPaint.measureText(text) + padH * 2).toInt().coerceAtLeast(1)
        val height = ((metrics.descent - metrics.ascent) + padV * 2).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val radius = 6f * density
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLACE_BLUE.toArgb() }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, background)
        canvas.drawText(text, padH, padV - metrics.ascent, textPaint)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

/**
 * 등록해 둔 지정 장소를 지도에 그린다 — 반경 원 + 마커 + 이름표.
 *
 * 위치 화면과 지정 장소 관리 화면이 같은 그림을 써야 한다. 관리 화면에서는 목록에
 * 이름·반경만 적혀 있어서 그 장소가 실제로 어디인지 확인할 방법이 없었다.
 */
@Composable
@GoogleMapComposable
internal fun SavedPlacesOverlay(places: List<Geofence>) {
    places.forEach { place ->
        val center = place.latLngOrNull() ?: return@forEach
        Circle(
            center = center,
            radius = place.radius.toDouble(),
            strokeColor = PLACE_BLUE,
            strokeWidth = 2f,
            fillColor = PLACE_BLUE.copy(alpha = 0.15f),
        )
        Marker(
            state = rememberMarkerState(key = place.id, position = center),
            title = place.name,
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
        )
        Marker(
            state = rememberMarkerState(key = place.id + "_label", position = center),
            title = place.name,
            icon = rememberPlaceLabelIcon(place.name),
            anchor = Offset(0.5f, -0.2f),
            zIndex = 1f,
        )
    }
}
