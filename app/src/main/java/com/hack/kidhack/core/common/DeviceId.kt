package com.hack.kidhack.core.common

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * 이 기기를 가리키는 식별자.
 *
 * 서버가 전원/GPS 이벤트를 기기 단위로 구분한다(`DeviceEventRequest.deviceId`).
 * 한 자녀가 기기를 여러 대 쓸 수 있어서 필요하다.
 *
 * `ANDROID_ID` 는 앱·사용자·기기 조합마다 다르고 재설치해도 유지된다(공장 초기화 시 바뀐다).
 * 값을 못 읽는 드문 경우에는 서버 요청이 통째로 실패하지 않도록 고정 문자열로 떨어뜨린다 —
 * 기기 구분은 못 하지만 이벤트 자체는 전달되는 편이 낫다.
 */
object DeviceId {

    private const val UNKNOWN = "unknown-device"

    @Volatile
    private var cached: String? = null

    @SuppressLint("HardwareIds")
    fun get(context: Context): String = cached ?: synchronized(this) {
        cached ?: run {
            val id = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN
            cached = id
            id
        }
    }
}
