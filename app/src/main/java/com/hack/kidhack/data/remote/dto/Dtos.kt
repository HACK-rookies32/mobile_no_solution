package com.hack.kidhack.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** Accepts JSON string or number as a string (signup `id` is numeric in v0.2). */
internal object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return ""
        return primitive.content
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/**
 * Spring STOMP(Jackson)은 LocalDateTime을 ISO 문자열이 아니라
 * `[year, month, day, hour, minute, second, nano]` 배열로 보낼 수 있다.
 */
internal object FlexibleDateTimeSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.content
            is JsonArray -> {
                val parts = element.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                when {
                    parts.size >= 6 -> "%04d-%02d-%02dT%02d:%02d:%02d".format(
                        parts[0], parts[1], parts[2], parts[3], parts[4], parts[5],
                    )
                    parts.size >= 5 -> "%02d:%02d".format(parts[3], parts[4])
                    else -> ""
                }
            }
            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class LoginRequest(val loginId: String, val password: String)

@Serializable
data class LoginResponse(
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val loginId: String? = null,
    val username: String? = null,
    val role: String? = null,
    val user: UserDto? = null,
) {
    fun resolvedAccessToken(): String? =
        accessToken?.takeIf { it.isNotBlank() } ?: token?.takeIf { it.isNotBlank() }
}

@Serializable
data class SignupRequest(
    val loginId: String,
    val username: String,
    val password: String,
    val nickname: String? = null,
    val email: String? = null,
    val phoneNumber: String,
    val birthDate: String,
    val address: String? = null,
    val profileImageUrl: String? = null,
    val role: String,
    val parentId: Long? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

/**
 * 인증코드 발송 결과. 아이디 찾기·비밀번호 재설정이 같은 형태를 쓴다.
 *
 * `code` 는 서버가 mock 메일 발송(`MockEmailSender`) 대신 응답에 그대로 실어 주는 값이라
 * 운영 화면에 노출하면 안 된다. 디버그 빌드에서만 보여 준다. (ForgotViewModel 참고)
 */
@Serializable
data class SendCodeResponse(
    val message: String? = null,
    val code: String? = null,
)

@Serializable
data class FindIdSendCodeRequest(val username: String, val email: String)

@Serializable
data class FindIdVerifyRequest(val email: String, val code: String)

@Serializable
data class FindIdVerifyResponse(val loginId: String)

@Serializable
data class ResetPasswordSendCodeRequest(val loginId: String, val email: String)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String,
)

@Serializable
data class UserDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val username: String? = null,
    val loginId: String? = null,
    val nickname: String? = null,
    val name: String? = null,
    val role: String? = null,
    val phoneNumber: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
    val parentId: Long? = null,
)

/**
 * 내 활동 내역 항목. 서버 ActivityResponse 와 1:1.
 *
 * 예전에는 게시글(PostDto)로 받았는데 서버는 활동 요약을 준다 — 게시글 본문이 아니라
 * 어떤 글에 무슨 활동(작성/댓글/좋아요)을 했는지다.
 */
@Serializable
data class ActivityDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    /** POST | COMMENT | LIKE */
    val type: String? = null,
    val postId: Long? = null,
    val preview: String? = null,
    val createdAt: String? = null,
)

/**
 * PATCH /users/me 요청. 서버 MemberUpdateRequest 와 필드가 1:1 이다.
 *
 * 예전에는 전화번호를 `phone` 으로 보냈는데 서버 키는 `phoneNumber` 라 **저장되지 않았다**
 * (200 이 오고 화면은 저장됐다고 표시했다). 실명(`username`)은 서버가 수정 대상으로 받지 않는다.
 */
@Serializable
data class UserUpdateRequest(
    val nickname: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val address: String? = null,
    val profileImageUrl: String? = null,
)

