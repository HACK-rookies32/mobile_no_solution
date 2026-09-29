package com.hack.kidhack.data.repository.fake

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.hack.kidhack.data.local.CapturedShotStore
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.remote.PageResponse
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.paging.PageResponsePagingSource
import com.hack.kidhack.data.ws.ChatWebSocketClient
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AllowanceType
import com.hack.kidhack.domain.model.Activity
import com.hack.kidhack.domain.model.ActivityType
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.data.local.BlocklistStore
import com.hack.kidhack.domain.model.InstalledApp
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.model.childAlertTypeToParent
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.QrTicket
import com.hack.kidhack.domain.model.QrPaymentHistoryItem
import com.hack.kidhack.domain.model.StoreTerminalInfo
import com.hack.kidhack.domain.model.Charge
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.MessageType
import com.hack.kidhack.domain.model.ChatMessagePage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildDashboard
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Availability
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryAnswer
import com.hack.kidhack.domain.model.InquiryDetail
import com.hack.kidhack.domain.model.LikeResult
import com.hack.kidhack.domain.model.Notice
import com.hack.kidhack.domain.model.NoticeDetail
import com.hack.kidhack.domain.model.Paged
import com.hack.kidhack.domain.model.LocationEvent
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.SentCode
import com.hack.kidhack.domain.model.TransferResult
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.AllowanceRepository
import com.hack.kidhack.domain.repository.QrPayRepository
import com.hack.kidhack.domain.repository.StoreTerminalRepository
import com.hack.kidhack.domain.repository.EfinRepository
import com.hack.kidhack.domain.repository.IdentityResult
import com.hack.kidhack.domain.repository.OtpIssue
import com.hack.kidhack.domain.repository.TxSecurity
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.CardRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.CommunityRepository
import com.hack.kidhack.domain.repository.InquiryRepository
import com.hack.kidhack.domain.repository.NoticeRepository
import com.hack.kidhack.domain.repository.LocationEventRepository
import com.hack.kidhack.domain.repository.DashboardRepository
import com.hack.kidhack.domain.repository.DeviceRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.ChildAlertRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.PairingRepository
import com.hack.kidhack.domain.repository.PaymentRepository
import com.hack.kidhack.domain.repository.PhotoRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.PointRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.UserRepository
import com.hack.kidhack.domain.repository.WalletRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FakeAuthRepository(
    private val sessionRepository: SessionRepository,
    private val sessionEvents: SessionEvents,
    private val chatMessageStore: ChatMessageStore,
    private val chatWebSocketClient: ChatWebSocketClient,
) : AuthRepository {
    /** 목 모드에는 가입한 계정 목록이 없다. 늘 쓸 수 있다고 답해 폼을 막지 않는다. */
    override suspend fun checkAvailability(loginId: String?, email: String?): Availability =
        Availability(
            loginIdAvailable = loginId?.takeIf { it.isNotBlank() }?.let { true },
            emailAvailable = email?.takeIf { it.isNotBlank() }?.let { true },
        )

    override suspend fun signup(
        loginId: String,
        username: String,
        password: String,
        nickname: String?,
        email: String?,
        phoneNumber: String,
        birthDate: String,
        address: String?,
        profileImageUrl: String?,
        role: Role,
        parentId: Long?,
        phoneVerified: Boolean,
    ): Session {
        delay(300)
        require(loginId.isNotBlank() && password.isNotBlank()) { "아이디와 비밀번호를 입력해 주세요" }
        require(username.isNotBlank()) { "이름을 입력해 주세요" }
        require(!nickname.isNullOrBlank()) { "닉네임을 입력해 주세요" }
        require(phoneNumber.isNotBlank()) { "휴대폰 번호를 입력해 주세요" }
        require(birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) { "생년월일은 yyyy-MM-dd 형식이어야 해요" }
        if (role != Role.CHILD) {
            require(!email.isNullOrBlank()) { "이메일을 입력해 주세요" }
        }
        // 자녀는 가입 시 부모를 지정하지 않는다. 로그인 후 페어링 코드로 연결한다.
        // 채팅 등 API가 member id를 Long으로 쓰므로 목업도 숫자 id를 쓴다.
        val numericId = when (role) {
            Role.CHILD -> if (loginId.equals("minjun", ignoreCase = true)) "101" else "102"
            Role.PARENT -> "1"
        }
        val user = User(
            id = numericId,
            loginId = loginId,
            name = username,
            role = role,
            nickname = nickname,
            phone = phoneNumber,
            email = email,
            profileImageUrl = profileImageUrl,
            parentId = parentId.takeIf { role == Role.CHILD },
        )

        val session = FakeData.sessionFor(user)
        sessionRepository.save(session)
        return session
    }

    /**
     * 목업 로그인.
     *
     * 예전에는 아이디가 정확히 `minjun` 일 때만 자녀로 들어갔다. 그래서 로그인 화면에서
     * 「자녀」를 골라도 다른 아이디를 넣으면 부모 계정으로 로그인됐다.
     * 실서버가 role 을 보고 판단하듯, 목업은 화면에서 고른 역할을 그대로 따른다.
     */
    override suspend fun login(loginId: String, password: String, preferredRole: Role?): Session {
        delay(300)
        val role = preferredRole
            ?: if (loginId.equals(FakeData.childUser.loginId, ignoreCase = true)) Role.CHILD else Role.PARENT
        val user = if (role == Role.CHILD) FakeData.childUser else FakeData.parent
        val session = FakeData.sessionFor(user.copy(loginId = loginId.ifBlank { user.loginId }))
        sessionRepository.save(session)
        return session
    }

    override suspend fun logout() {
        sessionRepository.clear()
        chatMessageStore.clear()
        chatWebSocketClient.reset()
        sessionEvents.emitLogout()
    }

    /** 목업 휴대폰 인증 발송. 서버처럼 코드를 반환한다(화면 미노출, 클라가 대조). 검증은 [FAKE_CODE] 로 통과. */
    override suspend fun sendPhoneCode(phoneNumber: String): String {
        delay(300)
        require(phoneNumber.isNotBlank()) { "휴대폰 번호를 입력해 주세요" }
        return FAKE_CODE
    }

    /** 목업 휴대폰 인증 검증 — [FAKE_CODE] 와 일치할 때만 valid. */
    override suspend fun verifyPhoneCode(phoneNumber: String, code: String): Boolean {
        delay(300)
        return code.trim() == FAKE_CODE
    }

    /** 목업은 서버처럼 6자리 코드를 응답에 실어 준다. 검증은 [FAKE_CODE] 로만 통과한다. */
    override suspend fun sendFindIdCode(username: String, email: String): SentCode {
        delay(300)
        require(username.isNotBlank()) { "이름을 입력해 주세요" }
        require(email.contains("@")) { "이메일 형식이 올바르지 않아요" }
        return SentCode(message = "인증코드가 발송되었습니다.", code = FAKE_CODE)
    }

    override suspend fun verifyFindId(email: String, code: String): String {
        delay(300)
        require(code.trim() == FAKE_CODE) { "인증코드가 올바르지 않습니다." }
        return "parent01"
    }

    override suspend fun sendResetPasswordCode(loginId: String, email: String): SentCode {
        delay(300)
        require(loginId.isNotBlank()) { "아이디를 입력해 주세요" }
        require(email.contains("@")) { "이메일 형식이 올바르지 않아요" }
        return SentCode(message = "인증코드가 발송되었습니다.", code = FAKE_CODE)
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        delay(300)
        require(code.trim() == FAKE_CODE) { "인증코드가 올바르지 않습니다." }
    }

    private companion object {
        const val FAKE_CODE = "123456"
    }
}

