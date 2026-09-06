package com.hack.kidhack.background.command

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
import com.hack.kidhack.background.ChildNotifications
import com.hack.kidhack.background.capture.MediaProjectionRequestActivity
import com.hack.kidhack.background.capture.ScreenCaptureService
import com.hack.kidhack.background.capture.ScreenCaptureWorker
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType

/**
 * 부모 요청을 자녀 기기 알림으로 띄운다.
 *
 * 알림을 누르면 [MainActivity] 가 requestId 를 들고 뜨고,
 * 화면 캡처면 MediaProjection 동의 → 캡처, 카메라면 카메라 화면 자동 진입으로 이어진다.
 */
class ChildCommandNotifier(private val context: Context) {

    fun notify(command: RemoteCommand) {
        if (!canPostNotifications()) return
        ChildNotifications.ensureChannel(context)

        val who = KoreanParticles.subject(
            command.requestedBy?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.child_request_default_sender),
        )
        // 1시간 주기 정기 캡처는 부모가 보낸 요청이 아니다. 같은 문구를 쓰면
        // 자녀가 「부모님이 지금 보고 싶어 해요」로 오해한다.
        val scheduled = command.requestId.startsWith(ScreenCaptureWorker.SCHEDULED_PREFIX)
        val title = when {
            command.type == RemoteCommandType.SCREEN_CAPTURE && scheduled ->
                context.getString(R.string.child_alert_scheduled_capture)
            command.type == RemoteCommandType.SCREEN_CAPTURE ->
                context.getString(R.string.child_request_capture_title)
            command.type == RemoteCommandType.CAMERA_SHOT ->
                context.getString(R.string.child_request_camera_title)
            else -> return
        }
        val body = when {
            command.type == RemoteCommandType.SCREEN_CAPTURE && scheduled ->
                context.getString(R.string.child_alert_scheduled_capture_body)
            command.type == RemoteCommandType.SCREEN_CAPTURE ->
                context.getString(R.string.child_request_capture_body, who)
            command.type == RemoteCommandType.CAMERA_SHOT ->
                context.getString(R.string.child_request_camera_body, who)
            else -> return
        }

        val notification = NotificationCompat.Builder(context, ChildNotifications.REQUEST_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(command))
            .build()

        NotificationManagerCompat.from(context)
            .notify(ChildNotifications.requestNotificationId(command.requestId), notification)
    }

    fun cancel(requestId: String) {
        NotificationManagerCompat.from(context)
            .cancel(ChildNotifications.requestNotificationId(requestId))
    }

    /**
     * 화면 캡처는 앱 UI 를 거칠 필요가 없으므로 동의 액티비티로 바로 보낸다.
     * 카메라 촬영은 자녀가 직접 찍어야 하므로 앱(카메라 탭)으로 보낸다.
     */
    private fun contentIntent(command: RemoteCommand): PendingIntent {
        val intent = when (command.type) {
            RemoteCommandType.SCREEN_CAPTURE -> {
                val trigger = if (command.requestId.startsWith(ScreenCaptureWorker.SCHEDULED_PREFIX)) {
                    ScreenCaptureService.TRIGGER_SCHEDULED
                } else {
                    ScreenCaptureService.TRIGGER_MANUAL
                }
                MediaProjectionRequestActivity.intent(context, command.requestId, trigger)
            }
            else -> Intent(context, MainActivity::class.java).apply {
                action = ACTION_REMOTE_COMMAND
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_REQUEST_ID, command.requestId)
                putExtra(EXTRA_TYPE, command.type.name)
                putExtra(EXTRA_CHILD_ID, command.childId)
                putExtra(EXTRA_REQUESTED_BY, command.requestedBy)
            }
        }
        return PendingIntent.getActivity(
            context,
            ChildNotifications.requestNotificationId(command.requestId),
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
        const val ACTION_REMOTE_COMMAND = "com.hack.kidhack.action.REMOTE_COMMAND"
        const val EXTRA_REQUEST_ID = "requestId"
        const val EXTRA_TYPE = "commandType"
        const val EXTRA_CHILD_ID = "childId"
        const val EXTRA_REQUESTED_BY = "requestedBy"
    }
}
