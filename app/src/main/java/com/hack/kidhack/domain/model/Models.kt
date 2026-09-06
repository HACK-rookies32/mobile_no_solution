package com.hack.kidhack.domain.model

enum class Role { PARENT, CHILD }

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
)

/**
 * 인증코드 발송 결과(아이디 찾기 / 비밀번호 재설정 공용).
 *
 * 백엔드가 실제 메일을 보내지 않고(`MockEmailSender`) 6자리 코드를 응답에 그대로 실어 준다.
 * 그래서 [code] 가 채워져 오지만, 운영 화면에 띄우면 인증이 무력화된다 — 디버그 빌드에서만 쓴다.
 */
data class SentCode(
    val message: String?,
    val code: String?,
)

data class User(
    val id: String,
    val loginId: String,
    val name: String,
    val role: Role,
    val nickname: String? = null,
    val phone: String = "",
    val email: String? = null,
    val profileImageUrl: String? = null,
    /** 자녀 계정만. 연결된 부모 member id. */
    val parentId: Long? = null,
)

data class ChildProfile(
    val id: String,
    val name: String,
    val alias: String? = null,
    val birthDate: String? = null,
    val profileImageUrl: String? = null,
    /** 실시간 위치 추적 on/off. 목록 조회에만 담겨 온다. */
    val realtimeEnabled: Boolean = false,
    /** 상세 조회에만 담겨 온다. 부모와 연결돼 있는지. */
    val paired: Boolean = false,
    val loginId: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
)

data class LocationPoint(
    val lat: String,
    val lng: String,
    val recordedAt: String,
    val label: String? = null,
    val segmentStart: String? = null,
    val segmentEnd: String? = null,
    val updatedAt: String? = null,
)

/** 지오펜스(진입/이탈) / 목적지(도착) 구분. */
enum class PlaceType { GEOFENCE, DESTINATION }

data class Geofence(
    val id: String,
    val name: String,
    val lat: String,
    val lng: String,
    val radius: Int,
    val notifyOnArrival: Boolean,
    val type: PlaceType = PlaceType.GEOFENCE,
)

enum class LocationEventType { ENTER, EXIT, ARRIVED }

data class LocationEvent(
    val id: String,
    val placeId: String,
    val eventType: LocationEventType,
    val occurredAt: String,
)

data class AppNotification(
    val id: String,
    val type: String,
    /** 푸시 알림창에 떴던 제목. 서버가 안 주면 null 이라 화면이 유형 라벨로 대신한다. */
    val title: String? = null,
    val message: String,
    val createdAt: String,
    val read: Boolean,
    /**
     * 어느 자녀의 알림인지. 자녀를 여러 명 둔 보호자가 대시보드에서 한 명을 고르면
     * 그 자녀의 알림만 보여 주는 데 쓴다.
     *
     * null 이면 「어느 자녀인지 알 수 없음」이다(서버가 알려 주지 않은 경우).
     * 그런 알림은 숨기지 않고 모든 자녀에게 공통으로 보여 준다 — 감춰서 놓치는 것보다 낫다.
     */
    val childId: String? = null,
)

/**
 * 화면·푸시에 쓸 제목.
 *
 * 서버가 `title` 을 주기 시작했으므로 그걸 우선 쓰고(백엔드 #150 이후), 없으면 예전처럼
 * message 첫 줄을 제목으로 삼는다. 로컬 보관함에 남아 있는 옛 알림이 그 경우다.
 */
val AppNotification.displayTitle: String
    get() = title?.takeIf { it.isNotBlank() }
        ?: message.lineSequence().first().ifBlank { message }

/** 제목을 뺀 본문. 서버 title 이 있으면 message 가 통째로 본문이다. */
val AppNotification.body: String
    get() = if (!title.isNullOrBlank()) message
    else message.lineSequence().drop(1).joinToString(separator = System.lineSeparator())

/** GET /children/{childId}/dashboard 통합 조회 결과. */
data class ChildDashboard(
    val childId: String,
    val currentLocation: LocationPoint?,
    val realtimeEnabled: Boolean,
    /** 자녀 알림과 부모 알림을 시간순으로 합친 목록. 서버는 둘을 나눠서 준다. */
    val recentNotifications: List<AppNotification>,
    val blockRulePatterns: List<String>,
    val places: List<Geofence>,
)

data class NotificationSettings(
    val location: Boolean,
    val harmfulAccess: Boolean,
    val appUsage: Boolean,
    val notice: Boolean,
)

/** 자녀별 차단 규칙. 서버에 허용 목록(whitelist) 개념은 없다. */
data class BlocklistItem(
    /** 규칙 id. 삭제할 때 이 값을 쓴다(패턴이 아니라). */
    val id: String,
    val pattern: String,
)

