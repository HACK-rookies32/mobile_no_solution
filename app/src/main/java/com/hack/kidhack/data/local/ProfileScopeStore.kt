package com.hack.kidhack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.profileScopeDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "profile_scope")

/**
 * 기기에 남아 있는 로컬 기록(채팅·알림·사진)이 **누구 것인지** 기억한다.
 *
 * 예전에는 로그아웃할 때마다 전부 지웠다. 그래서 같은 자녀가 다시 로그인해도
 * 최근 알림과 대화가 초기화됐다. 실제로 지워야 하는 경우는 **다른 계정이 들어올 때**뿐이다.
 */
class ProfileScopeStore(context: Context) {

    private val dataStore = context.profileScopeDataStore

    /**
     * 현재 소유자를 [userId] 로 바꾼다.
     *
     * @return 이전 소유자가 있고 그 사람이 아니면 true — 이때만 로컬 기록을 비워야 한다.
     * 첫 로그인(이전 소유자 없음)이나 같은 계정 재로그인은 false.
     */
    suspend fun switchTo(userId: String): Boolean {
        if (userId.isBlank()) return false
        val previous = dataStore.data.first()[OWNER]
        if (previous == userId) return false
        dataStore.edit { it[OWNER] = userId }
        return previous != null
    }

    private companion object {
        val OWNER = stringPreferencesKey("owner_user_id")
    }
}
