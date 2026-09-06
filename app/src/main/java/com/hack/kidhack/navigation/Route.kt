package com.hack.kidhack.navigation

import androidx.annotation.DrawableRes
import com.hack.kidhack.R

sealed class Route(val path: String) {
    data object Splash : Route("splash")
    data object Login : Route("login")
    data object Register : Route("register")
    data object Forgot : Route("forgot")

    data object ParentGraph : Route("parent")
    data object ParentChildren : Route("parent/children")
    data object ParentChildCare : Route("parent/childcare")
    data object ParentReport : Route("parent/report")
    data object ParentLocation : Route("parent/location")
    data object ParentChat : Route("parent/chat")
    data object ParentNotifications : Route("parent/notifications")
    data object ParentMypage : Route("parent/mypage")
    data object ParentAccountSettings : Route("parent/account")
    data object ParentNotificationSettings : Route("parent/account/notifications")
    data object ParentEditProfile : Route("parent/editprofile")
    data object ParentChildAccount : Route("parent/childaccount")
    data object ParentWithdraw : Route("parent/withdraw")
    data object ParentPaymentPassword : Route("parent/efin/payment-password")
    data object ParentIdentityVerify : Route("parent/efin/identity")
    data object ParentAppControl : Route("parent/appcontrol")
    data object ParentScreenshot : Route("parent/screenshot")
    data object ParentCameraRequest : Route("parent/camera")
    data object ParentChildProfile : Route("parent/profile")
    data object ParentHarmful : Route("parent/harmful")
    data object ParentPairing : Route("parent/pairing")
    data object ParentCommunity : Route("parent/community")
    data object ParentCommunityDetail : Route("parent/community/detail/{postId}") {
        fun create(postId: String) = "parent/community/detail/$postId"
    }
    data object ParentCommunityWrite : Route("parent/community/write")

    /** 글쓰기 화면을 그대로 쓰되 기존 글을 채워 넣는다(PATCH). */
    data object ParentCommunityEdit : Route("parent/community/edit/{postId}") {
        fun create(postId: String) = "parent/community/edit/$postId"
    }
    data object ParentScreenTime : Route("parent/screentime")
    data object ParentNotice : Route("parent/notice")
    data object ParentContact : Route("parent/contact")
    /**
     * 옛 「루키즈 페이」 화면. [ParentWallet] 로 대체돼 정상 내비게이션에서는 더 이상 닿지 않지만,
     * 새 화면에서 빠진 것이 나올 때를 대비해 남겨 둔다.
     */
    data object ParentPay : Route("parent/pay")
    data object ParentWallet : Route("parent/wallet")
    data object ParentCards : Route("parent/cards")

    /**
     * 매장 단말 테스트 화면. **회원 앱 정상 내비게이션에 노출하지 않는 숨은 라우트**로,
     * 결제(태그 16) 시연·검증 전용이다. 자녀 앱의 옛 자가승인과 명확히 구분된다.
     *
     * [VULN-MOBILE-PAY-1] 이 숨은 라우트는 일반 유저도 도달 가능하며(StoreTerminalScreen),
     * X-Store-Terminal-Token 헤더만으로 임의 qrToken·임의 금액 결제 승인 UI 를 제공한다.
     * 서버 BFLA(백엔드 VULN-WALLET-11: 일반 유저 단말 토큰 발급, VULN-WALLET-15: 토큰 없이 승인)와 결합해
     * 임의 자녀 지갑 차감으로 이어진다. 의도된 취약점(OWASP Mobile M3/M8). 명세: mobile/docs/vuln-spec-mobile.md
     */
    data object StoreTerminal : Route("dev/terminal")
    data object ParentGeofence : Route("parent/geofence")
    data object ParentAppUsageDetail : Route("parent/usage-detail")
    data object ParentTerms : Route("parent/terms")

    data object ChildGraph : Route("child")
    data object ChildStart : Route("child/start")
    data object ChildOnboarding : Route("child/onboarding")
    data object ChildHome : Route("child/home")
    data object ChildChat : Route("child/chat")
    data object ChildServices : Route("child/services")
    data object ChildCamera : Route("child/camera")
    data object ChildCameraRequest : Route("child/camera?requestId={requestId}&gallery={gallery}") {
        fun create(requestId: String) = "child/camera?requestId=$requestId&gallery=false"

        /** 마이페이지 「카메라 갤러리」처럼 갤러리 탭을 바로 열 때. */
        fun gallery() = "child/camera?gallery=true"

        /** 하단 카메라 탭. 갤러리로 열린 적이 있어도 항상 카메라로 시작한다. */
        fun camera() = "child/camera?gallery=false"
    }
    data object ChildMypage : Route("child/mypage")
    data object ChildScreenshot : Route("child/screenshot")
    data object ChildPay : Route("child/pay")
    data object ChildProfileEdit : Route("child/profile/edit")
    data object ChildPairing : Route("child/pairing")
    data object ChildTerms : Route("child/terms")
    data object ChildNotice : Route("child/notice")
    data object ChildNotifications : Route("child/notifications")
}

data class TabItem(
    val route: Route,
    val label: String,
    /** 탭 아이콘. 이모지는 기기마다 모양·크기가 달라 탭 줄이 흔들린다. */
    @DrawableRes val icon: Int,
)

/**
 * 부모 하단 탭.
 *
 * <p>「알림」 자리를 커뮤니티가 대신한다. 알림은 자녀관리 헤더의 종으로 옮겼다 — 탭 다섯
 * 자리는 매일 오가는 곳에 주는 것이 맞고, 알림은 일이 생겼을 때만 들여다본다.
 */
val ParentTabs = listOf(
    TabItem(Route.ParentChildren, "자녀관리", R.drawable.ic_child),
    TabItem(Route.ParentLocation, "위치", R.drawable.ic_place),
    TabItem(Route.ParentChat, "채팅", R.drawable.ic_chat),
    TabItem(Route.ParentCommunity, "커뮤니티", R.drawable.ic_people),
    TabItem(Route.ParentMypage, "마이", R.drawable.ic_person),
)

// 서비스 탭은 홈과 중복이라 접고(위치·보호상태는 홈으로 흡수), 카메라는 뺀 기능이라 내렸다.
// 대신 아이가 실제로 쓰는 「페이」를 탭으로 올린다.
val ChildTabs = listOf(
    TabItem(Route.ChildHome, "홈", R.drawable.ic_home),
    TabItem(Route.ChildChat, "채팅", R.drawable.ic_chat),
    TabItem(Route.ChildPay, "페이", R.drawable.ic_card),
    TabItem(Route.ChildMypage, "마이", R.drawable.ic_person),
)
