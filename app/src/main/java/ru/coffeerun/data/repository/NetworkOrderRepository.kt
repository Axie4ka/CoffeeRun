package ru.coffeerun.data.repository

import ru.coffeerun.data.CancelOrderResult
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.PlaceOrderResult

import android.database.sqlite.SQLiteException
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.local.room.withPersonalCache
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.OrderHistorySync
import ru.coffeerun.data.local.room.saved
import ru.coffeerun.data.network.mapper.orderRequest
import ru.coffeerun.data.network.mapper.toModel
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
    private val db: CoffeeRunDatabase? = null,
    scope: CoroutineScope? = null
) : OrderRepository {
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    override val orders: StateFlow<List<Order>> = _orders.asStateFlow()
    // Владелец кеша читается/меняется только внутри SessionProvider.withSession.
    private var owner: AuthSession? = null
    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
    private var hydrated = false
    private var revision = 0L
    private var memoryFallback = false

    init {
        if (db != null && scope != null) scope.launch {
            sessions.sessions.collectLatest { session ->
                if (session.localOwner == null) return@collectLatest
                db.invalidationTracker.createFlow("saved_order", "order_history_sync")
                    .retryWhen { cause, attempt ->
                        if (cause !is SQLiteException || attempt >= 2) false
                        else {
                            delay(1_000)
                            true
                        }
                    }
                    .catch { if (it !is SQLiteException) throw it }
                    .collect {
                        try {
                            val before = sessions.withSession(session) { revision }
                            val cached = readHistory(session) ?: return@collect
                            sessions.withSession(session) {
                                if (revision == before && !memoryFallback) {
                                    selectOwner(session)
                                    publishHistory(cached.first, cached.second)
                                }
                            }
                        } catch (_: SessionChangedException) {
                            // The next session has its own observation.
                        }
                    }
            }
        }
    }

    override suspend fun refresh(session: AuthSession) = sessions.runInSession(session) {
        hydrate(session)
        val orders = network { api.getOrders() }
        val confirmedAt = System.currentTimeMillis()
        val products = menuNames()
        val loaded = orders.mapNotNull { it.toModel(products)?.copy(confirmedAtMillis = confirmedAt) }
        if (loaded.size != orders.size || loaded.distinctBy { it.number }.size != loaded.size) {
            // Сломанный или частичный ответ не должен заменять подтверждённую историю.
            if (!_isLoaded.value && _orders.value.isEmpty()) commit(session) { _orders.value = loaded.distinctBy { it.number } }
            throw IOException("Получена неполная история заказов")
        }
        val saved = db.withPersonalCache(sessions, session) { database, owner ->
            val dao = database.orderHistory()
            dao.clear(owner)
            dao.save(loaded.map { it.saved(owner) })
            dao.saveSync(OrderHistorySync(owner, confirmedAt))
        }
        commit(session) {
            memoryFallback = saved == null
            publishHistory(loaded, complete = true)
        }
    }

    override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession, attemptId: String): PlaceOrderResult {
        require(lines.isNotEmpty()) { "Пустой заказ" }
        return sessions.runInSession(session) {
            hydrate(session)
            val dto = try {
                network { api.placeOrder(attemptId, orderRequest(lines, shop, pickupAtMillis)) }
            } catch (e: IOException) {
                return@runInSession when ((e.cause as? HttpException)?.code()) {
                    HTTP_CONFLICT -> if ((e.cause as HttpException).isAttemptMismatch()) PlaceOrderResult.OutcomeUnknown
                        else PlaceOrderResult.Unavailable
                    410 -> PlaceOrderResult.Unavailable
                    400, 401, 404, 405 -> PlaceOrderResult.NetworkError
                    else -> PlaceOrderResult.OutcomeUnknown
                }
            }
            val order = dto.toModel(lines.associate { it.product.id to it.product })?.copy(confirmedAtMillis = System.currentTimeMillis())
                ?: return@runInSession PlaceOrderResult.OutcomeUnknown
            if (db == null) commit(session) { _orders.update { listOf(order) + it.filterNot { item -> item.number == order.number } } }
            PlaceOrderResult.Placed(order)
        }
    }

    override suspend fun resolveAttempt(attemptId: String, session: AuthSession): Order? = sessions.runInSession(session) {
        val dto = try {
            network { api.resolveAttempt(attemptId) }
        } catch (error: IOException) {
            if ((error.cause as? HttpException)?.code() == 410) return@runInSession null
            throw error
        }
        val confirmedAt = System.currentTimeMillis()
        val order = dto.toModel(menuNames())?.copy(confirmedAtMillis = confirmedAt)
            ?: throw IOException("Invalid checkout resolution")
        if (db == null) commit(session) { _orders.update { list -> (list.filterNot { it.number == order.number } + order).sortedByDescending { it.placedAtMillis } } }
        order
    }

    override suspend fun publishConfirmed(order: Order, session: AuthSession) = commit(session) {
        val cachedAt = find(order.number)?.confirmedAtMillis
        if (cachedAt == null || cachedAt < (order.confirmedAtMillis ?: Long.MIN_VALUE)) updateOrder(order)
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
        val order = dto.toModel(emptyMap(), knownItems = known)?.copy(confirmedAtMillis = System.currentTimeMillis())
            ?: return@runInSession CancelOrderResult.NetworkError
        val saved = saveOrder(session, order)
        commit(session) {
            if (!saved) memoryFallback = true
            updateOrder(order)
        }
        CancelOrderResult.Cancelled(order)
    }

    override suspend fun load(number: Int, session: AuthSession): Order? = sessions.runInSession(session) {
        hydrate(session)
        val dto = try {
            network { api.getOrder(number) }
        } catch (e: IOException) {
            if ((e.cause as? HttpException)?.code() == HTTP_NOT_FOUND) {
                val saved = db.withPersonalCache(sessions, session) { database, owner -> database.orderHistory().remove(owner, number) }
                commit(session) {
                    if (saved == null) memoryFallback = true
                    _orders.update { list -> list.filterNot { it.number == number } }
                }
                return@runInSession null
            }
            throw e
        }
        val confirmedAt = System.currentTimeMillis()
        val products = menuNames()
        val order = dto.toModel(products)?.copy(confirmedAtMillis = confirmedAt) ?: throw IOException("Заказ не по контракту")
        if (order.number != number) throw IOException("Не совпадает номер заказа")
        val saved = saveOrder(session, order)
        commit(session) {
            if (!saved) memoryFallback = true
            updateOrder(order)
        }
        order
    }

    override fun find(number: Int): Order? = _orders.value.find { it.number == number }

    override fun clear(session: AuthSession) = sessions.withSession(session) {
        selectOwner(session)
        _isLoaded.value = false
        _orders.value = emptyList()
        hydrated = false
        memoryFallback = false
        revision++
        Unit
    }

    private suspend fun <T> commit(session: AuthSession, action: () -> T): T {
        currentCoroutineContext().ensureActive()
        return sessions.withSession(session) {
            selectOwner(session)
            revision++
            action()
        }
    }

    private fun selectOwner(session: AuthSession) {
        if (owner != session) {
            _isLoaded.value = false
            _orders.value = emptyList()
            hydrated = false
            memoryFallback = false
            owner = session
        }
    }

    private suspend fun hydrate(session: AuthSession) {
        if (sessions.withSession(session) { owner == session && hydrated }) return
        val cached = readHistory(session)
        commit(session) {
            if (!hydrated && cached != null) publishHistory(cached.first, cached.second)
        }
    }

    private suspend fun readHistory(session: AuthSession) =
        db.withPersonalCache(sessions, session) { database, owner ->
            val dao = database.orderHistory()
            dao.orders(owner).map {
                CoffeeRunJson.decodeFromString<Order>(it.payload).copy(confirmedAtMillis = it.confirmedAtMillis)
            }.sortedByDescending { it.placedAtMillis } to (dao.sync(owner) != null)
        }

    private fun publishHistory(orders: List<Order>, complete: Boolean) {
        hydrated = true
        _orders.value = orders
        _isLoaded.value = complete
    }

    private fun updateOrder(order: Order) {
        _orders.update { list ->
            (list.filterNot { it.number == order.number } + order).sortedByDescending { it.placedAtMillis }
        }
    }

    private suspend fun saveOrder(session: AuthSession, order: Order): Boolean =
        db.withPersonalCache(sessions, session) { database, owner ->
            database.orderHistory().save(listOf(order.saved(owner)))
        } != null

    private suspend fun menuNames(): Map<String, Product> = try {
        menu.getMenu().associateBy { it.id }
    } catch (_: IOException) {
        emptyMap()
    }

    private fun HttpException.isAttemptMismatch(): Boolean = try {
        val body = response()?.errorBody()?.string()
        val code = body?.let { CoffeeRunJson.parseToJsonElement(it) as? JsonObject }?.get("code")
        code == JsonPrimitive("ATTEMPT_PAYLOAD_MISMATCH")
    } catch (_: SerializationException) {
        false
    } catch (_: IOException) {
        true
    }

    private suspend fun <T> network(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpException) {
        throw IOException("HTTP " + e.code(), e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }
}
