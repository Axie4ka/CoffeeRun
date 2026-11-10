package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.orderRequest
import ru.coffeerun.data.network.toModel
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.Shop

private const val HTTP_CONFLICT = 409
private const val HTTP_NOT_FOUND = 404

/** Общие заказы текущей сессии. Сетевые ответы применяются только к тому входу, который их начал. */
class NetworkOrderRepository(
    private val api: CoffeeRunApi,
    private val menu: MenuRepository,
    private val sessions: SessionProvider
) : OrderRepository {
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    override val orders: StateFlow<List<Order>> = _orders.asStateFlow()
    // Владелец кеша читается/меняется только внутри SessionProvider.withSession.
    private var owner: AuthSession? = null
    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    override suspend fun refresh(session: AuthSession) = sessions.runInSession(session) {
        val products = menu.getMenu().associateBy { it.id }
        val loaded = network { api.getOrders() }.mapNotNull { it.toModel(products) }
        commit(session) { _orders.value = loaded; _isLoaded.value = true }
    }

    override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession): PlaceOrderResult {
        require(lines.isNotEmpty()) { "Пустой заказ" }
        return sessions.runInSession(session) {
            val dto = try {
                network { api.placeOrder(orderRequest(lines, shop, pickupAtMillis)) }
            } catch (e: IOException) {
                return@runInSession if ((e.cause as? HttpException)?.code() == HTTP_CONFLICT) {
                    PlaceOrderResult.Unavailable
                } else PlaceOrderResult.NetworkError
            }
            val order = dto.toModel(lines.associate { it.product.id to it.product })
                ?: return@runInSession PlaceOrderResult.NetworkError
            commit(session) { _orders.update { listOf(order) + it } }
            PlaceOrderResult.Placed(order)
        }
    }

    override suspend fun cancel(number: Int, session: AuthSession): CancelOrderResult = sessions.runInSession(session) {
        val dto = try {
            network { api.cancelOrder(number) }
        } catch (e: IOException) {
            if ((e.cause as? HttpException)?.code() != HTTP_CONFLICT) return@runInSession CancelOrderResult.NetworkError
            return@runInSession CancelOrderResult.TooLate
        }
        val known = commit(session) { find(number)?.lines.orEmpty() }
        val order = dto.toModel(emptyMap(), knownItems = known) ?: return@runInSession CancelOrderResult.NetworkError
        commit(session) { _orders.update { list -> list.map { if (it.number == number) order else it } } }
        CancelOrderResult.Cancelled(order)
    }

    override suspend fun load(number: Int, session: AuthSession): Order? = sessions.runInSession(session) {
        val dto = try {
            network { api.getOrder(number) }
        } catch (e: IOException) {
            if ((e.cause as? HttpException)?.code() == HTTP_NOT_FOUND) {
                commit(session) { _orders.update { list -> list.filterNot { it.number == number } } }
                return@runInSession null
            }
            throw e
        }
        val products = menu.getMenu().associateBy { it.id }
        val order = dto.toModel(products) ?: throw IOException("Заказ не по контракту")
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
            owner = session
        }
    }

    private suspend fun <T> network(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpException) {
        throw IOException("HTTP " + e.code(), e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }
}
