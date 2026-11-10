package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession

/** Сетевые операции избранного и подтверждённый снимок текущей сессии. */
class NetworkFavoritesRepository(
    private val api: CoffeeRunApi,
    private val sessions: SessionProvider
) : FavoritesRepository {
    private val _confirmedIds = MutableStateFlow<Set<String>>(emptySet())
    override val confirmedIds: StateFlow<Set<String>> = _confirmedIds.asStateFlow()
    private var owner: AuthSession? = null

    override suspend fun refresh(session: AuthSession) = sessions.runInSession(session) {
        val loaded = network { api.getFavorites() }.toSet()
        currentCoroutineContext().ensureActive()
        commit(session) { _confirmedIds.value = loaded }
    }

    override suspend fun setFavorite(productId: String, selected: Boolean, session: AuthSession) =
        sessions.runInSession(session) {
            network { if (selected) api.addFavorite(productId) else api.removeFavorite(productId) }
            currentCoroutineContext().ensureActive()
            commit(session) {
                _confirmedIds.value = if (selected) _confirmedIds.value + productId else _confirmedIds.value - productId
            }
        }

    override fun clear(session: AuthSession) = commit(session) { _confirmedIds.value = emptySet() }

    private fun commit(session: AuthSession, action: () -> Unit) = sessions.withSession(session) {
        if (owner != session) {
            owner = session
            _confirmedIds.value = emptySet()
        }
        action()
    }

    private suspend fun <T> network(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpException) {
        throw IOException("HTTP ${e.code()}", e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }
}
