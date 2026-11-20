package ru.coffeerun.data

import java.io.IOException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.local.CoffeeRunDatabase
import ru.coffeerun.data.local.OrderHistorySync
import ru.coffeerun.data.local.SavedOrder
import ru.coffeerun.data.network.orderRequest
import ru.coffeerun.data.network.toModel
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop

private const val HTTP_CONFLICT = 409
private const val HTTP_NOT_FOUND = 404

/** Общие заказы текущей сессии. Сетевые ответы применяются только к тому входу, который их начал. */
class NetworkOrderRepository(
    private val api: CoffeeRunApi,
    private val menu: MenuRepository,
    private val sessions: SessionProvider,
    private val db: CoffeeRunDatabase? = null
) : OrderRepository {
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    override val orders: StateFlow<List<Order>> = _orders.asStateFlow()
    // Владелец кеша читается/меняется только внутри SessionProvider.withSession.
    private var owner: AuthSession? = null
    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
    private var hydrated = false

    override suspend fun refresh(session: AuthSession) = sessions.runInSession(session) {
        hydrate(session)
        val orders = network { api.getOrders() }
        val products = menuNames()
        val loaded = orders.mapNotNull { it.toModel(products) }
        if (loaded.size != orders.size || loaded.distinctBy { it.number }.size != loaded.size) {
            // Сломанный или частичный ответ не должен заменять подтверждённую историю.
            if (_orders.value.isEmpty()) commit(session) { _orders.value = loaded.distinctBy { it.number } }
            throw IOException("Получена неполная история заказов")
        }
        onDisk(session) { database, owner ->
            val dao = database.orderHistory()
            dao.clear(owner)
            dao.save(loaded.map { it.saved(owner) })
            dao.saveSync(OrderHistorySync(owner, System.currentTimeMillis()))
        }
        commit(session) { _orders.value = loaded; _isLoaded.value = true }
    }

    override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession): PlaceOrderResult {
        require(lines.isNotEmpty()) { "Пустой заказ" }
        return sessions.runInSession(session) {
            hydrate(session)
            val dto = try {
                network { api.placeOrder(orderRequest(lines, shop, pickupAtMillis)) }
            } catch (e: IOException) {
                return@runInSession when ((e.cause as? HttpException)?.code()) {
                    HTTP_CONFLICT -> PlaceOrderResult.Unavailable
                    400, 401 -> PlaceOrderResult.NetworkError
                    else -> PlaceOrderResult.OutcomeUnknown
                }
            }
            val order = dto.toModel(lines.associate { it.product.id to it.product })
                ?: return@runInSession PlaceOrderResult.OutcomeUnknown
            saveOrder(session, order)
            commit(session) { _orders.update { listOf(order) + it.filterNot { item -> item.number == order.number } } }
            PlaceOrderResult.Placed(order)
        }
    }

    override suspend fun cancel(number: Int, session: AuthSession): CancelOrderResult = sessions.runInSession(session) {
        hydrate(session)
        val dto = try {
            network { api.cancelOrder(number) }
        } catch (e: IOException) {
            if ((e.cause as? HttpException)?.code() != HTTP_CONFLICT) return@runInSession CancelOrderResult.NetworkError
            return@runInSession CancelOrderResult.TooLate
        }
        val known = commit(session) { find(number)?.lines.orEmpty() }
        val order = dto.toModel(emptyMap(), knownItems = known) ?: return@runInSession CancelOrderResult.NetworkError
        saveOrder(session, order)
        commit(session) { _orders.update { list -> list.map { if (it.number == number) order else it } } }
        CancelOrderResult.Cancelled(order)
    }

    override suspend fun load(number: Int, session: AuthSession): Order? = sessions.runInSession(session) {
        hydrate(session)
        val dto = try {
            network { api.getOrder(number) }
        } catch (e: IOException) {
            if ((e.cause as? HttpException)?.code() == HTTP_NOT_FOUND) {
                onDisk(session) { database, owner -> database.orderHistory().remove(owner, number) }
                commit(session) { _orders.update { list -> list.filterNot { it.number == number } } }
                return@runInSession null
            }
            throw e
        }
        val products = menuNames()
        val order = dto.toModel(products) ?: throw IOException("Заказ не по контракту")
        if (order.number != number) throw IOException("Не совпадает номер заказа")
        saveOrder(session, order)
        commit(session) {
            _orders.update { list ->
                if (list.any { it.number == number }) list.map { if (it.number == number) order else it }
                else (list + order).sortedByDescending { it.placedAtMillis }
            }
        }
        order
    }

    override fun find(number: Int): Order? = _orders.value.find { it.number == number }

    override fun clear(session: AuthSession) = sessions.withSession(session) {
        selectOwner(session)
        _isLoaded.value = false
        _orders.value = emptyList()
        hydrated = false
    }

    private suspend fun <T> commit(session: AuthSession, action: () -> T): T {
        currentCoroutineContext().ensureActive()
        return sessions.withSession(session) {
            selectOwner(session)
            action()
        }
    }

    private fun selectOwner(session: AuthSession) {
        if (owner != session) {
            _isLoaded.value = false
            _orders.value = emptyList()
            hydrated = false
            owner = session
        }
    }

    private suspend fun hydrate(session: AuthSession) {
        if (commit(session) { hydrated }) return
        val cached = onDisk(session) { database, owner ->
            val dao = database.orderHistory()
            dao.orders(owner).map { CoffeeRunJson.decodeFromString<Order>(it.payload) }
                .sortedByDescending { it.placedAtMillis } to (dao.sync(owner) != null)
        }
        commit(session) {
            hydrated = true
            if (cached != null) {
                _orders.value = cached.first
                _isLoaded.value = cached.second
            }
        }
    }

    private suspend fun saveOrder(session: AuthSession, order: Order) {
        onDisk(session) { database, owner -> database.orderHistory().save(listOf(order.saved(owner))) }
    }

    private fun Order.saved(owner: String) = SavedOrder(owner, number, CoffeeRunJson.encodeToString(this))

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

    private suspend fun menuNames(): Map<String, Product> = try {
        menu.getMenu().associateBy { it.id }
    } catch (_: IOException) {
        emptyMap()
    }

    private suspend fun <T> network(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpException) {
        throw IOException("HTTP " + e.code(), e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }
}
