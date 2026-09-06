package com.hack.kidhack.data.repository.fake

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
 * 목업 빌드의 **서버측 카메라 촬영 보관함** ([MockNotificationFeed] 와 같은 역할).
 *
 * 자녀가 올린 촬영본이 여기 쌓이고 부모 「카메라 촬영」 갤러리가 이걸 읽는다.
 * 자녀 본인의 갤러리([com.hack.kidhack.data.local.ChildPhotoStore])와 달리
 * 계정 전환에도 지워지지 않아, 한 기기에서 계정을 바꿔 가며 흐름을 검증할 수 있다.
 */
class MockPhotoFeed(context: Context) {

    @Serializable
    private data class Entry(
        val id: String,
        val fileName: String,
        val capturedAt: String,
        val atMillis: Long,
        val requestId: String? = null,
    )

    private val root = File(context.filesDir, "mock_photo_feed")
    private val index = File(root, "index.json")
    private val mutex = Mutex()
    private val json = KidhackJson.instance
    private val serializer = ListSerializer(Entry.serializer())

    /** 업로드. 호출자가 원본 임시 파일을 지우므로 여기서 복사해 보관한다. */
    suspend fun add(source: File, requestId: String?): ChildPhoto = withContext(Dispatchers.IO) {
        mutex.withLock {
            root.mkdirs()
            val now = System.currentTimeMillis()
            val fileName = "shot-$now.jpg"
            runCatching { source.copyTo(File(root, fileName), overwrite = true) }
            val entry = Entry(
                id = fileName.removeSuffix(".jpg"),
                fileName = fileName,
                capturedAt = LABEL_FORMAT.format(Date(now)),
                atMillis = now,
                requestId = requestId,
            )
            val kept = (listOf(entry) + readUnlocked()).take(MAX_PHOTOS)
            // 목록에서 밀려난 사진은 파일도 지운다.
            readUnlocked().drop(MAX_PHOTOS - 1).forEach {
                runCatching { File(root, it.fileName).delete() }
            }
            runCatching { index.writeText(json.encodeToString(serializer, kept)) }
            entry.toDomain()
        }
    }

    suspend fun list(): List<ChildPhoto> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
            .sortedByDescending { it.atMillis }
            .map { it.toDomain() }
    }

    private fun Entry.toDomain(): ChildPhoto = ChildPhoto(
        id = id,
        capturedAt = capturedAt,
        imageUrl = File(root, fileName).takeIf { it.exists() }?.absolutePath,
        requestId = requestId,
    )

    private fun readUnlocked(): List<Entry> {
        if (!index.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, index.readText()) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_PHOTOS = 30

        val LABEL_FORMAT = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault())
    }
}
