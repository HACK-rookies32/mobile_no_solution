package com.hack.kidhack.data.remote.api

import com.hack.kidhack.data.remote.ApiEnvelope
import com.hack.kidhack.data.remote.PageResponse
import com.hack.kidhack.data.remote.dto.ActivityDto
import com.hack.kidhack.data.remote.dto.AllowanceDto
import com.hack.kidhack.data.remote.dto.AllowanceEntryDto
import com.hack.kidhack.data.remote.dto.AllowancePaymentDto
import com.hack.kidhack.data.remote.dto.AllowancePaymentRequest
import com.hack.kidhack.data.remote.dto.AllowanceTransferRequest
import com.hack.kidhack.data.remote.dto.AvailabilityDto
import com.hack.kidhack.data.remote.dto.AppControlDto
import com.hack.kidhack.data.remote.dto.AppControlUpdateRequest
import com.hack.kidhack.data.remote.dto.AppControlCreateRequest
import com.hack.kidhack.data.remote.dto.AppSyncItemDto
import com.hack.kidhack.data.remote.dto.AppSyncRequest
import com.hack.kidhack.data.remote.dto.AppUsageAggregateResponse
import com.hack.kidhack.data.remote.dto.AppUsageReportRequest
import com.hack.kidhack.data.remote.dto.BlockRuleDto
import com.hack.kidhack.data.remote.dto.BlockRuleRequest
import com.hack.kidhack.data.remote.dto.CardDto
import com.hack.kidhack.data.remote.dto.CardRegisterRequest
import com.hack.kidhack.data.remote.dto.ChargeCancelRequest
import com.hack.kidhack.data.remote.dto.ChargeConfirmRequest
import com.hack.kidhack.data.remote.dto.ChargeConfirmResponse
import com.hack.kidhack.data.remote.dto.ChargePrepareRequest
import com.hack.kidhack.data.remote.dto.ChargePrepareResponse
import com.hack.kidhack.data.remote.dto.ChatMessageDto
import com.hack.kidhack.data.remote.dto.ChatMessagePageDto
import com.hack.kidhack.data.remote.dto.ChatRoomCreatedDto
import com.hack.kidhack.data.remote.dto.ChatRoomDto
import com.hack.kidhack.data.remote.dto.CheckoutRequest
import com.hack.kidhack.data.remote.dto.CreateChatRoomRequest
import com.hack.kidhack.data.remote.dto.ChildDetailDto
import com.hack.kidhack.data.remote.dto.ChildDto
import com.hack.kidhack.data.remote.dto.ChildPhotoDto
import com.hack.kidhack.data.remote.dto.ChildUpdateRequest
import com.hack.kidhack.data.remote.dto.CommentDto
import com.hack.kidhack.data.remote.dto.CommentRequest
import com.hack.kidhack.data.remote.dto.InquiryDetailDto
import com.hack.kidhack.data.remote.dto.InquiryRequest
import com.hack.kidhack.data.remote.dto.NoticeDetailDto
import com.hack.kidhack.data.remote.dto.NoticeDto
import com.hack.kidhack.data.remote.dto.UploadedFileDto
import com.hack.kidhack.data.remote.dto.LikeToggleDto
import com.hack.kidhack.data.remote.dto.PostRequest
import com.hack.kidhack.data.remote.dto.FindIdSendCodeRequest
import com.hack.kidhack.data.remote.dto.FindIdVerifyRequest
import com.hack.kidhack.data.remote.dto.FindIdVerifyResponse
import com.hack.kidhack.data.remote.dto.DashboardResponse
import com.hack.kidhack.data.remote.dto.DeviceEventRequest
import com.hack.kidhack.data.remote.dto.DeviceTokenRequest
import com.hack.kidhack.data.remote.dto.DeviceTokenResponse
import com.hack.kidhack.data.remote.dto.InquiryDto
import com.hack.kidhack.data.remote.dto.InstalledAppDto
import com.hack.kidhack.data.remote.dto.LocationEventDto
import com.hack.kidhack.data.remote.dto.LocationTrackRequest
import com.hack.kidhack.data.remote.dto.LocationTrackResponse
import com.hack.kidhack.data.remote.dto.LoginRequest
import com.hack.kidhack.data.remote.dto.LoginResponse
import com.hack.kidhack.data.remote.dto.ChildAlertDto
import com.hack.kidhack.data.remote.dto.NotificationDto
import com.hack.kidhack.data.remote.dto.NotificationSettingsDto
import com.hack.kidhack.data.remote.dto.PairingCodeDto
import com.hack.kidhack.data.remote.dto.PairingConnectRequest
import com.hack.kidhack.data.remote.dto.PaymentDto
import com.hack.kidhack.data.remote.dto.PhoneVerifyRequest
import com.hack.kidhack.data.remote.dto.PhoneVerifyResponse
import com.hack.kidhack.data.remote.dto.PhoneVerifySendCodeRequest
import com.hack.kidhack.data.remote.dto.PlaceRequest
import com.hack.kidhack.data.remote.dto.PlaceResponse
import com.hack.kidhack.data.remote.dto.PointBalanceDto
import com.hack.kidhack.data.remote.dto.PointHistoryDto
import com.hack.kidhack.data.remote.dto.PostDto
import com.hack.kidhack.data.remote.dto.RealtimeToggleResponse
import com.hack.kidhack.data.remote.dto.TrackResponse
import com.hack.kidhack.data.remote.dto.RefreshRequest
import com.hack.kidhack.data.remote.dto.RemoteCommandAckRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordSendCodeRequest
import com.hack.kidhack.data.remote.dto.SendCodeResponse
import com.hack.kidhack.data.remote.dto.ScreenTimeDto
import com.hack.kidhack.data.remote.dto.ScreenTimeRequest
import com.hack.kidhack.data.remote.dto.ScreenshotDto
import com.hack.kidhack.data.remote.dto.ScreenshotUploadDto
import com.hack.kidhack.data.remote.dto.SignupRequest
import com.hack.kidhack.data.remote.dto.UsePointRequest
import com.hack.kidhack.data.remote.dto.TransferRequest
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.dto.UserDto
import com.hack.kidhack.data.remote.dto.VisitRequest
import com.hack.kidhack.data.remote.dto.VisitResponse
import com.hack.kidhack.data.remote.dto.UserUpdateRequest
import com.hack.kidhack.data.remote.dto.WalletDto
import com.hack.kidhack.data.remote.dto.WalletTransactionDto
import com.hack.kidhack.data.remote.dto.WalletChargePrepareRequest
import com.hack.kidhack.data.remote.dto.WalletChargePrepareResponse
import com.hack.kidhack.data.remote.dto.WalletChargeApproveRequest
import com.hack.kidhack.data.remote.dto.WalletChargeApproveResponse
import com.hack.kidhack.data.remote.dto.ChargeOrderResponseDto
import com.hack.kidhack.data.remote.dto.WalletRefundResponse
import com.hack.kidhack.data.remote.dto.TxResponseDto
import com.hack.kidhack.data.remote.dto.ChargeHistoryResponseDto
import com.hack.kidhack.data.remote.dto.WalletTransferRequest
import com.hack.kidhack.data.remote.dto.WalletTransferResponse
import com.hack.kidhack.data.remote.dto.MyWalletResponse
import com.hack.kidhack.data.remote.dto.ChildWalletResponse
import com.hack.kidhack.data.remote.dto.EfinOtpIssueResponse
import com.hack.kidhack.data.remote.dto.EfinOtpVerifyRequest
import com.hack.kidhack.data.remote.dto.EfinOtpVerifyResponse
import com.hack.kidhack.data.remote.dto.EfinPaymentPasswordSetupRequest
import com.hack.kidhack.data.remote.dto.EfinPaymentPasswordChangeRequest
import com.hack.kidhack.data.remote.dto.EfinPaymentPasswordVerifyRequest
import com.hack.kidhack.data.remote.dto.EfinPaymentPasswordVerifyResponse
import com.hack.kidhack.data.remote.dto.EfinPaymentPasswordStatusResponse
import com.hack.kidhack.data.remote.dto.EfinIdentityStatusResponse
import com.hack.kidhack.data.remote.dto.EfinIdentityVerifyRequest
import com.hack.kidhack.data.remote.dto.EfinIdentityVerifyResponse
import com.hack.kidhack.data.remote.dto.CardPublicKeyResponse
import com.hack.kidhack.data.remote.dto.EncryptedCardRequest
import com.hack.kidhack.data.remote.dto.CardResponse
import com.hack.kidhack.data.remote.dto.QrIssueResponse
import com.hack.kidhack.data.remote.dto.QrStatusResponse
import com.hack.kidhack.data.remote.dto.QrCancelResponse
import com.hack.kidhack.data.remote.dto.QrPaymentHistoryResponseDto
import com.hack.kidhack.data.remote.dto.TerminalIssueRequest
import com.hack.kidhack.data.remote.dto.TerminalResponse
import com.hack.kidhack.data.remote.dto.TerminalAuthResponse
import com.hack.kidhack.data.remote.dto.QrApproveRequest
import com.hack.kidhack.data.remote.dto.QrApproveResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    /**
     * 가입 전 아이디·이메일 중복 확인. 로그인 불필요.
     *
     * 빈 값은 보내지 않는다 — 서버가 "확인하지 않음"(null)과 "쓸 수 있음"을 구분한다.
     */
    @GET("auth/availability")
    suspend fun availability(
        @Query("loginId") loginId: String? = null,
        @Query("email") email: String? = null,
    ): ApiEnvelope<AvailabilityDto>

    @POST("auth/signup")
    suspend fun signup(@Body body: SignupRequest): ApiEnvelope<UserDto>

    /**
     * 회원가입 휴대폰 인증 — 번호로 6자리 인증코드 발송. 로그인 불필요.
     *
     * 서버는 코드를 저장·검증하지 않고 응답 body 에 그대로 실어 준다([SendCodeResponse.code]).
     * 코드는 화면에 노출하지 않는다(패킷으로만 확인 가능).
     */
    @POST("auth/phone/send-code")
    suspend fun sendPhoneCode(@Body body: PhoneVerifySendCodeRequest): ApiEnvelope<SendCodeResponse>

    /**
     * 회원가입 휴대폰 인증 — 6자리 코드 검증. 로그인 불필요.
     *
     * 서버가 실제로 대조해 [PhoneVerifyResponse.valid] 로 결과를 준다. 클라는 이 응답을 신뢰한다
     * ([VULN-MOBILE-PHONEVERIFY-1]) — 응답을 valid:true 로 변조하면 우회된다.
     */
    @POST("auth/phone/verify")
    suspend fun verifyPhoneCode(@Body body: PhoneVerifyRequest): ApiEnvelope<PhoneVerifyResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): ApiEnvelope<Unit>

    @POST("auth/token/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiEnvelope<LoginResponse>

    /** 아이디 찾기 1단계 — 실명 + 이메일로 6자리 인증코드를 발송한다. */
    @POST("auth/find-id/send-code")
    suspend fun sendFindIdCode(@Body body: FindIdSendCodeRequest): ApiEnvelope<SendCodeResponse>

    /** 아이디 찾기 2단계 — 이메일 + 코드를 검증하고 로그인 아이디를 돌려준다. */
    @POST("auth/find-id/verify")
    suspend fun verifyFindId(@Body body: FindIdVerifyRequest): ApiEnvelope<FindIdVerifyResponse>

    /** 비밀번호 재설정 1단계 — 아이디 + 이메일로 6자리 인증코드를 발송한다. */
    @POST("auth/reset-password/send-code")
    suspend fun sendResetPasswordCode(
        @Body body: ResetPasswordSendCodeRequest,
    ): ApiEnvelope<SendCodeResponse>

    /** 비밀번호 재설정 2단계 — 이메일 + 코드 + 새 비밀번호. */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): ApiEnvelope<Unit>
}

