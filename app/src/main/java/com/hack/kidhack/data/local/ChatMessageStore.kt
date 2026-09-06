package com.hack.kidhack.data.local

import android.content.Context
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.data.remote.dto.ChatMessageDto
import com.hack.kidhack.data.remote.dto.ChatRoomDto
import com.hack.kidhack.data.repository.toDomain
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * 채팅 기록을 기기에 남겨 앱을 완전히 종료했다 켜도 대화가 이어지게 한다.
 *
 * 방마다 JSON 파일 하나(`files/chat/<roomId>.json`)를 쓴다. 메시지 수는 [MAX_MESSAGES] 로
 * 잘라 파일이 무한정 커지지 않게 한다. 서버가 살아 있으면 항상 서버 값이 우선이고,
 * 이 저장소는 **로그인 중 오프라인/콜드 스타트용 캐시**다. 로그아웃하면 [clear]로 비운다.
 */
class ChatMessageStore(context: Context) {

    // [VULN-MOBILE-STORE-3] 채팅 대화 전문을 filesDir/chat/<roomId>.json 에 평문 저장(암호화 없음).
    // 캡처 이미지는 CapturedShotStore(filesDir/captures)도 동일. 루팅/백업/기기이전 시 대화·캡처 유출.
    // 의도된 취약점(OWASP Mobile M9 Insecure Data Storage). 보존: 고치지 말 것. 명세: mobile/docs/vuln-spec-mobile.md
    private val root = File(context.filesDir, "chat")
    private val mutex = Mutex()
    private val json = KidhackJson.instance

    private val messageSerializer = ListSerializer(ChatMessageDto.serializer())
    private val roomSerializer = ListSerializer(ChatRoomDto.serializer())

    suspend fun loadMessages(roomId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val file = messageFile(roomId)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(messageSerializer, file.readText()).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    suspend fun saveMessages(roomId: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        mutex.withLock { write(roomId, messages) }
    }

    /** 소켓으로 새로 받은(또는 방금 보낸) 메시지 한 건을 이어 붙인다. */
    suspend fun append(roomId: String, message: ChatMessage) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = loadUnlocked(roomId)
            write(roomId, merge(current, listOf(message)))
        }
    }

    /**
     * 캐시와 서버 응답을 합친다.
     *
     * 서버가 확정한 메시지가 낙관적으로 넣어 둔 로컬 메시지(`local-`/`fake-`)와 같은 내용이면
     * 로컬 쪽을 버리고 서버 것으로 갈아 끼운다. 그러지 않으면 재시작 때 같은 말이 두 번 보인다.
     */
    suspend fun mergeAndSave(
        roomId: String,
        cached: List<ChatMessage>,
        fresh: List<ChatMessage>,
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val merged = merge(cached, fresh)
            write(roomId, merged)
            merged
        }
    }

    suspend fun loadRooms(): List<ChatRoom> = withContext(Dispatchers.IO) {
        val file = roomsFile()
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(roomSerializer, file.readText()).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    suspend fun saveRooms(rooms: List<ChatRoom>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // 서버 계약(ChatRoomSummaryResponse)과 같은 모양으로 캐시한다.
            // 제목은 서버가 counterpart* 로 주므로 그 자리에 담아 두면 복원 시 그대로 살아난다.
            val dtos = rooms.map {
                ChatRoomDto(
                    roomId = it.id,
                    parentId = it.parentId,
                    childId = it.childId,
                    counterpartName = it.title,
                    counterpartProfileImageUrl = it.counterpartProfileImageUrl,
                    lastMessage = it.lastMessage,
                    lastMessageAt = it.lastAt,
                    unreadCount = it.unreadCount,
                )
            }
            runCatching {
                root.mkdirs()
                roomsFile().writeText(json.encodeToString(roomSerializer, dtos))
            }
        }
        Unit
    }

    /** 로그아웃 시 다른 계정에 이전 대화가 보이지 않도록 지운다. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { root.deleteRecursively() } }
        Unit
    }

    private fun loadUnlocked(roomId: String): List<ChatMessage> {
        val file = messageFile(roomId)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(messageSerializer, file.readText()).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    private fun write(roomId: String, messages: List<ChatMessage>) {
        val dtos = messages.takeLast(MAX_MESSAGES).map {
            ChatMessageDto(it.id, it.roomId, it.fromRole.name, it.text, it.sentAt)
        }
        runCatching {
            root.mkdirs()
            messageFile(roomId).writeText(json.encodeToString(messageSerializer, dtos))
        }
    }

    private fun merge(cached: List<ChatMessage>, fresh: List<ChatMessage>): List<ChatMessage> {
        if (fresh.isEmpty()) return cached
        val result = cached.toMutableList()
        fresh.forEach { incoming ->
            val sameId = result.indexOfFirst { it.id == incoming.id }
            if (sameId >= 0) {
                result[sameId] = incoming
                return@forEach
            }
            val optimistic = result.indexOfLast {
                it.isOptimistic() && it.text == incoming.text && it.fromRole == incoming.fromRole
            }
            if (optimistic >= 0 && !incoming.isOptimistic()) {
                result[optimistic] = incoming
            } else {
                result.add(incoming)
            }
        }
        return result
    }

    private fun ChatMessage.isOptimistic(): Boolean =
        id.startsWith("local-") || id.startsWith("fake-") || id.startsWith("ws-")

    private fun messageFile(roomId: String) = File(root, roomId.sanitized() + ".json")

    private fun roomsFile() = File(root, "rooms.json")

    /** roomId 가 경로 구분자를 품고 있어도 파일명이 깨지지 않게 한다. */
    private fun String.sanitized(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val MAX_MESSAGES = 500
    }
}
