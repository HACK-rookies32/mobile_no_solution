package com.hack.kidhack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")
private val Context.childDataStore: DataStore<Preferences> by preferencesDataStore(name = "selected_child")
private val Context.childOnboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "child_onboarding")

private object Keys {
    val access = stringPreferencesKey("access_token")
    val refresh = stringPreferencesKey("refresh_token")
    val userId = stringPreferencesKey("user_id")
    val loginId = stringPreferencesKey("login_id")
    val name = stringPreferencesKey("name")
    val role = stringPreferencesKey("role")
    val phone = stringPreferencesKey("phone")
    val email = stringPreferencesKey("email")
    val nickname = stringPreferencesKey("nickname")
    val profileImageUrl = stringPreferencesKey("profile_image_url")
    val parentId = stringPreferencesKey("parent_id")
    val childId = stringPreferencesKey("child_id")
    val childOnboardingDone = booleanPreferencesKey("childOnboardingDone")
}

class SessionRepositoryImpl(
    context: Context,
) : SessionRepository {

    private val dataStore = context.sessionDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cached: Session? = null

    @Volatile
    private var loaded: Boolean = false

    override val session: Flow<Session?> = dataStore.data.map { it.toSession() }

    init {
        // 생성 시점에 디스크를 읽지 않는다. 예전에는 여기서 runBlocking 으로 읽는 바람에
        // Application.onCreate 가 메인 스레드를 붙잡아 첫 프레임이 늦어졌다.
        scope.launch {
            dataStore.data.collect { prefs ->
                cached = prefs.toSession()
                loaded = true
            }
        }
    }

    override suspend fun current(): Session? {
        val value = dataStore.data.first().toSession()
        cached = value
        loaded = true
        return value
    }

    override suspend fun save(session: Session) {
        cached = session
        loaded = true
        // [VULN-MOBILE-STORE-1] access/refresh 토큰을 DataStore(session.preferences_pb)에 평문 저장 —
        // 의도된 취약점(OWASP Mobile M9 Insecure Data Storage / MOB-FIN 자격증명 보호).
        // Android Keystore·EncryptedSharedPreferences·EncryptedFile 미사용. 보존: 고치지 말 것.
        // 명세: mobile/docs/vuln-spec-mobile.md
        dataStore.edit { prefs ->
            prefs[Keys.access] = session.accessToken
            prefs[Keys.refresh] = session.refreshToken
            prefs[Keys.userId] = session.user.id
            prefs[Keys.loginId] = session.user.loginId
            prefs[Keys.name] = session.user.name
            prefs[Keys.role] = session.user.role.name
            prefs[Keys.phone] = session.user.phone
            if (session.user.email != null) prefs[Keys.email] = session.user.email else prefs.remove(Keys.email)
            if (session.user.nickname != null) {
                prefs[Keys.nickname] = session.user.nickname
            } else {
                prefs.remove(Keys.nickname)
            }
            if (session.user.profileImageUrl != null) {
                prefs[Keys.profileImageUrl] = session.user.profileImageUrl
            } else {
                prefs.remove(Keys.profileImageUrl)
            }
            if (session.user.parentId != null) {
                prefs[Keys.parentId] = session.user.parentId.toString()
            } else {
                prefs.remove(Keys.parentId)
            }
        }
    }

    override suspend fun clear() {
        cached = null
        loaded = true
        dataStore.edit { it.clear() }
    }

    override fun accessToken(): String? = snapshot()?.accessToken

    override fun refreshToken(): String? = snapshot()?.refreshToken

    /**
     * 동기 토큰 조회.
     *
     * 캐시가 아직 안 채워졌으면 한 번만 블로킹해서 읽는다. 호출자는 OkHttp 인터셉터와
     * 소켓 연결 코루틴뿐이라 항상 백그라운드 스레드다(메인 스레드에서 부르면 안 된다).
     */
    private fun snapshot(): Session? {
        if (loaded) return cached
        return runBlocking { dataStore.data.first().toSession() }
            .also {
                cached = it
                loaded = true
            }
    }

    private fun Preferences.toSession(): Session? {
        val access = this[Keys.access] ?: return null
        val refresh = this[Keys.refresh] ?: return null
        val role = this[Keys.role]?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            user = User(
                id = this[Keys.userId].orEmpty(),
                loginId = this[Keys.loginId].orEmpty(),
                name = this[Keys.name].orEmpty(),
                role = role,
                nickname = this[Keys.nickname],
                phone = this[Keys.phone].orEmpty(),
                email = this[Keys.email],
                profileImageUrl = this[Keys.profileImageUrl],
                parentId = this[Keys.parentId]?.toLongOrNull(),
            ),
        )
    }
}

class SelectedChildRepositoryImpl(
    context: Context,
) : SelectedChildRepository {

    private val dataStore = context.childDataStore

    override val selectedChildId: Flow<String?> = dataStore.data.map { it[Keys.childId] }

    override suspend fun current(): String? = dataStore.data.first()[Keys.childId]

    override suspend fun select(childId: String?) {
        dataStore.edit { prefs ->
            if (childId == null) prefs.remove(Keys.childId) else prefs[Keys.childId] = childId
        }
    }
}

class SessionEventsImpl : SessionEvents {
    private val _logout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val logout: Flow<Unit> = _logout.asSharedFlow()
    override fun emitLogout() {
        _logout.tryEmit(Unit)
    }
}

class ChildOnboardingStoreImpl(
    context: Context,
) : ChildOnboardingStore {

    private val dataStore = context.childOnboardingDataStore

    override val done: Flow<Boolean> = dataStore.data.map { it[Keys.childOnboardingDone] ?: false }

    override suspend fun isDone(): Boolean = dataStore.data.first()[Keys.childOnboardingDone] ?: false

    override suspend fun markDone() {
        dataStore.edit { it[Keys.childOnboardingDone] = true }
    }
}
