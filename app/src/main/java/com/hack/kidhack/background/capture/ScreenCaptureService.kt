package com.hack.kidhack.background.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildNotifications
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.data.local.ChildCaptureStore
import com.hack.kidhack.domain.model.CommandAckStatus
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaProjection 캡처 파이프라인 (REQ-M-12).
 *
 * `MediaProjectionRequestActivity 동의 → 이 서비스` 순서로만 진입한다.
 *
 * 1. `mediaProjection` 타입으로 startForeground (안드로이드 14+ 필수 선행 조건)
 * 2. VirtualDisplay + ImageReader 로 화면 한 장을 떠서 Bitmap 으로 변환
 * 3. JPEG 로 캐시에 쓰고 `POST children/{childId}/screenshots` 멀티파트 업로드
 * 4. 서버에 DONE ack → 자원 해제 → stopSelf
 *
 * 한 장만 뜨고 바로 종료하므로 projection 을 오래 붙들지 않는다.
 */
class ScreenCaptureService : Service() {

    private val screenshotRepository: ScreenshotRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val commandRepository: CommandRepository by inject()
    private val notifier: ChildCommandNotifier by inject()
    private val captureStore: ChildCaptureStore by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handled = AtomicBoolean(false)

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null

    private var requestId: String = ""
    private var trigger: String = TRIGGER_MANUAL

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            teardown()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        requestId = intent?.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: TRIGGER_MANUAL

        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        ChildNotifications.ensureChannel(this)
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                ChildNotifications.CAPTURE_ID,
                ChildNotifications.foreground(
                    this,
                    getString(R.string.child_capture_notif_title),
                    getString(R.string.child_capture_notif_text),
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val granted = runCatching { manager?.getMediaProjection(resultCode, resultData) }.getOrNull()
        if (granted == null) {
            fail("projection unavailable")
            return START_NOT_STICKY
        }
        projection = granted

        val thread = HandlerThread("kidhack-capture").also { it.start() }
        worker = thread
        val handler = Handler(thread.looper)

        // 안드로이드 14+ 는 VirtualDisplay 생성 전에 콜백 등록을 요구한다.
        granted.registerCallback(projectionCallback, handler)

        val metrics = screenMetrics()
        val reader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            IMAGE_BUFFER_SIZE,
        )
        imageReader = reader

        virtualDisplay = runCatching {
            granted.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
        }.getOrNull()

        if (virtualDisplay == null) {
            fail("virtual display failed")
            return START_NOT_STICKY
        }

        reader.setOnImageAvailableListener({ available ->
            if (!handled.compareAndSet(false, true)) {
                runCatching { available.acquireLatestImage()?.close() }
                return@setOnImageAvailableListener
            }
            val bitmap = runCatching { available.acquireLatestImage()?.use { it.toBitmap() } }.getOrNull()
            if (bitmap == null) fail("empty frame") else uploadAndFinish(bitmap)
        }, handler)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun uploadAndFinish(bitmap: Bitmap) {
        scope.launch {
            val childId = sessionRepository.current()?.user?.id.orEmpty()
            val capturedMillis = System.currentTimeMillis()
            val capturedAt = Instant.ofEpochMilli(capturedMillis).toString()
            // 기기에 남긴 사본이 곧 자녀 화면의 「캡처 내역」이다(서버 목록은 부모 전용).
            // 업로드도 이 파일로 하므로 사본을 두 번 만들지 않는다. 지우지 않는다.
            val file = captureStore.save(bitmap, capturedMillis, trigger)
            val ok = runCatching {
                screenshotRepository.upload(childId, file, capturedAt, trigger)
            }.isSuccess
            bitmap.recycle()

            if (requestId.isNotBlank()) notifier.cancel(requestId)
            ackIfRemote(childId, if (ok) CommandAckStatus.DONE else CommandAckStatus.FAILED)
            stopSelf()
        }
    }

    private fun fail(reason: String) {
        scope.launch {
            val childId = sessionRepository.current()?.user?.id.orEmpty()
            ackIfRemote(childId, CommandAckStatus.FAILED, reason)
            stopSelf()
        }
    }

    /**
     * 부모가 실제로 보낸 요청일 때만 ack 한다.
     * 앱이 스스로 만든 정기 캡처(scheduled-…)는 서버에 대응하는 요청이 없다.
     */
    private suspend fun ackIfRemote(childId: String, status: String, message: String? = null) {
        if (requestId.isBlank()) return
        if (requestId.startsWith(ScreenCaptureWorker.SCHEDULED_PREFIX)) return
        runCatching { commandRepository.ack(childId, requestId, status, message) }
    }

    private fun teardown() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        runCatching { worker?.quitSafely() }
        virtualDisplay = null
        imageReader = null
        projection = null
        worker = null
    }

    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        // ImageReader 는 홀수 폭에서 stride 계산이 어긋날 수 있어 짝수로 맞춘다.
        metrics.widthPixels -= metrics.widthPixels % 2
        metrics.heightPixels -= metrics.heightPixels % 2
        return metrics
    }

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_REQUEST_ID = "requestId"
        const val EXTRA_TRIGGER = "trigger"

        const val TRIGGER_MANUAL = "MANUAL"
        const val TRIGGER_SCHEDULED = "SCHEDULED"

        private const val VIRTUAL_DISPLAY_NAME = "kidhack-screen"
        private const val IMAGE_BUFFER_SIZE = 2

        fun startCapture(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            requestId: String,
            trigger: String,
        ) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_TRIGGER, trigger)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

/** ImageReader 가 주는 RGBA_8888 평면을 Bitmap 으로. rowStride 패딩을 잘라 낸다. */
private fun Image.toBitmap(): Bitmap {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val padded = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888,
    )
    padded.copyPixelsFromBuffer(plane.buffer)
    if (rowPadding == 0) return padded
    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
    padded.recycle()
    return cropped
}
