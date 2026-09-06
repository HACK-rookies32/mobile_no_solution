package com.hack.kidhack.background.push

/**
 * FCM 데이터 메시지를 앱 내부 표현으로 옮긴 것.
 *
 * `FirebaseMessagingService` 는 `RemoteMessage.data`(Map<String,String>)만 이 클래스로 바꿔
 * [PushMessageRouter] 에 넘긴다. 그래서 라우팅 로직이 Firebase SDK 에 묶이지 않고,
 * SDK 없이도 단위 테스트할 수 있다.
 *
 * 서버는 **data 전용** 메시지를 보내야 한다(notification 필드 없이). 그래야 앱이 포그라운드든
 * 백그라운드든 항상 [PushMessageRouter] 를 거쳐 같은 방식으로 처리되고, 자녀 명령처럼
 * 시스템이 마음대로 트레이에 띄우면 안 되는 것도 우리가 통제할 수 있다.
 */
data class PushMessage(
    val kind: Kind,
    val type: String,
    val title: String,
    val body: String,
    val childId: String?,
    /** 보호자 알림 합침 키 / 자녀 명령 식별자. */
    val id: String,
    val requestedBy: String?,
) {
    enum class Kind { PARENT_ALERT, CHILD_COMMAND, UNKNOWN }

    companion object {
        // 서버와 합의한 data 페이로드 키. 바꾸려면 여기와 백엔드만 고치면 된다.
        const val KEY_KIND = "kind"
        const val KEY_TYPE = "type"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_CHILD_ID = "childId"
        const val KEY_ID = "id"
        const val KEY_REQUEST_ID = "requestId"
        /** 서버 `NotificationService.toPushMessage` 가 싣는 알림 행 id — 합침 키이자 자녀 명령 requestId. */
        const val KEY_NOTIFICATION_ID = "notificationId"
        const val KEY_REQUESTED_BY = "requestedBy"

        const val KIND_PARENT_ALERT = "PARENT_ALERT"
        const val KIND_CHILD_COMMAND = "CHILD_COMMAND"

        /**
         * `kind` 없이 `type` 만 왔을 때 자녀 명령으로 볼 유형. 서버 NotificationType 의
         * `SCREENSHOT_REQUESTED` 와 예전 소켓 계약의 동의어(Mappers.toRemoteCommandType 와 같은 집합).
         */
        private val COMMAND_TYPES = setOf(
            "SCREENSHOT_REQUESTED", "SCREEN_CAPTURE", "CAPTURE", "CAPTURE_REQUEST", "SCREENSHOT",
            "CAMERA_SHOT", "CAMERA", "CAMERA_REQUEST", "PHOTO",
            "USAGE_REPORT_REQUESTED", "USAGE_REQUEST", "USAGE_REPORT",
        )

        /**
         * FCM data 맵을 파싱한다. 알 수 없는 형식은 [Kind.UNKNOWN] 으로 떨어뜨려
         * 라우터가 조용히 무시하게 한다(잘못된 푸시로 앱이 죽지 않도록).
         *
         * 실제 서버(`FirebasePushSender`)는 `kind` 를 보내지 않고 `data{type, notificationId, childId?, title, body}`
         * 만 보낸다. 그래서 `kind` 가 없으면 `type` 으로 추론한다 — 명령 유형이면 [Kind.CHILD_COMMAND],
         * 그 밖의 알림 유형이면 [Kind.PARENT_ALERT](수신 기기 역할에 따른 분기는 라우터가 한다).
         */
        fun fromData(data: Map<String, String>): PushMessage {
            val type = data[KEY_TYPE].orEmpty().trim()
            val kind = when (data[KEY_KIND]?.trim()?.uppercase()) {
                KIND_PARENT_ALERT -> Kind.PARENT_ALERT
                KIND_CHILD_COMMAND -> Kind.CHILD_COMMAND
                null, "" -> when {
                    type.uppercase() in COMMAND_TYPES -> Kind.CHILD_COMMAND
                    type.isNotBlank() -> Kind.PARENT_ALERT
                    else -> Kind.UNKNOWN
                }
                else -> Kind.UNKNOWN
            }
            // 자녀 명령은 requestId 가 식별자이고, 보호자 알림은 id 가 합침 키다. 서버 푸시는 notificationId 만 준다.
            val id = data[KEY_ID]?.takeIf { it.isNotBlank() }
                ?: data[KEY_REQUEST_ID]?.takeIf { it.isNotBlank() }
                ?: data[KEY_NOTIFICATION_ID]?.takeIf { it.isNotBlank() }
                ?: ""
            return PushMessage(
                kind = kind,
                type = type,
                title = data[KEY_TITLE].orEmpty(),
                body = data[KEY_BODY].orEmpty(),
                childId = data[KEY_CHILD_ID]?.takeIf { it.isNotBlank() },
                id = id,
                requestedBy = data[KEY_REQUESTED_BY]?.takeIf { it.isNotBlank() },
            )
        }
    }
}
