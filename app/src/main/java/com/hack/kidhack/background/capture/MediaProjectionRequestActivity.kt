package com.hack.kidhack.background.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.hack.kidhack.R
import com.hack.kidhack.domain.model.CommandAckStatus
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 화면 녹화 동의만 받고 즉시 사라지는 투명 액티비티.
 *
 * 안드로이드는 MediaProjection 을 **매번** 사용자 동의로만 허용한다(토큰 영속화 불가).
 * 그래서 부모 요청 → 알림 → 이 화면 → 시스템 동의 다이얼로그 → [ScreenCaptureService] 순서가 된다.
 */
class MediaProjectionRequestActivity : ComponentActivity() {

    private val commandRepository: CommandRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val requestId: String by lazy { intent?.getStringExtra(EXTRA_REQUEST_ID).orEmpty() }
    private val trigger: String by lazy {
        intent?.getStringExtra(EXTRA_TRIGGER) ?: ScreenCaptureService.TRIGGER_MANUAL
    }

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.startCapture(
                context = this,
                resultCode = result.resultCode,
                resultData = data,
                requestId = requestId,
                trigger = trigger,
            )
        } else {
            Toast.makeText(this, getString(R.string.child_capture_denied), Toast.LENGTH_SHORT).show()
            if (requestId.isNotBlank()) {
                scope.launch {
                    val childId = sessionRepository.current()?.user?.id.orEmpty()
                    runCatching {
                        commandRepository.ack(childId, requestId, CommandAckStatus.DENIED)
                    }
                }
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            finish()
            return
        }
        consent.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        const val EXTRA_REQUEST_ID = "requestId"
        const val EXTRA_TRIGGER = "trigger"

        fun intent(
            context: Context,
            requestId: String,
            trigger: String = ScreenCaptureService.TRIGGER_MANUAL,
        ): Intent = Intent(context, MediaProjectionRequestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_REQUEST_ID, requestId)
            .putExtra(EXTRA_TRIGGER, trigger)
    }
}
