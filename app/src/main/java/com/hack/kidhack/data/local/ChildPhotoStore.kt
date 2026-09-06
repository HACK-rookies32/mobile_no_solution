package com.hack.kidhack.data.local

import android.content.Context
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.domain.model.ChildPhoto
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
 * 자녀가 찍은 사진을 기기에 보관한다.
 *
 * 업로드는 서버로 보내는 것이고, 자녀 본인의 「카메라 갤러리」는 기기에 남은 사본을 본다.
 * 그래서 업로드가 실패하거나 앱을 껐다 켜도 찍은 사진이 사라지지 않는다.
 *
 * 원본 JPEG 은 `files/photos/` 에, 목록은 JSON 인덱스 한 파일에 둔다.
 * 오래된 사진은 [MAX_PHOTOS] 개를 넘으면 파일까지 함께 지운다.
 */
class ChildPhotoStore(context: Context) {

    @Serializable
    private data class Entry(
        val id: String,
        val fileName: String,
        val capturedAt: String,
        val atMillis: Long,
        val requestId: String? = null,
        val requestedBy: String? = null,
    )

    private val root = File(context.filesDir, "photos")
    private val index = File(root, "index.json")
    private val mutex = Mutex()
    private val json = KidhackJson.instance
    private val serializer = ListSerializer(Entry.serializer())

    /**
     * 촬영 직후 임시 파일을 갤러리로 복사한다. 원본은 호출자가 업로드에 계속 쓰므로 지우지 않는다.
     * 업로드보다 먼저 불러야 실패해도 사진이 남는다.
     */
    suspend fun save(
        source: File,
        requestId: String? = null,
        requestedBy: String? = null,
    ): ChildPhoto? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                root.mkdirs()
                val now = System.currentTimeMillis()
                val fileName = "photo-$now.jpg"
                source.copyTo(File(root, fileName), overwrite = true)
                val entry = Entry(
                    id = fileName.removeSuffix(".jpg"),
                    fileName = fileName,
                    capturedAt = LABEL_FORMAT.format(Date(now)),
                    atMillis = now,
                    requestId = requestId,
                    requestedBy = requestedBy,
                )
                val kept = (listOf(entry) + readUnlocked()).take(MAX_PHOTOS)
                pruneUnlocked(kept)
                writeUnlocked(kept)
                entry.toDomain()
            }.getOrNull()
        }
    }

    suspend fun list(): List<ChildPhoto> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
            .filter { File(root, it.fileName).exists() }
            .map { it.toDomain() }
    }

    /** 로그아웃 시 사진까지 전부 지운다. 다른 계정이 이전 사진을 보면 안 된다. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { root.deleteRecursively() } }
        Unit
    }

    private fun Entry.toDomain() = ChildPhoto(
        id = id,
        capturedAt = capturedAt,
        imageUrl = File(root, fileName).absolutePath,
        requestId = requestId,
        requestedBy = requestedBy,
    )

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

    /** 인덱스에서 밀려난 사진의 실제 파일을 지운다. 안 그러면 저장 공간만 계속 먹는다. */
    private fun pruneUnlocked(kept: List<Entry>) {
        val keepNames = kept.map { it.fileName }.toSet() + index.name
        runCatching {
            root.listFiles()?.forEach { file ->
                if (file.name !in keepNames) file.delete()
            }
        }
    }

    private companion object {
        const val MAX_PHOTOS = 60
        val LABEL_FORMAT = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault())
    }
}
