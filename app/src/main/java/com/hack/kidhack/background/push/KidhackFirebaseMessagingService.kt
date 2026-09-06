package com.hack.kidhack.background.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/**
 * FCM 수신 진입점. Firebase SDK 에 묶인 코드는 이 파일뿐이다 — 페이로드는 곧바로
 * [PushMessage.fromData] 로 바꿔 [PushMessageRouter] 에 넘긴다.
 *
 * 서버(`FirebasePushSender`)는 **data 전용** 메시지를 보낸다:
 * `data{type, notificationId, childId?, title, body}`. 그래서 앱이 포그라운드든 백그라운드든
 * 항상 [onMessageReceived] 가 불리고, 화면 캡처 요청(`type=SCREENSHOT_REQUESTED`)처럼 앱이
 * 직접 처리해야 하는 제어 신호도 여기로 온다.
 *
 * 매니페스트: `<service … exported="false"><intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT"/>`.
 */
class KidhackFirebaseMessagingService : FirebaseMessagingService() {

    private val router: PushMessageRouter by inject()
    private val registrar: PushTokenRegistrar by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Log.d(TAG, "onMessageReceived type=${data["type"]} id=${data["notificationId"] ?: data["id"]}")
        // FirebaseMessagingService 는 이 콜백이 끝나면 프로세스를 내려도 되는 것으로 본다(약 10초).
        // 라우팅은 짧은 로컬 작업 + REST 1~2건이라 여기서 동기로 끝낸다.
        runCatching { runBlocking { router.route(PushMessage.fromData(data)) } }
            .onFailure { Log.w(TAG, "route failed: ${it.message}") }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "onNewToken")
        runCatching { runBlocking { registrar.onTokenRefreshed(token) } }
            .onFailure { Log.w(TAG, "token refresh failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "KidhackFcm"
    }
}