interface UserApi {
    @GET("users/me")
    suspend fun me(): ApiEnvelope<UserDto>

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UserUpdateRequest): ApiEnvelope<UserDto>

    @DELETE("users/me")
    suspend fun deleteMe(): ApiEnvelope<Unit>

    @GET("users/me/activities")
    suspend fun activities(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<ActivityDto>>

    @GET("users/me/notification-settings")
    suspend fun notificationSettings(): ApiEnvelope<NotificationSettingsDto>

    @PATCH("users/me/notification-settings")
    suspend fun patchNotificationSettings(
        @Body body: NotificationSettingsDto,
    ): ApiEnvelope<NotificationSettingsDto>
}

interface PairingApi {
    @POST("pairing/code")
    suspend fun issueCode(): ApiEnvelope<PairingCodeDto>

    /**
     * 아직 만료되지 않은 내 페어링 코드 목록.
     *
     * 서버는 연결이 성공하면 그 코드를 삭제한다. 그래서 발급한 코드가 이 목록에 아직
     * 남아 있으면 「아무도 쓰지 않았다」는 뜻이다.
     */
    @GET("pairing/code")
    suspend fun activeCodes(): ApiEnvelope<List<PairingCodeDto>>

    /** 서버는 ApiResponse<Void> 를 준다 — data 가 null 이므로 UserDto 로 받으면 안 된다. */
    @POST("pairing/connect")
    suspend fun connect(@Body body: PairingConnectRequest): ApiEnvelope<Unit>
}

