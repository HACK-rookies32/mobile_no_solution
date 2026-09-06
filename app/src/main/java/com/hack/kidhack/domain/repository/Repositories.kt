package com.hack.kidhack.domain.repository

import androidx.paging.PagingData
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Availability
import com.hack.kidhack.domain.model.Activity
import com.hack.kidhack.domain.model.ActivityType
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.ChargeHistoryItem
import com.hack.kidhack.domain.model.QrTicket
import com.hack.kidhack.domain.model.QrPaymentHistoryItem
import com.hack.kidhack.domain.model.StoreTerminalInfo
import com.hack.kidhack.domain.model.Charge
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatMessagePage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.model.ChildDashboard
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryDetail
import com.hack.kidhack.domain.model.LikeResult
import com.hack.kidhack.domain.model.InstalledApp
import com.hack.kidhack.domain.model.LocationEvent
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.Notice
import com.hack.kidhack.domain.model.NoticeDetail
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.Paged
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.SentCode
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.TransferResult
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AuthRepository {
    suspend fun signup(
        loginId: String,
        username: String,
        password: String,
        nickname: String?,
        email: String?,
        phoneNumber: String,
        birthDate: String,
        address: String?,
        profileImageUrl: String? = null,
        role: Role,
        parentId: Long?,
    ): Session

    /**
     * 가입 전 아이디·이메일 중복 확인.
     *
     * 가입 폼의 「다음」에서 부른다. 마지막 단계에서야 409 를 받으면 약관·권한 화면을
     * 다 지나온 뒤라 되돌아가기가 번거롭다.
     */
    suspend fun checkAvailability(loginId: String?, email: String?): Availability

    suspend fun login(loginId: String, password: String, preferredRole: Role? = null): Session
    suspend fun logout()
    /**
     * 아이디 찾기 1단계 — 실명 + 이메일로 인증코드 발송.
     *
     * 서버가 mock 메일러를 써서 코드를 응답에 실어 주므로 [SentCode.code] 가 채워져 올 수 있다.
     * 운영에서는 화면에 노출하지 않는다.
     */
    suspend fun sendFindIdCode(username: String, email: String): SentCode

    /** 아이디 찾기 2단계 — 코드 검증 후 로그인 아이디 반환. */
    suspend fun verifyFindId(email: String, code: String): String

    /** 비밀번호 재설정 1단계 — 아이디 + 이메일로 인증코드 발송. */
    suspend fun sendResetPasswordCode(loginId: String, email: String): SentCode

    /** 비밀번호 재설정 2단계 — 코드 검증과 동시에 새 비밀번호 적용. */
    suspend fun resetPassword(email: String, code: String, newPassword: String)
}

interface UserRepository {
    suspend fun me(): User
    /** 서버가 받는 항목만. 실명(username)은 수정 대상이 아니다. */
    suspend fun updateMe(
        nickname: String?,
        phoneNumber: String?,
        email: String?,
        address: String?,
        profileImageUrl: String?,
    ): User
    suspend fun deleteMe()

    /**
     * 커뮤니티 활동 내역.
     *
     * 서버는 `type` 을 **필수**로 받는다(미전달 시 400). 「전체」 조회는 없으므로
     * 널을 허용하지 않는다. [ActivityType.UNKNOWN] 은 응답 파싱용이라 필터로 쓰지 않는다.
     */
    fun pagedActivities(type: ActivityType): Flow<PagingData<Activity>>
    suspend fun notificationSettings(): NotificationSettings
    suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings
}

interface PairingRepository {
    suspend fun issueCode(): PairingCode

    /** 아직 만료되지 않은 내 코드 목록. 연결에 쓰인 코드는 서버가 지우므로 목록에서 빠진다. */
    suspend fun activeCodes(): List<PairingCode>

    suspend fun connect(pairingCode: String)
}

interface ChildRepository {
    suspend fun children(): List<ChildProfile>
    suspend fun child(childId: String): ChildProfile
    /** 서버 ChildUpdateRequest 가 받는 항목만. 이름·생년월일은 수정 대상이 아니다. */
    suspend fun updateChild(
        childId: String,
        nickname: String?,
        profileImageUrl: String?,
    ): ChildProfile

