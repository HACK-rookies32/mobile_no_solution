package com.hack.kidhack.data.local

import android.content.Context
import android.graphics.Bitmap
import com.hack.kidhack.domain.model.Screenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 기기에 남긴 화면 캡처 사본.
 *
 * <p>서버의 캡처 목록 조회(`GET /children/{id}/screenshots`)는 <b>부모(PARENT) 전용</b>이라,
 * 자녀 토큰으로 부르면 403 이다(명세 4.6). 그래서 자녀 화면의 「캡처 내역」은 서버가 아니라
 * <b>이 기기에 실제로 찍혀 남은 파일</b>을 보여 준다. 자녀에게는 "내 폰에서 찍힌 내 화면"이
 * 곧 내역이므로, 서버를 거치지 않는 편이 관점에도 맞는다.
 *
 * <p>업로드용 임시 파일은 [android.content.Context.getCacheDir] 에 잠깐 썼다 지웠는데, 그러면
 * 남는 것이 없어 목록이 늘 비었다. 여기서는 [android.content.Context.getFilesDir] 아래
 * 전용 폴더에 <b>지우지 않고</b> 남긴다. 촬영 시각·트리거는 파일 이름에 담아, 목록을
 * 만들 때 별도 DB 없이 되읽는다.
 */
class ChildCaptureStore(context: Context) {

    private val dir: File = File(context.filesDir, "child_captures").apply { mkdirs() }

    /**
     * 캡처 한 장을 기기에 남긴다.
     *
     * <p>파일 이름은 `<촬영시각millis>__<TRIGGER>.jpg`. 목록을 만들 때 이 이름만 보고
     * 시각과 트리거를 되읽으므로 별도 기록이 필요 없다.
     *
     * @return 남긴 파일. 이 파일을 그대로 서버 업로드에도 쓰면 사본을 두 번 쓰지 않는다.
     */
    suspend fun save(bitmap: Bitmap, capturedAtMillis: Long, trigger: String): File =
        withContext(Dispatchers.IO) {
            prune()
            val safeTrigger = trigger.uppercase().filter { it.isLetter() }.ifBlank { "MANUAL" }
            val file = File(dir, "${capturedAtMillis}__$safeTrigger.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            file
        }

    /** 최신 촬영 순 목록. 이름을 못 읽는 파일은 건너뛴다. */
    suspend fun list(): List<Screenshot> = withContext(Dispatchers.IO) {
        (dir.listFiles()?.toList() ?: emptyList())
            .mapNotNull { file -> file.toScreenshot() }
            .sortedByDescending { it.capturedAtMillis }
            .map { it.screenshot }
    }

    private data class Parsed(val capturedAtMillis: Long, val screenshot: Screenshot)

    private fun File.toScreenshot(): Parsed? {
        if (!isFile || extension.lowercase() != "jpg") return null
        val base = nameWithoutExtension
        val millis = base.substringBefore("__").toLongOrNull() ?: return null
        val trigger = base.substringAfter("__", "MANUAL").ifBlank { "MANUAL" }
        return Parsed(
            capturedAtMillis = millis,
            screenshot = Screenshot(
                id = name,
                // 화면 표기는 뷰가 한다(ChildFormat). 여기서는 원본 millis 를 ISO 로만 넘긴다.
                capturedAt = java.time.Instant.ofEpochMilli(millis).toString(),
                trigger = trigger,
                imageUrl = absolutePath,
            ),
        )
    }

    /** 오래된 것부터 지워 최근 [MAX_KEEP] 장만 남긴다. 저장 공간이 무한정 늘지 않게 한다. */
    private fun prune() {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.name } ?: return
        files.drop(MAX_KEEP - 1).forEach { runCatching { it.delete() } }
    }

    private companion object {
        const val JPEG_QUALITY = 80
        const val MAX_KEEP = 50
    }
}
