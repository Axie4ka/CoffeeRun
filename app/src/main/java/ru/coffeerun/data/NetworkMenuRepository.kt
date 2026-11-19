package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.toModel
import ru.coffeerun.data.network.toOptionPrices
import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product
import ru.coffeerun.model.MenuSnapshot

/**
 * Меню с сервера (GET /menu). Позиции и цены приходят одним ответом и запоминаются, поэтому экран
 * позиции и «Избранное» не ходят в сеть повторно. Неудачный ответ не запоминается — «Повторить»
 * спросит сервер заново.
 *
 * Все ошибки сети отдаются как [IOException]: ViewModel ловят только его, а ответ 500 или битый JSON —
 * это тоже «не удалось загрузить», а не баг приложения.
 */
class NetworkMenuRepository(
    private val api: CoffeeRunApi,
    private val baseUrl: String,
    private val language: () -> String
) : MenuRepository {
    private val mutex = Mutex() // меню и позиция грузятся одновременно — запрос должен уйти один
    private val snapshots = MutableStateFlow<Map<String, MenuSnapshot>>(emptyMap())

    override fun observeSnapshot() = flow {
        val selectedLanguage = language()
        load(selectedLanguage)
        emitAll(snapshots.mapNotNull { it[selectedLanguage] }.distinctUntilChanged())
    }

    override suspend fun refresh(): MenuSnapshot = load(language(), refresh = true)

    override suspend fun getMenu(): List<Product> = load().products
    override suspend fun getProduct(id: String): Product? = load().products.firstOrNull { it.id == id }
    override suspend fun getOptionPrices(): OptionPrices = load().prices

    private suspend fun load(language: String = this.language(), refresh: Boolean = false): MenuSnapshot = mutex.withLock {
        val previous = snapshots.value[language]
        if (!refresh && previous != null) previous
        else fetch(language).also { snapshots.value = snapshots.value + (language to it) }
    }

    private suspend fun fetch(language: String): MenuSnapshot {
        val dto = try {
            api.getMenu(language)
        } catch (e: HttpException) {
            throw IOException("HTTP ${e.code()}", e)
        } catch (e: SerializationException) {
            throw IOException("Ответ сервера не по контракту", e)
        }
        if (dto.products.any { it.id.isBlank() || it.price < 0 } ||
            dto.products.distinctBy { it.id }.size != dto.products.size ||
            dto.sizes.any { it.price < 0 } || dto.milks.any { it.price < 0 } || dto.extras.any { it.price < 0 }) {
            throw IOException("Некорректные идентификаторы или цены в меню")
        }
        return MenuSnapshot(dto.products.mapNotNull { it.toModel(baseUrl) }, dto.toOptionPrices())
    }
}