data class Screenshot(
    val id: String,
    val capturedAt: String,
    val trigger: String,
    val imageUrl: String? = null,
)

/** 커뮤니티에서 내가 한 활동(글 작성 / 댓글 / 좋아요). GET /users/me/activities */
data class Activity(
    val id: String,
    val type: ActivityType,
    val postId: Long?,
    val preview: String,
    val createdAtLabel: String,
)

enum class ActivityType { POST, COMMENT, LIKE, UNKNOWN }

/**
 * 커뮤니티 게시글.
 *
 * 목록 응답에는 본문·첨부·댓글이 없다(상세에만 온다). `liked` 는 서버 목록·상세에
 * 아예 없고 좋아요 토글 응답에만 있어서, 이 화면에서 누른 뒤에만 채워진다.
 */
data class Post(
    val id: String,
    val authorId: String = "",
    val title: String,
    val author: String,
    val content: String = "",
    /** 목록에서 한 줄로 보여 줄 본문 앞부분. 상세에서는 비어 있다. */
    val preview: String = "",
    /** 목록 오른쪽에 붙는 첫 사진. 사진이 없으면 null. */
    val thumbnail: String? = null,
    val likeCount: Int = 0,
    val createdAtLabel: String = "",
    val liked: Boolean = false,
    /** 첨부 공개 경로. 화면에서 절대 URL 로 바꿔 연다. */
    val attachments: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val comments: List<Comment> = emptyList(),
)

data class Comment(
    val id: String,
    /** 작성자 회원 id. 내 댓글일 때만 삭제 버튼을 보인다(남의 것은 서버가 403). */
    val memberId: String = "",
    val author: String,
    val text: String,
    val createdAt: String,
)

/**
 * 가입 전 중복 확인 결과.
 *
 * null 은 "확인하지 않았다"는 뜻이다 — 빈 값은 물어보지 않는다(자녀는 이메일이 선택).
 */
data class Availability(
    val loginIdAvailable: Boolean? = null,
    val emailAvailable: Boolean? = null,
)

/** 좋아요 토글 결과. 서버가 준 값을 그대로 쓴다. */
data class LikeResult(val liked: Boolean, val likeCount: Int)

/**
 * 문의 목록 항목. 서버 목록 응답에는 **본문·첨부가 없다** — [InquiryDetail] 로 따로 받는다.
 */
data class Inquiry(
    val id: String,
    val title: String,
    /** 답변 완료 여부. 답변이 달리면 수정·삭제가 막힌다(서버 400). */
    val answered: Boolean,
    val createdAt: String,
    val authorNickname: String = "",
)

data class InquiryDetail(
    val id: String,
    val title: String,
    val content: String,
    /** 첨부 공개 경로. 화면에서 절대 URL 로 바꿔 연다. */
    val attachments: List<String> = emptyList(),
    /** 서버가 링크를 하나만 받는다. */
    val linkUrl: String? = null,
    val answered: Boolean = false,
    val createdAt: String = "",
    val answer: InquiryAnswer? = null,
)

data class InquiryAnswer(
    val id: String,
    val content: String,
    val createdAt: String,
)

/** 공지 목록 항목. 본문은 [NoticeDetail] 에 있고 여기엔 첨부·링크 개수만 온다. */
data class Notice(
    val id: String,
    val title: String,
    val createdAt: String,
    val attachmentCount: Int = 0,
    val linkCount: Int = 0,
)

data class NoticeDetail(
    val id: String,
    val title: String,
    val content: String,
    val attachments: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val createdAt: String = "",
)

/**
 * 페이지 단위 조회 결과.
 *
 * 목록이 길어질 수 있는 공지·문의가 쓴다. 화면은 [items] 를 이어 붙이고
 * [hasMore] 로 「더 보기」를 보일지 정한다.
 */
data class Paged<T>(
    val items: List<T>,
    val page: Int = 0,
    val totalPages: Int = 1,
) {
    val hasMore: Boolean get() = page + 1 < totalPages
}

data class ChatRoom(
    val id: String,
    /** 요청자 기준 상대방 이름. 부모가 보면 자녀, 자녀가 보면 부모다. */
    val title: String,
    val lastMessage: String,
    val lastAt: String,
    /** 안 읽은 메시지 수. 목록 배지에 쓴다. */
    val unreadCount: Int = 0,
    val counterpartProfileImageUrl: String? = null,
    /** 서버 방 소유자. 계정 전환 시 잘못된 room 재사용 방지용. */
    val parentId: Long? = null,
    val childId: Long? = null,
)

data class ChatMessage(
    val id: String,
    val roomId: String,
    val fromRole: Role,
    val text: String,
    val sentAt: String,
)

