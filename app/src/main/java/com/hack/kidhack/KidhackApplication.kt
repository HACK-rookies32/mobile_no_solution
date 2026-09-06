package com.hack.kidhack

import android.app.Application
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.background.push.PushTokenRegistrar
import com.hack.kidhack.background.ParentPlatformController
import com.hack.kidhack.data.local.CapturedShotStore
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.local.ChildPhotoStore
import com.hack.kidhack.data.local.ProfileScopeStore
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.hack.kidhack.di.appModule
import com.hack.kidhack.di.backgroundModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class KidhackApplication : Application() {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@KidhackApplication)
            modules(appModule, backgroundModule)
        }
        // ChildPlatformController 를 꺼내는 순간 SessionRepository(DataStore 디스크 읽기)와
        // OkHttp/Retrofit 이 함께 생성된다. 메인 스레드에서 하면 첫 프레임이 그만큼 늦어져
        // 실행 직후 검은 화면이 길어지므로 백그라운드로 넘긴다.
        startupScope.launch {
            val koin = GlobalContext.get()
            koin.get<ChildPlatformController>().bind()
            // 부모 세션은 15분 주기로 서버 알림을 긁어와 보관함에 넣고 푸시로 띄운다.
            koin.get<ParentPlatformController>().bind()
            // FCM 토큰을 로그인한 계정에 묶는다. 세션이 생기면 등록, 사라지면 해제.
            val pushRegistrar = koin.get<PushTokenRegistrar>()
            // 채팅과 사진은 계정이 바뀔 때 비운다.
            // 알림 보관함(ChildAlertStore/ServerAlertStore)은 계정별 파일로 나뉘어 있어
            // 여기서 지우지 않는다. 지우면 부모와 자녀를 오갈 때마다 최근 알림이 비워진다.
            val chatStore = koin.get<ChatMessageStore>()
            val photoStore = koin.get<ChildPhotoStore>()
            val shotStore = koin.get<CapturedShotStore>()
            val profileScope = koin.get<ProfileScopeStore>()
            var pushBoundTo: String? = null
            koin.get<SessionRepository>().session.collect { session ->
                val userId = session?.user?.id
                if (userId == null) {
                    if (pushBoundTo != null) { pushRegistrar.onSignedOut(); pushBoundTo = null }
                    return@collect
                }
                if (pushBoundTo != userId) { pushRegistrar.onSignedIn(); pushBoundTo = userId }
                if (profileScope.switchTo(userId)) {
                    chatStore.clear()
                    photoStore.clear()
                    shotStore.clear()
                }
            }
        }
    }
}
