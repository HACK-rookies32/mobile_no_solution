package com.hack.kidhack.data.repository

import com.hack.kidhack.data.remote.dto.ActivityDto
import com.hack.kidhack.data.remote.dto.AllowanceDto
import com.hack.kidhack.data.remote.dto.AllowanceEntryDto
import com.hack.kidhack.data.remote.dto.AllowancePaymentDto
import com.hack.kidhack.data.remote.dto.AppControlDto
import com.hack.kidhack.data.remote.dto.AppUsageStatDto
import com.hack.kidhack.data.remote.dto.CardDto
import com.hack.kidhack.data.remote.dto.ChargePrepareResponse
import com.hack.kidhack.data.remote.dto.ChatMessageDto
import com.hack.kidhack.data.remote.dto.ChatRoomCreatedDto
import com.hack.kidhack.data.remote.dto.ChatRoomDto
import com.hack.kidhack.data.remote.dto.BlockRuleDto
import com.hack.kidhack.data.remote.dto.ChildDetailDto
import com.hack.kidhack.data.remote.dto.ChildDto
import com.hack.kidhack.data.remote.dto.ChildPhotoDto
import com.hack.kidhack.data.remote.dto.CommentDto
import com.hack.kidhack.data.remote.dto.InquiryAnswerDto
import com.hack.kidhack.data.remote.dto.InquiryDetailDto
import com.hack.kidhack.data.remote.dto.LikeToggleDto
import com.hack.kidhack.data.remote.dto.InquiryDto
import com.hack.kidhack.data.remote.dto.NoticeDetailDto
import com.hack.kidhack.data.remote.dto.NoticeDto
import com.hack.kidhack.data.remote.dto.InstalledAppDto
import com.hack.kidhack.data.remote.dto.LocationEventDto
import com.hack.kidhack.data.remote.dto.LocationTrackResponse
import com.hack.kidhack.data.remote.dto.TrackPointDto
import com.hack.kidhack.data.remote.dto.DashboardResponse
import com.hack.kidhack.data.remote.dto.ChildAlertDto
import com.hack.kidhack.data.remote.dto.NotificationDto
import com.hack.kidhack.data.remote.dto.NotificationSettingsDto
import com.hack.kidhack.data.remote.dto.PairingCodeDto
import com.hack.kidhack.data.remote.dto.PaymentDto
import com.hack.kidhack.data.remote.dto.PlaceResponse
import com.hack.kidhack.data.remote.dto.PointBalanceDto
import com.hack.kidhack.data.remote.dto.PointHistoryDto
import com.hack.kidhack.data.remote.dto.PostDto
import com.hack.kidhack.data.remote.dto.ScreenshotDto
import com.hack.kidhack.data.remote.dto.ScreenshotUploadDto
import com.hack.kidhack.data.remote.dto.LoginResponse
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.dto.UserDto
import com.hack.kidhack.domain.model.Activity
import com.hack.kidhack.domain.model.ActivityType
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AllowanceType
import com.hack.kidhack.data.remote.dto.WalletDto
import com.hack.kidhack.data.remote.dto.WalletTransactionDto
import com.hack.kidhack.data.remote.dto.TxResponseDto
import com.hack.kidhack.data.remote.dto.MyWalletResponse
import com.hack.kidhack.data.remote.dto.ChildWalletResponse
import com.hack.kidhack.data.remote.dto.WalletChargePrepareResponse
import com.hack.kidhack.data.remote.dto.ChargeHistoryResponseDto
import com.hack.kidhack.data.remote.dto.CardResponse
import com.hack.kidhack.data.remote.dto.QrIssueResponse
import com.hack.kidhack.data.remote.dto.QrStatusResponse
import com.hack.kidhack.data.remote.dto.QrPaymentHistoryResponseDto
import com.hack.kidhack.data.remote.dto.TerminalResponse
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.ChildDashboard
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.ChargeHistoryItem
import com.hack.kidhack.domain.model.QrTicket
import com.hack.kidhack.domain.model.QrPaymentHistoryItem
import com.hack.kidhack.domain.model.StoreTerminalInfo
import com.hack.kidhack.domain.model.Charge
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.InstalledApp
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryAnswer
import com.hack.kidhack.domain.model.InquiryDetail
import com.hack.kidhack.domain.model.LikeResult
import com.hack.kidhack.domain.model.Notice
import com.hack.kidhack.domain.model.NoticeDetail
import com.hack.kidhack.domain.model.LocationEvent
import com.hack.kidhack.domain.model.LocationEventType
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PlaceType
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.TransferResult
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import java.time.Duration
import java.time.LocalDateTime