/**
 * 목 모드 사용자 저장소.
 *
 * 예전에는 로그인한 계정과 무관하게 항상 [FakeData.parent] 를 돌려줘서
 * 자녀로 로그인해도 부모 정보가 보였고, 수정해도 아무 데도 반영되지 않았다.
 * 지금은 현재 세션의 사용자를 기준으로 동작한다.
 */
class FakeUserRepository(
    private val sessionRepository: SessionRepository,
) : UserRepository {
    private var settings = NotificationSettings(location = true, harmfulAccess = true, appUsage = true, notice = true)

    override suspend fun me(): User =
        sessionRepository.current()?.user ?: FakeData.parent

    override suspend fun updateMe(
        nickname: String?,
        phoneNumber: String?,
        email: String?,
        address: String?,
        profileImageUrl: String?,
    ): User {
        delay(200)
        val current = me()
        // 서버가 실명(username)은 수정 대상으로 받지 않으므로 목업도 바꾸지 않는다.
        return current.copy(
            nickname = nickname ?: current.nickname,
            phone = phoneNumber ?: current.phone,
            email = email,
            profileImageUrl = profileImageUrl ?: current.profileImageUrl,
        )
    }

    override suspend fun deleteMe() = Unit

    override fun pagedActivities(type: ActivityType): Flow<PagingData<Activity>> =
        flowOf(PagingData.from(FakeData.activities.filter { it.type == type }))
    override suspend fun notificationSettings(): NotificationSettings = settings
    override suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings {
        this.settings = settings
        return settings
    }
}

class FakePairingRepository : PairingRepository {
    // 서버는 연결 성공 시 코드를 지운다. 목업도 같은 관찰 가능한 동작을 흉내 내야
    // 「코드가 아직 살아 있으면 아무도 안 쓴 것」이라는 판정을 화면에서 검증할 수 있다.
    private val active = mutableListOf<PairingCode>()

    override suspend fun issueCode(): PairingCode =
        FakeData.pairing.also { active += it }

    override suspend fun activeCodes(): List<PairingCode> = active.toList()

    override suspend fun connect(pairingCode: String) {
        active.removeAll { it.pairingCode == pairingCode }
    }
}

class FakeChildRepository : ChildRepository {
    /** 목업에서 해제된 자녀. 목록에서 빠져야 화면이 결과를 확인할 수 있다. */
    private val removed = mutableSetOf<String>()

    override suspend fun children(): List<ChildProfile> =
        FakeData.children.filterNot { it.id in removed }
    override suspend fun child(childId: String): ChildProfile =
        FakeData.children.first { it.id == childId }
    override suspend fun updateChild(
        childId: String,
        nickname: String?,
        profileImageUrl: String?,
    ): ChildProfile = child(childId).copy(
        alias = nickname ?: child(childId).alias,
        profileImageUrl = profileImageUrl ?: child(childId).profileImageUrl,
    )

    // 목 빌드에는 올릴 서버가 없다. 고른 주소를 그대로 돌려주면 화면 흐름은 확인된다.
    override suspend fun uploadProfileImage(uri: String): String = uri

    override suspend fun unpair(childId: String) {
        delay(200)
        removed += childId
    }

    override suspend fun forceLogout(childId: String) {
        delay(200)
    }
}


class FakeLocationRepository : LocationRepository {
    private val historyMap = mutableMapOf<String, MutableList<LocationPoint>>()
    private val realtimeMap = mutableMapOf<String, Boolean>()

    override suspend fun track(childId: String, latitude: String, longitude: String): LocationPoint {
        val stamp = "2026-08-25T17:10:00"
        val point = LocationPoint(
            lat = latitude,
            lng = longitude,
            recordedAt = stamp,
            segmentStart = "2026-08-25T17:05:00",
            segmentEnd = stamp,
            updatedAt = stamp,
        )
        historyMap.getOrPut(childId) { FakeData.locationSegments.toMutableList() }.add(0, point)
        return point
    }

    override suspend fun history(childId: String): List<LocationPoint> =
        historyMap[childId] ?: FakeData.locationSegments

