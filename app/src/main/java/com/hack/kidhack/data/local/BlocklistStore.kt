package com.hack.kidhack.data.local

import android.content.Context
import com.hack.kidhack.core.common.HarmfulCategories
import com.hack.kidhack.core.common.KidhackJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * 목업 빌드(`USE_MOCK`)의 블랙/화이트리스트 보관소.
 *
 * 예전 `FakeBlocklistRepository` 는 목록을 메모리에만 들고 있어서 앱을 다시 켜면
 * 부모가 바꾼 차단 설정이 통째로 초기값으로 돌아갔다. 목업이라도 「설정이 저장된다」는
 * 동작은 실서버와 같아야 화면을 검증할 수 있으므로 파일로 남긴다.
 */
class BlocklistStore(context: Context) {

    @Serializable
    private data class Snapshot(
        val blacklist: List<String> = DEFAULT_BLACK,
        val whitelist: List<String> = DEFAULT_WHITE,
    )

    private val file = File(context.filesDir, "blocklist_mock.json")
    private val mutex = Mutex()
    private val json = KidhackJson.instance

    suspend fun blacklist(): List<String> = read().blacklist

    suspend fun whitelist(): List<String> = read().whitelist

    suspend fun add(listType: String, keyword: String) = update { snapshot ->
        val kw = keyword.trim()
        if (kw.isBlank()) return@update snapshot
        if (listType.equals("WHITE", ignoreCase = true)) {
            if (snapshot.whitelist.any { it.equals(kw, true) }) snapshot
            else snapshot.copy(whitelist = snapshot.whitelist + kw)
        } else {
            if (snapshot.blacklist.any { it.equals(kw, true) }) snapshot
            else snapshot.copy(blacklist = snapshot.blacklist + kw)
        }
    }

    /** itemId 는 키워드 그 자체다([com.hack.kidhack.data.repository.real.RealBlocklistRepository] 와 동일한 규칙). */
    suspend fun remove(itemId: String) = update { snapshot ->
        snapshot.copy(
            blacklist = snapshot.blacklist.filterNot { it.equals(itemId, true) },
            whitelist = snapshot.whitelist.filterNot { it.equals(itemId, true) },
        )
    }

    private suspend fun read(): Snapshot = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
    }

    private suspend fun update(block: (Snapshot) -> Snapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = block(readUnlocked())
            runCatching { file.writeText(json.encodeToString(Snapshot.serializer(), next)) }
        }
        Unit
    }

    private fun readUnlocked(): Snapshot {
        if (!file.exists()) return Snapshot()
        return runCatching { json.decodeFromString(Snapshot.serializer(), file.readText()) }
            .getOrDefault(Snapshot())
    }

    private companion object {
        // 디자인 기본값은 4개 카테고리 전부 ON 이다. 목업에서도 같은 상태로 시작해야
        // 토글과 실제 차단 동작을 그대로 확인할 수 있다.
        val DEFAULT_BLACK = HarmfulCategories.allKeywords + "욕설"
        val DEFAULT_WHITE = listOf("https://edu.example.com", "https://library.org")
    }
}