fun UserDto.toDomain(): User {
    val resolvedLogin = loginId?.takeIf { it.isNotBlank() }.orEmpty()
    val resolvedName = username?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: resolvedLogin
    return User(
        id = id,
        loginId = resolvedLogin,
        name = resolvedName,
        role = role.toRoleOrNull() ?: Role.PARENT,
        nickname = nickname,
        phone = phoneNumber?.takeIf { it.isNotBlank() } ?: phone.orEmpty(),
        email = email,
        profileImageUrl = profileImageUrl,
        parentId = parentId,
    )
}

/**
 * 서버가 role 을 어떤 표기로 주든 받아들인다.
 *
 * `Role.valueOf` 만 쓰면 스프링 시큐리티의 `ROLE_CHILD` 나 소문자 `child` 가 예외로 떨어지고,
 * 그때마다 조용히 PARENT 로 처리돼 **자녀 계정이 부모로 로그인되는** 문제가 생겼다.
 * 판별할 수 없으면 마음대로 정하지 말고 null 을 돌려준다. 무엇으로 대체할지는 호출자가 정한다.
 */
fun String?.toRoleOrNull(): Role? {
    val raw = this?.trim()?.uppercase()?.removePrefix("ROLE_")?.takeIf { it.isNotBlank() } ?: return null
    return when (raw) {
        "PARENT", "GUARDIAN" -> Role.PARENT
        "CHILD", "KID" -> Role.CHILD
        else -> Role.entries.firstOrNull { it.name == raw }
    }
}

fun LoginResponse.toSession(user: User): Session = Session(
    accessToken = resolvedAccessToken().orEmpty(),
    refreshToken = refreshToken.orEmpty(),
    user = user,
)

fun ChildDto.toDomain(): ChildProfile = ChildProfile(
    id = id,
    // Backend ChildSummaryResponse: username / nickname (no name/alias).
    name = name?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: "자녀",
    alias = alias?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() },
    birthDate = birthDate,
    profileImageUrl = profileImageUrl,
    realtimeEnabled = realtimeEnabled,
)

/** 동선 좌표. 서버가 숫자로 주지만 도메인은 문자열을 쓴다(지도 표시·기존 화면 호환). */
fun TrackPointDto.toDomain(): LocationPoint = LocationPoint(
    lat = lat.toString(),
    lng = lng.toString(),
    recordedAt = at.orEmpty(),
    updatedAt = at,
)

fun LocationTrackResponse.toDomain(): LocationPoint = LocationPoint(
    lat = latitude,
    lng = longitude,
    recordedAt = updatedAt ?: segmentEnd ?: segmentStart.orEmpty(),
    label = null,
    segmentStart = segmentStart,
    segmentEnd = segmentEnd,
    updatedAt = updatedAt,
)

/** places 응답은 좌표가 숫자라 도메인 문자열로 옮긴다. */
fun PlaceResponse.toDomain(): Geofence = Geofence(
    id = id.toString(),
    name = name,
    lat = latitude.toString(),
    lng = longitude.toString(),
    radius = radiusMeters.toInt(),
    notifyOnArrival = true,
    type = runCatching { PlaceType.valueOf(type) }.getOrDefault(PlaceType.GEOFENCE),
)

fun LocationEventDto.toDomain(): LocationEvent = LocationEvent(
    id = id.toString(),
    placeId = placeId.toString(),
    eventType = runCatching { LocationEventType.valueOf(eventType) }
        .getOrDefault(LocationEventType.ENTER),
    occurredAt = occurredAt,
)

fun DashboardResponse.toDomain(): ChildDashboard = ChildDashboard(
    childId = childId.toString(),
    currentLocation = currentLocation?.toDomain(),
    realtimeEnabled = realtimeEnabled,
    // 서버가 나눠 준 두 목록을 최신순으로 합친다. createdAt 은 오프셋 없는 ISO 라
    // 문자열 내림차순이 곧 시간 내림차순이다.
    recentNotifications = (childNotifications + parentNotifications)
        .map { it.toDomain() }
        .sortedByDescending { it.createdAt },
    blockRulePatterns = blockRules.map { it.pattern },
    places = places.map { it.toDomain() },
)