    override suspend fun realtimeEnabled(childId: String): Boolean =
        realtimeMap[childId] ?: false

    override suspend fun toggleRealtime(childId: String, enabled: Boolean): Boolean {
        realtimeMap[childId] = enabled
        return enabled
    }
}

class FakeDeviceRepository : DeviceRepository {
    override suspend fun reportEvent(childId: String, type: String) {}
    override suspend fun registerPushToken(token: String) = Unit
    override suspend fun unregisterPushToken(token: String) = Unit
}

class FakeDashboardRepository(
    private val locationRepository: LocationRepository,
    private val notificationRepository: NotificationRepository,
    private val placeRepository: PlaceRepository,
) : DashboardRepository {
    override suspend fun get(childId: String): ChildDashboard = ChildDashboard(
        childId = childId,
        currentLocation = locationRepository.history(childId).firstOrNull(),
        realtimeEnabled = false,
        recentNotifications = notificationRepository.list(childId).take(3),
        blockRulePatterns = emptyList(),
        places = placeRepository.list(childId),
    )
}

class FakePlaceRepository : PlaceRepository {
    private val items = FakeData.places.toMutableList()
    override suspend fun list(childId: String): List<Geofence> = items.toList()
    override suspend fun create(childId: String, place: Geofence): Geofence {
        val created = place.copy(id = "${items.size + 1}")
        items.add(created)
        return created
    }
    override suspend fun delete(childId: String, placeId: String) {
        items.removeAll { it.id == placeId }
    }
}

class FakeLocationEventRepository : LocationEventRepository {
    override suspend fun events(childId: String): List<LocationEvent> = FakeData.locationEvents
}

class FakeChildAlertRepository(private val feed: MockNotificationFeed) : ChildAlertRepository {
    override suspend fun upload(childId: String, alerts: List<ChildAlertEvent>) {
        alerts.forEach { alert ->
            val parentType = childAlertTypeToParent(alert.type) ?: return@forEach
            feed.add(
                type = parentType,
                title = alert.title,
                body = alert.body.ifBlank { alert.title },
                dedupeKey = alert.id,
                childId = FakeData.loggedInChildProfileId,
            )
        }
    }

    override suspend fun list(childId: String): List<ChildAlertEvent> = emptyList()
}

class FakeNotificationRepository(private val feed: MockNotificationFeed) : NotificationRepository {
    /** 목업 피드에는 읽음 상태가 없어 여기서 들고 있는다. 화면이 읽음 처리 결과를 확인할 수 있어야 한다. */
    private val readIds = mutableSetOf<String>()

    override suspend fun list(recipientId: String, childId: String?): List<AppNotification> =
        (feed.all() + SAMPLES)
            .filter { childId == null || it.childId == null || it.childId == childId }
            .map { if (it.id in readIds) it.copy(read = true) else it }

    override suspend fun markRead(notificationId: String) {
        readIds += notificationId
    }

    override suspend fun markAllRead(recipientId: String): Int {
        val unread = list(recipientId).filterNot { it.read }
        readIds += unread.map { it.id }
        return unread.size
    }

    override suspend fun unreadCount(recipientId: String): Int =
        list(recipientId).count { !it.read }

    private companion object {
        val SAMPLES = listOf(
            AppNotification(
                id = "n1", type = "LOCATION_EXIT", title = "장소 이탈",
                message = "민준이가 '학교' 구역을 벗어났습니다.", createdAt = "14:20",
                read = false, childId = FakeData.minjun.id,
            ),
            AppNotification(
                id = "n2", type = "HARMFUL_SITE", title = "유해 접속 감지",
                message = "지아의 부적절한 사이트 접속 시도가 차단되었습니다.", createdAt = "12:05",
                read = true, childId = FakeData.jia.id,
            ),
        )
    }
}
/**
 * 목업 블랙/화이트리스트.
 *
 * 예전에는 목록을 메모리에만 들고 있어서 앱을 다시 켜면 부모가 바꾼 차단 설정이 통째로
 * 초기값으로 돌아갔고, `remove` 가 빈 함수라 카테고리 토글을 꺼도 아무 일도 일어나지 않았다.
 *
 * id 는 인덱스가 아니라 **키워드 그 자체**다. 인덱스로 만들면 목록이 바뀔 때마다 id 가 밀려서
 * `remove(itemId)` 가 엉뚱한 항목을 지운다. 실구현체도 같은 규칙을 쓴다.
 */
class FakeBlocklistRepository(
    private val store: BlocklistStore,
    private val feed: MockNotificationFeed,
) : BlocklistRepository {
    // 서버에 허용 목록 개념이 없어 차단 패턴만 다룬다. 목업 저장소의 blacklist 만 쓴다.
    override suspend fun get(childId: String): List<BlocklistItem> =
        store.blacklist().map { BlocklistItem(id = it, pattern = it) }

    override suspend fun add(childId: String, pattern: String): BlocklistItem {
        store.add("BLACK", pattern)
        return BlocklistItem(id = pattern, pattern = pattern)
    }

    override suspend fun remove(ruleId: String) {
        store.remove(ruleId)
    }

    override suspend fun reportVisit(childId: String, target: String): Boolean {
        feed.add(
            type = "HARMFUL",
            title = "유해 사이트 접속 차단",
            body = target + " 이(가) 포함된 사이트 접속 시도가 차단되었어요.",
            dedupeKey = "HARMFUL-" + target,
            childId = FakeData.loggedInChildProfileId,
        )
        // 목업은 저장된 패턴에 걸리면 차단으로 판정한다.
        return store.blacklist().any { target.contains(it, ignoreCase = true) }
    }
}

