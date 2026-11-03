package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.toModel
import ru.coffeerun.data.network.toOptionPrices
import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product

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
    private class Loaded(val language: String, val products: List<Product>, val prices: OptionPrices)

    private val mutex = Mutex() // меню и позиция грузятся одновременно — запрос должен уйти один
    // shortcut: меню грузится раз за запуск (и при смене языка); если цены надо обновлять на ходу — сбрасывать по таймеру
    private var cached: Loaded? = null

    override suspend fun getMenu(): List<Product> = load().products
    override suspend fun getProduct(id: String): Product? = load().products.firstOrNull { it.id == id }
    override suspend fun getOptionPrices(): OptionPrices = load().prices

    private suspend fun load(): Loaded = mutex.withLock {
        val language = language()
        cached?.takeIf { it.language == language } ?: fetch(language).also { cached = it }
    }

    private suspend fun fetch(language: String): Loaded {
        val dto = try {
            api.getMenu()
        } catch (e: HttpException) {
            throw IOException("HTTP ${e.code()}", e)
        } catch (e: SerializationException) {
            throw IOException("Ответ сервера не по контракту", e)
        }
        return Loaded(language, dto.products.mapNotNull { it.toModel(baseUrl) }, dto.toOptionPrices())
    }
}
