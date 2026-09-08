package com.hack.kidhack.data.repository.real

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hack.kidhack.core.common.DeviceId
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.remote.api.AllowanceApi
import com.hack.kidhack.data.remote.api.AppControlApi
import com.hack.kidhack.data.remote.api.AppUsageApi
import com.hack.kidhack.data.remote.api.AuthApi
import com.hack.kidhack.data.remote.api.BlocklistApi
import com.hack.kidhack.data.remote.api.CardApi
import com.hack.kidhack.data.remote.api.QrPayApi
import com.hack.kidhack.data.remote.api.StoreTerminalApi
import com.hack.kidhack.data.crypto.CardCrypto
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.data.remote.api.ChatApi
import com.hack.kidhack.data.remote.api.ChildApi
import com.hack.kidhack.data.remote.api.FileApi
import com.hack.kidhack.data.remote.api.CommandApi
import com.hack.kidhack.data.remote.api.CommunityApi
import com.hack.kidhack.data.remote.api.InquiryApi
import com.hack.kidhack.data.remote.api.NoticeApi
import com.hack.kidhack.data.remote.api.DashboardApi
import com.hack.kidhack.data.remote.api.DeviceApi
import com.hack.kidhack.data.remote.api.LocationApi
import com.hack.kidhack.data.remote.api.LocationEventApi
import com.hack.kidhack.data.remote.api.ChildAlertApi
import com.hack.kidhack.data.remote.api.NotificationApi
import com.hack.kidhack.data.remote.api.PairingApi
import com.hack.kidhack.data.remote.api.PhotoApi
import com.hack.kidhack.data.remote.api.PlaceApi
import com.hack.kidhack.data.remote.api.PaymentApi
import com.hack.kidhack.data.remote.api.PaymentChargeApi
import com.hack.kidhack.data.remote.api.PointApi
import com.hack.kidhack.data.remote.api.ScreenshotApi
import com.hack.kidhack.data.remote.api.UserApi
import com.hack.kidhack.data.remote.dto.AllowancePaymentRequest
import com.hack.kidhack.data.remote.dto.WalletChargePrepareRequest
import com.hack.kidhack.data.remote.dto.WalletChargeApproveRequest
import com.hack.kidhack.data.remote.dto.WalletTransferRequest
import com.hack.kidhack.data.remote.dto.CardPlainPayload
import com.hack.kidhack.data.remote.dto.EncryptedCardRequest
import com.hack.kidhack.data.remote.dto.QrApproveRequest
import com.hack.kidhack.data.remote.dto.TerminalIssueRequest
import com.hack.kidhack.data.remote.dto.AllowanceTransferRequest
import com.hack.kidhack.data.remote.api.WalletApi
import com.hack.kidhack.data.remote.api.EfinApi
import com.hack.kidhack.data.remote.dto.EfinPaymentPasswordSetupRequest
import com.hack.kidhack.data.remote.dto.EfinPaymentPasswordChangeRequest
import com.hack.kidhack.data.remote.dto.EfinIdentityVerifyRequest
import com.hack.kidhack.data.remote.dto.EfinOtpVerifyRequest
import com.hack.kidhack.data.remote.dto.EfinPaymentPasswordVerifyRequest
import com.hack.kidhack.domain.repository.EfinRepository
import com.hack.kidhack.domain.repository.IdentityResult
import com.hack.kidhack.domain.repository.OtpIssue
import com.hack.kidhack.domain.repository.TxSecurity
import com.hack.kidhack.data.remote.dto.AppControlCreateRequest
import com.hack.kidhack.data.remote.dto.AppControlUpdateRequest
import com.hack.kidhack.data.remote.dto.AppSyncItemDto
import com.hack.kidhack.data.remote.dto.AppSyncRequest
import com.hack.kidhack.data.remote.dto.ScreenTimeRequest
import com.hack.kidhack.data.remote.dto.AppUsageReportItemDto
import com.hack.kidhack.data.remote.dto.AppUsageReportRequest
import com.hack.kidhack.data.remote.dto.BlockRuleRequest
import com.hack.kidhack.data.remote.dto.VisitRequest
import com.hack.kidhack.data.remote.dto.CardRegisterRequest
import com.hack.kidhack.data.remote.dto.DeviceTokenRequest
import com.hack.kidhack.data.remote.dto.ChargeConfirmRequest
import com.hack.kidhack.data.remote.dto.ChargePrepareRequest
import com.hack.kidhack.data.remote.dto.CheckoutRequest
import com.hack.kidhack.data.remote.dto.ChildUpdateRequest
import com.hack.kidhack.data.remote.dto.CommentRequest
import com.hack.kidhack.data.remote.dto.CreateChatRoomRequest
import com.hack.kidhack.data.remote.dto.InquiryRequest
import com.hack.kidhack.data.remote.dto.PostRequest
import com.hack.kidhack.data.remote.dto.FindIdSendCodeRequest
import com.hack.kidhack.data.remote.dto.FindIdVerifyRequest
import com.hack.kidhack.data.remote.dto.DeviceEventRequest
import com.hack.kidhack.data.remote.dto.LocationTrackRequest
import com.hack.kidhack.data.remote.dto.LoginRequest
import com.hack.kidhack.data.remote.dto.LoginResponse
import com.hack.kidhack.data.remote.dto.PairingConnectRequest
import com.hack.kidhack.data.remote.dto.PlaceRequest
import com.hack.kidhack.data.remote.dto.RemoteCommandAckRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordSendCodeRequest
import com.hack.kidhack.data.remote.dto.SignupRequest
import com.hack.kidhack.data.remote.dto.TransferRequest
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.dto.UsePointRequest
import com.hack.kidhack.data.remote.dto.UserUpdateRequest
import com.hack.kidhack.data.remote.paging.PageResponsePagingSource
import com.hack.kidhack.data.remote.unwrap
import com.hack.kidhack.data.remote.unwrapOrNull
import com.hack.kidhack.data.repository.toDomain
import com.hack.kidhack.data.repository.toWallet
import com.hack.kidhack.data.repository.toCharge
import com.hack.kidhack.data.repository.toRoleOrNull
import com.hack.kidhack.data.repository.toDto
import com.hack.kidhack.data.ws.ChatWebSocketClient
import com.hack.kidhack.data.ws.ChatWsEvent
import com.hack.kidhack.data.ws.ChildCommandSocket
import com.hack.kidhack.domain.model.Availability
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Activity
import com.hack.kidhack.domain.model.ActivityType
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.QrTicket
import com.hack.kidhack.domain.model.QrPaymentHistoryItem
import com.hack.kidhack.domain.model.StoreTerminalInfo
import com.hack.kidhack.domain.model.ChildDashboard
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.MessageType
import com.hack.kidhack.domain.model.ChatMessagePage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryDetail
import com.hack.kidhack.domain.model.LikeResult
import com.hack.kidhack.domain.model.Notice
import com.hack.kidhack.domain.model.NoticeDetail
import com.hack.kidhack.domain.model.Paged
import com.hack.kidhack.domain.model.InstalledApp
import com.hack.kidhack.domain.model.LocationEvent
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.SentCode
import com.hack.kidhack.domain.model.TransferResult
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.AllowanceRepository
import com.hack.kidhack.domain.repository.QrPayRepository
import com.hack.kidhack.domain.repository.StoreTerminalRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RealAuthRepository(
    private val api: AuthApi,
    private val userApi: UserApi,
    private val sessionRepository: SessionRepository,
    private val sessionEvents: SessionEvents,
    private val chatMessageStore: ChatMessageStore,
    private val chatWebSocketClient: ChatWebSocketClient,
) : AuthRepository {
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
    ): Session {
        // Signup response has User only (no tokens). Do not auto-login.
        // [VULN-MOBILE-AUTHZ-1] 가입 role 을 클라이언트(가입 화면 라디오 선택값)가 결정해 그대로 전송한다.
        // 서버가 이 값을 신뢰(백엔드 VULN-7)하므로 요청 변조로 관리자 자가생성이 가능한 클라이언트측 진입점이다.
        // 의도된 취약점(OWASP Mobile M3 Insecure Authentication/Authorization). 보존: 고치지 말 것.
        // 명세: mobile/docs/vuln-spec-mobile.md
        val user = api.signup(
            SignupRequest(
                loginId = loginId,
                username = username,
                password = password,
                nickname = nickname,
                email = email,
                phoneNumber = phoneNumber,
                birthDate = birthDate,
                address = address,
                profileImageUrl = profileImageUrl,
                role = role.name,
                parentId = parentId,
            ),
        ).unwrap().toDomain()
        val session = Session(
            accessToken = "",
            refreshToken = "",
            user = user.copy(
                loginId = user.loginId.ifBlank { loginId },
                name = user.name.ifBlank { username },
                role = role,
            ),
        )
        sessionRepository.save(session)
        return session
    }

    override suspend fun checkAvailability(loginId: String?, email: String?): Availability {
        // 빈 값은 아예 보내지 않는다 — 서버가 "확인하지 않음"(null)과 "쓸 수 있음"을 구분한다.
        val dto = api.availability(
            loginId = loginId?.takeIf { it.isNotBlank() },
            email = email?.takeIf { it.isNotBlank() },
        ).unwrap()
        return Availability(dto.loginIdAvailable, dto.emailAvailable)
    }

    override suspend fun login(loginId: String, password: String, preferredRole: Role?): Session {
        val login = api.login(LoginRequest(loginId, password)).unwrap()
        return establishSession(login, preferredRole)
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        sessionRepository.clear()
        chatMessageStore.clear()
        chatWebSocketClient.reset()
        sessionEvents.emitLogout()
    }

    override suspend fun sendFindIdCode(username: String, email: String): SentCode =
        api.sendFindIdCode(FindIdSendCodeRequest(username = username, email = email))
            .unwrap()
            .let { SentCode(message = it.message, code = it.code) }

    override suspend fun verifyFindId(email: String, code: String): String =
        api.verifyFindId(FindIdVerifyRequest(email = email, code = code)).unwrap().loginId

    override suspend fun sendResetPasswordCode(loginId: String, email: String): SentCode =
        api.sendResetPasswordCode(ResetPasswordSendCodeRequest(loginId = loginId, email = email))
            .unwrap()
            .let { SentCode(message = it.message, code = it.code) }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        api.resetPassword(
            ResetPasswordRequest(email = email, code = code, newPassword = newPassword),
        ).unwrapOrNull()
    }

    /**
     * 역할 결정 순서: 로그인 응답 → `GET /users/me` → 로그인 화면에서 고른 값 → PARENT.
     *
     * 예전에는 응답의 role 문자열을 `Role.valueOf` 로만 읽어서, 서버가 필드를 안 주거나
     * `ROLE_CHILD` 같은 표기를 쓰면 **말없이 PARENT 로 떨어졌다**. 자녀 계정으로 로그인해도
     * 부모 화면이 열린 이유다. 이제 표기를 폭넓게 받아들이고([toRoleOrNull]), 그래도 알 수
     * 없을 때만 사용자가 고른 [preferredRole] 을 쓴다.
     */
    private suspend fun establishSession(login: LoginResponse, preferredRole: Role?): Session {
        val access = login.resolvedAccessToken()?.takeIf { it.isNotBlank() }
            ?: error("로그인 토큰이 없습니다")
        val refresh = login.refreshToken.orEmpty()
        val loginRole = login.role.toRoleOrNull() ?: login.user?.role.toRoleOrNull()
        val fromUser = login.user?.toDomain()
        val placeholder = User(
            id = fromUser?.id.orEmpty(),
            loginId = login.loginId?.takeIf { it.isNotBlank() }
                ?: fromUser?.loginId.orEmpty(),
            name = login.username?.takeIf { it.isNotBlank() }
                ?: fromUser?.name.orEmpty(),
            role = loginRole ?: preferredRole ?: Role.PARENT,
            phone = fromUser?.phone.orEmpty(),
            email = fromUser?.email,
            profileImageUrl = fromUser?.profileImageUrl,
            parentId = fromUser?.parentId,
        )
        sessionRepository.save(Session(access, refresh, placeholder))
        return try {
            // DTO 를 먼저 잡아 둔다. toDomain() 은 모르는 role 을 PARENT 로 메워 버려서
            // 「서버가 PARENT 라고 했다」와 「서버가 아무 말도 안 했다」를 구분할 수 없다.
            val meDto = userApi.me().unwrap()
            val me = meDto.toDomain()
            val merged = me.copy(
                loginId = me.loginId.ifBlank { placeholder.loginId },
                name = me.name.ifBlank { placeholder.name },
                role = loginRole ?: meDto.role.toRoleOrNull() ?: preferredRole ?: Role.PARENT,
                parentId = me.parentId ?: placeholder.parentId,
            )
            Session(access, refresh, merged).also { sessionRepository.save(it) }
        } catch (t: Throwable) {
            sessionRepository.clear()
            throw t
        }
    }
}

