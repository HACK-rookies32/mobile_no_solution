package com.hack.kidhack.data.repository.fake

import android.content.Context
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.domain.model.AppNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 목업 빌드의 **서버 알림 생성기**.
 *
 * 실서버에서는 자녀 기기가 사건을 보고하면(`POST /harmful-access/report` 등) 서버가 그걸 받아
 * 보호자 알림을 만들고, 보호자 앱이 `GET /notifications` 로 가져간다. 목업에는 그 중간 단계가
 * 없어서 자녀가 유해 사이트에 걸려도 부모 화면에는 아무것도 뜨지 않았다.
 * (`FakeBlocklistRepository.reportHarmfulAccess` 는 빈 함수였고, `FakeNotificationRepository` 는
 * 고정된 예시 2건만 돌려줬다.)
 *
 * 이 클래스가 그 빈자리를 메운다. 자녀 쪽 Fake 저장소가 보고를 받으면 여기에 알림을 쌓고,
 * `FakeNotificationRepository` 가 그걸 목록에 얹어 준다. 한 기기에서 계정을 바꿔 가며
 * 테스트할 때 자녀의 행동이 부모 화면까지 실제로 흘러가는지 확인할 수 있다.
 *
 * 실서버(`USE_MOCK=false`)에서는 쓰이지 않는다.
 */
class MockNotificationFeed(context: Context) {

    @Serializable
    private data class Entry(
        val id: String,
        val type: String,
        val title: String,
        val body: String,
        val atMillis: Long,
        val childId: String? = null,
    )

    private val file = File(context.filesDir, "mock_notifications.json")
    private val mutex = Mutex()
    private val json = KidhackJson.instance
    private val serializer = ListSerializer(Entry.serializer())

    suspend fun add(
        type: String,
        title: String,
        body: String,
        dedupeKey: String = type,
        dedupeWindowMs: Long = DEFAULT_DEDUPE_MS,
        childId: String? = null,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val stored = readUnlocked()
            val duplicate = stored.firstOrNull {
                it.id.startsWith(dedupeKey) && now - it.atMillis < dedupeWindowMs
            }
            if (duplicate != null) return@withLock
            val next = listOf(
                Entry(
                    id = dedupeKey + "-" + now,
                    type = type,
                    title = title,
                    body = body,
                    atMillis = now,
                    childId = childId,
                ),
            ) + stored
            runCatching {
                file.writeText(json.encodeToString(serializer, next.take(MAX_ENTRIES)))
            }
        }
        Unit
    }

    /** 하루에 한 번만 만들어야 하는 알림(사용량 한도 도달 등). */
    suspend fun addOncePerDay(
        type: String,
        title: String,
        body: String,
        childId: String? = null,
    ) = add(
        type = type,
        title = title,
        body = body,
        dedupeKey = type + "-" + childId.orEmpty() + "-" + SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()),
        dedupeWindowMs = Long.MAX_VALUE,
        childId = childId,
    )

    suspend fun all(): List<AppNotification> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
            .sortedByDescending { it.atMillis }
            .map {
                AppNotification(
                    id = it.id,
                    type = it.type,
                    title = it.title.takeIf { s -> s.isNotBlank() },
                    message = it.body.ifBlank { it.title },
                    createdAt = TIME_FORMAT.format(Date(it.atMillis)),
                    read = false,
                    childId = it.childId,
                )
            }
    }

    private fun readUnlocked(): List<Entry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_ENTRIES = 50
        const val DEFAULT_DEDUPE_MS = 60_000L

        val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}