class FakeScreenshotRepository(
    private val store: CapturedShotStore,
    private val commandRepository: CommandRepository,
) : ScreenshotRepository {
    override fun paged(childId: String): Flow<PagingData<Screenshot>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            val all = store.list()
            val totalPages = kotlin.math.ceil(all.size / size.toDouble()).toInt().coerceAtLeast(1)
            val slice = all.drop(page * size).take(size)
            PageResponse(slice, page, size, all.size.toLong(), totalPages)
        }
    }.flow

    override suspend fun list(childId: String) = store.list()

    /**
     * 실서버라면 202 후 서버가 자녀 소켓으로 명령을 민다. 목업은 같은 프로세스의
     * 명령 채널로 직접 밀어서, 자녀 계정으로 전환했을 때 요청 푸시가 뜨게 한다.
     */
    override suspend fun requestCapture(childId: String) {
        (commandRepository as? FakeCommandRepository)?.emit(
            RemoteCommand(
                requestId = "mock-cap-" + System.currentTimeMillis(),
                type = RemoteCommandType.SCREEN_CAPTURE,
                childId = childId,
                requestedBy = FakeData.parent.name,
            ),
        )
    }

    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        trigger: String,
    ): Screenshot = store.add(
        source = file,
        description = "화면 캡처",
        trigger = trigger,
    )
}

class FakeCommunityRepository : CommunityRepository {
    private val items = FakeData.posts.toMutableList()

    override fun pagedPosts(keyword: String?): Flow<PagingData<Post>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            // 서버와 같이 최신순으로 내린다. 정렬하지 않으면 목 빌드에서만 순서가 뒤죽박죽이라,
            // 실기기로 옮기기 전까지 정렬 문제를 알아채지 못한다.
            val all = (
                if (keyword.isNullOrBlank()) items.toList()
                else items.filter {
                    it.title.contains(keyword, ignoreCase = true) ||
                        it.author.contains(keyword, ignoreCase = true)
                }
                ).sortedWith(compareByDescending<Post> { it.createdAtLabel }.thenByDescending { it.id })
            val totalPages = kotlin.math.ceil(all.size / size.toDouble()).toInt().coerceAtLeast(1)
            val slice = all.drop(page * size).take(size)
            PageResponse(slice, page, size, all.size.toLong(), totalPages)
        }
    }.flow

    override suspend fun posts() = items.toList()

    override suspend fun post(postId: String) = items.first { it.id == postId }

    override suspend fun create(
        title: String,
        content: String,
        attachments: List<String>,
        links: List<String>,
    ): Post {
        val created = Post(
            id = (items.size + 1).toString(),
            title = title,
            author = FakeData.parent.nickname ?: FakeData.parent.name,
            content = content,
            createdAtLabel = "방금",
            attachments = attachments,
            links = links,
        )
        items.add(0, created)
        return created
    }

    override suspend fun update(
        postId: String,
        title: String,
        content: String,
        attachments: List<String>,
        links: List<String>,
    ): Post {
        val index = items.indexOfFirst { it.id == postId }
        val updated = items[index].copy(title = title, content = content, attachments = attachments, links = links)
        items[index] = updated
        return updated
    }

    override suspend fun delete(postId: String) {
        items.removeAll { it.id == postId }
    }

    override suspend fun like(postId: String): LikeResult {
        val i = items.indexOfFirst { it.id == postId }
        if (i < 0) return LikeResult(liked = false, likeCount = 0)
        val p = items[i]
        val liked = !p.liked
        val count = (if (liked) p.likeCount + 1 else p.likeCount - 1).coerceAtLeast(0)
        items[i] = p.copy(liked = liked, likeCount = count)
        return LikeResult(liked, count)
    }

    override suspend fun comment(postId: String, content: String): Comment {
        val created = Comment(
            id = (items.sumOf { it.comments.size } + 1).toString(),
            memberId = FakeData.parent.id,
            author = FakeData.parent.nickname ?: FakeData.parent.name,
            text = content,
            createdAt = "방금",
        )
        val i = items.indexOfFirst { it.id == postId }
        if (i >= 0) items[i] = items[i].copy(comments = items[i].comments + created)
        return created
    }

    override suspend fun deleteComment(postId: String, commentId: String) {
        val i = items.indexOfFirst { it.id == postId }
        if (i >= 0) items[i] = items[i].copy(comments = items[i].comments.filterNot { it.id == commentId })
    }

    /** 목 모드에서는 올릴 서버가 없으니 고른 URI 를 그대로 경로처럼 쓴다. */
    override suspend fun uploadAttachment(uri: String): String = uri
}

class FakeInquiryRepository : InquiryRepository {
    private val items = mutableListOf(
        InquiryDetail(
            id = "1",
            title = "페어링 코드가 안 나와요",
            content = "부모 앱에서 버튼을 눌러도 코드가 생성되지 않습니다.",
            answered = true,
            createdAt = "2024-08-20T10:12:00",
            answer = InquiryAnswer("1", "안녕하세요. 앱을 최신 버전으로 업데이트하신 후 다시 시도해 주세요.", "2024-08-21T09:00:00"),
        ),
        InquiryDetail(
            id = "2",
            title = "위치 추적 간격 설정 문의",
            content = "위치 추적 간격을 10분보다 짧게 설정할 수 있나요?",
            answered = false,
            createdAt = "2024-08-24T18:30:00",
        ),
    )

    private fun InquiryDetail.summary() = Inquiry(id, title, answered, createdAt, "나")

    override suspend fun list(page: Int): Paged<Inquiry> =
        Paged(items.map { it.summary() }, page = 0, totalPages = 1)

    override suspend fun get(id: String): InquiryDetail = items.first { it.id == id }

    override suspend fun create(
        title: String,
        content: String,
        attachments: List<String>,
        linkUrl: String?,
    ): Inquiry {
        val created = InquiryDetail(
            id = (items.size + 1).toString(),
            title = title,
            content = content,
            attachments = attachments,
            linkUrl = linkUrl,
            answered = false,
            createdAt = "방금",
        )
        items.add(0, created)
        return created.summary()
    }

