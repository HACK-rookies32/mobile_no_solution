package com.hack.kidhack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.harmfulDataStore: DataStore<Preferences> by preferencesDataStore(name = "harmful_settings")

/**
 * 유해사이트 관리 화면의 **기기 쪽 설정**.
 *
 * 카테고리 on/off 자체는 서버 블랙리스트가 원본이다(자녀 기기에 전달돼야 하므로).
 * 하지만 「키워드 기반 차단」 마스터 토글은 서버에 대응하는 필드가 없어서
 * 예전에는 화면 상태로만 존재했다 — 화면을 나갔다 오면 그대로 초기값으로 돌아갔다.
 *
 * 여기에 자녀별로 남겨 두고, 마스터를 껐을 때 어떤 카테고리가 켜져 있었는지도 기억한다.
 * 다시 켰을 때 원래 조합을 복구하기 위해서다.
 */
class HarmfulSettingsStore(context: Context) {

    private val dataStore = context.harmfulDataStore

    suspend fun autoOn(childId: String): Boolean =
        dataStore.data.first()[autoKey(childId)] ?: true

    suspend fun setAutoOn(childId: String, value: Boolean) {
        dataStore.edit { it[autoKey(childId)] = value }
    }

    /** 마스터를 끌 때 켜져 있던 카테고리들. 다시 켤 때 이 조합으로 되돌린다. */
    suspend fun rememberedCategories(childId: String): List<String> =
        dataStore.data.first()[rememberedKey(childId)]
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    suspend fun rememberCategories(childId: String, categoryIds: List<String>) {
        dataStore.edit { it[rememberedKey(childId)] = categoryIds.joinToString(SEPARATOR) }
    }

    private fun autoKey(childId: String) = booleanPreferencesKey("auto_$childId")

    private fun rememberedKey(childId: String) = stringPreferencesKey("remembered_$childId")

    private companion object {
        const val SEPARATOR = ","
    }
}
