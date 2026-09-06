package com.hack.kidhack.background.command

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildNotifications
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.CommandAckStatus
import com.hack.kidhack.background.usage.AppUsageWorker
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 부모 요청 수신용 상주 포그라운드 서비스 — **Firebase 없이 푸시를 구현하는 핵심**.
 *
 * 하는 일은 두 가지뿐이다.
 *  1. [CommandRepository.observe] (= WebSocket) 를 계속 구독한다.
 *  2. 요청이 오면 알림으로 띄우고 서버에 RECEIVED ack 를 보낸다.
 *
 * 실제 캡처/촬영은 사용자가 알림을 눌러야 시작된다. 화면 녹화(MediaProjection)와
 * 카메라는 안드로이드가 사용자 동의 없이 백그라운드 실행을 허용하지 않기 때문이다.
 *
 * 배포 시 유의: `dataSync` 포그라운드 타입은 안드로이드 15 에서 하루 6시간 제한이 있다.
 * 자녀 보호 용도로 상시 유지하려면 `specialUse` 선언 + 스토어 심사가 필요하다.
 */
class ChildCommandService : Service() {

    private val commandRepository: CommandRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val notifier: ChildCommandNotifier by inject()
    private val alertStore: ChildAlertStore by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ChildNotifications.ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(
                this,
                ChildNotifications.COMMAND_ID,
                ChildNotifications.foreground(
                    this,
                    getString(R.string.child_command_notif_title),
                    getString(R.string.child_command_notif_text),
                ),
                type,
            )
        }.onFailure {
            stopSelf()
            return
        }

        scope.launch {
            commandRepository.observe().collect { command ->
                if (command.type == RemoteCommandType.UNKNOWN) return@collect
                if (command.type == RemoteCommandType.USAGE_REPORT) {
                    // 조용히 처리한다. 부모가 리포트를 새로고침할 때마다 나가는 요청이라
                    // 알림을 띄우면 자녀 폰이 계속 울린다.
                    AppUsageWorker.enqueueNow(this@ChildCommandService)
                } else {
                    notifier.notify(command)
                    // 알림은 지나가면 사라진다. 홈 탭 「최근 알림」에서 다시 볼 수 있게 기록해 둔다.
                    recordAlert(command)
                }
                val childId = command.childId.ifBlank {
                    sessionRepository.current()?.user?.id.orEmpty()
                }
                runCatching {
                    commandRepository.ack(childId, command.requestId, CommandAckStatus.RECEIVED)
                }
            }
        }
    }

    private suspend fun recordAlert(command: com.hack.kidhack.domain.model.RemoteCommand) {
        val who = KoreanParticles.subject(
            command.requestedBy?.takeIf { it.isNotBlank() }
                ?: getString(R.string.child_request_default_sender),
        )
        when (command.type) {
            RemoteCommandType.SCREEN_CAPTURE -> alertStore.record(
                type = ChildAlertStore.TYPE_CAPTURE_REQUEST,
                title = getString(R.string.child_alert_capture_request),
                body = getString(R.string.child_alert_request_from, who),
                dedupeKey = ChildAlertStore.TYPE_CAPTURE_REQUEST + "-" + command.requestId,
            )
            RemoteCommandType.CAMERA_SHOT -> alertStore.record(
                type = ChildAlertStore.TYPE_CAMERA_REQUEST,
                title = getString(R.string.child_alert_camera_request),
                body = getString(R.string.child_alert_request_from, who),
                dedupeKey = ChildAlertStore.TYPE_CAMERA_REQUEST + "-" + command.requestId,
            )
            RemoteCommandType.USAGE_REPORT, RemoteCommandType.UNKNOWN -> Unit
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        commandRepository.disconnect()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ChildCommandService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChildCommandService::class.java))
        }
    }
}