    override suspend fun update(
        id: String,
        title: String,
        content: String,
        attachments: List<String>,
        linkUrl: String?,
    ): Inquiry {
        val index = items.indexOfFirst { it.id == id }
        val updated = items[index].copy(
            title = title,
            content = content,
            attachments = attachments,
            linkUrl = linkUrl,
        )
        items[index] = updated
        return updated.summary()
    }

    override suspend fun delete(id: String) {
        items.removeAll { it.id == id }
    }

    /** 목 모드에서는 올릴 서버가 없으니 고른 URI 를 그대로 경로처럼 쓴다. */
    override suspend fun uploadAttachment(uri: String): String = uri
}

class FakeNoticeRepository : NoticeRepository {
    private val items = listOf(
        NoticeDetail(
            id = "1",
            title = "[안내] 개인정보처리방침 개정",
            content = "루키즈 서비스 개인정보처리방침이 개정되었습니다. 자세한 내용은 아래 링크를 참고해 주세요.",
            links = listOf("https://rookiekids.example.com/privacy"),
            createdAt = "2024-08-20T09:00:00",
        ),
        NoticeDetail(
            id = "2",
            title = "루키즈 2.5.0 업데이트 - 유해사이트 감지 강화",
            content = "키워드 기반 유해사이트 차단이 강화되었습니다.",
            createdAt = "2024-08-15T09:00:00",
        ),
    )

    override suspend fun list(keyword: String?, page: Int): Paged<Notice> {
        val filtered = items.filter { keyword.isNullOrBlank() || it.title.contains(keyword, ignoreCase = true) }
        return Paged(
            filtered.map { Notice(it.id, it.title, it.createdAt, it.attachments.size, it.links.size) },
            page = 0,
            totalPages = 1,
        )
    }

    override suspend fun get(id: String): NoticeDetail = items.first { it.id == id }
}



class FakeWalletRepository : WalletRepository {
    private var balance = 45_000
    override suspend fun getMyWallet(): Wallet =
        Wallet("w-parent", balance, "방금", monthlyChargeTotal = 90_000, monthlySpendTotal = 32_000)
    override suspend fun getTransactions(type: String?, page: Int): List<WalletTransaction> = listOf(
        WalletTransaction("tx1", "CHARGE", 50000, 50000, null, "08.15"),
        WalletTransaction("tx2", "TRANSFER_OUT", -5000, 45000, "101", "08.20")
    )
    override suspend fun transferToChild(childId: String, amount: Int, memo: String?, security: TxSecurity?): TransferResult {
        balance -= amount
        return TransferResult(balance, 10000 + amount, "tx-${System.currentTimeMillis()}")
    }
    override suspend fun reclaimFromChild(childId: String, amount: Int, security: TxSecurity?): TransferResult {
        balance += amount
        return TransferResult(balance, 10000 - amount, "tx-${System.currentTimeMillis()}")
    }
    override suspend fun getChildWallet(childId: String): Wallet = Wallet("w-child", 10000, "방금")
    override suspend fun prepareCharge(amount: Int, method: String, cardId: String, security: TxSecurity?): Charge = Charge("c1", amount, "PENDING", "key1", method)
    override suspend fun confirmCharge(chargeId: String, paymentKey: String, amount: Int): Wallet {
        balance += amount
        return Wallet("w-parent", balance, "방금")
    }

    override suspend fun getChildTransfers(childId: String, page: Int): List<WalletTransaction> = listOf(
        WalletTransaction("tf1", "TRANSFER_OUT", -5000, 40000, childId, "08.20"),
        WalletTransaction("tf2", "TRANSFER_IN", 2000, 42000, childId, "08.22"),
    )

    override suspend fun getChildTransactions(childId: String, type: String?, page: Int): List<WalletTransaction> = listOf(
        WalletTransaction("ct1", "TRANSFER_IN", 20000, 20000, childId, "08.30"),
        WalletTransaction("ct2", "PAYMENT", -3200, 16800, childId, "08.29"),
        WalletTransaction("ct3", "TRANSFER_OUT", -3000, 13800, childId, "08.15"),
    )

    override suspend fun getChargeHistory(page: Int): List<com.hack.kidhack.domain.model.ChargeHistoryItem> = listOf(
        com.hack.kidhack.domain.model.ChargeHistoryItem("kid_demo1", 50000, "PAID", "08.15 10:00"),
        com.hack.kidhack.domain.model.ChargeHistoryItem("kid_demo2", 10000, "REFUNDED", "08.10 09:00", "08.11 12:00"),
    )
    override suspend fun refundCharge(merchantUid: String): Int = balance
    override suspend fun cancelCharge(merchantUid: String): String = "CANCELLED"
}

class FakeCardRepository : CardRepository {
    private val items = mutableListOf(
        Card("card1", "1234", "12/28", "김지영"),
        Card("card2", "5678", "05/26", "김지영")
    )
    override suspend fun register(
        cardNumber: String,
        expiry: String,
        cvc: String,
        cardPassword2: String,
        birthDate: String,
        ownerName: String,
        paymentPassword: String,
    ): Card {
        val new = Card("card-${items.size + 1}", cardNumber.takeLast(4), expiry, ownerName)
        items.add(new)
        return new
    }
    override suspend fun list(): List<Card> = items.toList()
    override suspend fun delete(cardId: String) { items.removeAll { it.cardId == cardId } }
}

