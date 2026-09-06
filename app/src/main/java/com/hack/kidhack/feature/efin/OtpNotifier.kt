package com.hack.kidhack.feature.efin

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hack.kidhack.R
import com.hack.kidhack.background.alert.ServerAlertNotifications

/**
 * 거래 OTP 코드를 로컬 알림으로 띄운다 — 실제 SMS/OTP 문자가 도착하는 것처럼 보이게 한다.
 *
 * 서버는 mock 이라 실제 문자를 보내지 않고, 발급 API 응답에 코드를 실어 준다([VULN-EFIN-1]).
 * 앱은 그 코드를 받아 이 알림으로 표시한다 — FCM 서버 푸시와 동일한 알림 채널을 써서 화면상 구분되지 않는다.
 * (실서버(api) 빌드에서도 응답 코드로 동일하게 동작한다.)
 */
class OtpNotifier(private val context: Context) {

    fun notifyCode(code: String) {
        ServerAlertNotifications.ensureChannel(context)
        val notification = NotificationCompat.Builder(context, ServerAlertNotifications.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("루키즈페이 인증번호")
            .setContentText("거래 인증번호 [$code] 를 입력해 주세요. (유효시간 3분)")
            .setStyle(NotificationCompat.BigTextStyle().bigText("거래 인증번호 [$code] 를 입력해 주세요. (유효시간 3분)"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()
        // 33+ 에서 권한이 없으면 조용히 무시된다(S8=API28 은 권한 불필요).
        NotificationManagerCompat.from(context).notify(OTP_NOTI_ID, notification)
    }

    companion object {
        /** 거래 OTP 알림 id — 서버알림(7300 대역)·자녀요청(7200 대역)과 겹치지 않게 7900. */
        const val OTP_NOTI_ID = 7900
    }
}