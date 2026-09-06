package com.hack.kidhack.data.ws

import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.data.repository.toRemoteCommandType
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * 부모 요청을 실시간으로 받는 상주 WebSocket. **FCM 대체 경로**다.
 *
 * - [start] 하면 `wss://…/ws/commands` 에 붙고, 끊기면 지수 백오프로 재연결한다.
 * - OkHttp 가 30초마다 ping 프레임을 보내 유휴 NAT 타임아웃을 막는다.
 * - 소켓이 죽어 있는 동안 놓친 요청은 FCM 이 메운다. 폴링 백업 경로는
 *   서버에 대응 엔드포인트가 없어 걷어냈다(2026-08-28).
 */
class ChildCommandSocket(
    private val okHttpClient: OkHttpClient,
    private val sessionRepository: SessionRepository,
    private val baseUrl: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = KidhackJson.instance

    /** 유휴 NAT 타임아웃 방지를 위해 ping 을 켠 전용 클라이언트(커넥션 풀은 공유). */
    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder().pingInterval(PING_SECONDS, TimeUnit.SECONDS).build()
    }

    private val _commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<RemoteCommand> = _commands.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var wanted: Boolean = false

    private var attempt: Int = 0
    private var reconnectJob: Job? = null

    fun start() {
        if (wanted) return
        wanted = true
        scope.launch { connect() }
    }

    fun stop() {
        wanted = false
        reconnectJob?.cancel()
        socket?.close(NORMAL_CLOSURE, "stopped")
        socket = null
        _connected.value = false
    }

    /** 요청 처리 결과를 소켓으로도 알려 준다(REST ack 과 중복돼도 서버가 멱등 처리). */
    fun ack(requestId: String, status: String, message: String? = null) {
        val frame = ChildCommandFrame(
            type = ChildCommandFrame.ACK,
            requestId = requestId,
            status = status,
            message = message,
        )
        socket?.send(json.encodeToString(ChildCommandFrame.serializer(), frame))
    }

    private suspend fun connect() {
        mutex.withLock {
            if (!wanted) return
            socket?.cancel()
            val token = sessionRepository.accessToken()
            val request = Request.Builder()
                .url(WsUrls.of(baseUrl, PATH, token))
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .build()
            socket = wsClient.newWebSocket(request, listener)
        }
    }

    private fun scheduleReconnect() {
        if (!wanted) return
        reconnectJob?.cancel()
        val delayMs = (1_000L * (1L shl attempt.coerceAtMost(5))).coerceAtMost(30_000L)
        attempt += 1
        reconnectJob = scope.launch {
            delay(delayMs)
            if (wanted) connect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            _connected.value = true
            scope.launch {
                val childId = sessionRepository.current()?.user?.id
                val hello = ChildCommandFrame(type = ChildCommandFrame.HELLO, childId = childId)
                webSocket.send(json.encodeToString(ChildCommandFrame.serializer(), hello))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching {
                json.decodeFromString(ChildCommandFrame.serializer(), text)
            }.getOrNull() ?: return

            when (frame.type.uppercase()) {
                ChildCommandFrame.PING -> {
                    val pong = ChildCommandFrame(type = ChildCommandFrame.PONG)
                    webSocket.send(json.encodeToString(ChildCommandFrame.serializer(), pong))
                }
                ChildCommandFrame.PONG, ChildCommandFrame.ACK, ChildCommandFrame.ERROR -> Unit
                else -> {
                    val type = frame.type.toRemoteCommandType()
                    if (type == RemoteCommandType.UNKNOWN) return
                    val requestId = frame.requestId ?: return
                    _commands.tryEmit(
                        RemoteCommand(
                            requestId = requestId,
                            type = type,
                            childId = frame.childId.orEmpty(),
                            requestedBy = frame.requestedBy,
                            requestedAt = frame.requestedAt,
                        ),
                    )
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connected.value = false
            if (wanted) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connected.value = false
            if (wanted) scheduleReconnect()
        }
    }

    private companion object {
        const val PATH = "/ws/commands"
        const val PING_SECONDS = 30L
        const val NORMAL_CLOSURE = 1000
    }
}
