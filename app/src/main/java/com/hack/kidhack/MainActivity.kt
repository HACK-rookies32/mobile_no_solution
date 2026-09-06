package com.hack.kidhack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hack.kidhack.background.alert.ServerAlertBus
import com.hack.kidhack.background.alert.ServerAlertNotifier
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.data.repository.toRemoteCommandType
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.permission.PermissionRequester
import com.hack.kidhack.navigation.KidhackNavHost
import org.koin.android.ext.android.inject

val LocalPermissionRequester = staticCompositionLocalOf<PermissionRequester> {
    error("PermissionRequester not provided")
}

class MainActivity : ComponentActivity() {

    private val commandBus: ChildCommandBus by inject()
    private val serverAlertBus: ServerAlertBus by inject()
    /** Registered before STARTED so system permission dialogs can be shown reliably. */
    val permissionRequester = PermissionRequester(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        routeRemoteCommand(intent)
        routeParentAlert(intent)
        setContent {
            CompositionLocalProvider(LocalPermissionRequester provides permissionRequester) {
                KidhackTheme {
                    KidhackNavHost()
                }
            }
        }
    }

    /** launchMode=singleTask 라 앱이 떠 있는 상태의 알림 탭은 여기로 들어온다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeRemoteCommand(intent)
        routeParentAlert(intent)
    }

    /** 부모 기기의 안전 알림 푸시 탭 → 알림 탭. 실제 이동은 `KidhackNavHost` 가 한다. */
    private fun routeParentAlert(intent: Intent?) {
        if (intent?.action != ServerAlertNotifier.ACTION_PARENT_ALERT) return
        serverAlertBus.post(intent.getStringExtra(ServerAlertNotifier.EXTRA_ALERT_ID).orEmpty())
        // 회전·복귀 때 같은 인텐트로 다시 이동하지 않도록 액션을 지운다.
        intent.action = null
    }

    /**
     * 부모 요청 알림에서 넘어온 인텐트를 [ChildCommandBus] 로 흘려 보낸다.
     * 실제 화면 이동은 `KidhackNavHost` 가 담당한다.
     */
    private fun routeRemoteCommand(intent: Intent?) {
        if (intent?.action != ChildCommandNotifier.ACTION_REMOTE_COMMAND) return
        val requestId = intent.getStringExtra(ChildCommandNotifier.EXTRA_REQUEST_ID) ?: return
        val type = intent.getStringExtra(ChildCommandNotifier.EXTRA_TYPE).toRemoteCommandType()
        if (type == RemoteCommandType.UNKNOWN) return
        commandBus.post(
            RemoteCommand(
                requestId = requestId,
                type = type,
                childId = intent.getStringExtra(ChildCommandNotifier.EXTRA_CHILD_ID).orEmpty(),
                requestedBy = intent.getStringExtra(ChildCommandNotifier.EXTRA_REQUESTED_BY),
            ),
        )
        // 같은 인텐트로 두 번 라우팅되지 않도록 액션을 지운다(화면 회전·복귀 대비).
        intent.action = null
    }
}

@Composable
fun permissionRequester(): PermissionRequester = LocalPermissionRequester.current
