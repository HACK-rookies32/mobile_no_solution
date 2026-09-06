package com.hack.kidhack.data.ws

import kotlinx.serialization.Serializable

/**
 * `/ws/commands` 로 오가는 프레임.
 *
 * Firebase(FCM) 대신 쓰는 푸시 채널의 페이로드다. 자녀 기기는 로그인 직후
 * 이 소켓을 상주 연결로 붙잡고 있다가 부모 요청 프레임을 즉시 받는다.
 *
 * 서버 → 자녀
 *  - `{"type":"CAPTURE_REQUEST","requestId":"req-1","childId":"child-1","requestedBy":"엄마"}`
 *  - `{"type":"CAMERA_REQUEST","requestId":"req-2","childId":"child-1","requestedBy":"엄마"}`
 *  - `{"type":"PING"}`
 *
 * 자녀 → 서버
 *  - `{"type":"HELLO","childId":"child-1"}`  (연결 직후 1회)
 *  - `{"type":"PONG"}`
 *  - `{"type":"ACK","requestId":"req-1","status":"DONE"}`
 */
@Serializable
data class ChildCommandFrame(
    val type: String,
    val requestId: String? = null,
    val childId: String? = null,
    val requestedBy: String? = null,
    val requestedAt: String? = null,
    val status: String? = null,
    val message: String? = null,
) {
    companion object {
        const val HELLO = "HELLO"
        const val PING = "PING"
        const val PONG = "PONG"
        const val ACK = "ACK"
        const val ERROR = "ERROR"
    }
}

/** `wss://…` origin 계산. BASE_URL 의 `/api/v1` 접미사를 떼고 스킴만 바꾼다. */
internal object WsUrls {
    fun of(baseUrl: String, path: String, token: String?): String {
        val origin = baseUrl.trimEnd('/')
            .removeSuffix("/api/v1")
            .replace(Regex("^https://"), "wss://")
            .replace(Regex("^http://"), "ws://")
        val raw = origin + path
        if (token.isNullOrBlank()) return raw
        val separator = if (raw.contains('?')) "&" else "?"
        // [VULN-MOBILE-NET-2] JWT 를 WebSocket URL 쿼리스트링(?token=<JWT>)에 실어 전송 →
        // 프록시·서버 액세스 로그·브라우저 히스토리에 토큰이 평문으로 남는다(헤더 방식이 안전).
        // 의도된 취약점(OWASP Mobile M5 Insecure Communication). 보존: 고치지 말 것. 명세: mobile/docs/vuln-spec-mobile.md
        return "$raw$separator" + "token=$token"
    }
}
