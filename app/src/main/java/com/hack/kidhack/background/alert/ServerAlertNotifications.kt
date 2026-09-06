package com.hack.kidhack.background.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.hack.kidhack.R

object ServerAlertNotifications {
    /**
 * 서버 알림(채팅·안전·스크린타임 등) 공용 채널.
 *
 * 부모·자녀가 같이 쓴다. 예전에는 부모 전용이라 이름이 「자녀 안전 알림」이었는데,
 * 자녀 기기에 그 이름으로 뜨면 말이 안 된다.
 *
 * 즉시 보여야 하는 알림들이라 IMPORTANCE_HIGH.
 */
    const val CHANNEL_ID = "kidhack_parent_alert"

    /** 알림마다 개별로 뜨게 id 를 흩어 놓는다(자녀 요청 알림 대역 7200 과 겹치지 않게 7300). */
    fun alertNotificationId(alertId: String): Int = 7300 + (alertId.hashCode() and 0x0FFF)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.server_alert_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.server_alert_channel_desc)
                enableVibration(true)
            },
        )
    }
}
