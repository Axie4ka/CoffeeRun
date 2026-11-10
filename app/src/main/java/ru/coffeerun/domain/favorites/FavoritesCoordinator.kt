package ru.coffeerun.domain.favorites

import ru.coffeerun.data.FavoritesRepository

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession

/**
 * Общий оптимистический сценарий избранного. После входа загружает подтверждённые ID,
 * после выхода очищает — чужое избранное на устройстве не остаётся. Живёт в [scope] приложения,
 * а не экрана: следить за входом надо всё время.
 */
class FavoritesCoordinator(
    private val repository: FavoritesRepository,
    private val sessions: SessionProvider,
    private val scope: CoroutineScope
) : Favorites {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()
    private data class Intent(val version: Long, val selected: Boolean)
    // Все поля ниже, кроме самого Mutex, читаются и меняются только под withSession.
    private var owner: AuthSession? = null
    private val pending = mutableMapOf<String, Intent>()
    private var version = 0L
    private var requests = Mutex()

    init {
        scope.launch {
            sessions.sessions.collect { session ->
                try { commit(session) { } } catch (_: SessionChangedException) { return@collect }
                if (session.isLoggedIn) scope.launch {
                    // Не загрузилось — экран «Избранное» сам попросит повторить через refresh()
                    try { refresh(session) } catch (_: IOException) { }
                }
            }
        }
    }

    override suspend fun refresh() = refresh(sessions.currentSession())

    private suspend fun refresh(session: AuthSession) = sessions.runInSession(session) {
        val serial = commit(session) { requests }
        serial.withLock {
            commit(session) { }
            repository.refresh(session)
            commit(session) { publish() }
        }
    }

    override suspend fun toggle(productId: String): Boolean {
        val session = sessions.currentSession()
        if (!session.isLoggedIn) throw SessionChangedException()
        val (intent, serial) = commit(session) {
            val intent = Intent(++version, productId !in _ids.value)
            pending[productId] = intent
            publish()
            intent to requests
        }
        // Закрытие экрана отменяет ожидание, но не уже принятое намерение пользователя.
        return scope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                sessions.runInSession(session) {
                    serial.withLock {
                        if (!commit(session) { pending[productId] == intent }) return@withLock true
                        val success = try {
                            repository.setFavorite(productId, intent.selected, session)
                            true
                        } catch (_: IOException) { false }
                        commit(session) {
                            val latest = pending[productId] == intent
                            if (latest) pending.remove(productId)
                            publish()
                            success || !latest
                        }
                    }
                }
            }
        }.await().getOrThrow()
    }

    private fun <T> commit(session: AuthSession, action: () -> T): T = sessions.withSession(session) {
        if (owner != session) {
            owner = session
            repository.clear(session)
            pending.clear()
            requests = Mutex()
            publish()
        }
        action()
    }

    private fun publish() {
        _ids.value = pending.entries.fold(repository.confirmedIds.value) { ids, (id, intent) -> if (intent.selected) ids + id else ids - id }
    }

}