interface ChildApi {
    @GET("children")
    suspend fun children(): ApiEnvelope<List<ChildDto>>

    @GET("children/{childId}")
    suspend fun child(@Path("childId") childId: String): ApiEnvelope<ChildDetailDto>

    @PATCH("children/{childId}")
    suspend fun update(
        @Path("childId") childId: String,
        @Body body: ChildUpdateRequest,
    ): ApiEnvelope<ChildDetailDto>

    /**
     * 자녀 페어링 해제 (PARENT).
     *
     * ⚠️ 서버는 연결만 끊는 게 아니라 **계정을 소프트 삭제까지** 한다
     * (`child.setParent(null)` + `setDeleted(true)`). 되돌릴 수 없다.
     */
    @DELETE("children/{childId}")
    suspend fun unpair(@Path("childId") childId: String): ApiEnvelope<Unit>

    /** 자녀 강제 로그아웃 (PARENT). 자녀의 refresh token 을 전부 무효화한다. */
    @POST("children/{childId}/logout")
    suspend fun forceLogout(@Path("childId") childId: String): ApiEnvelope<Unit>
}

interface LocationApi {
    /** 자녀 앱 전용(CHILD). 좌표를 올리면 서버가 지오펜스/도착을 판정한다. */
    @POST("location/track")
    suspend fun track(@Body body: LocationTrackRequest): ApiEnvelope<LocationTrackResponse>

    /**
      * 동선 좌표 목록. 서버는 **시간 오름차순**으로 준다(마지막 항목이 가장 최근).
      * 미사용 필터: `date`(하루치), `from`/`to`(구간). 없으면 보관 기간 전체.
      */
    @GET("location/track/{childId}")
    suspend fun history(@Path("childId") childId: String): ApiEnvelope<TrackResponse>

    /** 실시간 추적 현재 상태 조회. */
    @GET("location/tracking/{childId}/realtime")
    suspend fun realtime(@Path("childId") childId: String): ApiEnvelope<RealtimeToggleResponse>

