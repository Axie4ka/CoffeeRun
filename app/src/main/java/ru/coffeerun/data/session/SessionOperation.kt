package ru.coffeerun.data.session

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Смена входа отменяет только операцию; версия также доходит до HTTP-запроса. */
internal suspend fun <T> SessionProvider.runInSession(session: AuthSession, action: suspend () -> T): T = coroutineScope {
    if (!session.isLoggedIn) throw SessionChangedException()
    val operation = currentCoroutineContext().job
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        sessions.first { it != session }
        operation.cancel(SessionChangedException())
    }
    try {
        withSession(session) { }
        val result = withContext(RequestSession.current.asContextElement(session)) { action() }
        currentCoroutineContext().ensureActive()
        withSession(session) { result }
    } catch (e: Exception) {
        withSession(session) { }
        throw e
    } finally {
        watcher.cancel()
    }
}
