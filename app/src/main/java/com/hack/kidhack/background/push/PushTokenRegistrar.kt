package com.hack.kidhack.background.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.hack.kidhack.data.local.PushTokenStore
import com.hack.kidhack.domain.repository.DeviceRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * FCM 토큰을 서버(`POST/DELETE /api/devices/fcm-token {fcmToken}`)에 등록·해제하는 단일 창구.
 *
 * 토큰은 두 계기로 바뀐다 — FCM 이 새 토큰을 발급할 때(`onNewToken`), 그리고 로그인해서
 * 「이 계정 = 이 기기」가 정해질 때. 어느 쪽이 먼저 와도 되도록 마지막 토큰을 [PushTokenStore]
 * 에 남겨 두고, 로그인 시 그 토큰을 다시 올린다.
 *
 * `onNewToken` 은 앱 설치 후 첫 토큰 발급 때만 오는 경우가 많아, 이미 로그인된 기기(앱 시작 시
 * 세션 복원)에서는 [onSignedIn] 이 `FirebaseMessaging.token` 으로 직접 받아 온다.
 * `google-services.json` 이 없는 빌드에서는 Firebase 가 초기화되지 않아 예외가 나는데,
 * 전부 삼켜서 앱 동작에는 영향을 주지 않는다(그동안은 폴링 경로가 알림을 나른다).
 */
class PushTokenRegistrar(
    private val sessionRepository: SessionRepository,
    private val deviceRepository: DeviceRepository,
    private val tokenStore: PushTokenStore,
) {
    /** `onNewToken` 콜백에서 호출. 로그인 상태면 곧바로 서버에 올린다. */
    suspend fun onTokenRefreshed(token: String) {
        tokenStore.save(token)
        if (sessionRepository.current() != null) {
            runCatching { deviceRepository.registerPushToken(token) }
                .onFailure { Log.w(TAG, "register failed: ${it.message}") }
        }
    }

    /** 로그인 직후(및 앱 시작 시 세션 복원) 호출. 마지막으로 받은 토큰을 이 계정에 묶는다. */
    suspend fun onSignedIn() {
        val token = tokenStore.current() ?: fetchTokenFromFirebase()?.also { tokenStore.save(it) } ?: return
        runCatching { deviceRepository.registerPushToken(token) }
            .onSuccess { Log.d(TAG, "token registered") }
            .onFailure { Log.w(TAG, "register failed: ${it.message}") }
    }

    /**
     * 로그아웃 시 호출. 이 기기로 더 이상 이 계정 알림이 가지 않게 서버에서 토큰을 지운다.
     * 저장된 토큰 자체는 남긴다 — FCM 토큰은 계정이 아니라 기기에 속하기 때문이다.
     */
    suspend fun onSignedOut() {
        val token = tokenStore.current() ?: return
        runCatching { deviceRepository.unregisterPushToken(token) }
    }

    /** Firebase 에 현재 토큰을 물어본다. SDK 미초기화·GMS 없음 등은 null. */
    private suspend fun fetchTokenFromFirebase(): String? = runCatching {
        suspendCancellableCoroutine<String?> { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it?.takeIf { t -> t.isNotBlank() }) }
                .addOnFailureListener { cont.resumeWithException(it) }
                .addOnCanceledListener { cont.resume(null) }
        }
    }.onFailure { Log.w(TAG, "FCM token unavailable: ${it.message}") }.getOrNull()

    private companion object {
        const val TAG = "KidhackFcm"
    }
}