fun ChildAlertDto.toDomain(): ChildAlertEvent = ChildAlertEvent(id, type, title, body, occurredAt)

fun ChildAlertEvent.toDto(): ChildAlertDto = ChildAlertDto(id, type, title, body, occurredAt)

fun NotificationDto.toDomain(): AppNotification = AppNotification(
    id = id,
    type = type,
    title = title?.takeIf { it.isNotBlank() },
    message = message,
    createdAt = createdAt,
    read = read,
    childId = childId?.takeIf { it.isNotBlank() },
)

/**
 * 서버 키 → 화면 토글.
 *
 * 화면의 「GPS·위치 알림」 하나가 서버의 지오펜스 3종 + 전원/GPS 꺼짐을 함께 덮는다
 * (부제가 "GPS 꺼짐, 안전구역 이탈"). 하나라도 켜져 있으면 켜진 것으로 본다.
 * 서버가 모르는 키를 보내오거나 일부를 생략해도 기본값 true 로 떨어뜨린다.
 */
private val LOCATION_KEYS =
    listOf("LOCATION_ENTER", "LOCATION_EXIT", "LOCATION_ARRIVED", "DEVICE_POWER_OFF")

private fun Map<String, Boolean>.anyOn(keys: List<String>): Boolean =
    keys.mapNotNull { this[it] }.let { found -> found.isEmpty() || found.any { it } }

fun NotificationSettingsDto.toDomain(): NotificationSettings = NotificationSettings(
    location = anyOn(LOCATION_KEYS),
    harmfulAccess = this["HARMFUL_SITE"] ?: true,
    appUsage = this["APP_USAGE"] ?: true,
    notice = this["NOTICE"] ?: true,
)

/** 화면 토글 → 서버 키. 화면이 다루지 않는 유형은 보내지 않는다(부분 갱신). */
fun NotificationSettings.toDto(): NotificationSettingsDto = buildMap {
    LOCATION_KEYS.forEach { put(it, location) }
    put("HARMFUL_SITE", harmfulAccess)
    put("APP_USAGE", appUsage)
    put("NOTICE", notice)
}

/** 상세 응답. 목록에 없는 loginId·email·phone·address·paired 를 채운다. */
fun ChildDetailDto.toDomain(): ChildProfile = ChildProfile(
    id = id,
    name = username?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: "자녀",
    alias = nickname?.takeIf { it.isNotBlank() },
    birthDate = birthDate,
    profileImageUrl = profileImageUrl,
    paired = paired,
    loginId = loginId,
    email = email,
    phone = phoneNumber,
    address = address,
)

fun ActivityDto.toDomain(): Activity = Activity(
    id = id,
    type = when (type?.trim()?.uppercase()) {
        "POST" -> ActivityType.POST
        "COMMENT" -> ActivityType.COMMENT
        "LIKE" -> ActivityType.LIKE
        else -> ActivityType.UNKNOWN
    },
    postId = postId,
    preview = preview.orEmpty(),
    createdAtLabel = createdAt.orEmpty(),
)

fun BlockRuleDto.toDomain(): BlocklistItem = BlocklistItem(id = id, pattern = pattern)

fun AppControlDto.toDomain(): AppControlItem = AppControlItem(
    ruleId = id,
    packageName = packageName,
    name = appName.ifBlank { packageName },
    dailyLimitMinutes = dailyLimitMinutes,
    enabled = enabled,
)

fun InstalledAppDto.toDomain(): InstalledApp = InstalledApp(
    packageName = packageName,
    appName = appName.ifBlank { packageName },
)

fun ScreenshotDto.toDomain(): Screenshot = Screenshot(
    id = screenshotId,
    capturedAt = capturedAt.orEmpty(),
    trigger = trigger,
    imageUrl = imageUrl,
)

/** 업로드 응답에는 촬영 시각·트리거가 없어 요청에 쓴 값을 그대로 채운다. */
fun ScreenshotUploadDto.toDomain(capturedAt: String, trigger: String): Screenshot = Screenshot(
    id = screenshotId,
    capturedAt = capturedAt,
    trigger = trigger,
    imageUrl = imageUrl,
)

