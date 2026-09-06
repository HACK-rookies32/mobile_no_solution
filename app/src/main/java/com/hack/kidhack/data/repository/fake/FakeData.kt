package com.hack.kidhack.data.repository.fake

import com.hack.kidhack.domain.model.Activity
import com.hack.kidhack.domain.model.ActivityType
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.LocationEvent
import com.hack.kidhack.domain.model.LocationEventType
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.PlaceType
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.User

object FakeData {
    const val PAIRING_CODE = "382194"

    val parent = User(
        id = "1",
        loginId = "parent01",
        name = "김지영",
        role = Role.PARENT,
        nickname = "지영맘",
        phone = "010-1234-5678",
        email = "kim@email.com",
    )

    val childUser = User(
        id = "101",
        loginId = "minjun",
        name = "김민준",
        role = Role.CHILD,
        phone = "010-8765-4321",
        email = "minjun@email.com",
        parentId = 1L,
    )

    val minjun = ChildProfile(
        id = "101",
        name = "김민준",
        alias = "민준",
        birthDate = "2014년 3월 15일",
        realtimeEnabled = true,
    )

    val jia = ChildProfile(
        id = "102",
        name = "이지아",
        alias = "지아",
        birthDate = "2017년 6월 2일",
        realtimeEnabled = false,
    )

    val children = listOf(minjun, jia)
    // 자녀 없을 때 테스트 (empty)
    //val children = emptyList<ChildProfile>()

    /** 서울시청 주변 5분 세그먼트. 최신순(첫 항목 = 실시간 ON일 때의 현재 위치). */
    val locationSegments = listOf(
        LocationPoint(
            lat = "37.5665", lng = "126.9780", recordedAt = "2026-08-25T17:05:00", label = "📍 서울시청",
            segmentStart = "2026-08-25T17:00:00", segmentEnd = "2026-08-25T17:05:00",
            updatedAt = "2026-08-25T17:05:00",
        ),
        LocationPoint(
            lat = "37.5642", lng = "126.9770", recordedAt = "2026-08-25T16:40:00", label = "📍 학원",
            segmentStart = "2026-08-25T16:35:00", segmentEnd = "2026-08-25T16:40:00",
            updatedAt = "2026-08-25T16:40:00",
        ),
        LocationPoint(
            lat = "37.5700", lng = "126.9762", recordedAt = "2026-08-25T15:20:00", label = "📍 학교 앞",
            segmentStart = "2026-08-25T15:15:00", segmentEnd = "2026-08-25T15:20:00",
            updatedAt = "2026-08-25T15:20:00",
        ),
        LocationPoint(
            lat = "37.5729", lng = "126.9794", recordedAt = "2026-08-25T14:10:00", label = "📍 광화문",
            segmentStart = "2026-08-25T14:05:00", segmentEnd = "2026-08-25T14:10:00",
            updatedAt = "2026-08-25T14:10:00",
        ),
    )

    // 진입(도착)과 이탈(이동) 알림을 모두 받아야 해서 전부 GEOFENCE다.
    val places = listOf(
        Geofence("1", "학교", "37.5700", "126.9762", 200, true, PlaceType.GEOFENCE),
        Geofence("2", "OO학원", "37.5642", "126.9770", 150, true, PlaceType.GEOFENCE),
    )

    val locationEvents = listOf(
        LocationEvent("4", "2", LocationEventType.ENTER, "2026-08-25T16:36:12"),
        LocationEvent("3", "1", LocationEventType.EXIT, "2026-08-25T15:22:04"),
        LocationEvent("2", "1", LocationEventType.ENTER, "2026-08-25T08:41:37"),
        // DESTINATION으로 등록하던 시절의 잔여 이벤트. 도착 문구로 함께 표시된다.
        LocationEvent("1", "2", LocationEventType.ARRIVED, "2026-08-24T16:31:50"),
    )

    val usages: List<AppUsage> = listOf(
        AppUsage("YouTube", 4800, "동영상"),
        AppUsage("카카오톡", 2100, "메신저"),
        AppUsage("Chrome", 1320, "브라우저"),
        AppUsage("인스타그램", 2700, "소셜"),
        AppUsage("네이버", 900, "브라우저"),
        AppUsage("게임", 3600, "게임"),
    )

    val usagesByHour: List<AppUsageStat> = listOf(
        AppUsageStat("07", 600), AppUsageStat("09", 1200), AppUsageStat("12", 1800),
        AppUsageStat("15", 2400), AppUsageStat("18", 2100), AppUsageStat("20", 3000),
        AppUsageStat("22", 1500), AppUsageStat("23", 900),
    )

    val usagesByDay: List<AppUsageStat> = listOf(
        AppUsageStat("2026-08-21", 5400), AppUsageStat("2026-08-22", 3600),
        AppUsageStat("2026-08-23", 7200), AppUsageStat("2026-08-24", 2700),
        AppUsageStat("2026-08-25", 8200), AppUsageStat("2026-08-26", 6300),
        AppUsageStat("2026-08-27", 4100),
    )

    private val jiaUsages = listOf(
        AppUsage("Roblox", 46 * 60, "게임"),
        AppUsage("YouTube Kids", 25 * 60, "동영상"),
        AppUsage("클래스팅", 12 * 60, "학습"),
    )

    /**
     * 자녀마다 다른 사용 현황.
     *
     * 예전에는 어느 자녀를 골라도 같은 목록이 나와서, 대시보드의 자녀 선택이
     * 동작하는지 눈으로 확인할 수 없었다.
     */
    fun usagesFor(childId: String): List<AppUsage> =
        if (childId == jia.id) jiaUsages else usages