    /** body 없이 query `enabled`만 사용한다. */
    @PATCH("location/tracking/{childId}/realtime")
    suspend fun toggleRealtime(
        @Path("childId") childId: String,
        @Query("enabled") enabled: Boolean,
    ): ApiEnvelope<RealtimeToggleResponse>

}

interface DeviceApi {
    /** 기기 전원/GPS 꺼짐 보고. type = "POWER_OFF" | "GPS_OFF" (명세 4.16). */
    @POST("device/event")
    suspend fun reportEvent(@Body body: DeviceEventRequest): ApiEnvelope<Unit>

    /** FCM 토큰 등록. 로그인 직후와 onNewToken 때 호출한다. */
    @POST("devices/fcm-token")
    suspend fun registerPushToken(@Body body: DeviceTokenRequest): ApiEnvelope<Unit>

    /** FCM 토큰 해제. 로그아웃 때 호출한다. 멱등(없어도 200). */
    @HTTP(method = "DELETE", path = "devices/fcm-token", hasBody = true)
    suspend fun unregisterPushToken(@Body body: DeviceTokenRequest): ApiEnvelope<Unit>

    /** 내 기기 토큰 목록. 기기별 모델이라 여러 개일 수 있다. */
    @GET("devices/fcm-token")
    suspend fun pushTokens(): ApiEnvelope<List<DeviceTokenResponse>>
}

interface DashboardApi {
    /** 위치·실시간·최근알림3·차단·공간 통합 조회 (명세 4.3). */
    @GET("children/{childId}/dashboard")
    suspend fun get(@Path("childId") childId: String): ApiEnvelope<DashboardResponse>
}

interface PlaceApi {
    @POST("location/places")
    suspend fun create(@Body body: PlaceRequest): ApiEnvelope<PlaceResponse>

    @GET("location/places/{childId}")
    suspend fun list(@Path("childId") childId: String): ApiEnvelope<List<PlaceResponse>>

    /** 지정 장소 삭제. */
    @DELETE("location/places/{placeId}")
    suspend fun delete(@Path("placeId") placeId: String): ApiEnvelope<Unit>
}

interface LocationEventApi {
    @GET("location/events/{childId}")
    suspend fun events(@Path("childId") childId: String): ApiEnvelope<List<LocationEventDto>>
}

interface NotificationApi {
    /**
     * 알림 목록. 서버는 **배열이 아니라 페이지 래퍼**를 준다.
     *
     * 자녀별 필터 파라미터는 없다 — 응답의 `childId` 로 앱에서 거른다.
     */
    @GET("notifications/{recipientId}")
    suspend fun list(
        @Path("recipientId") recipientId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): ApiEnvelope<PageResponse<NotificationDto>>

    /** 1건 읽음. 경로 끝의 `/read` 가 빠지면 404 다. */
    @PATCH("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): ApiEnvelope<Unit>

    /** 안 읽은 알림을 전부 읽음 처리하고 처리 건수를 돌려준다. */
    @PATCH("notifications/{recipientId}/read-all")
    suspend fun markAllRead(@Path("recipientId") recipientId: String): ApiEnvelope<Int>

    /** 배지용 안 읽음 개수. */
    @GET("notifications/{recipientId}/unread-count")
    suspend fun unreadCount(@Path("recipientId") recipientId: String): ApiEnvelope<Int>
}

/**
 * 자녀 안전 사건 동기화 (docs/child-push-contract.md 4절 제안 계약).
 * 자녀 앱이 upload, 보호자 앱이 list 를 쓴다. 서버에 아직 없어도 앱은 정상 동작한다.
 */
interface ChildAlertApi {
    @POST("children/{childId}/alerts")
    suspend fun upload(
        @Path("childId") childId: String,
        @Body body: List<ChildAlertDto>,
    ): ApiEnvelope<Unit>

    @GET("children/{childId}/alerts")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("size") size: Int = 50,
    ): ApiEnvelope<List<ChildAlertDto>>
}

/** 유해사이트/앱 차단 — 자녀별 차단 규칙 CRUD + 접속 판정. */
interface BlocklistApi {
    @GET("filtering/rules/{childId}")
    suspend fun rules(@Path("childId") childId: String): ApiEnvelope<List<BlockRuleDto>>

    @POST("filtering/rules")
    suspend fun addRule(@Body body: BlockRuleRequest): ApiEnvelope<BlockRuleDto>

    /** 규칙 id 로 지운다. 자녀 id 는 경로에 들어가지 않는다. */
    @DELETE("filtering/rules/{ruleId}")
    suspend fun deleteRule(@Path("ruleId") ruleId: String): ApiEnvelope<Unit>

    /**
     * 접속 대상 보고 + 차단 여부 판정.
     * 서버는 자녀별 규칙에 더해 관리자 전역 유해 키워드까지 함께 본다.
     */
    @POST("filtering/visit")
    suspend fun visit(@Body body: VisitRequest): ApiEnvelope<VisitResponse>
}

interface ScreenshotApi {
    @GET("children/{childId}/screenshots")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<ScreenshotDto>>

