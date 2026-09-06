package com.hack.kidhack.background.alert

import com.hack.kidhack.data.local.ServerAlertStore
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.notificationRecipientId
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 부모 알림의 단일 진입점.
 *
 * 1. 서버 알림 목록을 가져와 [ServerAlertStore] 에 **영구 저장**한다.
 * 2. 이번에 처음 들어온 알림만 골라 푸시로 띄운다.
 * 3. 화면에 그릴 목록(로컬 보관함 전체)을 돌려준다.
 *
 * 대시보드·알림 탭의 새로고침과 15분 주기 [ServerAlertPollWorker] 가 모두 이 함수를 쓴다.
 * 그래서 앱을 켜 두든 꺼 두든 같은 결과가 나온다.
 */
class ServerAlertSync(
    private val sessionRepository: SessionRepository,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val store: ServerAlertStore,
    private val notifier: ServerAlertNotifier,
) {
    /** 워커와 화면 새로고침이 동시에 돌 때 같은 알림을 두 번 띄우지 않도록 직렬화한다. */
    private val mutex = Mutex()

    /**
     * 보관함의 최신 알림 목록.
     *
     * 워커가 백그라운드에서 새 알림을 받아 넣든, 테스트 버튼이 직접 기록하든
     * 같은 [ServerAlertStore] 를 거치므로 구독 중인 화면에 즉시 반영된다.
     */
    val alerts: StateFlow<List<AppNotification>> get() = store.alerts

    private val _unreadCount = MutableStateFlow(0)

    /** 대시보드 종 아이콘 배지에 쓰는 미확인 건수. */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    data class Result(
        val alerts: List<AppNotification>,
        /** 서버 조회가 실패했는지. 실패해도 [alerts] 에는 보관해 둔 알림이 그대로 들어 있다. */
        val remoteFailed: Boolean = false,
    )

    suspend fun sync(): Result = mutex.withLock {
        // 부모 전용이 아니다. 자녀도 채팅·스크린타임 한도 같은 서버 알림을 받아야 한다.
        // 예전에는 여기서 PARENT 가 아니면 빈 값을 돌려줘, 자녀는 목록을 새로고침할 때만
        // 알림을 봤고 팝업은 아예 뜨지 않았다.
        val recipientId = sessionRepository.notificationRecipientId().ifBlank { return@withLock Result(emptyList()) }
        val remote = runCatching { notificationRepository.list(recipientId) }
        remote.getOrNull()?.let { list ->
            val fresh = store.merge(list)
            if (fresh.isNotEmpty()) {
                // 수신 설정이 꺼진 종류는 푸시를 띄우지 않는다. 다만 보관함에는 남겨 두어
                // 알림 탭에서는 나중에라도 확인할 수 있게 한다.
                val settings = runCatching { userRepository.notificationSettings() }.getOrNull()
                notifier.notifyAll(fresh, settings)
                // 푸시를 건너뛴 알림도 notified 로 표시한다. 설정을 다시 켰을 때
                // 예전 알림이 한꺼번에 울리는 일을 막는다.
                store.markNotified(fresh.map { it.id })
            }
        }
        Result(alerts = store.all(), remoteFailed = remote.isFailure).also { refreshUnread() }
    }

    /**
     * 서버를 거치지 않고 알림 하나를 **직접** 발생시킨다.
     *
     * 기기에서 바로 감지되는 사건(GPS 꺼짐 등)과 알림 탭의 테스트 버튼이 쓴다.
     * 서버 폴링 경로와 똑같이 보관함에 남기고 푸시까지 띄우므로,
     * 「최근 알림 기록 + 푸시」 전체 경로를 그대로 검증할 수 있다.
     *
     * @return 푸시가 실제로 떴는지. 수신 설정이 꺼져 있거나 알림 권한이 없으면 false 다
     *  (그 경우에도 보관함에는 남는다).
     */
    suspend fun emitLocal(
        type: String,
        title: String,
        body: String,
        childId: String? = null,
    ): Boolean = recordRemote(
        id = "local-" + type + "-" + System.currentTimeMillis(),
        type = type,
        title = title,
        body = body,
        childId = childId,
    )

    /**
     * 자녀 기기·서버에서 유래한 알림을 **고정 id** 로 기록하고 푸시까지 띄운다.
     *
     * 같은 id 가 이미 보관함에 있으면 아무 일도 하지 않는다. 폴링이 같은 사건을
     * 몇 번을 다시 가져와도 푸시는 처음 한 번만 울린다.
     */
    suspend fun recordRemote(
        id: String,
        type: String,
        title: String,
        body: String,
        childId: String? = null,
    ): Boolean {
        val alert = store.record(
            id = id,
            type = type,
            title = title,
            body = body,
            childId = childId,
        ) ?: return false
        val settings = runCatching { userRepository.notificationSettings() }.getOrNull()
        val pushed = notifier.notifyAll(listOf(alert), settings)
        store.markNotified(listOf(alert.id))
        refreshUnread()
        return pushed.isNotEmpty()
    }

    /** 서버를 건드리지 않고 보관함만 읽는다. */
    suspend fun cached(): List<AppNotification> = store.all().also { refreshUnread() }

    suspend fun markAllRead() {
        store.markAllRead()
        refreshUnread()
    }

    private suspend fun refreshUnread() {
        _unreadCount.value = store.unreadCount()
    }
}
