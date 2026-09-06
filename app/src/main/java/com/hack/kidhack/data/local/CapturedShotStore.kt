package com.hack.kidhack.data.local

import android.content.Context
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.domain.model.Screenshot
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
 * 화면 캡처·카메라 촬영 기록을 기기에 남긴다.
 *
 * 예전에는 메모리 상 `object` 였던 탓에 앱 프로세스가 죽으면 캡처 내역이 시드 3건으로
 * 되돌아갔다. 이제 이미지 파일과 목록을 함께 파일로 보관해 재시작해도 유지된다.
 *
 * 목 모드의 「캡처 내역」이 이 저장소를 읽는다. 실서버 모드에서는 서버 목록을 쓰므로
 * 이 저장소를 거치지 않는다.
 */
class CapturedShotStore(context: Context) {

    @Serializable
    private data class Entry(
        val id: String,
        val fileName: String?,
        val capturedAt: String,
        val description: String,
        val trigger: String,
        val atMillis: Long,
    )

    // [VULN-MOBILE-STORE-3] 화면 캡처 원본 이미지 + 목록(index.json)을 filesDir/captures 에 평문 저장.
    // 채팅 저장(ChatMessageStore)과 같은 ID. 의도된 취약점(OWASP Mobile M9). 명세: mobile/docs/vuln-spec-mobile.md
    private val root = File(context.filesDir, "captures")
    private val index = File(root, "index.json")
    private val mutex = Mutex()
    private val json = KidhackJson.instance
    private val serializer = ListSerializer(Entry.serializer())

    /**
     * 캡처본을 보관한다. [source] 는 호출자가 업로드에 계속 쓰므로 복사만 하고 지우지 않는다.
     */
    suspend fun add(
        source: File?,
        description: String,
        trigger: String,
    ): Screenshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val fileName = source?.let { origin ->
                runCatching {
                    root.mkdirs()
                    val name = "shot-$now.jpg"
                    origin.copyTo(File(root, name), overwrite = true)
                    name
                }.getOrNull()
            }
            val entry = Entry(
                id = "shot-$now",
                fileName = fileName,
                capturedAt = LABEL_FORMAT.format(Date(now)),
                description = description,
                trigger = trigger,
                atMillis = now,
            )
            // 목록을 한 번도 연 적 없이 바로 캡처하면 시드가 안 심긴 채 덮어써진다.
            // 어느 순서로 진입해도 같은 목록이 되도록 여기서도 시드를 거친다.
            val kept = (listOf(entry) + seededUnlocked()).take(MAX_SHOTS)
            pruneUnlocked(kept)
            writeUnlocked(kept)
            entry.toDomain()
        }
    }

    suspend fun list(): List<Screenshot> = withContext(Dispatchers.IO) {
        mutex.withLock { seededUnlocked() }.map { it.toDomain() }
    }

    /** 로그인 계정이 바뀌면 남의 캡처가 보이면 안 된다. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { root.deleteRecursively() } }
        Unit
    }

    private fun Entry.toDomain() = Screenshot(
        id = id,
        capturedAt = capturedAt,
        // 서버 ScreenshotResponse 에 설명이 없어 도메인에서도 뺐다.
        // 로컬 Entry.description 은 시드 데이터용으로만 남는다.
        trigger = trigger,
        imageUrl = fileName?.let { File(root, it) }?.takeIf { it.exists() }?.absolutePath,
    )

    /** 처음 실행이면 데모용 시드를 한 번 심어 목록이 비어 보이지 않게 한다. */
    private fun seededUnlocked(): List<Entry> {
        val current = readUnlocked()
        if (current.isNotEmpty() || index.exists()) return current
        writeUnlocked(SEED)
        return SEED
    }

    private fun readUnlocked(): List<Entry> {
        if (!index.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, index.readText()) }
            .getOrDefault(emptyList())
    }

    private fun writeUnlocked(entries: List<Entry>) {
        runCatching {
            root.mkdirs()
            index.writeText(json.encodeToString(serializer, entries))
        }
    }

    /** 목록에서 밀려난 캡처의 실제 파일을 지운다. */
    private fun pruneUnlocked(kept: List<Entry>) {
        val keepNames = kept.mapNotNull { it.fileName }.toSet() + index.name
        runCatching {
            root.listFiles()?.forEach { file ->
                if (file.name !in keepNames) file.delete()
            }
        }
    }

    private companion object {
        const val MAX_SHOTS = 50
        val LABEL_FORMAT = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault())

        val SEED = listOf(
            Entry("s1", null, "오늘 14:22", "YouTube 시청 중", "MANUAL", 0),
            Entry("s2", null, "오늘 11:05", "카카오톡", "MANUAL", 0),
            Entry("s3", null, "어제 18:40", "Chrome", "SCHEDULED", 0),
        ) + (4..36).map { i ->
            // MAX_SHOTS(50) 캡을 넘지 않는 선에서 기존 패턴을 순환 생성한다.
            val descs = listOf("YouTube 시청 중", "카카오톡", "Chrome", "인스타그램", "게임 중", "네이버 검색")
            val triggers = listOf("MANUAL", "SCHEDULED")
            Entry(
                id = "s$i",
                fileName = null,
                capturedAt = "%02d.%02d %02d:%02d".format(8, (i % 28) + 1, i % 24, (i * 7) % 60),
                description = descs[i % descs.size],
                trigger = triggers[i % triggers.size],
                atMillis = 0,
            )
        }
    }
}
