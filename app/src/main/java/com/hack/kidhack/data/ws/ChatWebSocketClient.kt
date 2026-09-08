package com.hack.kidhack.data.ws

import android.util.Log
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.data.remote.dto.FlexibleDateTimeSerializer
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.MessageType
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.repository.SessionRepository
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader
import java.util.concurrent.atomic.AtomicInteger

/**
 * STOMP over SockJS client for Spring endpoint `/ws-chat`.
 *
 * - CONNECT → `{origin}/ws-chat/websocket`
 * - SUBSCRIBE → `/sub/chat/{roomId}`
 * - SEND → `/pub/chat/{roomId}` body `{ senderId, content }`
 *
 * 서버 WebSocketConfig 가 `enableSimpleBroker("/sub")` + `setApplicationDestinationPrefixes("/pub")`
 * 다. Spring 기본값인 `/topic`·`/app` 을 쓰면 **구독도 발행도 아무 데도 닿지 않는다** —
 * 연결은 되므로 오류 없이 조용히 메시지만 오가지 않는다.
 */
class ChatWebSocketClient(
    private val sessionRepository: SessionRepository,
    private val baseUrl: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val observers = AtomicInteger(0)
    private val json = KidhackJson.instance

    private val _events = MutableSharedFlow<ChatWsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatWsEvent> = _events.asSharedFlow()

    @Volatile
    private var stomp: StompClient? = null

    @Volatile
    private var wanted: Boolean = false

    @Volatile
    private var activeRoomId: String? = null

    private val disposables = CompositeDisposable()
    private var topicDisposable: Disposable? = null

    fun start() {
        if (observers.getAndIncrement() == 0) {
            wanted = true
            scope.launch { ensureConnected() }
        }
    }

    fun stop() {
        if (observers.decrementAndGet() <= 0) {
            observers.set(0)
            wanted = false
            scope.launch { disconnect() }
        }
    }

    /** 로그아웃 등 — 구독/연결을 즉시 끊고 이전 roomId를 버린다. */
    fun reset() {
        observers.set(0)
        wanted = false
        scope.launch { disconnect() }
    }

    /** 특정 방 토픽을 구독한다. 방이 바뀌면 이전 구독을 교체한다. */
    fun subscribeRoom(roomId: String) {
        activeRoomId = roomId
        scope.launch {
            ensureConnected()
            subscribeTopicWhenReady(roomId)
        }
    }

    fun send(roomId: String, senderId: Long, content: String) {
        sendBody(
            roomId,
            StompChatSendBody(senderId = senderId, content = content),
            echo = content,
        )
    }

    /** 첨부(IMAGE/AUDIO) 전송. content 는 비우고 type·mediaUrl 을 실어 보낸다. */
    fun sendMedia(roomId: String, senderId: Long, type: MessageType, mediaUrl: String) {
        sendBody(
            roomId,
            StompChatSendBody(
                senderId = senderId,
                content = "",
                type = type.name,
                mediaUrl = mediaUrl,
            ),
            echo = mediaUrl,
        )
    }

    private fun sendBody(roomId: String, body: StompChatSendBody, echo: String) {
        val payload = json.encodeToString(StompChatSendBody.serializer(), body)
        scope.launch {
            ensureConnected()
            val client = stomp
            if (client == null || !client.isConnected) {
                _events.tryEmit(ChatWsEvent.Error("채팅 연결이 없어요"))
                return@launch
            }
            disposables.add(
                client.send("/pub/chat/$roomId", payload)
                    .subscribe(
                        { _events.tryEmit(ChatWsEvent.Send(roomId, echo)) },
                        { t ->
                            Log.w(TAG, "stomp send failed", t)
                            _events.tryEmit(ChatWsEvent.Error(t.message ?: "전송 실패"))
                        },
                    ),
            )
        }
    }

    /** 서버 미지원 — no-op (호환용). */
    fun read(roomId: String, messageId: String?) {
        _events.tryEmit(ChatWsEvent.Read(roomId, messageId))
    }

    private suspend fun ensureConnected() {
        mutex.withLock {
            if (!wanted) return
            val existing = stomp
            if (existing != null && existing.isConnected) return

            existing?.disconnect()
            disposables.clear()

            val token = sessionRepository.accessToken()
            val url = buildStompUrl()
            val client = Stomp.over(Stomp.ConnectionProvider.OKHTTP, url)
            stomp = client

            disposables.add(
                client.lifecycle().subscribe { event ->
                    when (event.type) {
                        LifecycleEvent.Type.OPENED -> {
                            Log.d(TAG, "stomp opened")
                            activeRoomId?.let { roomId -> subscribeTopicWhenReady(roomId) }
                        }
                        LifecycleEvent.Type.ERROR -> {
                            Log.w(TAG, "stomp error", event.exception)
                            _events.tryEmit(
                                ChatWsEvent.Error(event.exception?.message ?: "채팅 연결 오류"),
                            )
                        }
                        LifecycleEvent.Type.CLOSED -> {
                            Log.d(TAG, "stomp closed")
                            if (wanted) {
                                scope.launch {
                                    delay(2_000)
                                    if (wanted) ensureConnected()
                                }
                            }
                        }
                        LifecycleEvent.Type.FAILED_SERVER_HEARTBEAT -> {
                            _events.tryEmit(ChatWsEvent.Error("채팅 하트비트 실패"))
                        }
                    }
                },
            )

            val headers = mutableListOf<StompHeader>()
            if (!token.isNullOrBlank()) {
                headers.add(StompHeader("Authorization", "Bearer $token"))
            }
            client.connect(headers)
        }
    }

    /** WebSocket OPENED 직후에는 STOMP CONNECTED 전이라 isConnected=false일 수 있어 재시도한다. */
    private fun subscribeTopicWhenReady(roomId: String) {
        scope.launch {
            repeat(MAX_SUBSCRIBE_ATTEMPTS) { attempt ->
                val client = stomp
                if (client != null && client.isConnected) {
                    subscribeTopic(roomId)
                    return@launch
                }
                if (attempt == 0) {
                    Log.d(TAG, "stomp subscribe waiting for CONNECTED room=$roomId")
                }
                delay(SUBSCRIBE_RETRY_MS)
            }
            Log.w(TAG, "stomp subscribe timeout room=$roomId")
            _events.tryEmit(ChatWsEvent.Error("채팅방 구독에 실패했어요"))
        }
    }

    private fun subscribeTopic(roomId: String) {
        val client = stomp
        if (client == null) {
            Log.w(TAG, "stomp subscribe skipped: no client room=$roomId")
            return
        }
        if (!client.isConnected) {
            Log.w(TAG, "stomp subscribe skipped: not connected room=$roomId")
            return
        }
        synchronized(this) {
            topicDisposable?.dispose()
            topicDisposable = client.topic("/sub/chat/$roomId").subscribe(
                { message ->
                    // [VULN-MOBILE-LOG-1] 채팅 메시지 payload 전문을 빌드 변형 가드 없이 logcat 에 출력.
                    // 의도된 취약점(OWASP Mobile M9). 보존: 고치지 말 것. 명세: mobile/docs/vuln-spec-mobile.md
                    Log.d(TAG, "stomp recv room=$roomId payload=${message.payload}")
                    val payload = runCatching {
                        json.decodeFromString(
                            StompChatMessagePayload.serializer(),
                            message.payload,
                        )
                    }.getOrElse { t ->
                        Log.w(TAG, "stomp payload parse failed: ${message.payload}", t)
                        _events.tryEmit(ChatWsEvent.Error("잘못된 채팅 메시지"))
                        return@subscribe
                    }
                    scope.launch {
                        val session = sessionRepository.current()
                        val myId = session?.user?.id?.toLongOrNull()
                        val myRole = session?.user?.role ?: Role.PARENT
                        val fromRole = when {
                            payload.senderId != null && myId != null ->
                                if (payload.senderId == myId) myRole
                                else if (myRole == Role.PARENT) Role.CHILD else Role.PARENT
                            else -> Role.PARENT
                        }
                        val chat = ChatMessage(
                            id = payload.id?.takeIf { it.isNotBlank() }
                                ?: "stomp-${System.currentTimeMillis()}",
                            roomId = payload.roomId ?: roomId,
                            fromRole = fromRole,
                            text = payload.content.orEmpty(),
                            sentAt = payload.sentAt.orEmpty(),
                            type = MessageType.fromWire(payload.type),
                            mediaUrl = payload.mediaUrl?.takeIf { it.isNotBlank() },
                        )
                        Log.d(TAG, "stomp message parsed id=${chat.id} from=$fromRole")
                        _events.tryEmit(ChatWsEvent.Message(chat))
                    }
                },
                { t ->
                    Log.w(TAG, "stomp subscribe failed", t)
                    _events.tryEmit(ChatWsEvent.Error(t.message ?: "구독 실패"))
                },
            )
            topicDisposable?.let { disposables.add(it) }
            Log.d(TAG, "stomp subscribed /sub/chat/$roomId")
        }
    }

    private suspend fun disconnect() {
        mutex.withLock {
            topicDisposable?.dispose()
            topicDisposable = null
            disposables.clear()
            stomp?.disconnect()
            stomp = null
            activeRoomId = null
        }
    }

    /**
     * SockJS WebSocket transport URL.
     * BASE_URL 예: `http://192.168.x.x:8080/api` → `ws://192.168.x.x:8080/ws-chat/websocket`
     */
    private fun buildStompUrl(): String {
        val origin = baseUrl.trimEnd('/')
            .removeSuffix("/api/v1")
            .removeSuffix("/api")
            .replace(Regex("^https://"), "wss://")
            .replace(Regex("^http://"), "ws://")
        return "$origin/ws-chat/websocket"
    }

    companion object {
        private const val TAG = "ChatStomp"
        private const val MAX_SUBSCRIBE_ATTEMPTS = 30
        private const val SUBSCRIBE_RETRY_MS = 100L
    }
}

@Serializable
data class StompChatSendBody(
    val senderId: Long,
    val content: String,
    val type: String = "TEXT",
    val mediaUrl: String? = null,
)

@Serializable
data class StompChatMessagePayload(
    val id: String? = null,
    val roomId: String? = null,
    val senderId: Long? = null,
    val content: String? = null,
    val type: String? = null,
    val mediaUrl: String? = null,
    @Serializable(with = FlexibleDateTimeSerializer::class)
    val sentAt: String? = null,
)
