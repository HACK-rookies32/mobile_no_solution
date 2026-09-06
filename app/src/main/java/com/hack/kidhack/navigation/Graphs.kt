package com.hack.kidhack.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.feature.auth.forgot.ForgotScreen
import com.hack.kidhack.feature.auth.login.LoginScreen
import com.hack.kidhack.feature.auth.register.RegisterScreen
import com.hack.kidhack.feature.child.camera.ChildCameraScreen
import com.hack.kidhack.feature.child.ChildPairingScreen
import com.hack.kidhack.feature.child.chat.ChildChatScreen
import com.hack.kidhack.feature.child.home.ChildHomeScreen
import com.hack.kidhack.feature.child.notifications.ChildNotificationsScreen
import com.hack.kidhack.feature.child.mypage.ChildMypageScreen
import com.hack.kidhack.feature.child.mypage.ChildProfileEditScreen
import com.hack.kidhack.feature.child.mypage.ChildTermsScreen
import com.hack.kidhack.feature.child.mypage.ChildNoticeScreen
import com.hack.kidhack.feature.child.onboarding.ChildOnboardingScreen
import com.hack.kidhack.feature.child.pay.ChildPayScreen
import com.hack.kidhack.feature.child.onboarding.ChildStartRoute
import com.hack.kidhack.feature.child.screenshot.ChildScreenshotScreen
import com.hack.kidhack.feature.child.services.ChildServicesScreen
import com.hack.kidhack.feature.parent.ParentAppControlScreen
import com.hack.kidhack.feature.parent.ParentScreenTimeScreen
import com.hack.kidhack.feature.parent.ParentAppUsageDetailScreen
import com.hack.kidhack.feature.parent.ParentCardsScreen
import com.hack.kidhack.feature.parent.ParentChatScreen
import com.hack.kidhack.feature.parent.ParentChildProfileScreen
import com.hack.kidhack.feature.parent.ParentCommunityDetailScreen
import com.hack.kidhack.feature.parent.ParentCommunityHubScreen
import com.hack.kidhack.feature.parent.ParentCommunityWriteScreen
import com.hack.kidhack.feature.parent.ParentContactScreen
import com.hack.kidhack.feature.parent.ParentAccountSettingsScreen
import com.hack.kidhack.feature.parent.ParentChildCareScreen
import com.hack.kidhack.feature.parent.ParentChildrenScreen
import com.hack.kidhack.feature.parent.ParentReportScreen
import com.hack.kidhack.feature.parent.ParentEditProfileScreen
import com.hack.kidhack.feature.parent.ParentGeofenceScreen
import com.hack.kidhack.feature.parent.ParentHarmfulScreen
import com.hack.kidhack.feature.parent.ParentLocationScreen
import com.hack.kidhack.feature.parent.ParentChildAccountScreen
import com.hack.kidhack.feature.parent.ParentWithdrawScreen
import com.hack.kidhack.feature.parent.ParentMypageScreen
import com.hack.kidhack.feature.parent.ParentNoticeScreen
import com.hack.kidhack.feature.parent.ParentNotificationSettingsScreen
import com.hack.kidhack.feature.parent.ParentNotificationsScreen
import com.hack.kidhack.feature.parent.ParentPairingScreen
import com.hack.kidhack.feature.parent.ParentPayScreen
import com.hack.kidhack.feature.parent.ParentWalletScreen
import com.hack.kidhack.feature.efin.PaymentPasswordScreen
import com.hack.kidhack.feature.terminal.StoreTerminalScreen
import com.hack.kidhack.feature.parent.ParentCameraRequestScreen
import com.hack.kidhack.feature.parent.ParentScreenshotScreen
import com.hack.kidhack.feature.parent.ParentTermsScreen

fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    onAuthed: (Session) -> Unit,
) {
    composable(Route.Login.path) {
        LoginScreen(
            onLoggedIn = onAuthed,
            onRegister = { navController.navigate(Route.Register.path) },
            onForgot = { navController.navigate(Route.Forgot.path) },
        )
    }
    composable(Route.Register.path) {
        RegisterScreen(
            onRegistered = onAuthed,
            onLogin = { navController.popBackStack() },
        )
    }
    composable(Route.Forgot.path) {
        ForgotScreen(onLogin = { navController.popBackStack() })
    }
}

fun NavGraphBuilder.parentGraph(navController: NavHostController) {
    navigation(startDestination = Route.ParentChildren.path, route = Route.ParentGraph.path) {
        parentTab(Route.ParentChildren.path, navController) {
            ParentChildrenScreen(
                onOpenChild = { navController.navigate(Route.ParentChildCare.path) },
                onAddChild = { navController.navigate(Route.ParentPairing.path) },
                onOpenNotifications = { navController.navigate(Route.ParentNotifications.path) },
            )
        }
        composable(Route.ParentChildCare.path) {
            ParentShell(navController) {
                ParentChildCareScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReport = { navController.navigate(Route.ParentReport.path) },
                    onOpenUsageDetail = { navController.navigate(Route.ParentAppUsageDetail.path) },
                    onOpenScreenTime = { navController.navigate(Route.ParentScreenTime.path) },
                    onOpenAppControl = { navController.navigate(Route.ParentAppControl.path) },
                    onOpenScreenshot = { navController.navigate(Route.ParentScreenshot.path) },
                    onOpenHarmful = { navController.navigate(Route.ParentHarmful.path) },
                    onOpenGeofence = { navController.navigate(Route.ParentGeofence.path) },
                    onOpenChildProfile = { navController.navigate(Route.ParentChildProfile.path) },
                    onOpenChildAccount = { navController.navigate(Route.ParentChildAccount.path) },
                )
            }
        }
        composable(Route.ParentReport.path) {
            ParentShell(navController) {
                ParentReportScreen(onBack = { navController.popBackStack() })
            }
        }
        parentTab(Route.ParentLocation.path, navController) {
            ParentLocationScreen(onOpenGeofence = { navController.navigate(Route.ParentGeofence.path) })
        }
        parentTab(Route.ParentChat.path, navController) { ParentChatScreen() }
        parentTab(Route.ParentNotifications.path, navController) { 
            ParentNotificationsScreen(onBack = { navController.popBackStack() }) 
        }
        parentTab(Route.ParentMypage.path, navController) {
            ParentMypageScreen(
                onOpenAccountSettings = { navController.navigate(Route.ParentAccountSettings.path) },
                onOpenPairing = { navController.navigate(Route.ParentPairing.path) },
                onOpenContact = { navController.navigate(Route.ParentContact.path) },
                onOpenWallet = { navController.navigate(Route.ParentWallet.path) },
                onOpenCards = { navController.navigate(Route.ParentCards.path) },
                onOpenTerms = { navController.navigate(Route.ParentTerms.path) },
            )
        }
        composable(Route.ParentAccountSettings.path) {
            ParentShell(navController) {
                ParentAccountSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEditProfile = { navController.navigate(Route.ParentEditProfile.path) },
                    onOpenNotificationSettings = { navController.navigate(Route.ParentNotificationSettings.path) },
                    onWithdraw = { navController.navigate(Route.ParentWithdraw.path) },
                    onOpenPaymentPassword = { navController.navigate(Route.ParentPaymentPassword.path) },
                )
            }
        }
        composable(Route.ParentPaymentPassword.path) {
            ParentShell(navController) { PaymentPasswordScreen(onBack = { navController.popBackStack() }) }
        }
        composable(Route.ParentNotificationSettings.path) {
            ParentShell(navController) {
                ParentNotificationSettingsScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ParentEditProfile.path) { ParentShell(navController) { ParentEditProfileScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentChildAccount.path) { ParentShell(navController) { ParentChildAccountScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentWithdraw.path) { ParentShell(navController) { ParentWithdrawScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentAppControl.path) {
            ParentShell(navController) {
                ParentAppControlScreen(
                    onBack = { navController.popBackStack() },
                    onOpenScreenTime = { navController.navigate(Route.ParentScreenTime.path) },
                )
            }
        }
        composable(Route.ParentScreenTime.path) {
            ParentShell(navController) {
                ParentScreenTimeScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ParentScreenshot.path) { ParentShell(navController) { ParentScreenshotScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentCameraRequest.path) { ParentShell(navController) { ParentCameraRequestScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentChildProfile.path) { ParentShell(navController) { ParentChildProfileScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentHarmful.path) { ParentShell(navController) { ParentHarmfulScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentPairing.path) {
            ParentShell(navController) {
                ParentPairingScreen(
                    onBack = { navController.popBackStack() },
                    onDone = {
                        navController.navigate(Route.ParentChildren.path) {
                            popUpTo(Route.ParentGraph.path)
                        }
                    },
                )
            }
        }
        parentTab(Route.ParentCommunity.path, navController) {
            ParentCommunityHubScreen(
                onOpenPost = { navController.navigate(Route.ParentCommunityDetail.create(it)) },
                onWrite = { navController.navigate(Route.ParentCommunityWrite.path) },
            )
        }
        composable(Route.ParentCommunityDetail.path) { entry ->
            val postId = entry.arguments?.getString("postId").orEmpty()
            ParentShell(navController) {
                ParentCommunityDetailScreen(
                    postId = postId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Route.ParentCommunityEdit.create(it)) },
                )
            }
        }
        composable(Route.ParentCommunityWrite.path) {
            ParentShell(navController) {
                ParentCommunityWriteScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
        composable(Route.ParentCommunityEdit.path) { entry ->
            val postId = entry.arguments?.getString("postId").orEmpty()
            ParentShell(navController) {
                ParentCommunityWriteScreen(
                    postId = postId,
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
        composable(Route.ParentNotice.path) { ParentShell(navController) { ParentNoticeScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentContact.path) { ParentShell(navController) { ParentContactScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentTerms.path) { ParentShell(navController) { ParentTermsScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentPay.path) {
            ParentShell(navController) {
                ParentPayScreen(
                    onBack = { navController.popBackStack() },
                    onRegisterCard = { navController.navigate(Route.ParentCards.path) },
                    onOpenTerminal = { navController.navigate(Route.StoreTerminal.path) },
                )
            }
        }
        composable(Route.ParentWallet.path) {
            ParentShell(navController) {
                ParentWalletScreen(
                    onBack = { navController.popBackStack() },
                    onRegisterCard = { navController.navigate(Route.ParentCards.path) },
                    onOpenTerminal = { navController.navigate(Route.StoreTerminal.path) },
                )
            }
        }
        composable(Route.ParentCards.path) {
            ParentShell(navController) {
                ParentCardsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenWallet = { navController.navigate(Route.ParentWallet.path) },
                )
            }
        }
        // 매장 단말 테스트 화면 — 숨은 라우트(회원 앱 정상 내비게이션에 없음, 디버그/시연 전용).
        composable(Route.StoreTerminal.path) {
            StoreTerminalScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.ParentGeofence.path) { ParentShell(navController) { ParentGeofenceScreen(onBack = { navController.popBackStack() }) } }
        composable(Route.ParentAppUsageDetail.path) { ParentShell(navController) { ParentAppUsageDetailScreen(onBack = { navController.popBackStack() }) } }
    }
}

fun NavGraphBuilder.childGraph(navController: NavHostController) {
    navigation(startDestination = Route.ChildStart.path, route = Route.ChildGraph.path) {
        composable(Route.ChildStart.path) { ChildStartRoute(navController) }
        composable(Route.ChildOnboarding.path) {
            ChildOnboardingScreen(
                onFinished = {
                    navController.navigate(Route.ChildHome.path) {
                        popUpTo(Route.ChildOnboarding.path) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        childTab(Route.ChildHome.path, navController) {
            ChildHomeScreen(
                onNotifications = { navController.navigate(Route.ChildNotifications.path) },
            )
        }
        childTab(Route.ChildChat.path, navController) { ChildChatScreen() }
        childTab(Route.ChildServices.path, navController) { ChildServicesScreen() }
        composable(
            route = Route.ChildCameraRequest.path,
            arguments = listOf(
                navArgument(ARG_REQUEST_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(ARG_OPEN_GALLERY) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            ChildShell(navController) {
                ChildCameraScreen(
                    requestId = entry.arguments?.getString(ARG_REQUEST_ID),
                    openGallery = entry.arguments?.getBoolean(ARG_OPEN_GALLERY) == true,
                )
            }
        }
        childTab(Route.ChildMypage.path, navController) {
            ChildMypageScreen(
                onLinkParent = { navController.navigate(Route.ChildPairing.path) },
                onEditProfile = { navController.navigate(Route.ChildProfileEdit.path) },
                onScreenshots = { navController.navigate(Route.ChildScreenshot.path) },
                onTerms = { navController.navigate(Route.ChildTerms.path) },
                onNotice = { navController.navigate(Route.ChildNotice.path) },
            )
        }
        composable(Route.ChildScreenshot.path) {
            ChildShell(navController) {
                ChildScreenshotScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildNotifications.path) {
            ChildShell(navController) {
                ChildNotificationsScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildPairing.path) {
            ChildShell(navController) {
                ChildPairingScreen(onBack = { navController.popBackStack() })
            }
        }
        // 페이는 이제 하단 탭이다. 탭이라 별도 뒤로가기 대신 홈 탭으로 돌린다(추후 페이 화면 재설계 시 헤더 정리).
        childTab(Route.ChildPay.path, navController) {
            ChildPayScreen(onBack = { navController.navigateChildTab(Route.ChildHome.path) })
        }
        composable(Route.ChildProfileEdit.path) {
            ChildShell(navController) {
                ChildProfileEditScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildTerms.path) {
            ChildShell(navController) {
                ChildTermsScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(Route.ChildNotice.path) {
            ChildShell(navController) {
                ChildNoticeScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

internal const val ARG_REQUEST_ID = "requestId"
internal const val ARG_OPEN_GALLERY = "gallery"

/**
 * 마이페이지 「카메라 갤러리」 → 카메라 탭의 갤러리 화면.
 *
 * 일반 navigate 로 쌓으면 마이페이지 탭 스택이 `[마이페이지, 갤러리]` 가 되고,
 * 하단 「마이」를 눌렀을 때 그 스택이 통째로 복원돼 갤러리로 되돌아간다.
 * 그래서 탭 전환과 같은 방식으로 이동한다. 다만 `gallery=true` 인자를 살려야 하므로
 * 이전 카메라 상태는 복원하지 않는다.
 */
private fun NavHostController.navigateChildGallery() {
    navigate(Route.ChildCameraRequest.gallery()) {
        popUpTo(Route.ChildGraph.path) { saveState = true }
        launchSingleTop = true
        restoreState = false
    }
}

private fun NavHostController.navigateChildTab(route: String) {
    navigate(route) {
        popUpTo(Route.ChildGraph.path) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavGraphBuilder.parentTab(
    route: String,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    composable(route) { ParentShell(navController, content) }
}

private fun NavGraphBuilder.childTab(
    route: String,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    composable(route) { ChildShell(navController, content) }
}

@Composable
private fun ParentShell(navController: NavHostController, content: @Composable () -> Unit) {
    TabShell(
        navController = navController,
        tabs = ParentTabs,
        graphRoute = Route.ParentGraph.path,
        content = content,
    )
}

@Composable
private fun ChildShell(navController: NavHostController, content: @Composable () -> Unit) {
    TabShell(
        navController = navController,
        tabs = ChildTabs,
        graphRoute = Route.ChildGraph.path,
        content = content,
    )
}

/**
 * 탭 껍데기 안에서 「지금은 하단 탭 바를 접어 달라」고 알리는 스위치.
 *
 * <p>채팅방은 탭 화면의 하위 상태(선택된 방)라 라우트가 따로 없다. 라우트로 탭 바를
 * 여닫는 [TabShell] 이 이 방을 알 길이 없어, 콘텐츠가 이 값을 켜면 바를 접는다.
 * 화면을 떠날 때 반드시 되돌려야 하므로 켠 쪽에서 DisposableEffect 로 끈다.
 */
object ImmersiveContent {
    var active by mutableStateOf(false)
}

@Composable
private fun TabShell(
    navController: NavHostController,
    tabs: List<TabItem>,
    graphRoute: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalKidhackColors.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // 카메라 탭은 `child/camera?requestId={requestId}` 패턴으로 등록돼 있어 쿼리부를 떼고 비교한다.
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore('?')
    // 채팅방처럼 몰입이 필요한 화면에 들어가면 탭 바를 접어 풀스크린으로 만든다.
    // 그래야 입력창이 탭 바 위에 떠 있지 않고 화면 맨 아래에 앉는다.
    val showBar = tabs.any { it.route.path == currentRoute } && !ImmersiveContent.active
    Scaffold(
        // 상단 인셋은 BrandGradientHeader가 statusBarsPadding()으로 직접 처리한다.
        // 여기서 함께 넘기면 상태바 높이가 두 번 쌓여 헤더 제목이 아래로 밀린다.
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            if (showBar) {
              Column {
                // 위로 은은한 그림자 + 얇은 실선. 흰 콘텐츠와 흰 바를 확실히 가르되,
                // Material 기본의 흐릿한 톤 elevation(tonalElevation=0 으로 끔) 대신 이 조합을 쓴다.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.045f)))),
                )
                HorizontalDivider(thickness = 1.dp, color = colors.border)
                NavigationBar(containerColor = colors.card, contentColor = colors.muted, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route.path
                        // 카메라 탭만 예외: 마이페이지 「카메라 갤러리」로 들어간 적이 있으면
                        // 저장된 상태(gallery=true)가 복원돼 탭을 눌러도 갤러리가 뜬다.
                        // 탭은 언제나 카메라로 시작해야 하므로 인자를 명시하고 복원을 끈다.
                        val isCameraTab = tab.route.path == Route.ChildCamera.path
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val target = if (isCameraTab) {
                                    Route.ChildCameraRequest.camera()
                                } else {
                                    tab.route.path
                                }
                                navController.navigate(target) {
                                    popUpTo(graphRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = !isCameraTab
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(tab.icon),
                                    // 바로 아래 이름표가 같은 말을 한다.
                                    contentDescription = null,
                                    modifier = Modifier.size(21.dp),
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 10.sp,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.brand,
                                selectedTextColor = colors.brand,
                                unselectedIconColor = colors.muted,
                                unselectedTextColor = colors.muted,
                                indicatorColor = colors.brandLight,
                            ),
                        )
                    }
                }
              }
            }
        },
    ) { padding ->
        // padding 만 주고 끝내면, 그 안의 화면이 「아직 아무 인셋도 안 먹었다」고 알고 산다.
        // 그래서 채팅 입력창이 imePadding 을 걸면 이미 비워 둔 탭 바 높이 위에 키보드 높이를
        // 통째로 또 얹어, 입력창이 키보드보다 탭 바 하나만큼 더 떠올랐다.
        //
        // consumeWindowInsets 로 「여기까지는 내가 이미 비웠다」고 알려 주면, 아래 화면의
        // imePadding 은 모자란 만큼만 더한다 — 탭 바가 보이든 접혔든 식이 같아진다.
        Box(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) { content() }
    }
}