/**
 * 메시지 한 장.
 *
 * <p>채팅만 커서 페이징이다. 다음 장을 받으려면 [oldestSentAt] 을 `before` 로 넘긴다.
 * 서버가 이미 이렇게 내려 주는데 앱이 [messages] 만 꺼내 쓰고 있어, 최근 한 장 뒤로는
 * 볼 방법이 없었다.
 */
data class ChatMessagePage(
    /** 오래된 → 최신 순. 화면이 아래를 최신으로 그린다. */
    val messages: List<ChatMessage>,
    val hasMore: Boolean,
    /** 이 장에서 가장 오래된 메시지의 시각. 다음 장의 `before` 가 된다. */
    val oldestSentAt: String?,
)

data class Payment(
    val id: String,
    val description: String,
    val amount: Int,
    val dateLabel: String,
    val type: String,
)

data class PointBalance(
    val balance: Int,
)

data class PointHistory(
    val id: String,
    val description: String,
    val point: Int,
    val dateLabel: String,
)

data class AppUsage(
    val appName: String,
    val durationSec: Int,
    val category: String? = null,
    /**
     * 앱을 식별하는 패키지명. 서버가 이 값으로 집계를 묶는다.
     *
     * <p>앱 이름은 기기 언어에 따라 달라져서(「카메라」/「Camera」) 같은 앱이 따로 잡힌다.
     * 기기에서 실측하지 못한 대체값에는 없으므로 null 을 허용한다.
     */
    val packageName: String? = null,
)

/** 서버 GET /monitoring/usage 집계 결과. groupBy에 따라 key의 의미가 달라진다
 *  (APP=앱 이름, HOUR="00"~"23", DAY="yyyy-MM-dd"). */
data class AppUsageStat(
    val key: String,
    val durationSec: Int,
)

/**
 * 자녀 기기에서 감지된 안전 사건.
 *
 * 자녀 홈 「최근 알림」([com.hack.kidhack.data.local.ChildAlertStore])과 같은 데이터를
 * 서버로 나르는 단위다. id 는 기기가 만든 고유값이라 재업로드·재조회에도 중복되지 않는다.
 */
data class ChildAlertEvent(
    val id: String,
    val type: String,
    val title: String,
    val body: String = "",
    val occurredAt: String = "",
)

/**
 * 자녀 기기 사건 타입 → 보호자 알림 타입.
 *
 * 보호자 화면의 아이콘 매핑과 수신 설정(위치/유해/앱사용) 필터가 이 값 기준으로 돈다.
 * null 이면 안전 사건이 아니라는 뜻이다(캡처 요청 등) — 보호자에게 나르지 않는다.
 */
fun childAlertTypeToParent(type: String): String? = when (type.trim().uppercase()) {
    "HARMFUL_BLOCK" -> "HARMFUL"
    "GEOFENCE_EXIT" -> "LOCATION"
    "GEOFENCE_ENTER" -> "ARRIVAL"
    "SCREEN_TIME_LIMIT" -> "APP_USAGE"
    // 앱별 차단도 서버에는 「앱 사용」으로 간다. 서버에 따로 종류가 없다.
    "APP_BLOCK" -> "APP_USAGE"
    "GPS_OFF" -> "GPS_OFF"
    else -> null
}

data class PairingCode(
    val pairingCode: String,
    val expiresIn: Int,
)

/**
 * 앱 사용시간 제한 규칙.
 *
 * 서버에 「차단」 필드가 없다 — 하루 한도 0분이 곧 차단이다([blocked]).
 * 수정·삭제는 [ruleId] 로 한다(패키지명이 아니라).
 */
data class AppControlItem(
    val ruleId: String,
    val packageName: String,
    val name: String,
    val dailyLimitMinutes: Int? = null,
    val enabled: Boolean = true,
) {
    val blocked: Boolean get() = enabled && dailyLimitMinutes == 0

    /** 목록에 보여 줄 한도 표기. */
    val limit: String get() = when {
        !enabled -> "미적용"
        dailyLimitMinutes == null -> "무제한"
        dailyLimitMinutes == 0 -> "차단"
        else -> "하루 ${dailyLimitMinutes}분"
    }
}

data class InstalledApp(
    val packageName: String,
    val appName: String,
)

data class Wallet(
    val walletId: String,
    val balance: Int,
    val updatedAt: String,
    /**
     * 이번 달 충전 합계(원). 내 지갑 조회에만 담겨 온다 — 자녀 지갑 응답에는 없어 0 이다.
     * 잔액만 보여 주면 "이번 달에 얼마나 넣고 얼마나 썼나"를 원장을 세어 봐야 알 수 있다.
     */
    val monthlyChargeTotal: Int = 0,
    /** 이번 달 결제 합계(원, QR 매장결제). [monthlyChargeTotal] 과 같은 제약. */
    val monthlySpendTotal: Int = 0,
)