    /** 서버는 `capturedAt`·`trigger` 를 **쿼리 파라미터로 필수** 요구한다. body 는 파일뿐이다. */
    @Multipart
    @POST("children/{childId}/screenshots")
    suspend fun upload(
        @Path("childId") childId: String,
        @Part file: MultipartBody.Part,
        @Query("capturedAt") capturedAt: String,
        @Query("trigger") trigger: String,
    ): ApiEnvelope<ScreenshotUploadDto>

    @POST("children/{childId}/screenshots/request")
    suspend fun requestCapture(@Path("childId") childId: String): ApiEnvelope<Unit>
}

/**
 * 부모 커뮤니티 (명세 20).
 *
 * 첨부는 [uploadAttachment] 로 먼저 올려 받은 `url` 을 [PostRequest.attachments] 에 담는다.
 * 신고 엔드포인트는 서버에 없다 — 예전에는 앱이 `/report` 를 부르며 404 를 받고 있었다.
 */
interface CommunityApi {
    /** @param keyword 제목 또는 작성자 닉네임 부분 검색. */
    @GET("community/posts")
    suspend fun posts(
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<PostDto>>

    @POST("community/posts")
    suspend fun create(@Body body: PostRequest): ApiEnvelope<PostDto>

    /** 본문·첨부·댓글은 여기에만 있다. 목록 항목에는 없다. */
    @GET("community/posts/{postId}")
    suspend fun post(@Path("postId") postId: Long): ApiEnvelope<PostDto>

    /** 넣은 필드만 반영된다. */
    @PATCH("community/posts/{postId}")
    suspend fun update(
        @Path("postId") postId: Long,
        @Body body: PostRequest,
    ): ApiEnvelope<PostDto>

    /** 글과 첨부 실물을 함께 지운다. */
    @DELETE("community/posts/{postId}")
    suspend fun delete(@Path("postId") postId: Long): ApiEnvelope<Unit>

    @POST("community/posts/{postId}/comments")
    suspend fun comment(
        @Path("postId") postId: Long,
        @Body body: CommentRequest,
    ): ApiEnvelope<CommentDto>

    /** 본인 댓글만. 남의 댓글이면 403. */
    @DELETE("community/posts/{postId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("postId") postId: Long,
        @Path("commentId") commentId: Long,
    ): ApiEnvelope<Unit>

    /** 누른 상태면 취소된다. 토글 후 상태와 총 개수가 온다. */
    @POST("community/posts/{postId}/like")
    suspend fun like(@Path("postId") postId: Long): ApiEnvelope<LikeToggleDto>

    @Multipart
    @POST("community/attachments")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part): ApiEnvelope<UploadedFileDto>
}

/**
 * 공용 파일 업로드 (`POST /files/{category}`).
 *
 * <p>분류는 `community` / `inquiry` / `notice` / `profile` / `screenshot`. 응답의 `url` 을
 * 그대로 저장 요청에 실어 보낸다.
 *
 * <p>커뮤니티·문의는 각자의 전용 업로드 엔드포인트를 쓰고 있어 그대로 둔다. 프로필 사진은
 * 전용 엔드포인트가 없어 이쪽을 쓴다.
 */
interface FileApi {
    @Multipart
    @POST("files/{category}")
    suspend fun upload(
        @Path("category") category: String,
        @Part file: MultipartBody.Part,
    ): ApiEnvelope<UploadedFileDto>
}

interface ChatApi {
    /** PARENT 전용. 부모–자녀 조합의 방이 있으면 반환하고 없으면 생성한다(멱등). */
    @POST("chat/rooms")
    suspend fun createOrGetRoom(@Body body: CreateChatRoomRequest): ApiEnvelope<ChatRoomCreatedDto>

    @GET("chat/rooms")
    suspend fun rooms(): ApiEnvelope<List<ChatRoomDto>>

    /** 커서 페이징. `page` 파라미터는 서버가 받지 않는다. size 최대 100. */
    @GET("chat/rooms/{roomId}/messages")
    suspend fun messages(
        @Path("roomId") roomId: String,
        @Query("before") before: String? = null,
        @Query("size") size: Int = 30,
    ): ApiEnvelope<ChatMessagePageDto>

    /** 이 방을 지금 시점까지 읽음 처리. 참여자(부모/자녀) 모두 호출 가능. */
    @POST("chat/rooms/{roomId}/read")
    suspend fun markRead(@Path("roomId") roomId: String): ApiEnvelope<Unit>
}

interface PaymentApi {
    @POST("payments/checkout")
    suspend fun checkout(@Body body: CheckoutRequest): ApiEnvelope<PaymentDto>

    @GET("payments/history")
    suspend fun history(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<PaymentDto>>
}

interface PointApi {
    @GET("points/balance")
    suspend fun balance(): ApiEnvelope<PointBalanceDto>

    @GET("points/history")
    suspend fun history(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<PointHistoryDto>>

    @POST("points/use")
    suspend fun use(@Body body: UsePointRequest): ApiEnvelope<Unit>
}

interface AppUsageApi {
    @POST("monitoring/usage")
    suspend fun report(@Body body: AppUsageReportRequest): ApiEnvelope<Int>

