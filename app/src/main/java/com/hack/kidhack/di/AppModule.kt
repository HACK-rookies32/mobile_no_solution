@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.hack.kidhack.di

import com.hack.kidhack.BuildConfig
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.data.local.CapturedShotStore
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.local.BlocklistStore
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.data.local.ChildCaptureStore
import com.hack.kidhack.data.local.HarmfulSettingsStore
import com.hack.kidhack.data.local.AppUsageReportStore
import com.hack.kidhack.data.local.PushTokenStore
import com.hack.kidhack.data.local.ParentWatchStore
import com.hack.kidhack.data.local.ServerAlertStore
import com.hack.kidhack.data.local.ChildPhotoStore
import com.hack.kidhack.data.local.ProfileScopeStore
import com.hack.kidhack.data.local.ChildOnboardingStoreImpl
import com.hack.kidhack.data.local.SelectedChildRepositoryImpl
import com.hack.kidhack.data.local.SessionEventsImpl
import com.hack.kidhack.data.local.SessionRepositoryImpl
import com.hack.kidhack.data.remote.api.AllowanceApi
import com.hack.kidhack.data.remote.api.AppControlApi
import com.hack.kidhack.data.remote.api.AppUsageApi
import com.hack.kidhack.data.remote.api.AuthApi
import com.hack.kidhack.data.remote.api.BlocklistApi
import com.hack.kidhack.data.remote.api.CardApi
import com.hack.kidhack.data.remote.api.ChatApi
import com.hack.kidhack.data.remote.api.ChildApi
import com.hack.kidhack.data.remote.api.FileApi
import com.hack.kidhack.data.remote.api.ChildAlertApi
import com.hack.kidhack.data.remote.api.CommandApi
import com.hack.kidhack.data.remote.api.CommunityApi
import com.hack.kidhack.data.remote.api.DashboardApi
import com.hack.kidhack.data.remote.api.DeviceApi
import com.hack.kidhack.data.remote.api.InquiryApi
import com.hack.kidhack.data.remote.api.NoticeApi
import com.hack.kidhack.data.remote.api.LocationApi
import com.hack.kidhack.data.remote.api.LocationEventApi
import com.hack.kidhack.data.remote.api.NotificationApi
import com.hack.kidhack.data.remote.api.PairingApi
import com.hack.kidhack.data.remote.api.PlaceApi
import com.hack.kidhack.data.remote.api.PaymentApi
import com.hack.kidhack.data.remote.api.PhotoApi
import com.hack.kidhack.data.remote.api.PaymentChargeApi
import com.hack.kidhack.data.remote.api.PointApi
import com.hack.kidhack.data.remote.api.ScreenshotApi
import com.hack.kidhack.data.remote.api.UserApi
import com.hack.kidhack.data.remote.api.WalletApi
import com.hack.kidhack.data.remote.api.QrPayApi
import com.hack.kidhack.data.remote.api.StoreTerminalApi
import com.hack.kidhack.data.remote.api.EfinApi
import com.hack.kidhack.domain.repository.EfinRepository
import com.hack.kidhack.data.repository.real.RealEfinRepository
import com.hack.kidhack.data.repository.fake.FakeEfinRepository
import com.hack.kidhack.data.remote.interceptor.AuthInterceptor
import com.hack.kidhack.data.remote.interceptor.TokenAuthenticator
import com.hack.kidhack.data.repository.CachedChatRepository
import com.hack.kidhack.data.repository.fake.FakeAllowanceRepository
import com.hack.kidhack.data.repository.fake.FakeAppControlRepository
import com.hack.kidhack.data.repository.fake.FakeAppUsageRepository
import com.hack.kidhack.data.repository.fake.FakeAuthRepository
import com.hack.kidhack.data.repository.fake.FakeBlocklistRepository
import com.hack.kidhack.data.repository.fake.FakeChildAlertRepository
import com.hack.kidhack.data.repository.fake.FakeDeviceRepository
import com.hack.kidhack.data.repository.fake.MockNotificationFeed
import com.hack.kidhack.data.repository.fake.MockPhotoFeed
import com.hack.kidhack.data.repository.fake.FakeCardRepository
import com.hack.kidhack.data.repository.fake.FakeChatRepository
import com.hack.kidhack.data.repository.fake.FakeChildRepository
import com.hack.kidhack.data.repository.fake.FakeCommandRepository
import com.hack.kidhack.data.repository.fake.FakeCommunityRepository
import com.hack.kidhack.data.repository.fake.FakeInquiryRepository
import com.hack.kidhack.data.repository.fake.FakeNoticeRepository
import com.hack.kidhack.data.repository.fake.FakeLocationEventRepository
import com.hack.kidhack.data.repository.fake.FakeDashboardRepository
import com.hack.kidhack.data.repository.fake.FakeLocationRepository
import com.hack.kidhack.data.repository.fake.FakeNotificationRepository
import com.hack.kidhack.data.repository.fake.FakePairingRepository
import com.hack.kidhack.data.repository.fake.FakePlaceRepository
import com.hack.kidhack.data.repository.fake.FakePaymentRepository
import com.hack.kidhack.data.repository.fake.FakePhotoRepository
import com.hack.kidhack.data.repository.fake.FakePointRepository
import com.hack.kidhack.data.repository.fake.FakeScreenshotRepository
import com.hack.kidhack.data.repository.fake.FakeUserRepository
import com.hack.kidhack.data.repository.real.RealAllowanceRepository
import com.hack.kidhack.data.repository.fake.FakeWalletRepository
import com.hack.kidhack.data.repository.fake.FakeQrPayRepository
import com.hack.kidhack.data.repository.fake.FakeStoreTerminalRepository
import com.hack.kidhack.data.repository.real.RealQrPayRepository
import com.hack.kidhack.data.repository.real.RealStoreTerminalRepository
import com.hack.kidhack.data.repository.real.RealAppControlRepository
import com.hack.kidhack.data.repository.real.RealAppUsageRepository
import com.hack.kidhack.data.repository.real.RealAuthRepository
import com.hack.kidhack.data.repository.real.RealBlocklistRepository
import com.hack.kidhack.data.repository.real.RealChildAlertRepository
import com.hack.kidhack.data.repository.real.RealDeviceRepository
import com.hack.kidhack.data.repository.real.RealCardRepository
import com.hack.kidhack.data.repository.real.RealChatRepository
import com.hack.kidhack.data.repository.real.RealChildRepository
import com.hack.kidhack.data.repository.real.RealCommandRepository
import com.hack.kidhack.data.repository.real.RealCommunityRepository
import com.hack.kidhack.data.repository.real.RealInquiryRepository
import com.hack.kidhack.data.repository.real.RealNoticeRepository
import com.hack.kidhack.data.repository.real.RealLocationEventRepository
import com.hack.kidhack.data.repository.real.RealDashboardRepository
import com.hack.kidhack.data.repository.real.RealLocationRepository
import com.hack.kidhack.data.repository.real.RealNotificationRepository
import com.hack.kidhack.data.repository.real.RealPairingRepository
import com.hack.kidhack.data.repository.real.RealPlaceRepository
import com.hack.kidhack.data.repository.real.RealPaymentRepository
import com.hack.kidhack.data.repository.real.RealPhotoRepository
import com.hack.kidhack.data.repository.real.RealPointRepository
import com.hack.kidhack.data.repository.real.RealScreenshotRepository
import com.hack.kidhack.data.repository.real.RealUserRepository
import com.hack.kidhack.data.repository.real.RealWalletRepository
import com.hack.kidhack.data.ws.ChatWebSocketClient
import com.hack.kidhack.data.ws.ChildCommandSocket
import com.hack.kidhack.domain.repository.AllowanceRepository
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.ChildAlertRepository
import com.hack.kidhack.domain.repository.DeviceRepository
import com.hack.kidhack.domain.repository.CardRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.CommunityRepository
import com.hack.kidhack.domain.repository.InquiryRepository
import com.hack.kidhack.domain.repository.NoticeRepository
import com.hack.kidhack.domain.repository.LocationEventRepository
import com.hack.kidhack.domain.repository.DashboardRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.PairingRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.PaymentRepository
import com.hack.kidhack.domain.repository.PhotoRepository
import com.hack.kidhack.domain.repository.PointRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.UserRepository
import com.hack.kidhack.domain.repository.WalletRepository
import com.hack.kidhack.domain.repository.QrPayRepository
import com.hack.kidhack.domain.repository.StoreTerminalRepository
import com.hack.kidhack.feature.auth.forgot.ForgotViewModel
import com.hack.kidhack.feature.auth.login.LoginViewModel
import com.hack.kidhack.feature.auth.register.RegisterViewModel
import com.hack.kidhack.feature.parent.AppControlViewModel
import com.hack.kidhack.feature.parent.CardViewModel
import com.hack.kidhack.feature.parent.ChildProfileViewModel
import com.hack.kidhack.feature.parent.CommunityDetailViewModel
import com.hack.kidhack.feature.parent.ActivityViewModel
import com.hack.kidhack.feature.parent.ChildAccountViewModel
import com.hack.kidhack.feature.parent.WithdrawViewModel
import com.hack.kidhack.feature.parent.CommunityListViewModel
import com.hack.kidhack.feature.parent.CommunityWriteViewModel
import com.hack.kidhack.feature.parent.ContactViewModel
import com.hack.kidhack.feature.parent.EditProfileViewModel
import com.hack.kidhack.feature.parent.AppUsageDetailViewModel
import com.hack.kidhack.feature.parent.ChildCareViewModel
import com.hack.kidhack.feature.parent.ReportViewModel
import com.hack.kidhack.feature.parent.GeofenceViewModel
import com.hack.kidhack.feature.parent.HarmfulViewModel
import com.hack.kidhack.feature.parent.LocationViewModel
import com.hack.kidhack.feature.parent.MypageViewModel
import com.hack.kidhack.feature.common.NoticeViewModel
import com.hack.kidhack.feature.parent.NotificationsViewModel
import com.hack.kidhack.feature.parent.PairingViewModel
import com.hack.kidhack.feature.parent.ParentChatViewModel
import com.hack.kidhack.feature.parent.PayViewModel
import com.hack.kidhack.feature.parent.WalletViewModel
import com.hack.kidhack.feature.efin.PaymentPasswordViewModel
import com.hack.kidhack.feature.efin.IdentityVerifyViewModel
import com.hack.kidhack.feature.efin.OtpNotifier
import com.hack.kidhack.feature.efin.TxAuthCoordinator
import com.hack.kidhack.feature.terminal.StoreTerminalViewModel
import com.hack.kidhack.feature.parent.CameraRequestViewModel
import com.hack.kidhack.feature.parent.ScreenTimeViewModel
import com.hack.kidhack.feature.parent.ScreenshotViewModel
import com.hack.kidhack.feature.child.ChildPairingViewModel
import com.hack.kidhack.feature.child.chat.ChildChatViewModel
import com.hack.kidhack.feature.child.home.ChildHomeViewModel
import com.hack.kidhack.feature.child.notifications.ChildNotificationsViewModel
import com.hack.kidhack.feature.child.mypage.ChildMypageViewModel
import com.hack.kidhack.feature.child.mypage.ChildProfileEditViewModel
import com.hack.kidhack.feature.child.camera.ChildCameraViewModel
import com.hack.kidhack.feature.child.onboarding.ChildOnboardingViewModel
import com.hack.kidhack.feature.child.pay.ChildPayViewModel
import com.hack.kidhack.feature.child.screenshot.ChildScreenshotViewModel
import com.hack.kidhack.feature.child.services.ChildServicesViewModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private val useMock: Boolean = BuildConfig.USE_MOCK