/**
 * 알림 설정. 서버는 `NotificationType` 을 키로 하는 맵을 준다.
 *
 * 예전에는 location/harmfulAccess/appUsage/notice 4개 고정 필드로 받았는데, 서버 응답에
 * 그런 키가 없어 **역직렬화 단계에서 실패**했다(설정 조회 자체가 터졌다).
 * 사용자가 끌 수 있는 유형만 내려오므로 개수는 서버 정책에 따라 달라진다 —
 * 맵으로 받고 화면이 쓰는 키만 골라 쓴다. PATCH 는 부분 갱신이라 바꿀 키만 보내면 된다.
 */
typealias NotificationSettingsDto = Map<String, Boolean>

/**
 * 페어링 코드 발급 결과.
 *
 * 서버는 남은 초가 아니라 **만료 시각**을 준다. 타임존 오프셋이 없는 LocalDateTime
 * 문자열(예: `2026-08-29T21:34:00.123`)이고 서버 시간대는 KST 다.
 * 화면이 쓰는 「남은 초」로는 매퍼에서 환산한다.
 */
@Serializable
data class PairingCodeDto(
    val code: String,
    val expiresAt: String? = null,
)

@Serializable
data class PairingConnectRequest(val code: String)

@Serializable
data class ChildDto(
    /** Backend may send numeric id; accept string or number. */
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    /** App/domain field; list API may omit this and send username instead. */
    val name: String? = null,
    val username: String? = null,
    val nickname: String? = null,
    val alias: String? = null,
    val profileImageUrl: String? = null,
    val birthDate: String? = null,
    /** 목록(ChildSummaryResponse)에만 온다. 실시간 위치 추적 on/off. */
    val realtimeEnabled: Boolean = false,
)

/**
 * 자녀 상세 (`GET /children/{childId}` → ChildDetailResponse).
 *
 * 목록(ChildSummaryResponse)과 필드가 달라 DTO 를 나눈다. 예전에는 목록 DTO 로 받아
 * loginId·email·phoneNumber·address·paired 를 전부 버렸다.
 */
@Serializable
data class ChildDetailDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    val loginId: String? = null,
    val username: String? = null,
    val nickname: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val birthDate: String? = null,
    val address: String? = null,
    val profileImageUrl: String? = null,
    /** 부모와 연결돼 있는지. */
    val paired: Boolean = false,
)

@Serializable
data class ChildUpdateRequest(
    val nickname: String? = null,
    val profileImageUrl: String? = null,
)

/** POST /location/track 요청. 명세상 좌표가 문자열이다(places는 숫자). */
@Serializable
data class LocationTrackRequest(
    val childId: Long,
    val latitude: String,
    val longitude: String,
)

/**
 * 동선 조회 응답 (`GET /location/track/{childId}`).
 *
 * 배열이 아니라 래퍼 객체이고, 항목도 세그먼트가 아니라 **단순 좌표**다.
 * 예전에는 `List<LocationTrackResponse>` 로 받아 역직렬화 단계에서 터졌다.
 * Swagger: "위·경도 포인트 리스트를 시간 **오름차순**으로 반환한다."
 */