class FakeChatRepository(
    private val sessionRepository: SessionRepository,
) : ChatRepository {
    private val store = mutableMapOf(
        "room-minjun" to FakeData.chatMessages.toMutableList(),
        "room-jia" to mutableListOf<ChatMessage>(),
    )
    private val incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)

    override suspend fun getOrCreateRoom(parentId: Long, childId: Long): ChatRoom {
        return when (childId) {
            102L -> ChatRoom(
                id = "room-jia",
                title = "이지아",
                lastMessage = "아직 대화가 없어요",
                lastAt = "",
                parentId = parentId,
                childId = childId,
            )
            else -> ChatRoom(
                id = "room-minjun",
                title = "김민준",
                lastMessage = "학원 끝나요",
                lastAt = "14:32",
                parentId = parentId,
                childId = childId,
            )
        }
    }

    /** 방 제목은 보는 쪽 기준으로 바뀐다 — 부모는 자녀 이름을, 자녀는 보호자 이름을 본다. */
    override suspend fun rooms(): List<ChatRoom> = listOf(
        ChatRoom("room-minjun", "김민준", "학원 끝나요", "14:32", unreadCount = 2, parentId = 1L, childId = 101L),
        ChatRoom("room-jia", "이지아", "아직 대화가 없어요", "", unreadCount = 0, parentId = 1L, childId = 102L),
    )

    override suspend fun messages(roomId: String, before: String?, size: Int): ChatMessagePage =
        ChatMessagePage(
            messages = store[roomId] ?: emptyList(),
            // 목 데이터는 대화가 짧아 다음 장이 없다.
            hasMore = false,
            oldestSentAt = null,
        )

    override fun observeMessages(roomId: String): Flow<ChatMessage> = incoming.asSharedFlow()

    override suspend fun send(roomId: String, content: String): ChatMessage {
        val role = sessionRepository.current()?.user?.role ?: Role.PARENT
        val msg = ChatMessage("m-${System.currentTimeMillis()}", roomId, role, content, "방금")
        store.getOrPut(roomId) { mutableListOf() }.add(msg)
        incoming.emit(msg)
        return msg
    }

    override suspend fun uploadMedia(localUri: String, mimeType: String): String =
        // 목 모드에서는 서버가 없으니 넘어온 로컬 URI 를 그대로 미디어 경로로 쓴다.
        localUri

    override suspend fun sendMedia(
        roomId: String,
        type: MessageType,
        mediaUrl: String,
    ): ChatMessage {
        val role = sessionRepository.current()?.user?.role ?: Role.PARENT
        val msg = ChatMessage(
            id = "m-${System.currentTimeMillis()}",
            roomId = roomId,
            fromRole = role,
            text = "",
            sentAt = "방금",
            type = type,
            mediaUrl = mediaUrl,
        )
        store.getOrPut(roomId) { mutableListOf() }.add(msg)
        incoming.emit(msg)
        return msg
    }

    override suspend fun markRead(roomId: String, lastMessageId: String?) {}
}

class FakePaymentRepository : PaymentRepository {
    override suspend fun checkout(amount: Int, method: String, productId: Int): Payment =
        Payment("pay-${System.currentTimeMillis()}", "상품 구매", -amount, "방금", "out")
    override suspend fun history(): List<Payment> = FakeData.payments
}

/** 구독·충전 포인트 지갑(부모 PayScreen 용). 용돈 지갑은 [FakeAllowanceRepository] 가 따로 관리한다. */
class FakePointRepository : PointRepository {
    private var current = FakeData.pointBalance.balance
    private val log = mutableListOf(
        PointHistory("ph1", "충전", 50_000, "08.15"),
        PointHistory("ph2", "구독 결제", -9_900, "08.01"),
    )

    override suspend fun balance() = PointBalance(current)

    override suspend fun history() = log.toList()

    override suspend fun use(productId: Int, point: Int) {
        require(point in 1..current) { "포인트가 부족해요" }
        current -= point
        log.add(0, PointHistory("ph-" + System.currentTimeMillis(), "포인트 사용", -point, "오늘"))
    }
}

/**
 * 목업 카메라 촬영 저장소.
 *
 * 예전에는 촬영본을 캡처 저장소([CapturedShotStore])에 섞어 넣어 부모 「화면 캡처」
 * 목록에 끼워 보여 줬다. 부모 「카메라 촬영」 화면이 생겼으니 전용 피드로 분리한다.
 * 피드는 계정 전환에도 지워지지 않아 목업 서버 역할을 한다.
 */
class FakePhotoRepository(
    private val feed: MockPhotoFeed,
    private val commandRepository: CommandRepository,
) : PhotoRepository {
    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        requestId: String?,
    ): ChildPhoto {
        delay(300)
        return feed.add(file, requestId)
    }

    override suspend fun list(childId: String): List<ChildPhoto> = feed.list()

    override suspend fun requestShot(childId: String) {
        (commandRepository as? FakeCommandRepository)?.emit(
            RemoteCommand(
                requestId = "mock-cam-" + System.currentTimeMillis(),
                type = RemoteCommandType.CAMERA_SHOT,
                childId = childId,
                requestedBy = FakeData.parent.name,
            ),
        )
    }
}

/**
 * 목 모드용 요청 채널.
 *
 * 서버가 없어도 캡처/촬영 플로우 전체를 손으로 돌려 볼 수 있게 [emit] 을 열어 뒀다.
 * `ChildServicesScreen` 의 「요청 시뮬레이션」 버튼이 이걸 호출한다.
 */
class FakeCommandRepository : CommandRepository {
    private val stream = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 8)

    override fun observe(): Flow<RemoteCommand> = stream.asSharedFlow()

    /** 목 모드는 소켓 없이 같은 프로세스에서 바로 전달하므로 항상 연결된 것으로 본다. */
    override val connected: Flow<Boolean> = flowOf(true)

    override fun disconnect() = Unit

    override suspend fun ack(childId: String, requestId: String, status: String, message: String?) = Unit

    fun emit(command: RemoteCommand) {
        stream.tryEmit(command)
    }
}

class FakeAppUsageRepository(private val feed: MockNotificationFeed) : AppUsageRepository {
    override suspend fun list(
        childId: String,
        groupBy: String,
        from: String?,
        to: String?,
    ): List<AppUsageStat> = when (groupBy) {
        "HOUR" -> FakeData.usagesByHour
        "DAY" -> FakeData.usagesByDay
        else -> FakeData.usages.map { AppUsageStat(it.appName, it.durationSec) }
    }
    /** 목업에는 자녀 기기가 없다. 요청은 받되 아무 일도 일어나지 않는다. */
    override suspend fun requestReport(childId: String) = Unit