class RealUserRepository(private val api: UserApi) : UserRepository {
    override suspend fun me(): User = api.me().unwrap().toDomain()
    override suspend fun updateMe(
        nickname: String?,
        phoneNumber: String?,
        email: String?,
        address: String?,
        profileImageUrl: String?,
    ): User = api.updateMe(
        UserUpdateRequest(
            nickname = nickname,
            phoneNumber = phoneNumber,
            email = email,
            address = address,
            profileImageUrl = profileImageUrl,
        ),
    ).unwrap().toDomain()
    override suspend fun deleteMe() { api.deleteMe().unwrapOrNull() }

    override fun pagedActivities(type: ActivityType): Flow<PagingData<Activity>> =
        Pager(PagingConfig(20)) {
            PageResponsePagingSource { page, size ->
                api.activities(type = type.name, page = page, size = size).unwrap()
            }
        }.flow.map { paging -> paging.map { it.toDomain() } }
    override suspend fun notificationSettings(): NotificationSettings =
        api.notificationSettings().unwrap().toDomain()
    override suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings =
        api.patchNotificationSettings(settings.toDto()).unwrap().toDomain()
}

class RealPairingRepository(private val api: PairingApi) : PairingRepository {
    override suspend fun issueCode(): PairingCode = api.issueCode().unwrap().toDomain()

