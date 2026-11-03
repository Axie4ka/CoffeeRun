package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi

/** Избранное — id товаров. Наружу только чтение через [ids], менять можно лишь [toggle]. */
interface FavoritesRepository {
    val ids: StateFlow<Set<String>>

    /** Загрузить с сервера заново. Ошибка — [IOException], как у меню. */
    suspend fun refresh()

    /**
     * Добавляет товар в избранное, а если он уже там — убирает. [ids] меняется сразу;
     * false — сервер не принял изменение, и оно откачено.
     */
    suspend fun toggle(productId: String): Boolean
}

/**
 * Избранное на сервере (/me/favorites). Следит за [isLoggedIn]: после входа загружает избранное,
 * после выхода очищает — чужое избранное на устройстве не остаётся. Живёт в [scope] приложения,
 * а не экрана: следить за входом надо всё время.
 */
class NetworkFavoritesRepository(
    private val api: CoffeeRunApi,
    isLoggedIn: Flow<Boolean>,
    scope: CoroutineScope
) : FavoritesRepository {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()
    private val requests = Mutex() // быстрые нажатия уходят на сервер по порядку

    init {
        scope.launch {
            isLoggedIn.distinctUntilChanged().collect { loggedIn ->
                if (loggedIn) {
                    // Не загрузилось — экран «Избранное» сам попросит повторить через refresh()
                    try { refresh() } catch (e: IOException) { }
                } else {
                    _ids.value = emptySet()
                }
            }
        }
    }

    override suspend fun refresh() {
        _ids.value = network { api.getFavorites() }.toSet()
    }

    override suspend fun toggle(productId: String): Boolean {
        val adding = productId !in _ids.value
        // Сразу на экран, а не через полсекунды после ответа сервера
        _ids.update { if (adding) it + productId else it - productId }
        return try {
            requests.withLock { network { if (adding) api.addFavorite(productId) else api.removeFavorite(productId) } }
            true
        } catch (e: IOException) {
            _ids.update { if (adding) it - productId else it + productId }
            false
        }
    }

    /** Ответ с ошибкой и ответ не по контракту — тоже «не получилось», как нет сети. */
    private suspend fun <T> network(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpException) {
        throw IOException("HTTP ${e.code()}", e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }
}

/** Избранное в памяти — для UI-тестов и превью. */
class InMemoryFavoritesRepository : FavoritesRepository {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun toggle(productId: String): Boolean {
        _ids.update { if (productId in it) it - productId else it + productId }
        return true
    }
}