@Serializable
data class TrackResponse(
    val points: List<TrackPointDto> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class TrackPointDto(
    val lat: Double,
    val lng: Double,
    /** 기록 시각(ISO, 오프셋 없음). */
    val at: String? = null,
)

@Serializable
data class LocationTrackResponse(
    val id: Long,
    val childId: Long,
    val latitude: String,
    val longitude: String,
    val segmentStart: String? = null,
    val segmentEnd: String? = null,
    val updatedAt: String? = null,
)

/**
 * FCM 토큰 등록·해제. 서버 DeviceTokenRequest 와 1:1.
 *
 * 예전에는 `devices/push-token` 에 `{token, platform}` 을 보냈는데 서버 경로는
 * `devices/fcm-token` 이고 키는 `fcmToken` 이다. 경로가 달라 **토큰 등록이 아예
 * 되지 않았고** 그래서 푸시가 오지 않았다.
 */
@Serializable
data class DeviceTokenRequest(val fcmToken: String)

@Serializable
data class DeviceTokenResponse(
    val memberId: Long? = null,
    val token: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class RealtimeToggleResponse(
    val childId: Long,
    val realtimeEnabled: Boolean,
)

@Serializable
data class DeviceEventRequest(
    val childId: Long,
    /** 기기 식별자. 한 자녀가 기기를 여러 대 쓸 수 있어 서버가 요구한다. */
    val deviceId: String,
    val type: String, // "POWER_OFF" | "GPS_OFF"
)

/** GET /children/{childId}/dashboard (명세 4.3) 통합 응답. */
@Serializable
data class DashboardResponse(
    val childId: Long,
    val currentLocation: LocationTrackResponse? = null,
    val realtimeEnabled: Boolean,
    /**
     * 서버는 알림을 자녀 것과 부모 것으로 **나눠서** 준다.
     * 예전에는 `recentNotifications` 하나로 받았는데 서버에 그런 키가 없어
     * 기본값 emptyList() 로 조용히 비워졌다 — 대시보드 「최근 알림」이 늘 비어 보였다.
     */
    val childNotifications: List<NotificationDto> = emptyList(),
    val parentNotifications: List<NotificationDto> = emptyList(),
    val blockRules: List<DashboardBlockRuleDto> = emptyList(),
    val places: List<PlaceResponse> = emptyList(),
)

@Serializable
data class DashboardBlockRuleDto(
    val id: Long,
    val childId: Long,
    val pattern: String,
)

/** POST /location/places 요청. `type` = GEOFENCE | DESTINATION. */
@Serializable
data class PlaceRequest(
    val childId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val type: String,
)

@Serializable
data class PlaceResponse(
    val id: Long,
    val childId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val type: String,
)

/** GET /location/events/{childId}. `eventType` = ENTER | EXIT | ARRIVED. */
@Serializable
data class LocationEventDto(
    val id: Long,
    val childId: Long,
    val placeId: Long,
    val eventType: String,
    val occurredAt: String,
)

@Serializable
data class NotificationDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val recipientId: String = "",
    val type: String = "",
    /** 푸시 알림창에 떴던 제목과 같은 값. */
    val title: String? = null,
    val message: String = "",
    val read: Boolean = false,
    val createdAt: String = "",
    /**
     * 알림의 대상 자녀. 자녀 맥락이 없는 알림(문의 답변·전체 발송)은 null 이다.
     * 백엔드 #150 에서 추가됐다.
     */
    @Serializable(with = FlexibleStringSerializer::class)
    val childId: String? = null,
)

/** docs/child-push-contract.md 4절 — 자녀 안전 사건 동기화 제안 계약. */
@Serializable
data class ChildAlertDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String = "",
    val occurredAt: String = "",
)

/**
 * 차단 규칙 (서버 `BlockRuleResponse`).
 *
 * 서버 모델은 자녀별 **차단 패턴 목록** 하나다. 예전 앱은 blacklist/whitelist 두 배열을
 * 쓰고 있었는데 **서버에 허용 목록 개념이 없다.** 규칙 삭제는 패턴이 아니라 [id] 로 한다.
 */
@Serializable
data class BlockRuleDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val childId: Long? = null,
    val pattern: String = "",
)

@Serializable
data class BlockRuleRequest(val childId: Long, val pattern: String)

/** 자녀 앱이 접속 대상을 올리면 서버가 차단 여부를 판정해 돌려준다. */
@Serializable
data class VisitRequest(val childId: Long, val target: String)

@Serializable
data class VisitResponse(
    val childId: Long? = null,
    val target: String? = null,
    val blocked: Boolean = false,
)

/**
 * 캡처 목록 항목 (서버 `ScreenshotResponse`).
 *
 * 식별자 키가 `id` 가 아니라 `screenshotId` 다. `description` 은 서버에 없다.
 */
@Serializable
data class ScreenshotDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val screenshotId: String = "",
    val imageUrl: String? = null,
    val capturedAt: String? = null,
    val trigger: String = "MANUAL",
)

/** 업로드 응답 (서버 `ScreenshotUploadResponse`). 목록 항목보다 필드가 적다. */
@Serializable
data class ScreenshotUploadDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val screenshotId: String = "",
    val imageUrl: String? = null,
)