    /**
     * 자녀 기기에 「사용량을 지금 올려라」. 202 를 주고 끝나며, 실제 값은 자녀 앱이
     * 곧이어 [report] 로 올려 완성된다. 자녀 기기가 꺼져 있으면 요청은 닿지 않는다.
     */
    @POST("monitoring/usage/{childId}/request")
    suspend fun requestReport(@Path("childId") childId: String): ApiEnvelope<Unit>

    @GET("monitoring/usage/{childId}")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("groupBy") groupBy: String = "DAY",
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): ApiEnvelope<AppUsageAggregateResponse>
}

/**
 * 부모 요청 처리 결과 응답 (신규 제안 계약, GAP-11).
 *
 * 소켓이 꺼진 상태로 FCM 이 도착하면 `socket.ack` 이 no-op 이므로, 결과를 서버로
 * 되돌릴 경로는 이 REST ack 뿐이다. 서버가 아직 제공하지 않으면 404 를 무시한다.
 *
 * 폴링(`GET .../commands/pending`)은 서버에 대응 경로가 없어 제거했다(2026-08-28).
 */
interface CommandApi {
    @POST("children/{childId}/commands/{requestId}/ack")
    suspend fun ack(
        @Path("childId") childId: String,
        @Path("requestId") requestId: String,
        @Body body: RemoteCommandAckRequest,
    ): ApiEnvelope<Unit>
}

/** 자녀 카메라 촬영본 업로드·조회·요청 (신규 제안 계약, ScreenshotApi 와 대칭). */
interface PhotoApi {
    @GET("children/{childId}/photos")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<ChildPhotoDto>>

    @Multipart
    @POST("children/{childId}/photos")
    suspend fun upload(
        @Path("childId") childId: String,
        @Part file: MultipartBody.Part,
        @Part("capturedAt") capturedAt: RequestBody,
        @Part("requestId") requestId: RequestBody?,
    ): ApiEnvelope<ChildPhotoDto>

    /** 202 를 기대한다. 서버는 자녀 소켓/pending 큐로 CAMERA_REQUEST 를 밀어 줘야 한다. */
    @POST("children/{childId}/photos/request")
    suspend fun requestShot(@Path("childId") childId: String): ApiEnvelope<Unit>
}

/** 루키즈페이 용돈 지갑 (신규 제안 계약, GAP-05). */
interface AllowanceApi {
    @GET("children/{childId}/allowance")
    suspend fun balance(@Path("childId") childId: String): ApiEnvelope<AllowanceDto>

    @GET("children/{childId}/allowance/history")
    suspend fun history(
        @Path("childId") childId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): ApiEnvelope<PageResponse<AllowanceEntryDto>>

    /** 부모 전용: 용돈 보내기 */
    @POST("children/{childId}/allowance/send")
    suspend fun send(
        @Path("childId") childId: String,
        @Body body: AllowanceTransferRequest,
    ): ApiEnvelope<AllowanceDto>

    /** 부모 전용: 용돈 회수하기 */
    @POST("children/{childId}/allowance/withdraw")
    suspend fun withdraw(
        @Path("childId") childId: String,
        @Body body: AllowanceTransferRequest,
    ): ApiEnvelope<AllowanceDto>

    /** 자녀 전용: QR 결제 승인 */
    @POST("children/{childId}/allowance/payments")
    suspend fun pay(
        @Path("childId") childId: String,
        @Body body: AllowancePaymentRequest,
    ): ApiEnvelope<AllowancePaymentDto>
}

/** 앱 통제 — 설치 앱 동기화/조회 + 앱별 사용시간 제한 규칙 CRUD. */
interface AppControlApi {
    @GET("appcontrol/apps/{childId}")
    suspend fun installedApps(@Path("childId") childId: String): ApiEnvelope<List<InstalledAppDto>>

    /** 자녀 앱이 설치 목록을 올린다. 반영된 개수를 돌려준다. */
    @POST("appcontrol/sync")
    suspend fun syncApps(@Body body: AppSyncRequest): ApiEnvelope<Int>

    @GET("appcontrol/rules/{childId}")
    suspend fun rules(@Path("childId") childId: String): ApiEnvelope<List<AppControlDto>>

    @POST("appcontrol/rules")
    suspend fun addRule(@Body body: AppControlCreateRequest): ApiEnvelope<AppControlDto>

    @PATCH("appcontrol/rules/{ruleId}")
    suspend fun updateRule(
        @Path("ruleId") ruleId: String,
        @Body body: AppControlUpdateRequest,
    ): ApiEnvelope<AppControlDto>

    @DELETE("appcontrol/rules/{ruleId}")
    suspend fun deleteRule(@Path("ruleId") ruleId: String): ApiEnvelope<Unit>

    /** 하루 총 스크린타임 한도. 부모·자녀 모두 조회할 수 있다. */
    @GET("appcontrol/screentime/{childId}")
    suspend fun screenTime(@Path("childId") childId: String): ApiEnvelope<ScreenTimeDto>

    /** 한도 설정(부모). null 이면 제한 해제. */
    @PUT("appcontrol/screentime/{childId}")
    suspend fun setScreenTime(
        @Path("childId") childId: String,
        @Body body: ScreenTimeRequest,
    ): ApiEnvelope<ScreenTimeDto>