    override suspend fun activeCodes(): List<PairingCode> =
        api.activeCodes().unwrap().map { it.toDomain() }
    override suspend fun connect(pairingCode: String) {
        // 성공 응답의 data 가 null 이라 unwrap() 을 쓰면 "응답 데이터가 없습니다" 로 터진다.
        api.connect(PairingConnectRequest(code = pairingCode)).unwrapOrNull()
    }
}

class RealChildRepository(
    private val api: ChildApi,
    private val fileApi: FileApi,
    private val context: Context,
) : ChildRepository {

    /** 서버 업로드 분류. `/files/{category}` 의 경로 조각이다. */
    private val profileCategory = "profile"
    override suspend fun children(): List<ChildProfile> = api.children().unwrap().map { it.toDomain() }
    override suspend fun child(childId: String): ChildProfile = api.child(childId).unwrap().toDomain()
    override suspend fun updateChild(
        childId: String,
        nickname: String?,
        profileImageUrl: String?,
    ): ChildProfile = api.update(
        childId,
        ChildUpdateRequest(nickname = nickname, profileImageUrl = profileImageUrl)
    ).unwrap().toDomain()

    override suspend fun uploadProfileImage(uri: String): String =
        fileApi.upload(profileCategory, context.filePart(uri)).unwrap().url

    override suspend fun unpair(childId: String) {
        api.unpair(childId).unwrapOrNull()
    }

    override suspend fun forceLogout(childId: String) {
        api.forceLogout(childId).unwrapOrNull()
    }
}


class RealLocationRepository(private val api: LocationApi) : LocationRepository {
    override suspend fun track(childId: String, latitude: String, longitude: String): LocationPoint =
        api.track(LocationTrackRequest(childId.toServerId(), latitude, longitude)).unwrap().toDomain()
    /**
     * 서버는 시간 **오름차순**으로 주는데 화면들은 첫 항목을 현재 위치로 쓴다.
     * 여기서 뒤집어 최신순으로 맞춘다 — 호출부마다 정렬을 신경 쓰게 두면 또 어긋난다.
     */
    override suspend fun history(childId: String): List<LocationPoint> =
        api.history(childId).unwrap().points.asReversed().map { it.toDomain() }
    override suspend fun realtimeEnabled(childId: String): Boolean =
        api.realtime(childId).unwrap().realtimeEnabled

    override suspend fun toggleRealtime(childId: String, enabled: Boolean): Boolean =
        api.toggleRealtime(childId, enabled).unwrap().realtimeEnabled
}

class RealDeviceRepository(
    private val api: DeviceApi,
    private val context: Context,
) : DeviceRepository {
    override suspend fun reportEvent(childId: String, type: String) {
        api.reportEvent(
            DeviceEventRequest(
                childId = childId.toServerId(),
                deviceId = DeviceId.get(context),
                type = type,
            ),
        ).unwrapOrNull()
    }

    override suspend fun registerPushToken(token: String) {
        api.registerPushToken(DeviceTokenRequest(token)).unwrapOrNull()
    }

    override suspend fun unregisterPushToken(token: String) {
        api.unregisterPushToken(DeviceTokenRequest(token)).unwrapOrNull()
    }
}

class RealDashboardRepository(private val api: DashboardApi) : DashboardRepository {
    override suspend fun get(childId: String): ChildDashboard =
        api.get(childId).unwrap().toDomain()
}

class RealPlaceRepository(private val api: PlaceApi) : PlaceRepository {
    override suspend fun list(childId: String): List<Geofence> = api.list(childId).unwrap().map { it.toDomain() }
    override suspend fun create(childId: String, place: Geofence): Geofence =
        api.create(
            PlaceRequest(
                childId = childId.toServerId(),
                name = place.name,
                latitude = place.lat.toServerCoordinate("위도"),
                longitude = place.lng.toServerCoordinate("경도"),
                radiusMeters = place.radius.toDouble(),
                type = place.type.name,
            ),
        ).unwrap().toDomain()
    override suspend fun delete(childId: String, placeId: String) {
        api.delete(placeId).unwrapOrNull()
    }
}

class RealLocationEventRepository(private val api: LocationEventApi) : LocationEventRepository {
    override suspend fun events(childId: String): List<LocationEvent> =
        api.events(childId).unwrap().map { it.toDomain() }
}

class RealNotificationRepository(private val api: NotificationApi) : NotificationRepository {
    /**
     * 서버는 자녀별 필터를 지원하지 않아 전체를 받아 앱에서 거른다.
     * 자녀 맥락이 없는 알림(childId == null)은 어느 자녀를 골라도 함께 보여 준다.
     */
    override suspend fun list(recipientId: String, childId: String?): List<AppNotification> =
        api.list(recipientId).unwrap().content
            .map { it.toDomain() }
            .filter { childId == null || it.childId == null || it.childId == childId }

    override suspend fun markRead(notificationId: String) {
        api.markRead(notificationId).unwrapOrNull()
    }

