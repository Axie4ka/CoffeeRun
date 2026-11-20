package ru.coffeerun.data.session

import kotlinx.coroutines.flow.Flow

interface SessionProvider {
    val sessions: Flow<AuthSession>
    suspend fun currentSession(): AuthSession

    /** Проверка версии и короткое изменение локальных данных атомарны относительно смены сессии. */
    fun <T> withSession(session: AuthSession, action: () -> T): T
}