    /**
     * 한도 도달 보고(자녀 앱). 서버가 부모·자녀 양쪽에 알림을 발행한다.
     * 한도가 설정돼 있지 않으면 서버가 무시하고 false 를 준다.
     */
    @POST("appcontrol/screentime/{childId}/reached")
    suspend fun reportScreenTimeReached(
        @Path("childId") childId: String,
        @Query("usedMinutes") usedMinutes: Int,
    ): ApiEnvelope<Boolean>
}

/**
 * 1:1 문의 (명세 21) — 본인 문의만 보인다.
 *
 * 첨부는 [upload] 로 먼저 올려 받은 `url` 을 [InquiryRequest.attachments] 에 담는다.
 * 갤러리 URI 를 그대로 보내면 서버에 그 파일이 없다.
 */
interface InquiryApi {
    @GET("inquiries")
    suspend fun list(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<InquiryDto>>

    @POST("inquiries")
    suspend fun create(@Body body: InquiryRequest): ApiEnvelope<InquiryDto>

    /** 본문·첨부·답변은 여기에만 있다. 목록 항목에는 없다. */
    @GET("inquiries/{id}")
    suspend fun detail(@Path("id") id: Long): ApiEnvelope<InquiryDetailDto>

    /** 통째로 교체한다. 답변이 달린 문의는 400. */
    @PUT("inquiries/{id}")
    suspend fun update(@Path("id") id: Long, @Body body: InquiryRequest): ApiEnvelope<InquiryDto>

    /** 첨부 실물·답변까지 함께 지운다. 답변이 달린 문의는 400. */
    @DELETE("inquiries/{id}")
    suspend fun delete(@Path("id") id: Long): ApiEnvelope<Unit>

    @Multipart
    @POST("inquiries/upload")
    suspend fun upload(@Part file: MultipartBody.Part): ApiEnvelope<UploadedFileDto>
}

/** 공지사항 (명세 23) — 열람에 인증이 필요 없다(비로그인 포함 누구나). */
interface NoticeApi {
    /** @param keyword 제목 부분 검색(대소문자 무시). 비우면 전체. */
    @GET("notices")
    suspend fun list(
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10,
    ): ApiEnvelope<PageResponse<NoticeDto>>

    @GET("notices/{id}")
    suspend fun detail(@Path("id") id: Long): ApiEnvelope<NoticeDetailDto>
}

interface PaymentChargeApi {
    @POST("payments/charge/prepare")
    suspend fun prepare(@Body body: ChargePrepareRequest): ApiEnvelope<ChargePrepareResponse>

    @POST("payments/charge/confirm")
    suspend fun confirm(@Body body: ChargeConfirmRequest): ApiEnvelope<ChargeConfirmResponse>

    @GET("payments/charges")
    suspend fun list(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiEnvelope<PageResponse<ChargePrepareResponse>>

    @POST("payments/charges/{chargeId}/cancel")
    suspend fun cancel(@Path("chargeId") chargeId: String, @Body body: ChargeCancelRequest): ApiEnvelope<Unit>
}

/**
 * 지갑 API (서버 v0.2, /api/wallet) — Swagger 태그 11~13.
 * 옛 [PaymentChargeApi]·[PaymentApi]·[PointApi] 는 남겨 두되, 화면은 이 인터페이스를 쓴다.
 */
interface WalletApi {
    // 11. 충전/환불 (2단계: prepare → approve)
    @POST("wallet/charge/prepare")
    suspend fun chargePrepare(@Body body: WalletChargePrepareRequest): ApiEnvelope<WalletChargePrepareResponse>

    @POST("wallet/charge/approve")
    suspend fun chargeApprove(@Body body: WalletChargeApproveRequest): ApiEnvelope<WalletChargeApproveResponse>

    @POST("wallet/charge/{merchantUid}/cancel")
    suspend fun chargeCancel(@Path("merchantUid") merchantUid: String): ApiEnvelope<ChargeOrderResponseDto>

    @POST("wallet/charge/{merchantUid}/refund")
    suspend fun chargeRefund(@Path("merchantUid") merchantUid: String): ApiEnvelope<WalletRefundResponse>