    override suspend fun markAllRead(recipientId: String): Int =
        api.markAllRead(recipientId).unwrapOrNull() ?: 0

    override suspend fun unreadCount(recipientId: String): Int =
        api.unreadCount(recipientId).unwrapOrNull() ?: 0
}

class RealChildAlertRepository(private val api: ChildAlertApi) : ChildAlertRepository {
    override suspend fun upload(childId: String, alerts: List<ChildAlertEvent>) {
        api.upload(childId, alerts.map { it.toDto() }).unwrapOrNull()
    }

    override suspend fun list(childId: String): List<ChildAlertEvent> =
        api.list(childId).unwrap().map { it.toDomain() }
}
/**
 * 유해사이트/앱 차단. 서버는 자녀별 차단 패턴 목록(`/api/filtering/rules`)을 쓴다.
 *
 * 예전에는 `GET /children/{childId}/blocklist` 가 문자열 배열을 준다는 전제로 짜여 있었고,
 * 응답에 id 가 없어 키워드 문자열을 itemId 로 대신 쓰고 있었다. 실제 서버는 규칙마다
 * **id 를 주고 삭제도 그 id 로** 받으므로(`DELETE /filtering/rules/{ruleId}`) 그 우회가 필요 없다.
 */
class RealBlocklistRepository(private val api: BlocklistApi) : BlocklistRepository {
    override suspend fun get(childId: String): List<BlocklistItem> =
        api.rules(childId).unwrap().map { it.toDomain() }

    override suspend fun add(childId: String, pattern: String): BlocklistItem =
        api.addRule(BlockRuleRequest(childId.toServerId(), pattern)).unwrap().toDomain()

    override suspend fun remove(ruleId: String) {
        api.deleteRule(ruleId).unwrapOrNull()
    }

    override suspend fun reportVisit(childId: String, target: String): Boolean =
        api.visit(VisitRequest(childId.toServerId(), target)).unwrap().blocked
}

class RealScreenshotRepository(private val api: ScreenshotApi) : ScreenshotRepository {
    override fun paged(childId: String): Flow<PagingData<Screenshot>> =
        Pager(PagingConfig(20)) {
            PageResponsePagingSource { page, size -> api.list(childId, page = page, size = size).unwrap() }
        }.flow.map { paging -> paging.map { it.toDomain() } }
    override suspend fun list(childId: String): List<Screenshot> =
        api.list(childId, page = 0, size = 50).unwrap().content.map { it.toDomain() }
    override suspend fun requestCapture(childId: String) {
        api.requestCapture(childId).unwrapOrNull()
    }

    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        trigger: String,
    ): Screenshot = api.upload(
        childId = childId,
        file = file.toImagePart(),
        capturedAt = capturedAt,
        trigger = trigger,
    ).unwrap().toDomain(capturedAt = capturedAt, trigger = trigger)
}

class RealCommunityRepository(
    private val api: CommunityApi,
    private val context: Context,
) : CommunityRepository {

    override fun pagedPosts(keyword: String?): Flow<PagingData<Post>> =
        Pager(PagingConfig(20)) {
            PageResponsePagingSource { page, size -> api.posts(keyword = keyword, page = page, size = size).unwrap() }
        }.flow.map { paging -> paging.map { it.toDomain() } }

    override suspend fun posts(): List<Post> =
        api.posts(page = 0, size = 50).unwrap().content.map { it.toDomain() }

    override suspend fun post(postId: String): Post = api.post(postId.toServerId()).unwrap().toDomain()

    override suspend fun create(
        title: String,
        content: String,
        attachments: List<String>,
        links: List<String>,
    ): Post = api.create(PostRequest(title, content, attachments, links)).unwrap().toDomain()

    override suspend fun update(
        postId: String,
        title: String,
        content: String,
        attachments: List<String>,
        links: List<String>,
    ): Post = api.update(postId.toServerId(), PostRequest(title, content, attachments, links)).unwrap().toDomain()

    override suspend fun delete(postId: String) {
        // 삭제 성공은 본문이 없다({"success":true,"data":null}). unwrap 은 data 가 null 이면
        // 예외를 던져 성공을 실패로 뒤집는다 — 본문 없는 응답은 unwrapOrNull 로 받는다.
        api.delete(postId.toServerId()).unwrapOrNull()
    }

    override suspend fun like(postId: String): LikeResult =
        api.like(postId.toServerId()).unwrap().toDomain()

    override suspend fun comment(postId: String, content: String): Comment =
        api.comment(postId.toServerId(), CommentRequest(content)).unwrap().toDomain()

    override suspend fun deleteComment(postId: String, commentId: String) {
        api.deleteComment(postId.toServerId(), commentId.toServerId()).unwrapOrNull()
    }

    override suspend fun uploadAttachment(uri: String): String =
        api.uploadAttachment(context.filePart(uri)).unwrap().url
}

/**
 * 1:1 문의 (명세 21).
 *
 * 첨부는 반드시 [uploadAttachment] 로 먼저 올린다. 예전 코드는 갤러리에서 고른
 * `content://` URI 를 그대로 등록 본문에 실어 보냈는데, 서버에는 그런 파일이 없다.
 */
class RealInquiryRepository(
    private val api: InquiryApi,
    private val context: Context,
) : InquiryRepository {

    override suspend fun list(page: Int): Paged<Inquiry> {
        val res = api.list(page = page).unwrap()
        return Paged(res.content.map { it.toDomain() }, res.page, res.totalPages)
    }

    override suspend fun get(id: String): InquiryDetail =
        api.detail(id.toServerId()).unwrap().toDomain()

    override suspend fun create(
        title: String,
        content: String,
        attachments: List<String>,
        linkUrl: String?,
    ): Inquiry = api.create(InquiryRequest(title, content, attachments, linkUrl?.ifBlank { null }))
        .unwrap().toDomain()

    override suspend fun update(
        id: String,
        title: String,
        content: String,
        attachments: List<String>,
        linkUrl: String?,
    ): Inquiry = api.update(id.toServerId(), InquiryRequest(title, content, attachments, linkUrl?.ifBlank { null }))
        .unwrap().toDomain()

    override suspend fun delete(id: String) {
        api.delete(id.toServerId()).unwrapOrNull()
    }

    override suspend fun uploadAttachment(uri: String): String =
        api.upload(context.filePart(uri)).unwrap().url
}

/** 공지사항 (명세 23). 열람에 로그인이 필요 없다. */
class RealNoticeRepository(private val api: NoticeApi) : NoticeRepository {

