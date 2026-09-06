package com.hack.kidhack.background.alert

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hack.kidhack.MainActivity
import com.hack.kidhack.R
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.body
import com.hack.kidhack.domain.model.displayTitle

/**
 * 부모 기기에 자녀 안전 알림을 푸시로 띄운다.
 *
 * 알림을 누르면 [MainActivity] 가 알림 탭으로 이동시킨다(라우팅은 `KidhackNavHost`).
 */
class ServerAlertNotifier(private val context: Context) {

    /**
     * @return 실제로 띄운 알림들. 알림 권한이 없거나 수신 설정이 꺼져 있으면 비어 있다.
     */
    fun notifyAll(
        alerts: List<AppNotification>,
        settings: NotificationSettings?,
    ): List<AppNotification> {
        if (alerts.isEmpty() || !canPostNotifications()) return emptyList()
        ServerAlertNotifications.ensureChannel(context)
        val allowed = alerts.filter { settings == null || settings.allows(it.type) }
        allowed.forEach { notify(it) }
        return allowed
    }

    private fun notify(alert: AppNotification) {
        val title = alert.displayTitle.ifBlank { context.getString(R.string.parent_alert_default_title) }
        val body = alert.body.ifBlank { alert.displayTitle }
        val notification = NotificationCompat.Builder(context, ServerAlertNotifications.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(alert))
            .build()

        NotificationManagerCompat.from(context)
            .notify(ServerAlertNotifications.alertNotificationId(alert.id), notification)
    }

    private fun contentIntent(alert: AppNotification): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_PARENT_ALERT
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_ALERT_ID, alert.id)
        }
        return PendingIntent.getActivity(
            context,
            ServerAlertNotifications.alertNotificationId(alert.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean {
        val permission = KidhackPermissions.notifications ?: return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_PARENT_ALERT = "com.hack.kidhack.action.PARENT_ALERT"
        const val EXTRA_ALERT_ID = "alertId"
    }
}

/**
 * 알림 수신 설정(알림 탭의 토글)과 알림 종류를 잇는다.
 *
 * 서버가 새 종류를 추가해도 부모가 놓치지 않도록, 모르는 종류는 **허용**으로 둔다.
 */
fun NotificationSettings.allows(type: String): Boolean {
    val upper = type.uppercase()
    return when {
        upper.contains("HARMFUL") || upper.contains("BLOCK") -> harmfulAccess
        upper.contains("LOCATION") || upper.contains("GEOFENCE") ||
            upper.contains("ARRIVAL") || upper.contains("DEPART") ||
            upper.contains("GPS") -> location
        upper.contains("USAGE") || upper.contains("SCREEN_TIME") || upper.contains("LIMIT") -> appUsage
        upper.contains("NOTICE") -> notice
        else -> true
    }
}