    /**
     * 프로필 사진을 서버에 올리고 공개 경로를 받는다.
     *
     * <p>갤러리에서 고른 `content://` 주소는 그 기기에서만 열린다. 그대로 저장하면 다른
     * 기기에서도, 재설치 뒤에도 사진이 뜨지 않는다. 먼저 올려 받은 경로를
     * [updateChild] 의 `profileImageUrl` 로 넘겨야 한다.
     *
     * @param uri 갤러리 또는 카메라가 준 `content://` 주소
     */
    suspend fun uploadProfileImage(uri: String): String

    /**
     * 자녀 페어링 해제. **서버가 계정을 소프트 삭제까지 하므로 되돌릴 수 없다.**
     * 화면에서 「연결만 끊는다」는 인상을 주면 안 된다.
     */
    suspend fun unpair(childId: String)

    /** 자녀 기기를 강제 로그아웃시킨다(서버의 refresh token 무효화). */
    suspend fun forceLogout(childId: String)
}

interface LocationRepository {
    /** 자녀 앱에서 좌표 업로드. 저장된 세그먼트/포인트를 돌려준다. */
    suspend fun track(childId: String, latitude: String, longitude: String): LocationPoint

    /**
     * 동선 좌표를 **최신순**으로. 첫 항목이 가장 최근 위치다.
     * (서버는 오름차순으로 주고 구현에서 뒤집는다.)
     */
    suspend fun history(childId: String): List<LocationPoint>

    /** 실시간 추적 현재 상태. 화면 진입 시 스위치 초기값을 서버에서 가져오는 데 쓴다. */
    suspend fun realtimeEnabled(childId: String): Boolean

    /** 실시간 추적 토글. 서버가 확정한 상태를 돌려준다. */
    suspend fun toggleRealtime(childId: String, enabled: Boolean): Boolean
}

interface DeviceRepository {
    /** 기기 전원/GPS 꺼짐 보고. type = "POWER_OFF" | "GPS_OFF" */
    suspend fun reportEvent(childId: String, type: String)

    /** FCM 토큰 등록·해제. 서버가 아직 없으면 호출자가 runCatching으로 삼킨다. */
    suspend fun registerPushToken(token: String)
    suspend fun unregisterPushToken(token: String)
}

interface DashboardRepository {
    suspend fun get(childId: String): ChildDashboard
}

interface PlaceRepository {
    suspend fun list(childId: String): List<Geofence>
    suspend fun create(childId: String, place: Geofence): Geofence

    /** 백엔드에 신설 요청한 DELETE /location/places/{placeId} 선반영. */
    suspend fun delete(childId: String, placeId: String)
}

interface LocationEventRepository {
    suspend fun events(childId: String): List<LocationEvent>
}

interface NotificationRepository {
    /**
     * 알림 목록.
     *
     * 서버에는 자녀별 필터 파라미터가 없다. [childId] 를 주면 **앱에서** 거른다.
     * 자녀 맥락이 없는 알림(문의 답변·전체 발송)은 childId 가 null 인데, 감춰서 놓치는 것보다
     * 낫기 때문에 필터를 걸어도 함께 보여 준다.
     */
    suspend fun list(recipientId: String, childId: String? = null): List<AppNotification>

    suspend fun markRead(notificationId: String)

    /** 안 읽은 알림을 전부 읽음 처리한다. 처리 건수를 돌려준다. */
    suspend fun markAllRead(recipientId: String): Int

    /** 배지용 안 읽음 개수. */
    suspend fun unreadCount(recipientId: String): Int
}

/**
 * 자녀 안전 사건의 서버 동기화 (docs/child-push-contract.md 4절 제안 계약).
 *
 * 자녀 앱이 [upload] 로 올리고 보호자 앱이 [list] 로 읽는다.
 * 서버가 아직 구현하지 않아도 앱은 정상 동작해야 하므로, 실패는 호출자가 삼킨다.
 */
interface ChildAlertRepository {
    suspend fun upload(childId: String, alerts: List<ChildAlertEvent>)
    suspend fun list(childId: String): List<ChildAlertEvent>
}

interface BlocklistRepository {
    suspend fun get(childId: String): List<BlocklistItem>
    suspend fun add(childId: String, pattern: String): BlocklistItem

    /** 규칙 id 로 삭제한다. */
    suspend fun remove(ruleId: String)