    override suspend fun report(childId: String, usages: List<AppUsage>) {
        val totalMinutes = usages.sumOf { it.durationSec } / 60
        if (totalMinutes < DAILY_LIMIT_MINUTES) return
        feed.addOncePerDay(
            type = "APP_USAGE",
            title = "앱 사용 시간 한도 도달",
            body = "오늘 사용 시간이 " + DAILY_LIMIT_MINUTES / 60 + "시간 한도에 도달했어요.",
            childId = childId,
        )
    }

    private companion object {
        const val DAILY_LIMIT_MINUTES = 180
    }
}

class FakeAppControlRepository : AppControlRepository {
    private var nextId = 4
    private val db = mutableListOf(
        AppControlItem("1", "com.youtube", "YouTube", dailyLimitMinutes = 90),
        AppControlItem("2", "com.tiktok", "TikTok", dailyLimitMinutes = 0),
        AppControlItem("3", "com.roblox", "Roblox", dailyLimitMinutes = null),
    )
    private val installed = mutableListOf(
        InstalledApp("com.youtube", "YouTube"),
        InstalledApp("com.tiktok", "TikTok"),
        InstalledApp("com.instagram", "Instagram"),
        InstalledApp("com.roblox", "Roblox"),
        InstalledApp("com.pubg", "배틀그라운드"),
    )

    override suspend fun items(childId: String): List<AppControlItem> = db.toList()

    override suspend fun getInstalledApps(childId: String): List<InstalledApp> = installed.toList()

    override suspend fun syncInstalledApps(childId: String, apps: List<InstalledApp>): Int {
        apps.forEach { app ->
            if (installed.none { it.packageName == app.packageName }) installed += app
        }
        return apps.size
    }

    override suspend fun addRule(
        childId: String,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int?,
    ): AppControlItem {
        db.removeAll { it.packageName == packageName }
        val item = AppControlItem(
            ruleId = (nextId++).toString(),
            packageName = packageName,
            name = appName,
            dailyLimitMinutes = dailyLimitMinutes,
        )
        db += item
        return item
    }

    override suspend fun updateRule(
        ruleId: String,
        dailyLimitMinutes: Int?,
        enabled: Boolean?,
    ): AppControlItem {
        val idx = db.indexOfFirst { it.ruleId == ruleId }
        require(idx >= 0) { "규칙을 찾을 수 없어요" }
        val updated = db[idx].copy(
            dailyLimitMinutes = dailyLimitMinutes ?: db[idx].dailyLimitMinutes,
            enabled = enabled ?: db[idx].enabled,
        )
        db[idx] = updated
        return updated
    }

    override suspend fun deleteRule(ruleId: String) {
        db.removeAll { it.ruleId == ruleId }
    }

    /** 자녀당 한도. null 이면 제한 없음 — 목업도 서버와 같은 규약을 지킨다. */
    private val screenTimeLimits = mutableMapOf<String, Int?>()

    override suspend fun screenTimeLimit(childId: String): Int? = screenTimeLimits[childId]

    override suspend fun setScreenTimeLimit(childId: String, dailyLimitMinutes: Int?): Int? {
        screenTimeLimits[childId] = dailyLimitMinutes
        return dailyLimitMinutes
    }

    override suspend fun reportScreenTimeReached(childId: String, usedMinutes: Int): Boolean =
        screenTimeLimits[childId] != null
}

/**
 * 목 모드 용돈 지갑.
 *
 * Koin `single` 이라 한 프로세스 안에서 부모 계정과 자녀 계정이 **같은 지갑**을 본다.
 * 덕분에 서버 없이도 「부모로 로그인 → 용돈 보내기 → 자녀로 로그인 → QR 결제」가 그대로 재현된다.
 *
 * 목 데이터의 부모용 childId와 자녀 세션 userId를 동일하게 `101`로 맞춰
 * 두 역할이 같은 용돈 지갑을 보게 한다.
 * 실서버 구현([RealAllowanceRepository])은 childId 별로 정상 분리된다.
 */
class FakeAllowanceRepository : AllowanceRepository {
    private val lock = Any()
    private var balance = SEED_BALANCE
    private val ledger = mutableListOf(
        AllowanceEntry("seed-2", AllowanceType.PAYMENT, -1_500, "문구점 결제", "08.20"),
        AllowanceEntry("seed-1", AllowanceType.SEND, SEED_BALANCE + 1_500, "엄마가 보낸 용돈", "08.15"),
    )

    override suspend fun balance(childId: String): Allowance =
        synchronized(lock) { Allowance(balance) }

    override suspend fun history(childId: String): List<AllowanceEntry> =
        synchronized(lock) { ledger.toList() }

    override suspend fun send(childId: String, amount: Int, memo: String?): Allowance {
        require(amount > 0) { "금액을 확인해 주세요" }
        return synchronized(lock) {
            balance += amount
            ledger.add(0, entry(AllowanceType.SEND, amount, memo ?: "엄마가 보낸 용돈"))
            Allowance(balance)
        }
    }

    override suspend fun withdraw(childId: String, amount: Int, memo: String?): Allowance {
        require(amount > 0) { "금액을 확인해 주세요" }
        return synchronized(lock) {
            require(amount <= balance) { "회수할 수 있는 금액을 넘었어요" }
            balance -= amount
            ledger.add(0, entry(AllowanceType.WITHDRAW, -amount, memo ?: "부모님이 회수"))
            Allowance(balance)
        }
    }

