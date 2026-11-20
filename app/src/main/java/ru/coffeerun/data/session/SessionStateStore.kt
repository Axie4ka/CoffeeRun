package ru.coffeerun.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.random.Random

internal data class SessionCredentials(val session: AuthSession, val token: String?)

/** Общий снимок в памяти: токен и версия публикуются одним изменением. Диск принадлежит TokenStore. */
internal class SessionStateStore(initialToken: String? = null) : SessionProvider {
    private val guard = Any()
    // После перезапуска сохранённый стек не должен совпасть с новым счётчиком входов.
    private val state = MutableStateFlow(SessionCredentials(AuthSession(Random.nextLong(Long.MAX_VALUE / 2), initialToken != null), initialToken))

    val tokens = state.map { it.token }.distinctUntilChanged()
    override val sessions = state.map { it.session }.distinctUntilChanged()

    override suspend fun currentSession(): AuthSession = credentials().session

    fun credentials(): SessionCredentials = synchronized(guard) { state.value }

    fun replaceToken(token: String?, localOwner: String? = null) = synchronized(guard) {
        state.value = SessionCredentials(AuthSession(state.value.session.generation + 1, token != null, localOwner), token)
    }

    override fun <T> withSession(session: AuthSession, action: () -> T): T = synchronized(guard) {
        if (state.value.session != session) throw SessionChangedException()
        action()
    }
}
