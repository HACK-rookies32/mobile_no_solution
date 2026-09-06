package com.hack.kidhack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.parentWatchDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "parent_watch")

/**
 * 보호자 앱이 자녀 상태를 직접 판정할 때 쓰는 **이전 상태** 기억.
 *
 * 판정은 폴링이라 같은 상황을 몇 번이고 다시 본다. 상태가 바뀐 순간에만 알리려면
 * 「직전에 어땠는지」를 들고 있어야 한다. 이게 없으면 안전 구역 밖에 있는 동안
 * 15분마다 이탈 알림이 울린다.
 */
class ParentWatchStore(context: Context) {

    private val dataStore = context.parentWatchDataStore

    /** 직전 판정에서 안전 구역 **밖**이었는지. 기본값은 안(false) 이다. */
    suspend fun wasOutside(childId: String): Boolean =
        dataStore.data.first()[outsideKey(childId)] ?: false

    suspend fun setOutside(childId: String, outside: Boolean) {
        dataStore.edit { it[outsideKey(childId)] = outside }
    }

    /** 사용량 한도 알림을 마지막으로 띄운 날(yyyyMMdd). 하루 한 번만 울리게 한다. */
    suspend fun usageAlertedOn(childId: String): String? =
        dataStore.data.first()[usageKey(childId)]

    suspend fun setUsageAlertedOn(childId: String, day: String) {
        dataStore.edit { it[usageKey(childId)] = day }
    }

    /** 위치 갱신이 끊긴 상태로 이미 알렸는지. 다시 들어오면 해제한다. */
    suspend fun wasStale(childId: String): Boolean =
        dataStore.data.first()[staleKey(childId)] ?: false

    suspend fun setStale(childId: String, stale: Boolean) {
        dataStore.edit { it[staleKey(childId)] = stale }
    }

    private fun outsideKey(childId: String) = booleanPreferencesKey("outside_$childId")

    private fun usageKey(childId: String) = stringPreferencesKey("usage_$childId")

    private fun staleKey(childId: String) = booleanPreferencesKey("stale_$childId")
}