/** 서버 `CommentResponse`. 예전에는 `id`·`author`·`body` 로 받아 전부 빈 값이었다. */
@Serializable
data class CommentDto(
    val commentId: Long,
    val memberId: Long = 0,
    val nickname: String = "",
    val content: String = "",
    val createdAt: String = "",
)

/**
 * 게시글. 목록 응답에는 `content`·`attachments`·`comments` 가 없어 기본값으로 둔다
 * (상세에만 온다).
 */
@Serializable
data class PostDto(
    val postId: Long,
    val authorId: Long = 0,
    val authorNickname: String = "",
    val title: String = "",
    val content: String = "",
    /** 목록에만 오는 본문 앞부분. 상세 응답에는 없다. */
    val contentPreview: String? = null,
    /** 목록에만 오는 첫 사진 경로. 사진이 없으면 null. */
    val thumbnail: String? = null,
    /** 첨부 공개 경로. `/api/files/community/{filename}` 형태의 상대 경로다. */
    val attachments: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val likeCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String? = null,
    val comments: List<CommentDto> = emptyList(),
)

/** 작성(POST)·수정(PATCH) 공용. 수정은 넣은 필드만 반영된다. */
@Serializable
data class PostRequest(
    val title: String,
    val content: String,
    val attachments: List<String> = emptyList(),
    val links: List<String> = emptyList(),
)

@Serializable
data class CommentRequest(val content: String)

/** 좋아요 토글 결과. 누른 상태면 취소된다. */
@Serializable
data class LikeToggleDto(val liked: Boolean = false, val likeCount: Int = 0)

/**
 * 방 목록 항목 (서버 `ChatRoomSummaryResponse`).
 *
 * 예전에는 `id`·`lastAt`·`title` 로 받았는데 서버 키는 `roomId`·`lastMessageAt` 이고
 * 제목 대신 **`counterpart*`(요청자 기준 상대방)** 를 준다. 키가 달라 값이 안 채워졌다.
 * `online` 은 서버에 없다.
 */
@Serializable
data class ChatRoomDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val roomId: String = "",
    val parentId: Long? = null,
    val childId: Long? = null,
    val counterpartId: Long? = null,
    val counterpartNickname: String? = null,
    val counterpartName: String? = null,
    val counterpartProfileImageUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int = 0,
    val createdAt: String? = null,
)

/** 방 생성 응답 (서버 `ChatRoomResponse`). 목록과 형태가 다르다. */
@Serializable
data class ChatRoomCreatedDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val parentId: Long? = null,
    val childId: Long? = null,
    val createdAt: String? = null,
)

/**
 * 메시지 커서 페이지 (서버 `ChatMessagePageResponse`).
 *
 * 다른 도메인은 전부 슬림 페이지(`content`/`page`/…)인데 **채팅만 커서**다.
 * 다음 페이지는 [oldestSentAt] 을 `before` 로 넘긴다. 최신 → 과거 순.
 */
@Serializable
data class ChatMessagePageDto(
    val messages: List<ChatMessageDto> = emptyList(),
    val hasMore: Boolean = false,
    val oldestSentAt: String? = null,
)

@Serializable
data class CreateChatRoomRequest(
    val parentId: Long,
    val childId: Long,
)

@Serializable
data class ChatMessageDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    val roomId: String,
    val fromRole: String = "",
    val text: String = "",
    val sentAt: String = "",
    val senderId: Long? = null,
    val content: String? = null,
)

@Serializable
data class CheckoutRequest(val amount: Int, val method: String, val productId: Int)

@Serializable
data class PaymentDto(
    val id: String,
    val description: String = "",
    val amount: Int,
    val dateLabel: String = "",
    val type: String = "in",
    val status: String? = null,
    val pointEarned: Int? = null,
)

@Serializable
data class PointBalanceDto(val balance: Int)

@Serializable
data class PointHistoryDto(
    val id: String,
    val description: String,
    val point: Int,
    val dateLabel: String,
)

@Serializable
data class UsePointRequest(val productId: Int, val point: Int)

