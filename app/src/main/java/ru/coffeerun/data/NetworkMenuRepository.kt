package ru.coffeerun.data

import java.io.IOException
import android.database.sqlite.SQLiteException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.network.MenuDto
import ru.coffeerun.data.local.MenuSnapshotDao
import ru.coffeerun.data.local.MenuSnapshotEntity
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
    private val language: () -> String,
    private val disk: MenuSnapshotDao? = null
) : MenuRepository {
    private val mutex = Mutex() // меню и позиция грузятся одновременно — запрос должен уйти один
    private val snapshots = MutableStateFlow<Map<String, MenuSnapshot>>(emptyMap())

    override fun observeSnapshot() = flow {
        val selectedLanguage = language()
        val initial = load(selectedLanguage)
        emit(initial)
        coroutineScope {
            if (initial.isCached) launch {
                try { load(selectedLanguage, refresh = true, skipIfFresh = true) } catch (_: IOException) { }
            }
            emitAll(snapshots.mapNotNull { it[selectedLanguage] })
        }
    }.distinctUntilChanged()

    override suspend fun refresh(): MenuSnapshot = load(language(), refresh = true)

    override suspend fun getMenu(): List<Product> = load().products
    override suspend fun getProduct(id: String): Product? = load().products.firstOrNull { it.id == id }
    override suspend fun getOptionPrices(): OptionPrices = load().prices

    private suspend fun load(
        language: String = this.language(), refresh: Boolean = false, skipIfFresh: Boolean = false
    ): MenuSnapshot {
        if (!refresh) snapshots.value[language]?.let { return it }
        return mutex.withLock {
            val previous = snapshots.value[language]
            if (previous != null && (!refresh || (skipIfFresh && !previous.isCached))) return@withLock previous
            val value = (if (!refresh) readDisk(language) else null) ?: fetch(language)
            snapshots.value = snapshots.value + (language to value)
            value
        }
    }

    private suspend fun readDisk(language: String): MenuSnapshot? = try {
        disk?.find(language)?.let {
            CoffeeRunJson.decodeFromString<MenuDto>(it.payload).toSnapshot().copy(isCached = true)
        }
    } catch (_: SQLiteException) { null }
      catch (_: SerializationException) { null }
      catch (_: IOException) { null }

    private suspend fun fetch(language: String): MenuSnapshot {
        val dto = try {
            api.getMenu(language)
        } catch (e: HttpException) {
            throw IOException("HTTP ${e.code()}", e)
        } catch (e: SerializationException) {
            throw IOException("Ответ сервера не по контракту", e)
        }
        val snapshot = dto.toSnapshot()
        if (disk != null) {
            try {
                disk.save(MenuSnapshotEntity(language, CoffeeRunJson.encodeToString(dto), System.currentTimeMillis()))
            } catch (_: SQLiteException) {
                // Успешный ответ всё равно доступен в памяти.
            }
        }
        return snapshot
    }

    private fun MenuDto.toSnapshot(): MenuSnapshot {
        if (products.any { it.id.isBlank() || it.price < 0 } ||
            products.distinctBy { it.id }.size != products.size ||
            sizes.any { it.price < 0 } || milks.any { it.price < 0 } || extras.any { it.price < 0 }) {
            throw IOException("Некорректные идентификаторы или цены в меню")
        }
        return MenuSnapshot(products.mapNotNull { it.toModel(baseUrl) }, toOptionPrices())
    }
}