    /**
     * 접속 대상을 서버에 올리고 차단 여부를 돌려받는다.
     * 서버는 자녀별 규칙 + 관리자 전역 유해 키워드를 함께 본다.
     */
    suspend fun reportVisit(childId: String, target: String): Boolean
}

interface ScreenshotRepository {
    fun paged(childId: String): Flow<PagingData<Screenshot>>
    suspend fun list(childId: String): List<Screenshot>
    suspend fun requestCapture(childId: String)

    /** 자녀 기기가 캡처한 이미지를 multipart 로 올린다. trigger 는 SCHEDULED | MANUAL. */
    suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        trigger: String,
    ): Screenshot
}

/**
 * 자녀 카메라 촬영.
 *
 * 자녀 앱이 [upload] 로 올리고, 부모 앱이 [list] 로 갤러리를 보고
 * [requestShot] 으로 「지금 찍어 달라」 요청을 보낸다(ScreenshotRepository 와 대칭).
 */
interface PhotoRepository {
    suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        requestId: String?,
    ): ChildPhoto

    suspend fun list(childId: String): List<ChildPhoto>

    /** 부모 모드 전용. 서버가 자녀 기기로 CAMERA_REQUEST 푸시를 밀게 한다. */
    suspend fun requestShot(childId: String)
}

/**
 * 부모 → 자녀 원격 요청 수신 채널.
 *
 * 수신 경로는 WebSocket([observe]) 과 FCM 두 가지다. 폴링 백업 경로는
 * 서버에 대응 엔드포인트가 없어 걷어냈다(2026-08-28).
 * [ack] 은 두 경로 모두에서 처리 결과를 서버로 되돌릴 때 쓴다.
 */
interface CommandRepository {
    fun observe(): Flow<RemoteCommand>

    /**
     * 실시간 요청 채널(WebSocket)이 붙어 있는지.
     *
     * 끊겨 있으면 부모 요청이 FCM 으로만 들어오므로 화면에 그대로 알려 준다.
     */
    val connected: Flow<Boolean>

    /** 상주 소켓을 내린다. 자녀 세션이 끝날 때(로그아웃/역할 변경) 호출한다. */
    fun disconnect()

    suspend fun ack(childId: String, requestId: String, status: String, message: String? = null)
}

interface CommunityRepository {
    fun pagedPosts(keyword: String? = null): Flow<PagingData<Post>>
    suspend fun posts(): List<Post>

    /** 본문·첨부·댓글은 상세에만 있다. */
    suspend fun post(postId: String): Post

    /** @param attachments [uploadAttachment] 로 받은 공개 경로들. 로컬 URI 를 넣으면 안 된다. */
    suspend fun create(title: String, content: String, attachments: List<String>, links: List<String>): Post

    suspend fun update(postId: String, title: String, content: String, attachments: List<String>, links: List<String>): Post

    /** 글과 첨부 실물이 함께 지워진다. */
    suspend fun delete(postId: String)

    /** 토글 결과를 서버가 준 그대로 돌려준다. */
    suspend fun like(postId: String): LikeResult

    suspend fun comment(postId: String, content: String): Comment

    /** 본인 댓글만. 남의 댓글이면 서버가 403. */
    suspend fun deleteComment(postId: String, commentId: String)

    /** 파일 하나를 올리고 공개 경로를 받는다. */
    suspend fun uploadAttachment(uri: String): String
}

interface InquiryRepository {
    suspend fun list(page: Int = 0): Paged<Inquiry>

    /** 본문·첨부·답변은 상세에만 있다. */
    suspend fun get(id: String): InquiryDetail

    /**
     * 문의 등록.
     *
     * @param attachments [uploadAttachment] 로 받은 공개 경로들. 로컬 URI 를 넣으면 안 된다.
     * @param linkUrl 서버가 링크를 하나만 받는다.
     */
    suspend fun create(title: String, content: String, attachments: List<String>, linkUrl: String?): Inquiry

    /** 통째로 교체한다. 답변이 달린 문의는 서버가 400 으로 막는다. */
    suspend fun update(id: String, title: String, content: String, attachments: List<String>, linkUrl: String?): Inquiry

    /** 첨부 실물·답변까지 함께 지워진다. 답변이 달린 문의는 400. */
    suspend fun delete(id: String)