@Serializable
data class AppUsageReportItemDto(
    val appName: String,
    val durationSec: Int,
    val recordedAt: String,
    /** 서버가 집계를 묶는 기준. 없으면 서버가 앱 이름으로 묶는다. */
    val packageName: String? = null,
)

@Serializable
data class AppUsageReportRequest(
    val childId: Long,
    val usages: List<AppUsageReportItemDto>,
)

@Serializable
data class AppUsageStatDto(
    val key: String,
    val durationSec: Int,
)

/** GET /monitoring/usage/{childId} 집계 응답. groupBy = APP | HOUR | DAY. */
@Serializable
data class AppUsageAggregateResponse(
    val childId: Long,
    val groupBy: String,
    val items: List<AppUsageStatDto> = emptyList(),
)

/**
 * 원격 요청 처리 결과.
 *
 * 명세 4.x 에 아직 없는 **신규 제안 계약**이다(GAP-11: 202 만 있고 페이로드 미정의).
 * 백엔드와 키 이름을 맞출 때 이 DTO 를 고치면 된다.
 *
 * 수신 DTO(`RemoteCommandDto`)는 폴링 경로와 함께 제거했다(2026-08-28).
 * 소켓 프레임은 `data/ws/ChildCommandFrame`, FCM 은 `background/push/PushMessage` 가 맡는다.
 */
@Serializable
data class RemoteCommandAckRequest(
    val status: String,
    val message: String? = null,
)

@Serializable
data class ChildPhotoDto(
    val id: String,
    val capturedAt: String,
    val imageUrl: String? = null,
)

/**
 * 루키즈페이 용돈 지갑 (신규 제안 계약, GAP-05).
 *
 * 명세의 `points` 경로는 구독 적립 포인트라 「부모 → 자녀 송금」 필드가 없다.
 * 자녀 QR 결제는 송금자·수신자가 분명해야 하므로 별도 지갑 경로를 제안한다.
 */
@Serializable
data class AllowanceDto(
    val balance: Int,
    val updatedAt: String? = null,
)

@Serializable
data class AllowanceEntryDto(
    val id: String,
    val type: String,
    val amount: Int,
    val description: String = "",
    val dateLabel: String = "",
)

@Serializable
data class AllowanceTransferRequest(
    val amount: Int,
    val memo: String? = null,
)

@Serializable
data class AllowancePaymentRequest(
    val token: String,
    val amount: Int,
    val merchant: String? = null,
)

@Serializable
data class AllowancePaymentDto(
    val id: String,
    val amount: Int,
    val balanceAfter: Int,
    val approvedAt: String,
    val merchant: String? = null,
)

/** 자녀 기기에 설치된 앱 (서버 `AppResponse`). 서버에 category 는 없다. */
@Serializable
data class InstalledAppDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val childId: Long? = null,
    val packageName: String = "",
    val appName: String = "",
)

/** 자녀 앱이 설치 목록을 올릴 때 쓰는 항목 (서버 `AppItem`). */
@Serializable
data class AppSyncItemDto(val packageName: String, val appName: String)

@Serializable
data class AppSyncRequest(val childId: Long, val apps: List<AppSyncItemDto>)

/**
  * 앱 사용시간 제한 규칙 (서버 `RuleResponse`).
  *
  * 서버 모델에는 **`blocked` 가 없다.** `enabled` + `dailyLimitMinutes` 조합이고,
  * 「차단」은 하루 한도 0분으로 표현한다. 수정·삭제는 `packageName` 이 아니라 [id] 로 한다.
  */
@Serializable
data class AppControlDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val childId: Long? = null,
    val packageName: String = "",
    val appName: String = "",
    val dailyLimitMinutes: Int? = null,
    val enabled: Boolean = true,
)

/**
 * 자녀 하루 총 스크린타임 한도.
 *
 * `dailyLimitMinutes` 가 null 이면 **제한 없음**이다. 0 은 "한도 0분"(사실상 사용 금지)이라
 * 의미가 다르다 — 둘을 구분해야 해서 nullable 이다.
 */
