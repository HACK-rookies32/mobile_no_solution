package com.hack.kidhack

import com.hack.kidhack.background.push.PushMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FCM data 페이로드 파싱 검증.
 *
 * 잘못된 페이로드로 앱이 죽지 않고 UNKNOWN 으로 떨어지는지, 자녀 명령의 requestId 가
 * id 로 잡히는지 확인한다.
 */
class PushMessageTest {

    @Test
    fun `보호자 알림 페이로드를 파싱한다`() {
        val msg = PushMessage.fromData(
            mapOf(
                "kind" to "PARENT_ALERT",
                "type" to "HARMFUL",
                "title" to "유해 사이트 접속 차단",
                "body" to "민준 · 도박 사이트 차단",
                "childId" to "child-minjun",
                "id" to "alert-42",
            ),
        )
        assertEquals(PushMessage.Kind.PARENT_ALERT, msg.kind)
        assertEquals("HARMFUL", msg.type)
        assertEquals("child-minjun", msg.childId)
        assertEquals("alert-42", msg.id)
    }

    @Test
    fun `자녀 명령은 requestId 를 id 로 삼는다`() {
        val msg = PushMessage.fromData(
            mapOf(
                "kind" to "CHILD_COMMAND",
                "type" to "CAMERA_SHOT",
                "requestId" to "req-7",
                "childId" to "child-minjun",
                "requestedBy" to "엄마",
            ),
        )
        assertEquals(PushMessage.Kind.CHILD_COMMAND, msg.kind)
        assertEquals("req-7", msg.id)
        assertEquals("엄마", msg.requestedBy)
    }

    @Test
    fun `kind 대소문자를 가리지 않는다`() {
        assertEquals(PushMessage.Kind.PARENT_ALERT, PushMessage.fromData(mapOf("kind" to "parent_alert")).kind)
        assertEquals(PushMessage.Kind.CHILD_COMMAND, PushMessage.fromData(mapOf("kind" to " Child_Command ")).kind)
    }

    @Test
    fun `알 수 없는 kind 나 빈 페이로드는 UNKNOWN`() {
        assertEquals(PushMessage.Kind.UNKNOWN, PushMessage.fromData(emptyMap()).kind)
        assertEquals(PushMessage.Kind.UNKNOWN, PushMessage.fromData(mapOf("kind" to "GARBAGE")).kind)
    }

    @Test
    fun `서버 실제 페이로드 - kind 없이 type 만 오면 type 으로 추론한다`() {
        // 서버 FirebasePushSender: data{type, notificationId, childId?, title, body}
        val command = PushMessage.fromData(
            mapOf(
                "type" to "SCREENSHOT_REQUESTED",
                "notificationId" to "31",
                "title" to "화면 캡처 요청",
                "body" to "부모가 화면 캡처를 요청했습니다.",
            ),
        )
        assertEquals(PushMessage.Kind.CHILD_COMMAND, command.kind)
        assertEquals("31", command.id)

        val alert = PushMessage.fromData(
            mapOf(
                "type" to "HARMFUL_SITE",
                "notificationId" to "32",
                "childId" to "13",
                "title" to "유해 접속 감지",
                "body" to "유해 사이트/앱 접속이 감지되었습니다: badsite",
            ),
        )
        assertEquals(PushMessage.Kind.PARENT_ALERT, alert.kind)
        assertEquals("32", alert.id)
        assertEquals("13", alert.childId)
        assertEquals("유해 접속 감지", alert.title)
    }

    @Test
    fun `명시된 kind 가 type 추론보다 우선한다`() {
        val msg = PushMessage.fromData(mapOf("kind" to "PARENT_ALERT", "type" to "SCREENSHOT_REQUESTED", "notificationId" to "1"))
        assertEquals(PushMessage.Kind.PARENT_ALERT, msg.kind)
    }

    @Test
    fun `누락된 필드는 빈 문자열·null 로 안전하게 채운다`() {
        val msg = PushMessage.fromData(mapOf("kind" to "PARENT_ALERT"))
        assertEquals("", msg.type)
        assertEquals("", msg.title)
        assertEquals(null, msg.childId)
        assertEquals(null, msg.requestedBy)
    }
}
