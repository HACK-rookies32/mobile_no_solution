package com.hack.kidhack.data.repository

import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatMessagePage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * 어떤 [ChatRepository] 든 감싸서 대화 기록을 기기에 남긴다.
 *
 * 목/실서버 구현을 건드리지 않고 영속성만 얹기 위한 데코레이터다.
 * 부모·자녀 양쪽 채팅이 모두 이걸 거치므로 앱을 완전히 종료해도 대화가 남는다.
 *
 * 메시지: 서버 실패 시 디스크 캐시로 보조. 로그아웃 시 [ChatMessageStore.clear].
 * 방 목록: 서버만 사용(로컬 저장/폴백 없음).
 */
class CachedChatRepository(
    private val delegate: ChatRepository,
    private val store: ChatMessageStore,
) : ChatRepository {

    override suspend fun getOrCreateRoom(parentId: Long, childId: Long): ChatRoom =
        delegate.getOrCreateRoom(parentId, childId)

    override suspend fun rooms(): List<ChatRoom> =
        // 방 목록은 서버만 사용. 로컬 폴백/저장은 계정 교차 노출을 만든다.
        runCatching { delegate.rooms() }.getOrElse { emptyList() }

    override suspend fun messages(roomId: String, before: String?, size: Int): ChatMessagePage {
        // 더 이전 메시지를 요청하는 페이징은 캐시와 합치지 않는다(순서가 꼬인다).
        if (before != null) {
            return runCatching { delegate.messages(roomId, before, size) }
                .getOrDefault(ChatMessagePage(emptyList(), hasMore = false, oldestSentAt = null))
        }
        val cached = store.loadMessages(roomId)
        return runCatching { delegate.messages(roomId, null, size) }
            .fold(
                onSuccess = { page -> page.copy(messages = store.mergeAndSave(roomId, cached, page.messages)) },
                // 서버가 안 될 때는 캐시만 보여 준다. 그때는 「더 보기」를 걸 수 없다 —
                // 캐시 뒤로 무엇이 더 있는지 알 방법이 없다.
                onFailure = { ChatMessagePage(cached, hasMore = false, oldestSentAt = null) },
            )
    }

    override fun observeMessages(roomId: String): Flow<ChatMessage> =
        delegate.observeMessages(roomId).onEach { store.append(roomId, it) }

    override suspend fun send(roomId: String, content: String): ChatMessage =
        delegate.send(roomId, content).also { store.append(roomId, it) }

    override suspend fun markRead(roomId: String, lastMessageId: String?) =
        delegate.markRead(roomId, lastMessageId)
}