    override suspend fun list(keyword: String?, page: Int): Paged<Notice> {
        val res = api.list(keyword = keyword?.ifBlank { null }, page = page).unwrap()
        return Paged(res.content.map { it.toDomain() }, res.page, res.totalPages)
    }

    override suspend fun get(id: String): NoticeDetail =
        api.detail(id.toServerId()).unwrap().toDomain()
}

/**
 * 지갑 리포지토리(서버 v0.2). 충전은 prepare→approve 2단계이고, mock=true 면 화면에서
 * prepare 직후 곧바로 approve 를 부른다(이 클래스는 각 단계만 노출). 금액은 서버가 long 이라
 * 경계에서 [Int]→[Long] 으로 올려 보낸다. childId 는 [toServerId] 로 숫자 변환.
 */
class RealWalletRepository(
    private val walletApi: WalletApi
) : WalletRepository {
    override suspend fun getMyWallet(): Wallet = walletApi.me().unwrap().toWallet()

    override suspend fun getTransactions(type: String?, page: Int): List<WalletTransaction> =
        walletApi.me(type = type, page = page).unwrap().content.map { it.toDomain() }

    override suspend fun transferToChild(childId: String, amount: Int, memo: String?, security: TxSecurity?): TransferResult {
        // memo 는 서버 계약에 필드가 없어 전송하지 않는다(파라미터는 상위 호환 위해 유지).
        // 전자금융 보안 필드(otp·signature·nonce·accountHolder·paymentPassword)는 security 로 함께 싣는다.
        val r = walletApi.transfer(WalletTransferRequest(
            childId = childId.toServerId(), amount = amount.toLong(),
            otp = security?.otp, signature = security?.signature,
            nonce = security?.nonce, accountHolder = security?.accountHolder,
            paymentPassword = security?.paymentPassword)).unwrap()
        return TransferResult(parentBalance = r.fromBalance.toInt(), childBalance = r.toBalance.toInt(), txId = "")
    }

    override suspend fun reclaimFromChild(childId: String, amount: Int, security: TxSecurity?): TransferResult {
        // 회수는 자녀→부모: 받는 쪽(부모)=toBalance, 보내는 쪽(자녀)=fromBalance.
        val r = walletApi.recover(WalletTransferRequest(
            childId = childId.toServerId(), amount = amount.toLong(),
            otp = security?.otp, signature = security?.signature,
            nonce = security?.nonce, accountHolder = security?.accountHolder,
            paymentPassword = security?.paymentPassword)).unwrap()
        return TransferResult(parentBalance = r.toBalance.toInt(), childBalance = r.fromBalance.toInt(), txId = "")
    }

    override suspend fun getChildWallet(childId: String): Wallet =
        walletApi.childWallet(childId.toServerId()).unwrap().toWallet()

    override suspend fun prepareCharge(amount: Int, method: String, cardId: String, security: TxSecurity?): com.hack.kidhack.domain.model.Charge =
        walletApi.chargePrepare(WalletChargePrepareRequest(
            amount = amount.toLong(), otp = security?.otp, paymentPassword = security?.paymentPassword,
            signature = security?.signature, nonce = security?.nonce)).unwrap().toCharge()

    override suspend fun confirmCharge(chargeId: String, paymentKey: String, amount: Int): Wallet {
        // chargeId = prepare 가 발급한 merchantUid. paymentKey 는 토스 결제위젯 성공 콜백의 paymentKey —
        // 서버 approve 의 pgTransactionId 로 넘긴다(mock PG 는 검증하지 않지만 실제 PG 연동 시 필요).
        val r = walletApi.chargeApprove(
            WalletChargeApproveRequest(
                merchantUid = chargeId,
                amount = amount.toLong(),
                pgTransactionId = paymentKey.takeIf { it.isNotBlank() },
            ),
        ).unwrap()
        return Wallet(walletId = "me", balance = r.balance.toInt(), updatedAt = "")
    }

    override suspend fun getChildTransfers(childId: String, page: Int): List<WalletTransaction> =
        walletApi.childTransfers(childId.toServerId(), page = page).unwrap().content.map { it.toDomain() }

    override suspend fun getChildTransactions(childId: String, type: String?, page: Int): List<WalletTransaction> =
        walletApi.childTransactions(childId.toServerId(), type = type, page = page).unwrap()
            .content.map { it.toDomain() }

    override suspend fun getChargeHistory(page: Int): List<com.hack.kidhack.domain.model.ChargeHistoryItem> =
        walletApi.chargeHistory(page = page).unwrap().content.map { it.toDomain() }

    override suspend fun refundCharge(merchantUid: String): Int =
        walletApi.chargeRefund(merchantUid).unwrap().balance.toInt()

    override suspend fun cancelCharge(merchantUid: String): String =
        walletApi.chargeCancel(merchantUid).unwrap().status
}

/**
 * 카드 리포지토리(서버 v0.2, 태그 14). 등록 시 공개키를 실시간 조회해 payload 를 하이브리드
 * E2E 암호화([CardCrypto])한 뒤 4필드로 보낸다. keyId 회전 등으로 첫 시도가 실패하면
 * 공개키를 다시 받아 1회 재시도한다(400 KEY_ROTATED 투명 처리).
 */
class RealCardRepository(private val api: CardApi) : CardRepository {
    override suspend fun register(
        cardNumber: String,
        expiry: String,
        cvc: String,
        cardPassword2: String,
        birthDate: String,
        ownerName: String,
        paymentPassword: String,
    ): Card = runCatching {
        registerOnce(cardNumber, expiry, cvc, cardPassword2, birthDate, ownerName, paymentPassword)
    }.getOrElse {
        // 공개키 재조회 후 1회 재시도 (KEY_ROTATED 대응).
        registerOnce(cardNumber, expiry, cvc, cardPassword2, birthDate, ownerName, paymentPassword)
    }

    private suspend fun registerOnce(
        cardNumber: String,
        expiry: String,
        cvc: String,
        cardPassword2: String,
        birthDate: String,
        ownerName: String,
        paymentPassword: String,
    ): Card {
        val publicKey = api.publicKey().unwrap()
        val payloadJson = KidhackJson.instance.encodeToString(
            CardPlainPayload.serializer(),
            CardPlainPayload(
                cardNumber = cardNumber,
                expiry = expiry,
                cvc = cvc,
                passwordFront = cardPassword2,
                birthDate = birthDate,
                ownerName = ownerName,
                paymentPassword = paymentPassword,
                nonce = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
            ),
        )
        val encrypted = CardCrypto.encrypt(publicKey.publicKey, payloadJson)
        return api.register(
            EncryptedCardRequest(
                keyId = publicKey.keyId,
                encryptedKey = encrypted.encryptedKey,
                iv = encrypted.iv,
                ciphertext = encrypted.ciphertext,
            ),
        ).unwrap().toDomain()
    }