    /** 파일 하나를 올리고 공개 경로를 받는다. */
    suspend fun uploadAttachment(uri: String): String
}

/** 공지사항. 열람에 로그인이 필요 없다. */
interface NoticeRepository {
    /** @param keyword 제목 부분 검색. 비우면 전체. */
    suspend fun list(keyword: String? = null, page: Int = 0): Paged<Notice>
    suspend fun get(id: String): NoticeDetail
}

/**
 * 거래 전 과정 보안 번들 — 송금/회수 요청에 함께 실린다(전자금융 거래 보안, #59).
 *
 * otp: 발급받은 거래 2차 인증번호 / signature: amount:childId:nonce HMAC / nonce: 일회성 값 /
 * accountHolder: 대상 계좌 소유주명. null 이면 서버가 해당 검증을 건너뛴다(의도된 결함).
 */
data class TxSecurity(
    val otp: String? = null,
    val signature: String? = null,
    val nonce: String? = null,
    val accountHolder: String? = null,
    /** 결제 비밀번호(4자리). 서버 관문이 필수로 검증. */
    val paymentPassword: String? = null,
)

interface WalletRepository {
    suspend fun getMyWallet(): Wallet
    suspend fun getTransactions(type: String?, page: Int): List<WalletTransaction>
    suspend fun transferToChild(childId: String, amount: Int, memo: String?, security: TxSecurity? = null): TransferResult
    suspend fun reclaimFromChild(childId: String, amount: Int, security: TxSecurity? = null): TransferResult
    suspend fun getChildWallet(childId: String): Wallet
    suspend fun prepareCharge(amount: Int, method: String, cardId: String, security: TxSecurity? = null): Charge
    suspend fun confirmCharge(chargeId: String, paymentKey: String, amount: Int): Wallet

    /** 이 부모↔자녀 사이 이체/회수 원장(최신순 페이징). */
    suspend fun getChildTransfers(childId: String, page: Int = 0): List<WalletTransaction>

    /**
     * 자녀 지갑의 **전체** 원장(최신순 페이징).
     *
     * [getChildTransfers] 는 부모와 주고받은 이체·회수만 담는다. 자녀가 매장에서 쓴 결제까지
     * 시간순으로 보려면 이 경로가 필요하다.
     */
    suspend fun getChildTransactions(childId: String, type: String? = null, page: Int = 0): List<WalletTransaction>

    /** 충전 통합 내역(주문+환불시각). */
    suspend fun getChargeHistory(page: Int = 0): List<ChargeHistoryItem>

    /** 결제 완료(PAID)된 충전을 전액 환불한다. @return 환불 후 잔액. */
    suspend fun refundCharge(merchantUid: String): Int

    /** 아직 결제 안 된(READY) 충전 주문을 취소한다. @return 취소 후 주문 상태. */
    suspend fun cancelCharge(merchantUid: String): String
}

interface CardRepository {
    suspend fun register(
        cardNumber: String,
        expiry: String,
        cvc: String,
        cardPassword2: String,
        birthDate: String,
        ownerName: String,
        /** 접근매체 발급 본인확인용 결제 비밀번호(4자리). 서버 관문이 대조한다. */
        paymentPassword: String,
    ): Card
    suspend fun list(): List<Card>
    suspend fun delete(cardId: String)
}

/**
 * QR 결제(태그 15·18·19) — 서버 `/api/qrpay`.
 *
 * 자녀는 [issueQr] 로 **서버 발급** 토큰을 받아 QR 을 띄우고 [qrStatus] 로 승인 여부를 폴링한다.
 * 승인(PAID 전이)은 매장 단말이 하며(→ [StoreTerminalRepository]) 자녀 앱은 하지 않는다.
 * [cancel] 은 부모, [paymentHistory] 는 부모(childId 필수)·자녀(null) 공용.
 */
interface QrPayRepository {
    suspend fun issueQr(): QrTicket
    suspend fun qrStatus(qrToken: String): QrTicket
    suspend fun cancel(qrToken: String)
    suspend fun paymentHistory(childId: String?, page: Int = 0): List<QrPaymentHistoryItem>
}