fun PostDto.toDomain(): Post = Post(
    id = postId.toString(),
    authorId = authorId.toString(),
    title = title,
    author = authorNickname,
    content = content,
    preview = contentPreview.orEmpty(),
    thumbnail = thumbnail,
    likeCount = likeCount,
    createdAtLabel = createdAt,
    attachments = attachments,
    links = links,
    comments = comments.map { it.toDomain() },
)

fun CommentDto.toDomain(): Comment = Comment(
    id = commentId.toString(),
    memberId = memberId.toString(),
    author = nickname,
    text = content,
    createdAt = createdAt,
)

fun LikeToggleDto.toDomain(): LikeResult = LikeResult(liked = liked, likeCount = likeCount)

fun ChatRoomDto.toDomain(titleOverride: String? = null): ChatRoom = ChatRoom(
    id = roomId,
    // 서버는 제목 대신 「요청자 기준 상대방」을 준다 — 부모가 보면 자녀, 자녀가 보면 부모.
    title = titleOverride?.takeIf { it.isNotBlank() }
        ?: counterpartNickname?.takeIf { it.isNotBlank() }
        ?: counterpartName?.takeIf { it.isNotBlank() }
        ?: "채팅",
    lastMessage = lastMessage.orEmpty(),
    lastMessageType = lastMessageType?.takeIf { it.isNotBlank() }
        ?.let { com.hack.kidhack.domain.model.MessageType.fromWire(it) },
    lastAt = lastMessageAt.orEmpty(),
    unreadCount = unreadCount,
    counterpartProfileImageUrl = counterpartProfileImageUrl,
    parentId = parentId,
    childId = childId,
)

/** 방 생성 응답에는 상대방 정보가 없다. 목록을 다시 부르기 전까지 쓸 최소 형태. */
fun ChatRoomCreatedDto.toDomain(titleOverride: String? = null): ChatRoom = ChatRoom(
    id = id,
    title = titleOverride?.takeIf { it.isNotBlank() } ?: "채팅",
    lastMessage = "",
    lastAt = "",
    parentId = parentId,
    childId = childId,
)

fun ChatMessageDto.toDomain(myUserId: Long? = null, myRole: Role = Role.PARENT): ChatMessage {
    val resolvedText = content?.takeIf { it.isNotBlank() } ?: text
    val role = when {
        fromRole.isNotBlank() -> fromRole.toRoleOrNull() ?: Role.PARENT
        myUserId != null && senderId != null ->
            if (senderId == myUserId) {
                myRole
            } else if (myRole == Role.PARENT) {
                Role.CHILD
            } else {
                Role.PARENT
            }
        else -> Role.PARENT
    }
    return ChatMessage(
        id = id,
        roomId = roomId,
        fromRole = role,
        text = resolvedText,
        sentAt = sentAt,
        type = com.hack.kidhack.domain.model.MessageType.fromWire(type),
        mediaUrl = mediaUrl?.takeIf { it.isNotBlank() },
    )
}

fun PaymentDto.toDomain(): Payment = Payment(
    id = id,
    description = description,
    amount = amount,
    dateLabel = dateLabel,
    type = type,
)

fun PointBalanceDto.toDomain(): PointBalance = PointBalance(balance)

fun PointHistoryDto.toDomain(): PointHistory = PointHistory(id, description, point, dateLabel)

fun AppUsageStatDto.toDomain(): AppUsageStat = AppUsageStat(key, durationSec)

/**
 * 서버가 주는 만료 시각을 화면이 쓰는 「남은 초」로 바꾼다.
 *
 * `expiresAt` 은 오프셋 없는 LocalDateTime 이고 서버 시간대는 KST 다. 기기 시계도 KST 라는
 * 전제로 그대로 뺀다. 파싱이 안 되거나 필드가 비어 있으면 서버 기본값(발급 + 5분)을 쓴다 —
 * 카운트다운이 0 으로 시작해 코드가 즉시 만료된 것처럼 보이는 것보다 낫다.
 */