@Serializable
data class ScreenTimeDto(
    val childId: Long? = null,
    val dailyLimitMinutes: Int? = null,
)

@Serializable
data class ScreenTimeRequest(val dailyLimitMinutes: Int? = null)

@Serializable
data class AppControlCreateRequest(
    val childId: Long,
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int? = null,
)

@Serializable
data class AppControlUpdateRequest(
    val dailyLimitMinutes: Int? = null,
    val enabled: Boolean? = null,
)

/**
 * 문의 목록·등록 응답. **본문과 첨부는 없다** — 상세([InquiryDetailDto])에만 있다.
 */
/** 가입 전 중복 확인 결과. 확인하지 않은 항목은 null 이다(자녀는 이메일 없이 가입한다). */
@Serializable
data class AvailabilityDto(
    val loginIdAvailable: Boolean? = null,
    val emailAvailable: Boolean? = null,
)

@Serializable
data class InquiryDto(
    val id: Long,
    val memberId: Long = 0,
    val memberNickname: String = "",
    val title: String = "",
    val answered: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class InquiryDetailDto(
    val id: Long,
    val memberId: Long = 0,
    val memberNickname: String = "",
    val title: String = "",
    val content: String = "",
    /** 첨부 공개 경로. `/api/files/inquiry/{filename}` 형태의 상대 경로다. */
    val attachments: List<String> = emptyList(),
    /** 서버는 링크를 하나만 받는다. 없으면 null. */
    val linkUrl: String? = null,
    val answered: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String? = null,
    /** 관리자 답변. 아직 없으면 null. */
    val answer: InquiryAnswerDto? = null,
)

@Serializable
data class InquiryAnswerDto(
    val id: Long,
    val content: String = "",
    val createdAt: String = "",
    val updatedAt: String? = null,
)

/** 등록·수정 공용. 수정은 통째로 교체하는 PUT 이라 같은 모양이다. */
@Serializable
data class InquiryRequest(
    val title: String,
    val content: String,
    val attachments: List<String> = emptyList(),
    val linkUrl: String? = null,
)

/** 첨부 업로드 응답. 이 `url` 을 [InquiryRequest.attachments] 에 담아 보낸다. */
@Serializable
data class UploadedFileDto(val url: String)

/** 공지 목록 항목. 본문·첨부 실물은 상세에만 있고 여기엔 개수만 온다. */
@Serializable
data class NoticeDto(
    val id: Long,
    val title: String = "",
    val authorId: Long = 0,
    val attachmentCount: Int = 0,
    val linkCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String? = null,
)

@Serializable
data class NoticeDetailDto(
    val id: Long,
    val title: String = "",
    val content: String = "",
    val attachments: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val authorId: Long = 0,
    val createdAt: String = "",
    val updatedAt: String? = null,
)

@Serializable
data class ChargePrepareRequest(val amount: Int, val method: String, val cardId: String)

@Serializable
data class ChargePrepareResponse(
    val chargeId: String,
    val paymentKey: String,
    val amount: Int,
    val status: String
)

@Serializable
data class ChargeConfirmRequest(
    val chargeId: String,
    val paymentKey: String,
    val amount: Int
)

@Serializable
data class ChargeConfirmResponse(
    val chargeId: String,
    val status: String,
    val walletBalance: Int
)

@Serializable
data class ChargeCancelRequest(val reason: String)

@Serializable
data class WalletDto(
    val walletId: String,
    val balance: Int,
    val updatedAt: String
)

@Serializable
data class WalletTransactionDto(
    val txId: String,
    val type: String,
    val amount: Int,
    val balanceAfter: Int,
    val refId: String?,
    val createdAt: String
)

@Serializable
data class TransferRequest(
    val childId: String,
    val amount: Int,
    val memo: String? = null
)

@Serializable
data class TransferResponse(
    val parentBalance: Int,
    val childBalance: Int,
    val txId: String
)

@Serializable
data class CardDto(
    val cardId: String,
    val last4: String,
    val expiry: String,
    val ownerName: String
)

@Serializable
data class CardRegisterRequest(
    val cardNumber: String,
    val expiry: String,
    val cvc: String,
    val cardPassword2: String,
    val birthDate: String,
    val ownerName: String
)

/* ============================================================================
 * 11~19. 지갑·카드·QR 결제 — 서버 /api/wallet · /api/qrpay (v0.2)
 * ========================================================================== */

/** 지갑 원장 1건 (서버 TxResponse). */
@Serializable
data class TxResponseDto(
    val id: Long = 0,
    val type: String = "",
    val amount: Long = 0,
    val balanceAfter: Long = 0,
    val refUid: String? = null,
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val createdAt: String = "",
)

// --- 11. 충전/환불 ---

@Serializable
data class WalletChargePrepareRequest(
    val amount: Long,
    val otp: String? = null,
    val paymentPassword: String? = null,
    val signature: String? = null,
    val nonce: String? = null,
)

@Serializable
data class WalletChargePrepareResponse(
    val merchantUid: String,
    val amount: Long = 0,
    val pgProvider: String = "",
    val checkoutUrl: String? = null,
    val mock: Boolean = false,
)

@Serializable
data class WalletChargeApproveRequest(
    val merchantUid: String,
    val amount: Long,
    val pgTransactionId: String? = null,
)

@Serializable
data class WalletChargeApproveResponse(
    val merchantUid: String = "",
    val balance: Long = 0,
    val transaction: TxResponseDto? = null,
)

@Serializable
data class WalletRefundResponse(
    val merchantUid: String = "",
    val refundedAmount: Long = 0,
    val balance: Long = 0,
    val transaction: TxResponseDto? = null,
)

@Serializable
data class ChargeOrderResponseDto(
    val merchantUid: String = "",
    val amount: Long = 0,
    val status: String = "",
    val pgTransactionId: String? = null,
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val createdAt: String = "",
)

@Serializable
data class ChargeHistoryResponseDto(
    val merchantUid: String = "",
    val amount: Long = 0,
    val status: String = "",
    val pgTransactionId: String? = null,
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val createdAt: String = "",
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val refundedAt: String? = null,
)

// --- 12. 이체 ---

/**
 * 이체/회수 요청. childId·amount 는 필수, 전자금융 보안 필드는 nullable(서버 EfinTxGuard 가 검사).
 *
 * otp: 거래 2차 인증번호 / signature: amount:childId:nonce HMAC / nonce: 재사용 방지 일회성 값 /
 * accountHolder: 대상 계좌 소유주명. null 로 보내면 서버가 해당 검증을 건너뛰는 결함이 있다([VULN-EFIN-2] 등).
 */
@Serializable
data class WalletTransferRequest(
    val childId: Long,
    val amount: Long,
    val otp: String? = null,
    val signature: String? = null,
    val nonce: String? = null,
    val accountHolder: String? = null,
    val paymentPassword: String? = null,
)

// --- 20~22. 전자금융 거래 보안 (efin) ---

@Serializable
data class EfinOtpIssueResponse(
    val phone: String = "",
    /** [VULN-EFIN-1] 서버가 발급 코드를 응답에 그대로 실어 준다. */
    val code: String = "",
    val expiresIn: Long = 180,
)

@Serializable
data class EfinOtpVerifyRequest(val code: String)

@Serializable
data class EfinOtpVerifyResponse(val valid: Boolean = false)

/** 결제 비밀번호 최초 설정(카드 발급 흐름) — 직전 실명확인이 본인확인을 대신하므로 로그인 비번 없음. */
@Serializable
data class EfinPaymentPasswordSetupRequest(val password: String)

@Serializable
data class EfinPaymentPasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String,
    val loginPassword: String,
)

