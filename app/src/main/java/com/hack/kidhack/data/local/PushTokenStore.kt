package com.hack.kidhack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.pushTokenDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "push_token")

/**
 * 마지막으로 받은 FCM 토큰을 기기에 남긴다.
 *
 * `onNewToken` 은 로그인 전에 올 수도 있으므로, 토큰을 들고 있다가 로그인 시 서버에 붙인다.
 * 토큰은 계정이 아니라 기기에 속하므로 계정 전환·로그아웃에도 지우지 않는다.
 */
class PushTokenStore(context: Context) {

    private val dataStore = context.pushTokenDataStore

    suspend fun current(): String? = dataStore.data.first()[TOKEN]

    suspend fun save(token: String) {
        dataStore.edit { it[TOKEN] = token }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("fcm_token")
    }
}