data class WalletTransaction(
    val txId: String,
    val type: String,
    val amount: Int,
    val balanceAfter: Int,
    val refId: String?,
    val createdAt: String
)

data class Charge(
    val chargeId: String,
    val amount: Int,
    val status: String,
    val paymentKey: String?,
    val method: String
)

data class Card(
    val cardId: String,
    val last4: String,
    val expiry: String,
    val ownerName: String
)

data class TransferResult(
    val parentBalance: Int,
    val childBalance: Int,
    val txId: String
)

/** 충전 통합 내역 1건 (주문 + 환불시각). 태그 11 charge-history. */
data class ChargeHistoryItem(
    val merchantUid: String,
    val amount: Int,
    /** READY / PAID / FAILED / REFUNDED / CANCELLED / EXPIRED */
    val status: String,
    val createdAtLabel: String,
    val refundedAtLabel: String? = null,
)

/**
 * 서버가 발급한 결제 QR 티켓(자녀, 태그 15).
 *
 * 옛 [PayQrToken] 과 달리 토큰을 클라이언트가 만들지 않고 서버 `POST /qrpay/qr` 로 받는다.
 * **금액은 여기 없다** — 매장 단말이 승인(태그 16)할 때 확정된다.
 */
data class QrTicket(
    val qrToken: String,
    /** ISSUED / PAID / EXPIRED / CANCELLED */
    val status: String,
    val expiresAtMillis: Long,
)

/** QR 매장결제 내역 1건 (태그 19). */
data class QrPaymentHistoryItem(
    val qrToken: String,
    val storeName: String,
    val amount: Int,
    /** PAID / CANCELLED */
    val status: String,
    val paidAtLabel: String,
)

/** 매장 단말 (태그 16·17) — 회원 앱과 분리된 테스트/시연 전용. */
data class StoreTerminalInfo(
    val id: Long,
    val storeName: String,
    val terminalToken: String,
    val status: String,
)

/** 부모가 보낸 원격 요청 종류. Firebase 대신 WebSocket/폴링으로 전달된다. */
enum class RemoteCommandType {
    SCREEN_CAPTURE,
    CAMERA_SHOT,

    /**
     * 사용량을 지금 서버로 올리라는 요청.
     *
     * <p>다른 명령과 달리 자녀에게 알리지 않는다. 부모가 사용 리포트를 새로고침할
     * 때마다 나가는 요청이라, 알림을 띄우면 자녀 폰이 계속 울린다.
     */
    USAGE_REPORT,

    UNKNOWN,
}

data class RemoteCommand(
    val requestId: String,
    val type: RemoteCommandType,
    val childId: String,
    val requestedBy: String? = null,
    val requestedAt: String? = null,
)

/** 요청 처리 결과를 서버에 되돌려 줄 때 쓰는 상태값. */
object CommandAckStatus {
    const val RECEIVED = "RECEIVED"
    const val DONE = "DONE"
    const val DENIED = "DENIED"
    const val FAILED = "FAILED"
}

data class ChildPhoto(
    val id: String,
    val capturedAt: String,
    /** 서버 URL 또는 기기에 저장된 파일 경로. 갤러리는 이 값으로 이미지를 그린다. */
    val imageUrl: String? = null,
    /** 부모 요청으로 찍은 사진이면 그 요청 id. 자녀가 스스로 찍었으면 null. */
    val requestId: String? = null,
    val requestedBy: String? = null,
)

/** 자녀 모드 루키즈페이 QR 결제 토큰. */
data class PayQrToken(
    val token: String,
    val payload: String,
    val amount: Int,
    val expiresAtMillis: Long,
)

/**
 * 루키즈페이 용돈 지갑.
 *
 * 부모가 [AllowanceType.SEND] 로 채우고 [AllowanceType.WITHDRAW] 로 회수한다.
 * 자녀는 이 잔액 한도 안에서만 [AllowanceType.PAYMENT] 를 만들 수 있다.
 */
data class Allowance(
    val balance: Int,
    val updatedAt: String? = null,
)

enum class AllowanceType { SEND, WITHDRAW, PAYMENT }

data class AllowanceEntry(
    val id: String,
    val type: AllowanceType,
    /** 부호 있는 금액. 받으면 +, 회수/결제면 -. */
    val amount: Int,
    val description: String,
    val dateLabel: String,
)

data class AllowancePayment(
    val id: String,
    val amount: Int,
    val balanceAfter: Int,
    val approvedAt: String,
    val merchant: String? = null,
)