    override suspend fun list(): List<Card> = api.list().unwrap().map { it.toDomain() }
    // unwrap() 으로 서버 실패를 던진다 — 예전엔 unwrapOrNull 로 삼켜서, 삭제가 안 돼도 성공처럼 조용히 넘어갔다.
    override suspend fun delete(cardId: String) { api.delete(cardId.toServerId()).unwrap() }
}

/** QR 결제 리포지토리(서버 v0.2, 태그 15·18·19). */
class RealQrPayRepository(private val api: QrPayApi) : QrPayRepository {
    override suspend fun issueQr(): QrTicket = api.issue().unwrap().toDomain()

    override suspend fun qrStatus(qrToken: String): QrTicket = api.status(qrToken).unwrap().toDomain()

    override suspend fun cancel(qrToken: String) { api.cancel(qrToken).unwrapOrNull() }

    override suspend fun paymentHistory(childId: String?, page: Int): List<QrPaymentHistoryItem> =
        api.history(childId = childId?.toServerId(), page = page).unwrap().content.map { it.toDomain() }
}

/**
 * 매장 단말 리포지토리(서버 v0.2, 태그 16·17) — 테스트/시연 전용, 회원 앱 정상 흐름과 분리.
 * 단말 승인은 `X-Store-Terminal-Token` 헤더로만 인증된다.
 */
class RealStoreTerminalRepository(
    private val api: StoreTerminalApi,
) : StoreTerminalRepository {
    override suspend fun issueTerminal(storeName: String): StoreTerminalInfo =
        api.issue(TerminalIssueRequest(storeName)).unwrap().toDomain()

    override suspend fun terminals(): List<StoreTerminalInfo> =
        api.list().unwrap().map { it.toDomain() }

    override suspend fun approve(terminalToken: String, qrToken: String, amount: Int): QrPaymentHistoryItem {
        val r = api.pay(terminalToken, QrApproveRequest(qrToken, amount.toLong())).unwrap()
        return QrPaymentHistoryItem(
            qrToken = r.qrToken,
            storeName = r.storeName?.takeIf { it.isNotBlank() } ?: "매장",
            amount = r.amount.toInt(),
            status = r.status,
            paidAtLabel = "",
        )
    }
}

class RealChatRepository(
    private val api: ChatApi,
    private val ws: ChatWebSocketClient,
    private val sessionRepository: SessionRepository,
    private val fileApi: FileApi,
    private val context: Context,
) : ChatRepository {
    override suspend fun getOrCreateRoom(parentId: Long, childId: Long): ChatRoom =
        api.createOrGetRoom(CreateChatRoomRequest(parentId, childId)).unwrap().toDomain()

    /** 서버가 요청자 기준 상대방 정보와 안 읽음 수까지 담아 준다. */
    override suspend fun rooms(): List<ChatRoom> =
        api.rooms().unwrap().map { it.toDomain() }

    override suspend fun messages(roomId: String, before: String?, size: Int): ChatMessagePage {
        val session = sessionRepository.current()
        val myId = session?.user?.id?.toLongOrNull()
        val myRole = session?.user?.role ?: Role.PARENT
        val page = api.messages(roomId, before, size).unwrap()
        // 서버는 **최신 → 과거** 순으로 주는데 화면은 LazyColumn 에 순서대로 그리고 마지막
        // 항목으로 스크롤한다(= 아래가 최신). 그대로 쓰면 대화가 거꾸로 쌓이고, 새로 보낸
        // 메시지만 아래에 붙어 순서가 뒤섞인다. 여기서 뒤집어 「오래된 → 최신」으로 통일한다.
        return ChatMessagePage(
            messages = page.messages.asReversed().map { it.toDomain(myUserId = myId, myRole = myRole) },
            hasMore = page.hasMore,
            oldestSentAt = page.oldestSentAt,
        )
    }

    override fun observeMessages(roomId: String): Flow<ChatMessage> =
        ws.events
            .onStart {
                ws.subscribeRoom(roomId)
                ws.start()
            }
            .onCompletion { ws.stop() }
            .filterIsInstance<ChatWsEvent.Message>()
            .map { it.message }
            .filter { it.roomId == roomId }

    override suspend fun send(roomId: String, content: String): ChatMessage {
        val session = sessionRepository.current()
        val role = session?.user?.role ?: Role.PARENT
        val senderId = session?.user?.id?.toLongOrNull()
            ?: error("로그인 정보가 없어요")
        ws.send(roomId, senderId, content)
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        return ChatMessage(
            id = "local-${System.currentTimeMillis()}",
            roomId = roomId,
            fromRole = role,
            text = content,
            sentAt = now,
        )
    }

    override suspend fun uploadMedia(localUri: String, mimeType: String): String =
        // 공용 파일 업로드(POST /api/files/chat). 응답 url 예: `/api/files/chat/42`.
        fileApi.upload("chat", context.chatMediaPart(localUri, mimeType)).unwrap().url

    override suspend fun sendMedia(
        roomId: String,
        type: MessageType,
        mediaUrl: String,
    ): ChatMessage {
        val session = sessionRepository.current()
        val role = session?.user?.role ?: Role.PARENT
        val senderId = session?.user?.id?.toLongOrNull()
            ?: error("로그인 정보가 없어요")
        ws.sendMedia(roomId, senderId, type, mediaUrl)
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        return ChatMessage(
            id = "local-${System.currentTimeMillis()}",
            roomId = roomId,
            fromRole = role,
            text = "",
            sentAt = now,
            type = type,
            mediaUrl = mediaUrl,
        )
    }

    /** 서버는 "지금 시점까지" 읽음으로 표시한다. 개별 메시지 id 는 받지 않는다. */
    override suspend fun markRead(roomId: String, lastMessageId: String?) {
        api.markRead(roomId).unwrapOrNull()
    }
}

class RealPaymentRepository(private val api: PaymentApi) : PaymentRepository {
    override suspend fun checkout(amount: Int, method: String, productId: Int): Payment =
        api.checkout(CheckoutRequest(amount, method, productId)).unwrap().toDomain()
    override suspend fun history(): List<Payment> =
        api.history().unwrap().content.map { it.toDomain() }
}