@Serializable
data class EfinPaymentPasswordVerifyRequest(val password: String)

@Serializable
data class EfinPaymentPasswordVerifyResponse(val valid: Boolean = false)

@Serializable
data class EfinPaymentPasswordStatusResponse(val exists: Boolean = false)

@Serializable
data class EfinIdentityStatusResponse(val verified: Boolean = false)

@Serializable
data class EfinIdentityVerifyRequest(
    val name: String,
    val birthDate: String,
    val idType: String,
    val idNumber: String,
    val issuedDate: String,
)

@Serializable
data class EfinIdentityVerifyResponse(
    val verified: Boolean = false,
    val name: String = "",
    val verifiedAt: String? = null,
)

@Serializable
data class WalletTransferResponse(
    val fromOwnerId: Long = 0,
    val fromOwnerName: String = "",
    val toOwnerId: Long = 0,
    val toOwnerName: String = "",
    val amount: Long = 0,
    val fromBalance: Long = 0,
    val toBalance: Long = 0,
)

// --- 13. 조회 ---

@Serializable
data class MyWalletResponse(
    val balance: Long = 0,
    val monthlyChargeTotal: Long = 0,
    val monthlySpendTotal: Long = 0,
    val content: List<TxResponseDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class ChildWalletResponse(
    val childId: Long = 0,
    val balance: Long = 0,
    val recentTransactions: List<TxResponseDto> = emptyList(),
)

// --- 14. 카드 (E2E 암호화) ---

@Serializable
data class CardPublicKeyResponse(
    val keyId: String,
    val publicKey: String,
    val keyRotationDays: Long = 0,
)

@Serializable
data class EncryptedCardRequest(
    val keyId: String,
    val encryptedKey: String,
    val iv: String,
    val ciphertext: String,
)

/** 암호화 대상 평문 payload — 서버 CardPlain 스키마와 필드명 1:1. */
@Serializable
data class CardPlainPayload(
    val cardNumber: String,
    val expiry: String,
    val cvc: String,
    val passwordFront: String,
    val birthDate: String,
    val ownerName: String,
    /** 접근매체 발급 본인확인용 결제 비밀번호(4자리). 서버는 확인만 하고 저장하지 않는다. */
    val paymentPassword: String,
    val nonce: String,
    val timestamp: Long,
)

@Serializable
data class CardResponse(
    val id: Long = 0,
    // 서버가 명시적으로 null 을 준다(옛 카드는 소유자명이 없다). non-null 이면 그 null 에서
    // 목록 파싱이 통째로 터진다($data[N].ownerName). null 허용 후 매퍼에서 빈 문자열로 내린다.
    val maskedCardNumber: String? = null,
    val ownerName: String? = null,
    val expiry: String? = null,
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val createdAt: String = "",
)

// --- 15. QR 발급/조회 ---

@Serializable
data class QrIssueResponse(
    val qrToken: String,
    val status: String = "ISSUED",
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val expiresAt: String = "",
)

@Serializable
data class QrStatusResponse(
    val qrToken: String,
    val status: String = "",
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val expiresAt: String = "",
)

// --- 16. 매장 단말 승인 ---

@Serializable
data class QrApproveRequest(val qrToken: String, val amount: Long)

@Serializable
data class QrApproveResponse(
    val qrToken: String = "",
    val status: String = "",
    val amount: Long = 0,
    val storeName: String? = null,
    val balance: Long = 0,
    val transaction: TxResponseDto? = null,
)

// --- 17. 매장 단말 등록 ---

@Serializable
data class TerminalIssueRequest(val storeName: String)

@Serializable
data class TerminalResponse(
    val id: Long = 0,
    val storeName: String = "",
    val terminalToken: String? = null,
    val status: String = "",
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val createdAt: String = "",
)

@Serializable
data class TerminalAuthResponse(
    val terminalId: Long = 0,
    val storeName: String = "",
    val status: String = "",
)

// --- 18. QR 취소 ---

@Serializable
data class QrCancelResponse(
    val qrToken: String = "",
    val status: String = "",
    val canceledAmount: Long = 0,
    val balance: Long = 0,
    val transaction: TxResponseDto? = null,
)

// --- 19. 결제 내역 ---

@Serializable
data class QrPaymentHistoryResponseDto(
    val qrToken: String = "",
    val storeName: String? = null,
    val amount: Long = 0,
    val status: String = "",
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val paidAt: String = "",
)