val appModule = module {
    single<SessionEvents> { SessionEventsImpl() }
    single<SessionRepository> { SessionRepositoryImpl(androidContext()) }
    single<SelectedChildRepository> { SelectedChildRepositoryImpl(androidContext()) }
    single<ChildOnboardingStore> { ChildOnboardingStoreImpl(androidContext()) }
    single { ChatMessageStore(androidContext()) }
    single { ChildAlertStore(androidContext(), get()) }
    single { ChildCaptureStore(androidContext()) }
    single { ServerAlertStore(androidContext(), get()) }
    single { HarmfulSettingsStore(androidContext()) }
    single { ParentWatchStore(androidContext()) }
    single { PushTokenStore(androidContext()) }
    single { AppUsageReportStore(androidContext()) }
    single { BlocklistStore(androidContext()) }
    single { MockNotificationFeed(androidContext()) }
    single { MockPhotoFeed(androidContext()) }
    single { ChildPhotoStore(androidContext()) }
    single { CapturedShotStore(androidContext()) }
    single { ProfileScopeStore(androidContext()) }

    single {
        // [VULN-MOBILE-LOG-1] api 변형은 initWith(debug)라 BuildConfig.DEBUG=true → HTTP 요청/응답 본문 +
        // Authorization: Bearer 헤더가 전부 logcat 에 평문 출력된다(채팅 payload 로깅 ChatWebSocketClient 도 동반).
        // 의도된 취약점(OWASP Mobile M9 Insecure Data Storage / 민감정보 로그 노출). 보존: 고치지 말 것.
        // 명세: mobile/docs/vuln-spec-mobile.md
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get()))
            .authenticator(
                TokenAuthenticator(
                    sessionRepository = get(),
                    sessionEvents = get(),
                    chatMessageStore = get(),
                    chatWebSocketClient = get(),
                    baseUrl = BuildConfig.BASE_URL.trimEnd('/') + "/",
                    refreshClient = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .build(),
                ),
            )
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    single {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL.trimEnd('/') + "/")
            .client(get())
            .addConverterFactory(KidhackJson.instance.asConverterFactory(contentType))
            .build()
    }

    single { get<Retrofit>().create(AuthApi::class.java) }
    single { get<Retrofit>().create(UserApi::class.java) }
    single { get<Retrofit>().create(PairingApi::class.java) }
    single { get<Retrofit>().create(ChildApi::class.java) }
    single { get<Retrofit>().create(FileApi::class.java) }
    single { get<Retrofit>().create(LocationApi::class.java) }
    single { get<Retrofit>().create(DeviceApi::class.java) }
    single { get<Retrofit>().create(DashboardApi::class.java) }
    single { get<Retrofit>().create(PlaceApi::class.java) }
    single { get<Retrofit>().create(LocationEventApi::class.java) }
    single { get<Retrofit>().create(NotificationApi::class.java) }
    single { get<Retrofit>().create(BlocklistApi::class.java) }
    single { get<Retrofit>().create(ScreenshotApi::class.java) }
    single { get<Retrofit>().create(CommunityApi::class.java) }
    single { get<Retrofit>().create(ChatApi::class.java) }
    single { get<Retrofit>().create(PaymentApi::class.java) }
    single { get<Retrofit>().create(PointApi::class.java) }
    single { get<Retrofit>().create(AppUsageApi::class.java) }
    single { get<Retrofit>().create(AllowanceApi::class.java) }
    single { get<Retrofit>().create(CommandApi::class.java) }
    single { get<Retrofit>().create(ChildAlertApi::class.java) }
    single { get<Retrofit>().create(PhotoApi::class.java) }
    single { get<Retrofit>().create(AppControlApi::class.java) }
    single { get<Retrofit>().create(InquiryApi::class.java) }
    single { get<Retrofit>().create(NoticeApi::class.java) }
    single { get<Retrofit>().create(PaymentChargeApi::class.java) }
    single { get<Retrofit>().create(WalletApi::class.java) }
    single { get<Retrofit>().create(CardApi::class.java) }
    single { get<Retrofit>().create(QrPayApi::class.java) }
    single { get<Retrofit>().create(StoreTerminalApi::class.java) }
    single { get<Retrofit>().create(EfinApi::class.java) }

    single<AuthRepository> {
        if (useMock) {
            FakeAuthRepository(get(), get(), get(), get())
        } else {
            RealAuthRepository(get(), get(), get(), get(), get(), get())
        }
    }
    single<UserRepository> { if (useMock) FakeUserRepository(get()) else RealUserRepository(get()) }
    single<PairingRepository> { if (useMock) FakePairingRepository() else RealPairingRepository(get()) }
    single<ChildRepository> { if (useMock) FakeChildRepository() else RealChildRepository(get(), get(), androidContext()) }
    single<LocationRepository> { if (useMock) FakeLocationRepository() else RealLocationRepository(get()) }
    single<DeviceRepository> { if (useMock) FakeDeviceRepository() else RealDeviceRepository(get(), androidContext()) }
    single<DashboardRepository> { if (useMock) FakeDashboardRepository(get(), get(), get()) else RealDashboardRepository(get()) }
    single<PlaceRepository> { if (useMock) FakePlaceRepository() else RealPlaceRepository(get()) }
    single<LocationEventRepository> {
        if (useMock) FakeLocationEventRepository() else RealLocationEventRepository(get())
    }
    single<NotificationRepository> {
        if (useMock) FakeNotificationRepository(get()) else RealNotificationRepository(get())
    }
    single<ChildAlertRepository> {
        if (useMock) FakeChildAlertRepository(get()) else RealChildAlertRepository(get())
    }
    single<BlocklistRepository> { if (useMock) FakeBlocklistRepository(get(), get()) else RealBlocklistRepository(get()) }
    single<ScreenshotRepository> {
        if (useMock) FakeScreenshotRepository(get(), get()) else RealScreenshotRepository(get())
    }
    single<CommunityRepository> {
        if (useMock) FakeCommunityRepository() else RealCommunityRepository(get(), androidContext())
    }
    single<InquiryRepository> {
        if (useMock) FakeInquiryRepository() else RealInquiryRepository(get(), androidContext())
    }
    single<NoticeRepository> {
        if (useMock) FakeNoticeRepository() else RealNoticeRepository(get())
    }
    single { ChatWebSocketClient(get(), BuildConfig.BASE_URL) }
    // 로그인 중 콜드 스타트용 캐시. 로그아웃 시 AuthRepository가 ChatMessageStore를 비운다.
    single<ChatRepository> {
        val delegate = if (useMock) {
            FakeChatRepository(get())
        } else {
            RealChatRepository(get(), get(), get(), get(), androidContext())
        }
        CachedChatRepository(delegate, get())
    }
    single<PaymentRepository> { if (useMock) FakePaymentRepository() else RealPaymentRepository(get()) }
    single<PointRepository> { if (useMock) FakePointRepository() else RealPointRepository(get()) }
    single<AppUsageRepository> { if (useMock) FakeAppUsageRepository(get()) else RealAppUsageRepository(get()) }
    single<AppControlRepository> {
        if (useMock) FakeAppControlRepository() else RealAppControlRepository(get())
    }
    single<WalletRepository> {
        if (useMock) FakeWalletRepository() else RealWalletRepository(get())
    }
    single<CardRepository> {
        if (useMock) FakeCardRepository() else RealCardRepository(get())
    }
    // 루키즈페이 QR 결제 (자녀 발급/폴링 · 부모 취소/내역)
    single<QrPayRepository> {
        if (useMock) FakeQrPayRepository() else RealQrPayRepository(get())
    }
    // 매장 단말 — 회원 앱과 분리된 테스트/시연 전용
    single<StoreTerminalRepository> {
        if (useMock) FakeStoreTerminalRepository()
        else RealStoreTerminalRepository(get())
    }
    // 전자금융 거래 보안 (거래 OTP · 계좌비밀번호 · 실명확인)
    single<EfinRepository> {
        if (useMock) FakeEfinRepository() else RealEfinRepository(get())
    }
    single { OtpNotifier(androidContext()) }
    // 거래 승인 공용 코디네이터 — 송금·회수·충전이 하나의 승인 다이얼로그를 공유한다.
    single { TxAuthCoordinator(get(), get()) }

    // FCM 대체 푸시 채널 (WebSocket + 폴링 백업)
    single { ChildCommandSocket(get(), get(), BuildConfig.BASE_URL) }
    single<CommandRepository> {
        if (useMock) FakeCommandRepository() else RealCommandRepository(get(), get())
    }
    single<PhotoRepository> { if (useMock) FakePhotoRepository(get(), get()) else RealPhotoRepository(get()) }

    // 루키즈페이 용돈 지갑 (부모 송금/회수 ↔ 자녀 QR 결제)
    single<AllowanceRepository> {
        if (useMock) FakeAllowanceRepository() else RealAllowanceRepository(get())
    }

    viewModel { LoginViewModel(get()) }
    viewModel { RegisterViewModel(get(), get(), get(), get(), get(), get(), androidContext()) }
    viewModel { ForgotViewModel(get()) }

    viewModel { ChildCareViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ReportViewModel(get(), get(), get(), get(), get()) }
    viewModel { AppUsageDetailViewModel(get(), get(), get()) }
    viewModel { LocationViewModel(get(), get(), get(), get(), get()) }
    viewModel { GeofenceViewModel(get(), get(), get(), get()) }
    viewModel { ParentChatViewModel(get(), get(), get(), get()) }
    viewModel { NotificationsViewModel(get(), get(), get(), get()) }
    viewModel { MypageViewModel(get(), get(), get(), get()) }
    viewModel { EditProfileViewModel(get(), get()) }
    viewModel { ActivityViewModel(get()) }
    viewModel { ChildAccountViewModel(get(), get()) }
    viewModel { WithdrawViewModel(get(), get()) }
    viewModel { AppControlViewModel(get(), get(), get()) }
    viewModel { ScreenshotViewModel(get(), get(), get()) }
    viewModel { CameraRequestViewModel(get(), get(), get()) }
    viewModel { ChildProfileViewModel(get(), get()) }
    viewModel { HarmfulViewModel(get(), get(), get(), get()) }
    viewModel { PairingViewModel(get(), get()) }
    viewModel { CommunityListViewModel(get()) }
    viewModel { params -> CommunityDetailViewModel(params.get(), get(), get()) }
    viewModel { params -> CommunityWriteViewModel(get(), params.get()) }
    viewModel { ScreenTimeViewModel(get(), get(), get()) }
    viewModel { NoticeViewModel(get()) }
    viewModel { ContactViewModel(get<InquiryRepository>()) }
    viewModel { PayViewModel(get(), get(), get(), get(), get()) }
    viewModel { WalletViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PaymentPasswordViewModel(get()) }
    viewModel { IdentityVerifyViewModel(get()) }
    viewModel { CardViewModel(get(), get()) }
    // 매장 단말 테스트 화면 (숨은 라우트, 디버그/시연용)
    viewModel { StoreTerminalViewModel(get()) }

    viewModel { ChildHomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ChildNotificationsViewModel(get(), get()) }
    viewModel { ChildChatViewModel(get(), get()) }
    viewModel { ChildServicesViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ChildMypageViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ChildPairingViewModel(get()) }
    viewModel { ChildScreenshotViewModel(get(), get()) }
    viewModel { ChildCameraViewModel(get(), get(), get(), get()) }
    viewModel { ChildPayViewModel(get(), get(), get()) }
    viewModel { ChildProfileEditViewModel(get(), get()) }
    viewModel { (persistDone: Boolean) ->
        ChildOnboardingViewModel(get(), get(), persistDone)
    }
}
