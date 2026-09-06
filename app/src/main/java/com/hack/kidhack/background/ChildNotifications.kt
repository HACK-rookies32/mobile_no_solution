package com.hack.kidhack.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hack.kidhack.R

object ChildNotifications {
    /** 상시 실행 서비스용(조용한 채널). */
    const val CHANNEL_ID = "kidhack_child_platform"

    /** 부모 요청 알림용. 헤드업으로 떠야 하므로 IMPORTANCE_HIGH. */
    const val REQUEST_CHANNEL_ID = "kidhack_child_request"

    const val LOCATION_ID = 7101
    const val CAPTURE_ID = 7102
    const val COMMAND_ID = 7103

    /** 요청 알림은 requestId 해시로 id 를 만들어 요청마다 개별 알림이 뜨게 한다. */
    fun requestNotificationId(requestId: String): Int = 7200 + (requestId.hashCode() and 0x0FFF)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.child_platform_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.child_platform_channel_desc)
                    setShowBadge(false)
                },
            )
        }
        if (manager.getNotificationChannel(REQUEST_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REQUEST_CHANNEL_ID,
                    context.getString(R.string.child_request_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.child_request_channel_desc)
                    enableVibration(true)
                },
            )
        }
    }

    /**
     * 자녀 기기에 오는 일반 알림(용돈 받음·새 메시지 등, FCM `PARENT_ALERT` 경로)을 알림창에 띄운다.
     * 서버가 data 전용 푸시를 보내므로 앱이 직접 그린다. 같은 `key`(notificationId)면 갱신만 된다.
     */
    fun notifyAlert(context: Context, key: String, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val open = android.app.PendingIntent.getActivity(
            context,
            key.hashCode(),
            android.content.Intent(context, com.hack.kidhack.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REQUEST_CHANNEL_ID)
            .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(7600 + (key.hashCode() and 0x0FFF), notification)
    }

    fun foreground(context: Context, title: String, text: String): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
