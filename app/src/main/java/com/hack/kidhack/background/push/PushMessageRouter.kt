package com.hack.kidhack.background.push

import android.content.Context
import android.util.Log
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildNotifications
import com.hack.kidhack.background.alert.ServerAlertSync
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.CommandAckStatus
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.background.usage.AppUsageWorker
import com.hack.kidhack.data.repository.toRemoteCommandType
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.SessionRepository

/**
 * FCM 으로 들어온 푸시 하나를 알맞은 기존 처리 경로로 흘려 보낸다.
 *
 * 서버(`NotificationService` → `FirebasePushSender`)는 알림 행 하나를 저장할 때마다 수신자의 모든 기기로
 * `data{type, notificationId, childId?, title, body}` 를 보낸다. 두 갈래로 나눈다.
 *
 * - **자녀 명령**([PushMessage.Kind.CHILD_COMMAND], `type=SCREENSHOT_REQUESTED` 등) → 알림([ChildCommandNotifier])
 *   + 홈 기록([ChildAlertStore]) + 버스([ChildCommandBus]) + **서버 알림 읽음 처리**(= 수신 ACK).
 *   `ChildCommandService` 가 WebSocket 프레임을 처리하던 것과 **동일한 동작**이라 캡처 플로우가 그대로 이어진다.
 *   예전 `commands/{id}/ack` 호출은 서버에 없어(404) 남겨만 두고, `PATCH notifications/{id}/read` 가 ack 역할을 한다.
 *
 * - **알림**([PushMessage.Kind.PARENT_ALERT]) — 수신 기기 역할로 나눈다.
 *   - 부모: [ServerAlertSync.recordRemote]. 보관함 기록 + 헤드업 푸시 + 대시보드 「최근 알림」 즉시 반영.
 *     폴링 경로와 **같은 함수**라 FCM 과 15분 폴링이 같은 알림을 둘 다 물어와도 id(notificationId)로 합쳐진다.
 *   - 자녀(용돈 받음·새 메시지·스크린타임 등): [ChildAlertStore] 기록(홈 「최근 알림」) + 알림창.
 *
 * 계정 전환 직후 옛 토큰으로 온 푸시는 역할이 안 맞아 걸러진다.
 */
class PushMessageRouter(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val serverAlertSync: ServerAlertSync,
    private val childCommandBus: ChildCommandBus,
    private val childCommandNotifier: ChildCommandNotifier,
    private val childAlertStore: ChildAlertStore,
    private val commandRepository: CommandRepository,
    private val notificationRepository: NotificationRepository,
) {
    suspend fun route(message: PushMessage) {
        when (message.kind) {
            PushMessage.Kind.PARENT_ALERT -> routeAlert(message)
            PushMessage.Kind.CHILD_COMMAND -> routeChildCommand(message)
            PushMessage.Kind.UNKNOWN -> Log.d(TAG, "ignored unknown push type=${message.type}")
        }
    }

    private suspend fun routeAlert(message: PushMessage) {
        val role = sessionRepository.current()?.user?.role
        Log.d(TAG, "routeAlert type=${message.type} id=${message.id} role=$role")
        when (role) {
            Role.PARENT -> {
                val pushed = serverAlertSync.recordRemote(
                    id = message.id.ifBlank { "fcm-" + message.type + "-" + message.title },
                    type = message.type,
                    title = message.title,
                    body = message.body,
                    childId = message.childId,
                )
                Log.d(TAG, "parent alert recorded/pushed=$pushed")
            }
            Role.CHILD -> routeChildAlert(message)
            null -> Unit
        }
    }

    /** 자녀 기기에 오는 일반 알림. 홈 「최근 알림」에 남기고 알림창에 띄운다. */
    private suspend fun routeChildAlert(message: PushMessage) {
        val title = message.title.ifBlank { context.getString(R.string.app_name) }
        val key = message.id.ifBlank { message.type + "-" + message.body.hashCode() }
        val fresh = childAlertStore.record(
            type = message.type,
            title = title,
            body = message.body,
            dedupeKey = "fcm-" + key,
        )
        if (fresh) ChildNotifications.notifyAlert(context, key, title, message.body)
    }

    private suspend fun routeChildCommand(message: PushMessage) {
        if (sessionRepository.current()?.user?.role != Role.CHILD) return
        val type = message.type.toRemoteCommandType()
        if (type == RemoteCommandType.UNKNOWN) return
        val childId = message.childId?.takeIf { it.isNotBlank() }
            ?: sessionRepository.current()?.user?.id.orEmpty()
        val command = RemoteCommand(
            requestId = message.id,
            type = type,
            childId = childId,
            requestedBy = message.requestedBy,
        )
        // ChildCommandService 의 소켓 처리와 동일한 3동작.
        if (command.type == RemoteCommandType.USAGE_REPORT) {
            // 조용히 처리한다 — 소켓 경로와 같은 이유다.
            AppUsageWorker.enqueueNow(context)
        } else {
            childCommandNotifier.notify(command)
            recordChildAlert(command)
            childCommandBus.post(command)
        }
        // 수신 ACK: 서버 알림 행을 읽음 처리한다(같은 요청이 폴링으로 다시 잡히지 않도록). 옛 ack 경로는 백업.
        if (command.requestId.isNotBlank()) {
            runCatching { notificationRepository.markRead(command.requestId) }
                .onFailure { Log.w(TAG, "markRead failed: ${it.message}") }
        }
        runCatching {
            commandRepository.ack(childId, command.requestId, CommandAckStatus.RECEIVED)
        }
    }

    private suspend fun recordChildAlert(command: RemoteCommand) {
        val who = KoreanParticles.subject(
            command.requestedBy?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.child_request_default_sender),
        )
        when (command.type) {
            RemoteCommandType.SCREEN_CAPTURE -> childAlertStore.record(
                type = ChildAlertStore.TYPE_CAPTURE_REQUEST,
                title = context.getString(R.string.child_alert_capture_request),
                body = context.getString(R.string.child_alert_request_from, who),
                dedupeKey = ChildAlertStore.TYPE_CAPTURE_REQUEST + "-" + command.requestId,
            )
            RemoteCommandType.CAMERA_SHOT -> childAlertStore.record(
                type = ChildAlertStore.TYPE_CAMERA_REQUEST,
                title = context.getString(R.string.child_alert_camera_request),
                body = context.getString(R.string.child_alert_request_from, who),
                dedupeKey = ChildAlertStore.TYPE_CAMERA_REQUEST + "-" + command.requestId,
            )
            RemoteCommandType.USAGE_REPORT, RemoteCommandType.UNKNOWN -> Unit
        }
    }

    private companion object {
        const val TAG = "KidhackFcm"
    }
}
