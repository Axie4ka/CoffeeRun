package ru.coffeerun.data

import java.io.IOException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.local.CoffeeRunDatabase
import ru.coffeerun.data.local.SavedFavorites
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession

/** Сетевые операции избранного и подтверждённый снимок текущей сессии. */
class NetworkFavoritesRepository(
    private val api: CoffeeRunApi,
    private val sessions: SessionProvider,
    private val db: CoffeeRunDatabase? = null
) : FavoritesRepository {
    private val _confirmedIds = MutableStateFlow<Set<String>>(emptySet())
    override val confirmedIds: StateFlow<Set<String>> = _confirmedIds.asStateFlow()
    private var owner: AuthSession? = null
    @Volatile override var hasSnapshot: Boolean = false
        private set

    override suspend fun refresh(session: AuthSession) = sessions.runInSession(session) {
        hydrate(session)
        val loaded = network { api.getFavorites() }.toSet()
        persist(session, loaded)
        currentCoroutineContext().ensureActive()
        commit(session) {
            _confirmedIds.value = loaded
            hasSnapshot = true
        }
    }

    override suspend fun setFavorite(productId: String, selected: Boolean, session: AuthSession) =
        sessions.runInSession(session) {
            hydrate(session)
            network { if (selected) api.addFavorite(productId) else api.removeFavorite(productId) }
            currentCoroutineContext().ensureActive()
            val updated = if (selected) _confirmedIds.value + productId else _confirmedIds.value - productId
            if (hasSnapshot) persist(session, updated)
            commit(session) {
                _confirmedIds.value = updated
            }
        }

    override fun clear(session: AuthSession) = commit(session) {
        _confirmedIds.value = emptySet()
        hasSnapshot = false
    }

    private fun commit(session: AuthSession, action: () -> Unit) = sessions.withSession(session) {
        if (owner != session) {
            owner = session
            _confirmedIds.value = emptySet()
            hasSnapshot = false
        }
        action()
    }

    private suspend fun hydrate(session: AuthSession) {
        if (hasSnapshot) return
        val saved = onDisk(session) { database, owner ->
            database.favoritesSnapshots().find(owner)?.let { CoffeeRunJson.decodeFromString<Set<String>>(it.payload) }
        } ?: return
        commit(session) {
            _confirmedIds.value = saved
            hasSnapshot = true
        }
    }

    private suspend fun persist(session: AuthSession, ids: Set<String>) {
        onDisk(session) { database, owner ->
            database.favoritesSnapshots().save(SavedFavorites(owner, CoffeeRunJson.encodeToString(ids)))
        }
    }

    private suspend fun <T> onDisk(session: AuthSession, action: suspend (CoffeeRunDatabase, String) -> T): T? {
        val database = db ?: return null
        val owner = session.localOwner ?: return null
        return try {
            database.withTransaction {
                sessions.withSession(session) { }
                if (database.localOwner().current() == owner) action(database, owner) else null
            }
        } catch (_: SQLiteException) {
            null
        } catch (_: SerializationException) {
            null
        }
    }

    private suspend fun <T> network(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpException) {
        throw IOException("HTTP ${e.code()}", e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }
}
