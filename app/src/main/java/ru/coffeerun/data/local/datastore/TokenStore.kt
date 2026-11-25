package ru.coffeerun.data.local.datastore

import java.util.UUID
import java.io.IOException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionCredentials
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.SessionStateStore

/** Имя файла DataStore с токеном; он же исключён из резервной копии (backup_rules.xml, data_extraction_rules.xml). */
const val AUTH_DATASTORE = "auth"

/**
 * Токен входа. Хранится в DataStore в личной папке приложения и переживает перезапуск —
 * поэтому входить заново не нужно. Пароль не хранится никогда.
 * [dataStore] передаётся снаружи: в приложении — файл [AUTH_DATASTORE], в тестах — временный.
 */
class TokenStore(
    private val dataStore: DataStore<Preferences>,
    private val ownerScope: (suspend (String?) -> Unit)? = null
) : SessionProvider {
    private val updates = Mutex()
    private val state = SessionStateStore()
    private var initialized = false // Читается и меняется только под updates.
    private val readFailed = MutableStateFlow(false)
    val sessionReadFailed = readFailed.asStateFlow()

    fun retrySessionRead() {
        readFailed.value = false
    }

    /** null — пользователь не вошёл. Новое значение приходит сразу после [save] или [clear]. */
    val token: Flow<String?> = flow {
        awaitInitialization()
        emitAll(state.tokens)
    }

    override val sessions: Flow<AuthSession> = flow {
        awaitInitialization()
        emitAll(state.sessions)
    }

    override suspend fun currentSession(): AuthSession {
        initialize()
        return state.currentSession()
    }

    override fun <T> withSession(session: AuthSession, action: () -> T): T = state.withSession(session, action)

    internal suspend fun credentials(): SessionCredentials {
        initialize()
        return state.credentials()
    }

    suspend fun save(token: String, accountId: String? = null) {
        updates.withLock {
            initializeLocked()
            persist(token, accountId) // Даже одинаковый токен означает новый успешный вход.
        }
    }

    suspend fun clear() {
        updates.withLock {
            initializeLocked()
            persist(null)
        }
    }

    /**
     * Стирает токен, только если сохранён всё ещё [token]. Сервер отклонил старый токен,
     * а пользователь за это время мог войти заново — новый токен трогать нельзя.
     * [session] защищает и повторный вход с той же строкой токена.
     */
    suspend fun clearIfStill(token: String, session: AuthSession? = null) {
        updates.withLock {
            initializeLocked()
            val current = state.credentials()
            if (current.token != token || (session != null && current.session != session)) return@withLock
            persist(null)
        }
    }

    /** Под updates: после начала записи обязательно завершаем и публикацию снимка в памяти. */
    private suspend fun persist(token: String?, accountId: String? = null) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            val owner = token?.let {
                accountId?.takeIf(String::isNotBlank)?.let { "account:$it" } ?: UUID.randomUUID().toString()
            }
            dataStore.edit { preferences ->
                if (token == null) {
                    preferences.remove(TOKEN)
                    preferences.remove(LOCAL_OWNER)
                } else {
                    preferences[TOKEN] = token
                    preferences[LOCAL_OWNER] = checkNotNull(owner)
                }
            }
            if (ownerScope != null) {
                // С этого момента на диске уже новый вход. Поздние ответы старого входа недействительны.
                initialized = false
                state.replaceToken(null)
                try {
                    ownerScope.invoke(owner)
                } catch (error: IOException) {
                    readFailed.value = true
                    throw error
                }
                initialized = true
            }
            state.replaceToken(token, owner)
        }
    }

    private suspend fun initialize() = updates.withLock { initializeLocked() }

    private suspend fun awaitInitialization() {
        do {
            readFailed.first { !it }
            val ready = updates.withLock {
                if (readFailed.value) return@withLock false
                try {
                    initializeLocked()
                    true
                } catch (_: IOException) {
                    readFailed.value = true
                    false
                }
            }
        } while (!ready)
    }

    /** TokenStore — единственный владелец записи токена; дальнейшие изменения публикует сам. */
    private suspend fun initializeLocked() {
        if (initialized) return
        val stored = dataStore.data.first()
        val token = stored[TOKEN]
        val owner = if (token == null) null else stored[LOCAL_OWNER] ?: migrateOwner()
        ownerScope?.invoke(owner)
        state.replaceToken(token, owner)
        initialized = true
        readFailed.value = false
    }

    private suspend fun migrateOwner(): String =
        checkNotNull(dataStore.edit { it[LOCAL_OWNER] = UUID.randomUUID().toString() }[LOCAL_OWNER])

    private companion object {
        val LOCAL_OWNER = stringPreferencesKey("local_owner")
        val TOKEN = stringPreferencesKey("token")
    }
}