    fun locationFor(childId: String): LocationPoint =
        if (childId == jia.id) {
            LocationPoint("37.5410", "127.0270", "2024.08.20 15:10", "📍 집")
        } else {
            LocationPoint("37.5665", "126.9780", "2024.08.20 14:30", "📍 서울시청")
        }

    /** 목업 자녀 계정([childUser], 김민준)에 대응하는 프로필 id. */
    val loggedInChildProfileId: String get() = minjun.id

    val chatMessages = listOf(
        ChatMessage("m1", "room-minjun", Role.PARENT, "민준아, 지금 어디야?", "14:30"),
        ChatMessage("m2", "room-minjun", Role.CHILD, "엄마 저 학원이요! 6시에 끝나요", "14:31"),
        ChatMessage("m3", "room-minjun", Role.PARENT, "알겠어 🙂 공부 열심히!", "14:31"),
        ChatMessage("m4", "room-minjun", Role.CHILD, "네~! 사랑해요 ❤️", "14:32"),
    )

    val posts = listOf(
        Post(
            id = "1",
            authorId = "101",
            title = "유튜브 시청 시간 제한, 어떻게 하시나요?",
            author = "행복한엄마",
            content = "하루 한 시간으로 정해 두었는데 다들 어떻게 하시는지 궁금해요.",
            preview = "하루 한 시간으로 정해 두었는데 다들 어떻게 하시는지 궁금해요.",
            likeCount = 24,
            createdAtLabel = "2026-08-28T14:02:00",
            comments = listOf(
                Comment("1", "102", "도전맘", "저는 하루 1시간으로 정해두고 있어요.", "2026-08-28T15:10:00"),
                Comment("2", "103", "민준아빠", "약속을 정하는 게 가장 중요한 것 같아요.", "2026-08-28T15:40:00"),
            ),
        ),
        Post(
            id = "2",
            authorId = "104",
            title = "스마트폰 처음 사준 시기가 언제예요?",
            author = "두아이아빠",
            preview = "초등 5학년인데 주변에서 다 갖고 다닌다고 사 달라고 조르네요.",
            likeCount = 37,
            createdAtLabel = "2026-08-28T09:00:00",
        ),
        Post(
            id = "3",
            authorId = "105",
            title = "루키즈 앱 쓰고 마음이 편해졌어요 후기",
            author = "워킹맘",
            preview = "퇴근이 늦어 늘 불안했는데 위치 알림 하나로 마음이 놓입니다.",
            likeCount = 18,
            createdAtLabel = "2026-08-27T20:00:00",
        ),
    ) + (4..48).map { i ->
        // pageSize(20)보다 확실히 많게 하려고 기존 더미 패턴을 순환 생성한다.
        val titles = listOf(
            "아이 게임 시간 어떻게 관리하세요?",
            "학원 끝나고 바로 집에 오는지 확인하는 팁",
            "유해 사이트 차단 설정 공유합니다",
            "용돈 관리 앱이랑 같이 쓰니 편하네요",
            "위치 알림 덕분에 안심돼요",
            "우리 아이 스마트폰 첫 개통 후기",
        )
        val authors = listOf("행복한엄마", "두아이아빠", "워킹맘", "도전맘", "민준아빠", "하늘맘")
        // 목록 한 줄에 본문 앞부분이 붙는 안(B)이라, 더미도 미리보기를 갖고 있어야
        // 실제 화면과 같은 높이로 그려진다. 일부는 일부러 비워 둔다 — 예전 글에는
        // 서버가 미리보기를 주지 않아, 그 줄이 어떻게 보이는지도 확인해야 한다.
        val previews = listOf(
            "주중에는 한 시간, 주말에는 두 시간으로 정해 두고 있어요.",
            "학원 앞 지정 장소를 걸어 두니 도착 알림이 바로 옵니다.",
            "차단 목록 정리해서 올려 둡니다. 필요하신 분 가져가세요.",
            "",
            "아이가 먼저 위치 공유를 켜 두더라고요. 고마웠습니다.",
            "개통하면서 같이 설정한 것들 순서대로 적어 봤어요.",
        )
        Post(
            id = i.toString(),
            authorId = (100 + i).toString(),
            title = titles[i % titles.size],
            author = authors[i % authors.size],
            preview = previews[i % previews.size],
            likeCount = (i * 3) % 60,
            createdAtLabel = "2026-08-2" + (i % 8) + "T12:00:00",
        )
    }

    val payments = listOf(
        Payment("pay1", "민준 용돈 송금", -10_000, "08.20", "out"),
        Payment("pay2", "충전", 50_000, "08.15", "in"),
        Payment("pay3", "구독 결제", -9_900, "08.01", "out"),
    )

    val pointBalance = PointBalance(45_000)

    val pairing = PairingCode(PAIRING_CODE, 300)

    val activities = listOf(
        Activity("1", ActivityType.POST, 12L, "우리 아이 스마트폰 사용 시간 어떻게 정하세요?", "2026-08-28T21:14:02"),
        Activity("2", ActivityType.COMMENT, 9L, "저희도 같은 고민이에요. 저녁 9시 이후로 막아 뒀습니다.", "2026-08-27T09:31:40"),
        Activity("3", ActivityType.LIKE, 7L, "초등 저학년 유해사이트 차단 후기", "2026-08-26T18:02:11"),
    )

    const val CHILD_SCREEN_USED_MINUTES = 84
    const val CHILD_SCREEN_LIMIT_MINUTES = 180
    const val CHILD_BLOCKED_TODAY = 3
    const val CHILD_GPS_ENABLED = true
    const val CHILD_CHAT_ROOM_ID = "room-minjun"

    fun sessionFor(user: User): Session = Session(
        accessToken = "mock-access-${user.id}",
        refreshToken = "mock-refresh-${user.id}",
        user = user,
    )
}