class RealPointRepository(private val api: PointApi) : PointRepository {
    override suspend fun balance(): PointBalance = api.balance().unwrap().toDomain()
    override suspend fun history(): List<PointHistory> =
        api.history().unwrap().content.map { it.toDomain() }
    override suspend fun use(productId: Int, point: Int) {
        api.use(UsePointRequest(productId, point)).unwrapOrNull()
    }
}

class RealAppUsageRepository(private val api: AppUsageApi) : AppUsageRepository {
    override suspend fun list(
        childId: String,
        groupBy: String,
        from: String?,
        to: String?,
    ): List<AppUsageStat> =
        api.list(childId, groupBy, from, to).unwrap().items.map { it.toDomain() }

    override suspend fun requestReport(childId: String) {
        api.requestReport(childId).unwrapOrNull()
    }

    override suspend fun report(childId: String, usages: List<AppUsage>) {
        val recordedAt = java.time.Instant.now().toString()
        api.report(
            AppUsageReportRequest(
                childId = childId.toServerId(),
                usages = usages.map {
                    AppUsageReportItemDto(it.appName, it.durationSec, recordedAt, it.packageName)
                },
            ),
        ).unwrapOrNull()
    }
}

class RealAppControlRepository(private val api: AppControlApi) : AppControlRepository {
    override suspend fun items(childId: String): List<AppControlItem> =
        api.rules(childId).unwrap().map { it.toDomain() }

    override suspend fun getInstalledApps(childId: String): List<InstalledApp> =
        api.installedApps(childId).unwrap().map { it.toDomain() }

    override suspend fun syncInstalledApps(childId: String, apps: List<InstalledApp>): Int =
        api.syncApps(
            AppSyncRequest(
                childId = childId.toServerId(),
                apps = apps.map { AppSyncItemDto(it.packageName, it.appName) },
            ),
        ).unwrap()

    override suspend fun addRule(
        childId: String,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int?,
    ): AppControlItem = api.addRule(
        AppControlCreateRequest(
            childId = childId.toServerId(),
            packageName = packageName,
            appName = appName,
            dailyLimitMinutes = dailyLimitMinutes,
        ),
    ).unwrap().toDomain()

    override suspend fun updateRule(
        ruleId: String,
        dailyLimitMinutes: Int?,
        enabled: Boolean?,
    ): AppControlItem =
        api.updateRule(ruleId, AppControlUpdateRequest(dailyLimitMinutes, enabled)).unwrap().toDomain()

    override suspend fun deleteRule(ruleId: String) {
        api.deleteRule(ruleId).unwrapOrNull()
    }

    override suspend fun screenTimeLimit(childId: String): Int? =
        api.screenTime(childId).unwrap().dailyLimitMinutes

    override suspend fun setScreenTimeLimit(childId: String, dailyLimitMinutes: Int?): Int? =
        api.setScreenTime(childId, ScreenTimeRequest(dailyLimitMinutes)).unwrap().dailyLimitMinutes

    override suspend fun reportScreenTimeReached(childId: String, usedMinutes: Int): Boolean =
        api.reportScreenTimeReached(childId, usedMinutes).unwrapOrNull() ?: false
}

class RealPhotoRepository(private val api: PhotoApi) : PhotoRepository {
    override suspend fun list(childId: String): List<ChildPhoto> =
        api.list(childId, 0, 50).unwrap().content.map { it.toDomain() }

    override suspend fun requestShot(childId: String) {
        api.requestShot(childId).unwrapOrNull()
    }

    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        requestId: String?,
    ): ChildPhoto = api.upload(
        childId = childId,
        file = file.toImagePart(),
        capturedAt = capturedAt.toPlainBody(),
        requestId = requestId?.toPlainBody(),
    ).unwrap().toDomain()
}

/**
 * 수신 경로는 [ChildCommandSocket] (실시간) 과 FCM 두 가지다.
 * 폴링 백업 경로는 서버에 대응 엔드포인트가 없어 걷어냈다(2026-08-28).
 *
 * [ack] 은 소켓과 REST 로 모두 보낸다. FCM 이 소켓 꺼진 상태로 도착하면
 * `socket.ack` 이 no-op 이라 REST 쪽이 유일한 경로가 된다.
 */
class RealCommandRepository(
    private val api: CommandApi,
    private val socket: ChildCommandSocket,
) : CommandRepository {

    override fun observe(): Flow<RemoteCommand> = socket.commands
        .onStart { socket.start() }

    override val connected: Flow<Boolean> = socket.connected

    override fun disconnect() = socket.stop()

    override suspend fun ack(childId: String, requestId: String, status: String, message: String?) {
        socket.ack(requestId, status, message)
        runCatching { api.ack(childId, requestId, RemoteCommandAckRequest(status, message)).unwrapOrNull() }
    }
}

/** 서버 식별자는 숫자다(childId·문의 id·공지 id). 숫자가 아니면 엉뚱한 대상으로 보내지 말고 실패시킨다. */
private fun String.toServerId(): Long =
    toLongOrNull() ?: throw IllegalStateException("서버 id 는 숫자 형식이어야 합니다: $this")

/** 장소 좌표도 서버에서는 숫자다. (0.0, 0.0)으로 조용히 등록되는 것을 막는다. */
private fun String.toServerCoordinate(label: String): Double =
    toDoubleOrNull() ?: throw IllegalStateException("$label 값이 숫자 형식이어야 합니다: $this")

/**
 * 갤러리·파일 선택기에서 받은 URI 를 multipart 파트로 만든다.
 *
 * 문의(21)·커뮤니티(20) 첨부 업로드가 함께 쓴다. 파일 이름을 얻지 못하면 MIME
 * 서브타입으로 확장자를 만들어 붙인다 — 서버도 앱도 첨부 종류를 확장자로 판단한다.
 */
private fun Context.filePart(uri: String): MultipartBody.Part {
    val parsed = Uri.parse(uri)
    val resolver = contentResolver
    val bytes = resolver.openInputStream(parsed)?.use { it.readBytes() }
        ?: throw IllegalStateException("첨부 파일을 읽을 수 없습니다")
    val mime = resolver.getType(parsed) ?: "application/octet-stream"
    val name = runCatching {
        resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: ("attachment." + (mime.substringAfter('/').takeIf { it.isNotBlank() } ?: "bin"))
    return MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(mime.toMediaTypeOrNull()))
}

