package com.hack.kidhack.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hack.kidhack.BuildConfig
import com.hack.kidhack.background.alert.ServerAlertBus
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.repository.fake.FakeData
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import org.koin.core.context.GlobalContext
import com.hack.kidhack.feature.efin.TxAuthCoordinator
import com.hack.kidhack.feature.efin.TxAuthHost

@Composable
fun KidhackNavHost() {
    val koin = GlobalContext.get()
    val sessionRepository = koin.get<SessionRepository>()
    val sessionEvents = koin.get<SessionEvents>()
    val commandBus = koin.get<ChildCommandBus>()
    val serverAlertBus = koin.get<ServerAlertBus>()
    val navController = rememberNavController()
    val colors = LocalKidhackColors.current

    /**
     * 로그인·가입 직후 이동.
     *
     * 자녀 계정은 **가입 시점에 부모가 정해지지 않는다.** 부모가 발급한 6자리 코드를
     * `POST /pairing/connect` 로 입력해야 서버가 `parent` 를 채운다.
     *
     * 그 입력은 **가입 흐름의 「부모님과 연결」 단계에서만** 받는다. 예전에는 여기서도
     * 아직 연결되지 않은 자녀를 페어링 화면으로 보냈는데, 가입 단계에서 코드를 넣어
     * 연결한 자녀에게까지 같은 화면이 한 번 더 떠서 연결이 안 된 것처럼 보였다.
     *
     * 코드를 건너뛴 자녀는 마이 → 「부모님 연결」 에서 언제든 넣을 수 있다.
     */
    fun goAuthed(session: Session) {
        val dest = if (session.user.role == Role.PARENT) Route.ParentGraph.path else Route.ChildGraph.path
        navController.navigate(dest) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // 부모 요청 알림 탭 → 해당 화면으로 이동. 화면 캡처는 알림이 동의 액티비티로 바로 보내므로
    // 여기서는 카메라 촬영 요청만 처리한다.
    LaunchedEffect(Unit) {
        commandBus.pending.collect { command ->
            when (command.type) {
                RemoteCommandType.CAMERA_SHOT -> {
                    // 부모 촬영 요청 카메라는 현재 탭 위에 쌓지 않고 그래프 최상위로 연다.
                    // 그러지 않으면 현재 탭(예: 서비스) 백스택에 카메라가 얹혀 저장되고,
                    // 나중에 그 탭을 다시 누르면 restoreState 로 카메라가 되살아난다.
                    navController.navigate(Route.ChildCameraRequest.create(command.requestId)) {
                        popUpTo(Route.ChildGraph.path) { saveState = true }
                        launchSingleTop = true
                        restoreState = false
                    }
                    commandBus.consume()
                }
                else -> commandBus.consume()
            }
        }
    }

    // 서버 알림 팝업 탭 → 알림이 보이는 화면. 탭 위에 쌓지 않고 그래프 최상위로 연다.
    // 자녀에게는 알림 탭이 없어 「최근 알림」이 있는 홈으로 보낸다.
    LaunchedEffect(Unit) {
        serverAlertBus.pending.collect {
            when (sessionRepository.current()?.user?.role) {
                Role.PARENT -> navController.navigate(Route.ParentNotifications.path) {
                    popUpTo(Route.ParentGraph.path) { saveState = true }
                    launchSingleTop = true
                    restoreState = false
                }
                Role.CHILD -> navController.navigate(Route.ChildHome.path) {
                    popUpTo(Route.ChildGraph.path) { saveState = true }
                    launchSingleTop = true
                    restoreState = false
                }
                null -> Unit
            }
            serverAlertBus.consume()
        }
    }

    LaunchedEffect(Unit) {
        sessionEvents.logout.collect {
            navController.navigate(Route.Login.path) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Splash.path,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        composable(Route.Splash.path) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.brand),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
            }
            LaunchedEffect(Unit) {
                var session = sessionRepository.current()
                // 목업: 예전 가입 세션(id=user-…)이나 parentId 누락 세션을 더미 계정으로 교정
                if (BuildConfig.USE_MOCK && session != null) {
                    session = normalizeMockSession(sessionRepository, session)
                }
                when (session?.user?.role) {
                    Role.PARENT -> navController.navigate(Route.ParentGraph.path) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                    Role.CHILD -> navController.navigate(Route.ChildGraph.path) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                    null -> navController.navigate(Route.Login.path) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                }
            }
        }
        authGraph(navController, ::goAuthed)
        parentGraph(navController)
        childGraph(navController)
    }

    // 거래 승인 다이얼로그를 앱 루트에 딱 하나만 띄운다 — 송금·회수·충전이 공유(화면마다 붙이지 않음).
    // AlertDialog 는 자체 윈도우라 트리 위치와 무관하게 화면 중앙에 뜬다.
    TxAuthHost(koin.get<TxAuthCoordinator>())
}

/**
 * 목업 세션 교정.
 * - 예전 가입으로 남은 `user-…` id
 * - parentId 없이 저장된 자녀 세션
 * → 더미 minjun(101) / 숫자 id + parentId 로 맞춘다.
 */
private suspend fun normalizeMockSession(
    sessionRepository: SessionRepository,
    session: Session,
): Session {
    val user = session.user
    val idOk = user.id.toLongOrNull() != null
    val parentOk = user.role != Role.CHILD || user.parentId != null
    if (idOk && parentOk) return session

    val fixed = when {
        user.loginId.equals("minjun", ignoreCase = true) ||
            (user.role == Role.CHILD && !idOk) -> FakeData.sessionFor(FakeData.childUser)
        user.role == Role.CHILD -> FakeData.sessionFor(
            user.copy(
                id = user.id.toLongOrNull()?.toString() ?: "101",
                parentId = user.parentId ?: 1L,
            ),
        )
        else -> FakeData.sessionFor(
            user.copy(id = user.id.toLongOrNull()?.toString() ?: "1"),
        )
    }
    sessionRepository.save(fixed)
    return fixed
}