    @GET("wallet/transactions")
    suspend fun transactions(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<TxResponseDto>>

    @GET("wallet/charges")
    suspend fun charges(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<ChargeOrderResponseDto>>

    @GET("wallet/charge-history")
    suspend fun chargeHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<ChargeHistoryResponseDto>>

    // 12. 이체/회수
    @POST("wallet/transfer")
    suspend fun transfer(@Body body: WalletTransferRequest): ApiEnvelope<WalletTransferResponse>

    @POST("wallet/recover")
    suspend fun recover(@Body body: WalletTransferRequest): ApiEnvelope<WalletTransferResponse>

    // 13. 조회 (잔액 + 이번달 합계 + 원장이 한 응답)
    @GET("wallet/me")
    suspend fun me(
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<MyWalletResponse>

    @GET("wallet/children/{childId}")
    suspend fun childWallet(
        @Path("childId") childId: Long,
        @Query("limit") limit: Int = 5,
    ): ApiEnvelope<ChildWalletResponse>

    @GET("wallet/children/{childId}/transactions")
    suspend fun childTransactions(
        @Path("childId") childId: Long,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<MyWalletResponse>

    @GET("wallet/children/{childId}/transfers")
    suspend fun childTransfers(
        @Path("childId") childId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<TxResponseDto>>
}

/**
 * 카드 API (서버 v0.2, /api/wallet/cards) — Swagger 태그 14. E2E 암호화 등록.
 * 등록 흐름: [publicKey] 조회 → 클라이언트 암호화 → [register].
 */
interface CardApi {
    @GET("wallet/cards/public-key")
    suspend fun publicKey(): ApiEnvelope<CardPublicKeyResponse>

    @POST("wallet/cards")
    suspend fun register(@Body body: EncryptedCardRequest): ApiEnvelope<CardResponse>

    @GET("wallet/cards")
    suspend fun list(): ApiEnvelope<List<CardResponse>>

    @DELETE("wallet/cards/{cardId}")
    suspend fun delete(@Path("cardId") cardId: Long): ApiEnvelope<Unit>
}

/**
 * QR 결제 API (서버 v0.2, /api/qrpay) — Swagger 태그 15·18·19.
 * 발급(자녀)·상태 폴링·취소(부모)·결제 내역. 매장 단말 승인은 [StoreTerminalApi] 로 분리.
 */
interface QrPayApi {
    // 15. QR 발급/조회 (자녀)
    @POST("qrpay/qr")
    suspend fun issue(): ApiEnvelope<QrIssueResponse>

    @GET("qrpay/qr/{qrToken}")
    suspend fun status(@Path("qrToken") qrToken: String): ApiEnvelope<QrStatusResponse>

    // 18. 결제 취소 (부모)
    @POST("qrpay/payments/{qrToken}/cancel")
    suspend fun cancel(@Path("qrToken") qrToken: String): ApiEnvelope<QrCancelResponse>

    // 19. 결제 내역 (부모는 childId 필수, 자녀는 파라미터 무시)
    @GET("qrpay/payments")
    suspend fun history(
        @Query("childId") childId: Long? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<QrPaymentHistoryResponseDto>>
}

/**
 * 매장 단말 API (서버 v0.2, /api/qrpay/terminals · /api/qrpay/terminal) — Swagger 태그 16·17.
 *
 * **회원 앱 정상 흐름과 분리된 테스트/시연 전용 경로다.** 단말 승인은 앱 JWT 가 아니라
 * `X-Store-Terminal-Token` 헤더로만 인증된다(자녀 앱의 자가승인과 명확히 구분).
 */
interface StoreTerminalApi {
    // 17. 단말 등록/토큰 발급 + 목록
    @POST("qrpay/terminals")
    suspend fun issue(@Body body: TerminalIssueRequest): ApiEnvelope<TerminalResponse>

    @GET("qrpay/terminals")
    suspend fun list(): ApiEnvelope<List<TerminalResponse>>

    // 16. 단말 인증 확인 + 결제 승인 (X-Store-Terminal-Token 헤더)
    @GET("qrpay/terminal/me")
    suspend fun me(@Header("X-Store-Terminal-Token") terminalToken: String): ApiEnvelope<TerminalAuthResponse>

    @POST("qrpay/terminal/pay")
    suspend fun pay(
        @Header("X-Store-Terminal-Token") terminalToken: String,
        @Body body: QrApproveRequest,
    ): ApiEnvelope<QrApproveResponse>
}

/**
 * 전자금융 거래 보안 API (/api/efin) — Swagger 태그 20~22.
 * 거래 OTP(2차 인증)·결제 비밀번호·실명확인. 거래 훅(서명·nonce·소유주)은 지갑/QR 요청 필드로 전달된다.
 */
interface EfinApi {
    // 20. 거래 OTP (발급 코드는 응답에 노출 — [VULN-EFIN-1])
    @POST("efin/otp/issue")
    suspend fun issueOtp(): ApiEnvelope<EfinOtpIssueResponse>

    @POST("efin/otp/verify")
    suspend fun verifyOtp(@Body body: EfinOtpVerifyRequest): ApiEnvelope<EfinOtpVerifyResponse>

    // 21. 결제 비밀번호 (설정=카드 발급 흐름, 변경=계정설정)
    @POST("efin/payment-password/setup")
    suspend fun setupPaymentPassword(@Body body: EfinPaymentPasswordSetupRequest): ApiEnvelope<Unit>

    @PUT("efin/payment-password")
    suspend fun changePaymentPassword(@Body body: EfinPaymentPasswordChangeRequest): ApiEnvelope<Unit>

    @POST("efin/payment-password/verify")
    suspend fun verifyPaymentPassword(@Body body: EfinPaymentPasswordVerifyRequest): ApiEnvelope<EfinPaymentPasswordVerifyResponse>

    @GET("efin/payment-password/status")
    suspend fun paymentPasswordStatus(): ApiEnvelope<EfinPaymentPasswordStatusResponse>

    // 22. 실명확인(신분증 진위확인) + 상태
    @POST("efin/identity/verify")
    suspend fun verifyIdentity(@Body body: EfinIdentityVerifyRequest): ApiEnvelope<EfinIdentityVerifyResponse>

    @GET("efin/identity/status")
    suspend fun identityStatus(): ApiEnvelope<EfinIdentityStatusResponse>
}
