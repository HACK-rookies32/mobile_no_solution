package com.hack.kidhack.data.local

import android.content.Context
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.time.Instant
import java.util.Locale

/**
 * 기기에서 **실제로 일어난** 사건을 남기는 로컬 알림 기록.
 *
 * 유해 사이트 차단이나 스크린타임 한도 도달은 자녀 기기에서 즉시 발생하는데,
 * 서버 알림 목록(`GET /notifications`)에는 서버가 내려 준 것만 들어 있다.
 * 그래서 홈 탭 「최근 알림」이 이 사건들을 놓쳤다. 이 저장소가 그 빈틈을 메운다.
 *
 * 서버 알림을 대체하는 게 아니라 **합쳐서** 보여 주는 용도다.
 */
class ChildAlertStore(
    context: Context,
    private val sessionRepository: SessionRepository,
) {

    @Serializable
    private data class Entry(
        val id: String,
        val type: String,
        val title: String,
        val body: String = "",
        val atMillis: Long,
        /** 서버로 이미 올렸는지(docs/child-push-contract.md 4절). 재업로드를 막는다. */
        val uploaded: Boolean = false,
    )

    private val dir = context.filesDir
    private val legacyFile = File(dir, "child_alerts.json")

    @Volatile
    private var cachedScope: String? = null

    @Volatile
    private var cachedFile: File = legacyFile

    private val mutex = Mutex()
    private val json = KidhackJson.instance
    private val serializer = ListSerializer(Entry.serializer())

    /**
     * 같은 사건이 연달아 쌓이지 않도록 종류별 최소 간격을 둔다.
     *
     * @return 이번에 **새로** 남겼으면 true. 중복이라 건너뛰었으면 false.
     *  호출자가 「처음 발생했을 때만」 서버 보고 같은 후속 동작을 하도록 쓴다.
     */
    suspend fun record(
        type: String,
        title: String,
        body: String = "",
        dedupeKey: String = type,
        dedupeWindowMs: Long = DEFAULT_DEDUPE_MS,
    ): Boolean = withContext(Dispatchers.IO) {
        val file = file()
        mutex.withLock {
            val now = System.currentTimeMillis()
            val entries = readUnlocked(file)
            val duplicate = entries.firstOrNull {
                it.id.startsWith(dedupeKey) && now - it.atMillis < dedupeWindowMs
            }
            if (duplicate != null) return@withLock false
            val next = listOf(
                Entry(
                    id = dedupeKey + "-" + now,
                    type = type,
                    title = title,
                    body = body,
                    atMillis = now,
                ),
            ) + entries
            writeUnlocked(file, next.take(MAX_ENTRIES))
            true
        }
    }

    /** 하루에 한 번만 남겨야 하는 사건(스크린타임 한도 도달 등). */
    suspend fun recordOncePerDay(type: String, title: String, body: String = ""): Boolean =
        record(
            type = type,
            title = title,
            body = body,
            dedupeKey = type + "-" + today(),
            dedupeWindowMs = Long.MAX_VALUE,
        )

    suspend fun recent(limit: Int = MAX_ENTRIES): List<AppNotification> = withContext(Dispatchers.IO) {
        val file = file()
        mutex.withLock { readUnlocked(file) }
            .sortedByDescending { it.atMillis }
            .take(limit)
            .map {
                AppNotification(
                    id = it.id,
                    type = it.type,
                    // 내부 저장은 title/body 로 유지하되 도메인 모델은 message 단일 필드로 합친다.
                    message = listOf(it.title, it.body).filter { s -> s.isNotBlank() }.joinToString("\n"),
                    createdAt = relativeLabel(it.atMillis),
                    read = false,
                )
            }
    }

    /** 아직 서버로 올리지 않은 사건(오래된 것부터). ChildAlertUploadWorker 가 쓴다. */
    suspend fun pendingUpload(limit: Int = 20): List<ChildAlertEvent> = withContext(Dispatchers.IO) {
        val file = file()
        mutex.withLock { readUnlocked(file) }
            .filter { !it.uploaded }
            .sortedBy { it.atMillis }
            .take(limit)
            .map {
                ChildAlertEvent(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    body = it.body,
                    occurredAt = Instant.ofEpochMilli(it.atMillis).toString(),
                )
            }
    }

    suspend fun markUploaded(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val target = ids.toSet()
        val file = file()
        mutex.withLock {
            writeUnlocked(
                file,
                readUnlocked(file).map { if (it.id in target) it.copy(uploaded = true) else it },
            )
        }
        Unit
    }

    /** 오늘 발생한 특정 종류의 건수. 「오늘 차단된 사이트: N건」에 쓴다. */
    suspend fun countToday(type: String): Int = withContext(Dispatchers.IO) {
        val dayStart = startOfToday()
        val file = file()
        mutex.withLock { readUnlocked(file) }
            .count { it.type == type && it.atMillis >= dayStart }
    }

    /** 계정 탈퇴처럼 정말 지워야 할 때만 쓴다. 계정 전환은 파일이 분리돼 있어 지울 필요가 없다. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        val file = file()
        mutex.withLock { runCatching { file.delete() } }
        Unit
    }

    /**
     * 지금 로그인한 계정의 기록 파일.
     *
     * 예전에는 파일이 하나뿐이라 다른 계정이 로그인하면 통째로 지워야 했고, 그래서
     * 부모↔자녀를 오갈 때마다 「최근 알림」이 매번 비워졌다. 계정별로 나눠 두면
     * 지울 필요가 없고 돌아왔을 때 그대로 남아 있다.
     */
    private suspend fun file(): File {
        val scope = runCatching { sessionRepository.current()?.user?.id }
            .getOrNull().orEmpty().ifBlank { ANON_SCOPE }
        if (scope != cachedScope) {
            val next = File(dir, "child_alerts_" + scope + ".json")
            // 계정별 분리 이전에 쌓인 기록은 지금 계정 것으로 넘겨 준다(한 번만).
            if (!next.exists() && legacyFile.exists()) {
                runCatching {
                    legacyFile.copyTo(next, overwrite = true)
                    legacyFile.delete()
                }
            }
            cachedScope = scope
            cachedFile = next
        }
        return cachedFile
    }

    private fun readUnlocked(file: File): List<Entry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyList())
    }

    private fun writeUnlocked(file: File, entries: List<Entry>) {
        runCatching { file.writeText(json.encodeToString(serializer, entries)) }
    }

    private fun relativeLabel(atMillis: Long): String {
        val diff = System.currentTimeMillis() - atMillis
        return when {
            diff < 60_000 -> "방금 전"
            diff < 3_600_000 -> (diff / 60_000).toString() + "분 전"
            diff < 86_400_000 -> (diff / 3_600_000).toString() + "시간 전"
            else -> SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(atMillis))
        }
    }

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val TYPE_HARMFUL_BLOCK = "HARMFUL_BLOCK"
        const val TYPE_SCREEN_TIME = "SCREEN_TIME_LIMIT"

        /** 부모가 걸어 둔 앱별 규칙에 막혔다. 총 스크린타임 한도와는 다른 사건이다. */
        const val TYPE_APP_BLOCK = "APP_BLOCK"
        const val TYPE_CAPTURE_REQUEST = "CAPTURE_REQUEST"
        const val TYPE_CAMERA_REQUEST = "CAMERA_REQUEST"
        const val TYPE_SCHEDULED_CAPTURE = "SCHEDULED_CAPTURE"
        const val TYPE_GEOFENCE_EXIT = "GEOFENCE_EXIT"
        const val TYPE_GEOFENCE_ENTER = "GEOFENCE_ENTER"
        const val TYPE_GPS_OFF = "GPS_OFF"

        private const val ANON_SCOPE = "anon"
        private const val MAX_ENTRIES = 50
        private const val DEFAULT_DEDUPE_MS = 60_000L
    }
}