/**
 * 매장 단말(태그 16·17) — 회원 앱 정상 흐름과 분리된 **테스트/시연 전용** 리포지토리.
 *
 * 단말 토큰을 발급/조회하고, 그 토큰으로 자녀 QR 을 승인한다(`X-Store-Terminal-Token`).
 * 자녀 앱의 옛 자가승인과 혼동되지 않도록 별도 인터페이스로 격리한다.
 */
interface StoreTerminalRepository {
    suspend fun issueTerminal(storeName: String): StoreTerminalInfo
    suspend fun terminals(): List<StoreTerminalInfo>
    suspend fun approve(terminalToken: String, qrToken: String, amount: Int): QrPaymentHistoryItem
}

interface ChatRepository {
    /** 부모–자녀 1:1 방. 없으면 서버가 생성한다. */
    suspend fun getOrCreateRoom(parentId: Long, childId: Long): ChatRoom
    suspend fun rooms(): List<ChatRoom>
    /**
     * 방의 메시지 한 장을 **오래된 → 최신** 순으로. 화면이 아래를 최신으로 그린다.
     * (서버는 최신순으로 주고 구현에서 뒤집는다.)
     *
     * 커서 페이징이라 [before] 에 직전 응답의 [ChatMessagePage.oldestSentAt] 을 넘기면
     * 그 이전 장을 준다.
     */
    suspend fun messages(roomId: String, before: String? = null, size: Int = 30): ChatMessagePage
    fun observeMessages(roomId: String): Flow<ChatMessage>
    suspend fun send(roomId: String, content: String): ChatMessage
    suspend fun markRead(roomId: String, lastMessageId: String? = null)
}

interface PaymentRepository {
    suspend fun checkout(amount: Int, method: String, productId: Int): Payment
    suspend fun history(): List<Payment>
}

interface PointRepository {
    suspend fun balance(): PointBalance
    suspend fun history(): List<PointHistory>
    suspend fun use(productId: Int, point: Int)
}

interface AppUsageRepository {
    suspend fun list(
        childId: String,
        groupBy: String = "DAY",
        from: String? = null,
        to: String? = null,
    ): List<AppUsageStat>
    suspend fun report(childId: String, usages: List<AppUsage>)

    /**
     * 자녀 기기가 사용량을 지금 올리도록 요청한다(부모).
     *
     * <p>자녀 앱은 평소 1시간 주기로만 올린다. 부모가 리포트를 새로고침할 때 이 요청을
     * 함께 보내야 방금까지의 사용시간이 반영된다. 자녀 기기가 꺼져 있거나 네트워크가
     * 없으면 요청은 닿지 않고 마지막 값이 그대로 남는다.
     */
    suspend fun requestReport(childId: String)
}

interface AppControlRepository {
    /** 자녀별 사용시간 제한 규칙 목록. */
    suspend fun items(childId: String): List<AppControlItem>

    /** 자녀 기기에 설치된 앱 목록(서버가 보관 중인 것). */
    suspend fun getInstalledApps(childId: String): List<InstalledApp>

    /** 자녀 앱이 설치 목록을 서버에 올린다. 반영된 개수를 돌려준다. */
    suspend fun syncInstalledApps(childId: String, apps: List<InstalledApp>): Int

    /** 규칙 신설. 한도 0분이면 사실상 차단이다. */
    suspend fun addRule(
        childId: String,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int?,
    ): AppControlItem

    /** 규칙 수정. 바꿀 값만 넘긴다. */
    suspend fun updateRule(
        ruleId: String,
        dailyLimitMinutes: Int? = null,
        enabled: Boolean? = null,
    ): AppControlItem

    suspend fun deleteRule(ruleId: String)

    /**
     * 자녀 하루 총 스크린타임 한도(분). **null 이면 제한 없음.**
     * 0 은 "한도 0분"이라 의미가 다르다.
     */
    suspend fun screenTimeLimit(childId: String): Int?

    /** 한도 설정(부모). null 이면 제한 해제. */
    suspend fun setScreenTimeLimit(childId: String, dailyLimitMinutes: Int?): Int?

    /**
     * 한도 도달 보고(자녀 앱). 서버가 부모·자녀 양쪽에 알림을 발행한다.
     * 한도가 설정돼 있지 않으면 서버가 무시하므로 false 가 온다.
     */
    suspend fun reportScreenTimeReached(childId: String, usedMinutes: Int): Boolean
}

