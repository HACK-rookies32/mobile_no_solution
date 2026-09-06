package com.hack.kidhack

import com.hack.kidhack.background.alert.ParentChildWatcher.Companion.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 보호자 앱이 자녀의 안전 구역 이탈을 직접 판정할 때 쓰는 거리 계산.
 *
 * 서버가 이탈 알림을 만들어 주지 않아도 보호자 기기에서 판정하려면 이 값이 정확해야 한다.
 */
class GeofenceDistanceTest {

    @Test
    fun `같은 좌표는 0미터`() {
        assertEquals(0.0, distanceMeters(37.5665, 126.9780, 37.5665, 126.9780), 0.5)
    }

    @Test
    fun `위도 1초는 약 31미터`() {
        val meters = distanceMeters(37.5665, 126.9780, 37.5665 + 1.0 / 3600, 126.9780)
        assertEquals(30.9, meters, 1.0)
    }

    @Test
    fun `서울시청에서 강남역까지는 약 8킬로미터`() {
        val meters = distanceMeters(37.5665, 126.9780, 37.4979, 127.0276)
        assertTrue("실제 " + meters.toInt() + "m", meters in 8_000.0..9_500.0)
    }

    @Test
    fun `반경 200미터 구역 판정`() {
        val zoneLat = 37.5665
        val zoneLng = 126.9780
        // 약 100m 북쪽 — 구역 안
        assertTrue(distanceMeters(zoneLat + 0.0009, zoneLng, zoneLat, zoneLng) < 200)
        // 약 550m 북쪽 — 구역 밖
        assertTrue(distanceMeters(zoneLat + 0.005, zoneLng, zoneLat, zoneLng) > 200)
    }
}
