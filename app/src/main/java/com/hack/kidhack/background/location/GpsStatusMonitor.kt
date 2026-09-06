package com.hack.kidhack.background.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import com.hack.kidhack.background.ChildAlertUploadWorker
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.repository.DeviceRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Observes GPS provider enabled state and reports `POST /device/event (type=GPS_OFF)`.
 *
 * A normal app cannot force GPS on (GAP-12 / REQ-M-10). This monitor never
 * writes secure settings, never uses DeviceAdmin, and only reports the OS
 * provider flag so the server can notify the parent. GPS 재활성화(ON) 이벤트는
 * 명세 type enum에 없어 전송하지 않는다.
 */
class GpsStatusMonitor(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val sessionRepository: SessionRepository,
    private val alertStore: ChildAlertStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastEnabled: Boolean? = null
    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        // 최초 상태가 이미 꺼져 있을 때만 보고한다. 켜져 있는 초기 상태는 이벤트가 아니다.
        val initial = currentEnabled()
        if (!initial) report(initial) else lastEnabled = initial
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        val next = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                report(currentEnabled())
            }
        }
        receiver = next
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(next, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(next, filter)
        }
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        lastEnabled = null
    }

    private companion object {
        /** 설정 화면에서 토글을 오가도 30분에 한 번만 남긴다. */
        const val GPS_ALERT_DEDUPE_MS = 30 * 60 * 1000L
    }

    private fun currentEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun report(enabled: Boolean) {
        if (lastEnabled == enabled) return
        lastEnabled = enabled
        // GPS가 다시 켜진 경우(true)는 대응 이벤트 type이 없어 상태만 갱신하고 끝낸다.
        if (enabled) return
        scope.launch {
            runCatching {
                val childId = sessionRepository.current()?.user?.id ?: return@launch
                deviceRepository.reportEvent(childId, "GPS_OFF")
            }
            if (!enabled) {
                // 자녀 홈 「최근 알림」에도 남기고, 보호자가 곧 받아 보도록 바로 올린다.
                alertStore.record(
                    type = ChildAlertStore.TYPE_GPS_OFF,
                    title = "GPS가 꺼졌어요",
                    body = "위치 전송이 멈췄어요. 위치 서비스를 다시 켜 주세요.",
                    dedupeWindowMs = GPS_ALERT_DEDUPE_MS,
                )
                ChildAlertUploadWorker.enqueueNow(context)
            }
        }
    }
}