fun PairingCodeDto.toDomain(): PairingCode {
    val seconds = expiresAt
        ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        ?.let { Duration.between(LocalDateTime.now(), it).seconds }
        ?.coerceIn(0L, Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: DEFAULT_PAIRING_TTL_SECONDS
    return PairingCode(pairingCode = code, expiresIn = seconds)
}

/** 서버 스펙상 페어링 코드 유효기간(발급 + 5분). */
private const val DEFAULT_PAIRING_TTL_SECONDS = 300

/** 서버/소켓이 쓸 법한 이름들을 넉넉하게 받아 준다. 모르는 값은 UNKNOWN 으로 떨어뜨린다. */
fun String?.toRemoteCommandType(): RemoteCommandType = when (this?.uppercase()) {
    // SCREENSHOT_REQUESTED = 서버 NotificationType(FCM data.type). 캡처 요청은 이 이름으로 온다.
    "SCREEN_CAPTURE", "CAPTURE", "CAPTURE_REQUEST", "SCREENSHOT", "SCREENSHOT_REQUESTED" -> RemoteCommandType.SCREEN_CAPTURE
    "CAMERA_SHOT", "CAMERA", "CAMERA_REQUEST", "PHOTO" -> RemoteCommandType.CAMERA_SHOT
    // USAGE_REPORT_REQUESTED = 서버 NotificationType. 나머지는 소켓 계약 쪽 이름.
    "USAGE_REPORT_REQUESTED", "USAGE_REQUEST", "USAGE_REPORT" -> RemoteCommandType.USAGE_REPORT
    else -> RemoteCommandType.UNKNOWN
}

fun ChildPhotoDto.toDomain(): ChildPhoto = ChildPhoto(
    id = id,
    capturedAt = capturedAt,
    imageUrl = imageUrl,
)

fun AllowanceDto.toDomain(): Allowance = Allowance(balance = balance, updatedAt = updatedAt)

fun AllowanceEntryDto.toDomain(): AllowanceEntry = AllowanceEntry(
    id = id,
    type = runCatching { AllowanceType.valueOf(type.uppercase()) }
        .getOrDefault(AllowanceType.PAYMENT),
    amount = amount,
    description = description,
    dateLabel = dateLabel,
)

fun AllowancePaymentDto.toDomain(): AllowancePayment = AllowancePayment(
    id = id,
    amount = amount,
    balanceAfter = balanceAfter,
    approvedAt = approvedAt,
    merchant = merchant,
 )

fun InquiryDto.toDomain(): Inquiry = Inquiry(
    id = id.toString(),
    title = title,
    answered = answered,
    createdAt = createdAt,
    authorNickname = memberNickname,
)

fun InquiryDetailDto.toDomain(): InquiryDetail = InquiryDetail(
    id = id.toString(),
    title = title,
    content = content,
    attachments = attachments,
    linkUrl = linkUrl?.takeIf { it.isNotBlank() },
    answered = answered,
    createdAt = createdAt,
    answer = answer?.toDomain(),
)

fun InquiryAnswerDto.toDomain(): InquiryAnswer = InquiryAnswer(
    id = id.toString(),
    content = content,
    createdAt = createdAt,
)

fun NoticeDto.toDomain(): Notice = Notice(
    id = id.toString(),
    title = title,
    createdAt = createdAt,
    attachmentCount = attachmentCount,
    linkCount = linkCount,
)

fun NoticeDetailDto.toDomain(): NoticeDetail = NoticeDetail(
    id = id.toString(),
    title = title,
    content = content,
    attachments = attachments,
    links = links,
    createdAt = createdAt,
)

fun WalletDto.toDomain(): Wallet = Wallet(
    walletId = walletId,
    balance = balance,
    updatedAt = updatedAt
)

fun WalletTransactionDto.toDomain(): WalletTransaction = WalletTransaction(
    txId = txId,
    type = type,
    amount = amount,
    balanceAfter = balanceAfter,
    refId = refId,
    createdAt = createdAt
)

fun ChargePrepareResponse.toDomain(): Charge = Charge(
    chargeId = chargeId,
    amount = amount,
    status = status,
    paymentKey = paymentKey,
    method = ""
)

fun CardDto.toDomain(): Card = Card(
    cardId = cardId,
    last4 = last4,
    expiry = expiry,
    ownerName = ownerName
)

fun TransferResponse.toDomain(): TransferResult = TransferResult(
    parentBalance = parentBalance,
    childBalance = childBalance,
    txId = txId
)

/* ---- 결제 서버(v0.2) 매퍼 — /api/wallet · /api/qrpay ---- */

/** 서버 원장(long) → 앱 도메인(Int). 금액은 이 앱 도메인에서 Int 로 충분하다. */
fun TxResponseDto.toDomain(): WalletTransaction = WalletTransaction(
    txId = id.toString(),
    type = type,
    amount = amount.toInt(),
    balanceAfter = balanceAfter.toInt(),
    refId = refUid,
    createdAt = createdAt.toShortDateTimeLabel(),
)

/** 내 지갑 응답 → 잔액 + 이번 달 합계(원장은 [toDomain] 으로 따로 편다). */
fun MyWalletResponse.toWallet(): Wallet = Wallet(
    walletId = "me",
    balance = balance.toInt(),
    updatedAt = "",
    monthlyChargeTotal = monthlyChargeTotal.toInt(),
    monthlySpendTotal = monthlySpendTotal.toInt(),
)

fun ChildWalletResponse.toWallet(): Wallet = Wallet(
    walletId = childId.toString(),
    balance = balance.toInt(),
    updatedAt = "",
)

fun WalletChargePrepareResponse.toCharge(): Charge = Charge(
    chargeId = merchantUid,
    amount = amount.toInt(),
    status = "READY",
    paymentKey = null,
    // 서버가 알려 준 PG 이름(mock 이면 "MOCK" 등) — 토스 결제창 목업에 표시한다.
    method = pgProvider,
)

fun ChargeHistoryResponseDto.toDomain(): ChargeHistoryItem = ChargeHistoryItem(
    merchantUid = merchantUid,
    amount = amount.toInt(),
    status = status,
    createdAtLabel = createdAt.toShortDateTimeLabel(),
    refundedAtLabel = refundedAt?.takeIf { it.isNotBlank() }?.toShortDateTimeLabel(),
)

fun CardResponse.toDomain(): Card = Card(
    cardId = id.toString(),
    last4 = maskedCardNumber.orEmpty().filter { it.isDigit() }.takeLast(4),
    expiry = expiry.orEmpty(),
    ownerName = ownerName.orEmpty(),
)

fun QrIssueResponse.toDomain(): QrTicket = QrTicket(
    qrToken = qrToken,
    status = status,
    // 서버 expiresAt 은 존(zone) 없는 LocalDateTime(서버 KST). 기기 시간대가 다르면(예: GMT 에뮬레이터)
    // 9시간짜리 카운트다운이 나오므로 QR TTL(60초)로 상한을 건다.
    expiresAtMillis = minOf(expiresAt.toEpochMillisOrFuture(), System.currentTimeMillis() + QR_TTL_MS),
)

/** 서버 QR 유효 시간(태그 15: 발급 후 60초). */
private const val QR_TTL_MS = 60_000L

fun QrStatusResponse.toDomain(): QrTicket = QrTicket(
    qrToken = qrToken,
    status = status,
    expiresAtMillis = expiresAt.toEpochMillisOrFuture(),
)

fun QrPaymentHistoryResponseDto.toDomain(): QrPaymentHistoryItem = QrPaymentHistoryItem(
    qrToken = qrToken,
    storeName = storeName?.takeIf { it.isNotBlank() } ?: "매장",
    amount = amount.toInt(),
    status = status,
    paidAtLabel = paidAt.toShortDateTimeLabel(),
)

fun TerminalResponse.toDomain(): StoreTerminalInfo = StoreTerminalInfo(
    id = id,
    storeName = storeName,
    terminalToken = terminalToken.orEmpty(),
    status = status,
)

/** "2026-08-30T11:20:00" → "08.30 11:20". 파싱 실패 시 원문 유지. */
private fun String.toShortDateTimeLabel(): String {
    if (isBlank()) return ""
    return runCatching {
        val dt = java.time.LocalDateTime.parse(take(19))
        "%02d.%02d %02d:%02d".format(dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
    }.getOrDefault(this)
}

/** ISO 문자열 → epoch millis. 파싱 실패 시 지금+60초(서버 QR TTL 기본값)로 안전 폴백. */
private fun String.toEpochMillisOrFuture(): Long {
    if (isNotBlank()) {
        runCatching {
            java.time.LocalDateTime.parse(take(19))
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()?.let { return it }
    }
    return System.currentTimeMillis() + 60_000L
}
