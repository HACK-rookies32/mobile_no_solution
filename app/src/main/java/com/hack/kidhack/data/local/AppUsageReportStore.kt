package com.hack.kidhack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hack.kidhack.core.common.KidhackJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

private val Context.appUsageReportDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "app_usage_report")

/**
 * 마지막으로 서버에 보고한 앱별 **누적** 사용 시간.
 *
 * `POST /api/monitoring/usage` 는 보고를 행으로 계속 쌓고, 조회 시 `durationSec` 을 **합산**한다.
 * 그래서 「오늘 지금까지 30분」 같은 누적값을 주기적으로 보내면 30+45+60… 으로 부풀려진다.
 * 실제로 에뮬레이터를 10시간도 안 켰는데 스크린타임이 13시간으로 뜬 원인이다.
 *
 * 여기에 직전 보고값을 남겨 두고 **증가분만** 보낸다. OS 의 사용량 통계는 자정에 리셋되므로
 * 날짜가 바뀌면 이전 누적은 버린다(그러지 않으면 새 날 첫 보고가 음수가 된다).
 */
class AppUsageReportStore(context: Context) {

    private val dataStore = context.appUsageReportDataStore

    @Serializable
    private data class Snapshot(
        val date: String = "",
        val totals: Map<String, Int> = emptyMap(),
    )

    /** [date] 기준 직전 보고 누적값. 날짜가 다르면 빈 값(= 전부 증가분). */
    suspend fun lastTotals(date: String): Map<String, Int> {
        val raw = dataStore.data.first()[SNAPSHOT] ?: return emptyMap()
        val snapshot = runCatching {
            KidhackJson.instance.decodeFromString(Snapshot.serializer(), raw)
        }.getOrNull() ?: return emptyMap()
        return if (snapshot.date == date) snapshot.totals else emptyMap()
    }

    /** 보고에 성공한 뒤에만 부른다. 실패한 값을 저장하면 그만큼이 영영 누락된다. */
    suspend fun save(date: String, totals: Map<String, Int>) {
        val raw = KidhackJson.instance.encodeToString(
            Snapshot.serializer(),
            Snapshot(date = date, totals = totals),
        )
        dataStore.edit { it[SNAPSHOT] = raw }
    }

    private companion object {
        val SNAPSHOT = stringPreferencesKey("last_reported")
    }
}