    override suspend fun pay(
        childId: String,
        token: String,
        amount: Int,
        merchant: String?,
    ): AllowancePayment {
        delay(400)
        return synchronized(lock) {
            require(amount in 1..balance) { "잔액이 부족해요" }
            balance -= amount
            ledger.add(0, entry(AllowanceType.PAYMENT, -amount, merchant ?: "QR 결제"))
            AllowancePayment(
                id = "apay-" + System.currentTimeMillis(),
                amount = amount,
                balanceAfter = balance,
                approvedAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                merchant = merchant,
            )
        }
    }

    private fun entry(type: AllowanceType, amount: Int, description: String) = AllowanceEntry(
        id = "al-" + System.currentTimeMillis(),
        type = type,
        amount = amount,
        description = description,
        dateLabel = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date()),
    )

    private companion object {
        const val SEED_BALANCE = 12_000
    }
}

/**
 * 목 모드 QR 결제 상태 저장소. 자녀 발급([FakeQrPayRepository]) ↔ 단말 승인
 * ([FakeStoreTerminalRepository]) 가 같은 프로세스에서 공유해 서버 없이도 전체 플로우를 재현한다.
 */
private object FakeQrPayStore {
    val lock = Any()
    class Rec(var status: String, var amount: Int, var storeName: String, val issuedAt: Long)
    val tokens = mutableMapOf<String, Rec>()
    val history = mutableListOf<QrPaymentHistoryItem>()
    var seq = 0
    const val TTL_MILLIS = 60_000L
}

/** 목 모드 QR 결제(태그 15·18·19). 서버 발급과 동일하게 클라이언트가 토큰을 만들지 않는다. */
class FakeQrPayRepository : QrPayRepository {
    override suspend fun issueQr(): QrTicket = synchronized(FakeQrPayStore.lock) {
        val now = System.currentTimeMillis()
        val token = "qr_fake_${++FakeQrPayStore.seq}_$now"
        FakeQrPayStore.tokens[token] = FakeQrPayStore.Rec("ISSUED", 0, "매장", now)
        QrTicket(token, "ISSUED", now + FakeQrPayStore.TTL_MILLIS)
    }

    override suspend fun qrStatus(qrToken: String): QrTicket = synchronized(FakeQrPayStore.lock) {
        val rec = FakeQrPayStore.tokens[qrToken]
        val now = System.currentTimeMillis()
        val status = when {
            rec == null -> "EXPIRED"
            rec.status == "ISSUED" && now > rec.issuedAt + FakeQrPayStore.TTL_MILLIS -> "EXPIRED"
            else -> rec.status
        }
        QrTicket(qrToken, status, (rec?.issuedAt ?: now) + FakeQrPayStore.TTL_MILLIS)
    }

    override suspend fun cancel(qrToken: String) = synchronized(FakeQrPayStore.lock) {
        FakeQrPayStore.tokens[qrToken]?.status = "CANCELLED"
        for (i in FakeQrPayStore.history.indices) {
            if (FakeQrPayStore.history[i].qrToken == qrToken) {
                FakeQrPayStore.history[i] = FakeQrPayStore.history[i].copy(status = "CANCELLED")
            }
        }
    }

    override suspend fun paymentHistory(childId: String?, page: Int): List<QrPaymentHistoryItem> =
        synchronized(FakeQrPayStore.lock) { FakeQrPayStore.history.toList() }
}

/** 목 모드 매장 단말(태그 16·17) — 회원 앱과 분리된 테스트용. */
class FakeStoreTerminalRepository : StoreTerminalRepository {
    private val terminals = mutableListOf<StoreTerminalInfo>()
    private var seq = 0

    override suspend fun issueTerminal(storeName: String): StoreTerminalInfo {
        seq += 1
        val terminal = StoreTerminalInfo(
            id = seq.toLong(),
            storeName = storeName,
            terminalToken = "term_fake_$seq",
            status = "ACTIVE",
        )
        terminals.add(terminal)
        return terminal
    }

    override suspend fun terminals(): List<StoreTerminalInfo> = terminals.toList()

    override suspend fun approve(terminalToken: String, qrToken: String, amount: Int): QrPaymentHistoryItem {
        val storeName = terminals.firstOrNull { it.terminalToken == terminalToken }?.storeName ?: "매장"
        val label = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date())
        return synchronized(FakeQrPayStore.lock) {
            FakeQrPayStore.tokens[qrToken]?.let {
                it.status = "PAID"
                it.amount = amount
                it.storeName = storeName
            }
            val item = QrPaymentHistoryItem(qrToken, storeName, amount, "PAID", label)
            FakeQrPayStore.history.add(0, item)
            item
        }
    }
}

/**
 * 목 전자금융 — 데모에서도 실서버처럼 틀린 값을 거부한다(2차 설계: 데모 충실성).
 * OTP 는 고정 코드 428193, 계좌비번은 마지막으로 설정한 값(없으면 기본 135790)만 통과.
 */
class FakeEfinRepository : EfinRepository {
    // 데모 시작 상태: 실명확인·결제비번 미완료 → 첫 카드 등록에서 위저드가 단계대로 흐른다.
    private var paymentPw: String? = null
    private var verified: Boolean = false

    override suspend fun issueOtp(): OtpIssue = OtpIssue(phone = "010-****-5678", code = "428193", expiresIn = 180)
    override suspend fun verifyOtp(code: String): Boolean = code == "428193"
    override suspend fun verifyPaymentPassword(password: String): Boolean = password == paymentPw
    override suspend fun setupPaymentPassword(password: String) { paymentPw = password }
    override suspend fun changePaymentPassword(currentPassword: String, newPassword: String, loginPassword: String) {
        paymentPw = newPassword
    }
    override suspend fun paymentPasswordSet(): Boolean = paymentPw != null
    override suspend fun identityVerified(): Boolean = verified
    override suspend fun verifyIdentity(
        name: String,
        birthDate: String,
        idType: String,
        idNumber: String,
        issuedDate: String,
    ): IdentityResult {
        verified = name.isNotBlank()
        return IdentityResult(verified = verified, name = name, verifiedAt = "방금")
    }
}