/**
 * 채팅 첨부 업로드용 파트. 카메라/갤러리(content://)와 녹음 파일(file://) 을 모두 받는다.
 * MIME 이 명시로 오므로 확장자를 그 값으로 만들어 붙인다 — 서버가 확장자로 종류를 본다.
 */
private fun Context.chatMediaPart(uri: String, mime: String): MultipartBody.Part {
    val parsed = Uri.parse(uri)
    val raw = contentResolver.openInputStream(parsed)?.use { it.readBytes() }
        ?: throw IllegalStateException("첨부 파일을 읽을 수 없습니다")

    // 이미지는 업로드 전 축소·압축한다. 카메라 원본은 1MB 를 넘어 서버(nginx)가 413 으로
    // 거부하기 때문이다. 오디오 등은 그대로 보낸다.
    val isImage = mime.startsWith("image")
    val bytes = if (isImage) downscaleJpeg(raw) else raw
    val outMime = if (isImage) "image/jpeg" else mime
    val ext = when {
        mime.startsWith("audio") -> "m4a"
        isImage -> "jpg"        // 이미지는 항상 jpeg 로 재인코딩한다
        else -> "bin"
    }
    val name = "chat-${System.currentTimeMillis()}.$ext"
    return MultipartBody.Part.createFormData(
        "file",
        name,
        bytes.toRequestBody(outMime.toMediaTypeOrNull()),
    )
}

private const val CHAT_IMAGE_MAX_DIM = 1600
private const val CHAT_IMAGE_QUALITY = 80

/**
 * 이미지 바이트를 긴 변 [CHAT_IMAGE_MAX_DIM] 이하로 줄이고 JPEG [CHAT_IMAGE_QUALITY] 로 압축한다.
 * EXIF 회전 정보를 픽셀에 반영해 세로/가로가 틀어지지 않게 한다. 디코드에 실패하면 원본을 그대로 돌려준다.
 */
private fun downscaleJpeg(data: ByteArray): ByteArray {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return data

    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > CHAT_IMAGE_MAX_DIM * 2) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return data

    bmp = applyExifRotation(bmp, data)

    val scale = CHAT_IMAGE_MAX_DIM.toFloat() / maxOf(bmp.width, bmp.height)
    if (scale < 1f) {
        val scaled = android.graphics.Bitmap.createScaledBitmap(
            bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1), true,
        )
        if (scaled != bmp) bmp.recycle()
        bmp = scaled
    }

    val out = java.io.ByteArrayOutputStream()
    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, CHAT_IMAGE_QUALITY, out)
    bmp.recycle()
    return out.toByteArray()
}

private fun applyExifRotation(bmp: android.graphics.Bitmap, data: ByteArray): android.graphics.Bitmap {
    val degrees = runCatching {
        val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(data))
        when (exif.getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL,
        )) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return bmp
    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
    val rotated = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    if (rotated != bmp) bmp.recycle()
    return rotated
}

private fun File.toImagePart(): MultipartBody.Part =
    MultipartBody.Part.createFormData("file", name, asRequestBody("image/jpeg".toMediaType()))

private fun String.toPlainBody(): RequestBody = toRequestBody("text/plain".toMediaType())

class RealAllowanceRepository(private val api: AllowanceApi) : AllowanceRepository {

    override suspend fun balance(childId: String): Allowance =
        api.balance(childId).unwrap().toDomain()

    override suspend fun history(childId: String): List<AllowanceEntry> =
        api.history(childId).unwrap().content.map { it.toDomain() }

    override suspend fun send(childId: String, amount: Int, memo: String?): Allowance =
        api.send(childId, AllowanceTransferRequest(amount, memo)).unwrap().toDomain()

    override suspend fun withdraw(childId: String, amount: Int, memo: String?): Allowance =
        api.withdraw(childId, AllowanceTransferRequest(amount, memo)).unwrap().toDomain()

    override suspend fun pay(
        childId: String,
        token: String,
        amount: Int,
        merchant: String?,
    ): AllowancePayment =
        api.pay(childId, AllowancePaymentRequest(token, amount, merchant)).unwrap().toDomain()
}

/**
 * 전자금융 거래 보안(#59) — 거래 OTP·계좌비밀번호·실명확인. (/api/efin)
 */
class RealEfinRepository(
    private val efinApi: EfinApi
) : EfinRepository {
    override suspend fun issueOtp(): OtpIssue {
        val r = efinApi.issueOtp().unwrap()
        return OtpIssue(phone = r.phone, code = r.code, expiresIn = r.expiresIn)
    }

    override suspend fun verifyOtp(code: String): Boolean =
        efinApi.verifyOtp(EfinOtpVerifyRequest(code)).unwrap().valid

    override suspend fun verifyPaymentPassword(password: String): Boolean =
        efinApi.verifyPaymentPassword(EfinPaymentPasswordVerifyRequest(password)).unwrap().valid

    override suspend fun setupPaymentPassword(password: String) {
        // 성공 응답이 data:null(ApiEnvelope<Unit>)이라 unwrap()은 "응답 데이터가 없습니다"로 오인한다.
        // 다른 Unit 엔드포인트처럼 unwrapOrNull()로 성공/실패만 판정한다(성공 시 200·data:null 정상 통과).
        efinApi.setupPaymentPassword(EfinPaymentPasswordSetupRequest(password)).unwrapOrNull()
    }

    override suspend fun changePaymentPassword(currentPassword: String, newPassword: String, loginPassword: String) {
        // setup과 동일 — Unit 응답이므로 unwrapOrNull()로 받는다.
        efinApi.changePaymentPassword(
            EfinPaymentPasswordChangeRequest(currentPassword, newPassword, loginPassword)).unwrapOrNull()
    }

    override suspend fun paymentPasswordSet(): Boolean =
        efinApi.paymentPasswordStatus().unwrap().exists

    override suspend fun identityVerified(): Boolean =
        efinApi.identityStatus().unwrap().verified

    override suspend fun verifyIdentity(
        name: String,
        birthDate: String,
        idType: String,
        idNumber: String,
        issuedDate: String,
    ): IdentityResult {
        val r = efinApi.verifyIdentity(
            EfinIdentityVerifyRequest(name, birthDate, idType, idNumber, issuedDate)).unwrap()
        return IdentityResult(verified = r.verified, name = r.name, verifiedAt = r.verifiedAt)
    }
}