/**
 * 루키즈페이 용돈 지갑.
 *
 * 구독·결제 적립에 쓰는 [PointRepository] 와 분리된 별도 지갑이다.
 * 잔액의 유일한 공급원은 부모의 [send] 이고, 자녀는 [pay] 로 소비만 한다.
 */
interface AllowanceRepository {
    suspend fun balance(childId: String): Allowance
    suspend fun history(childId: String): List<AllowanceEntry>

    /** 부모 모드 전용 — 자녀 앱에는 이 UI 가 없다. */
    suspend fun send(childId: String, amount: Int, memo: String?): Allowance

    /** 부모 모드 전용 — 남은 용돈을 회수한다. */
    suspend fun withdraw(childId: String, amount: Int, memo: String?): Allowance

    /**
     * 자녀 모드 QR 결제. 잔액 검증의 최종 권한은 서버에 있다.
     * (부모가 방금 회수했을 수 있으므로 클라이언트 잔액만 믿으면 안 된다.)
     */
    suspend fun pay(childId: String, token: String, amount: Int, merchant: String?): AllowancePayment
}

interface SessionRepository {
    val session: Flow<Session?>
    suspend fun current(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
    fun accessToken(): String?
    fun refreshToken(): String?
}

interface SelectedChildRepository {
    val selectedChildId: Flow<String?>
    suspend fun current(): String?
    suspend fun select(childId: String?)
}

interface SessionEvents {
    val logout: Flow<Unit>
    fun emitLogout()
}

interface ChildOnboardingStore {
    val done: Flow<Boolean>
    suspend fun isDone(): Boolean
    suspend fun markDone()
}

/** 자녀 앱에서 "내 childId". 서버 리소스 경로에 그대로 들어간다. */
suspend fun SessionRepository.childId(): String = current()?.user?.id.orEmpty()

/**
 * 알림 조회에 쓰는 수신자 id — 로그인한 본인.
 *
 * 서버 경로가 `/notifications/{recipientId}` 인데 소유권 검증이 없는 IDOR 이고
 * (`[VULN-NOTI-1]`, 의도적), 백엔드 2단계에서 `/notifications/me` 형태로 바뀔 가능성이 높다.
 * **조립을 여기 한 곳에만 두어** 그때 이 함수만 고치면 되게 한다.
 */
suspend fun SessionRepository.notificationRecipientId(): String = current()?.user?.id.orEmpty()

/* ---------------------------------------------------- 전자금융 (efin) ---- */

/** 거래 OTP 발급 결과. code 는 서버가 응답에 노출한 값([VULN-EFIN-1]). */
data class OtpIssue(val phone: String, val code: String, val expiresIn: Long)

/** 실명확인 결과. */
data class IdentityResult(val verified: Boolean, val name: String, val verifiedAt: String?)

/**
 * 전자금융 거래 보안 — 거래 OTP·결제 비밀번호·실명확인(#59).
 *
 * 거래 훅(서명·nonce·소유주 검증)은 별도 API 가 아니라 송금/QR 요청 필드로 전달되므로 여기 없다.
 */
interface EfinRepository {
    /** 거래 OTP 발급 → mock SMS 발신. 반환 code 는 응답 노출값. */
    suspend fun issueOtp(): OtpIssue
    suspend fun verifyOtp(code: String): Boolean
    /** 결제 비밀번호가 맞는지 사전 확인(거래 전 UI 점검). 최종 검증은 서버 관문. */
    suspend fun verifyPaymentPassword(password: String): Boolean
    /** 결제 비밀번호 최초 설정(카드 발급 흐름). 직전 실명확인이 본인확인을 대신한다. */
    suspend fun setupPaymentPassword(password: String)
    suspend fun changePaymentPassword(currentPassword: String, newPassword: String, loginPassword: String)
    /** 결제 비밀번호 설정 여부(카드 위저드 단계 판단). */
    suspend fun paymentPasswordSet(): Boolean
    /** 실명확인 완료 여부(카드 위저드 단계 판단). */
    suspend fun identityVerified(): Boolean
    suspend fun verifyIdentity(
        name: String,
        birthDate: String,
        idType: String,
        idNumber: String,
        issuedDate: String,
    ): IdentityResult
}
