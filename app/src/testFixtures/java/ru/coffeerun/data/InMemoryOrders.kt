package ru.coffeerun.data

import ru.coffeerun.domain.orders.Orders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkOptions
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.Shop
import ru.coffeerun.model.toOrderItem

private const val FIRST_ORDER_NUMBER = 1042
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Заказы в памяти, стартуют с демо-истории. */
class InMemoryOrders(private val now: () -> Long = System::currentTimeMillis) : Orders {
    private val _orders = MutableStateFlow(demoHistory(now()))
    override val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    override val isLoaded: StateFlow<Boolean> = MutableStateFlow(true)

    private var nextOrderNumber = FIRST_ORDER_NUMBER

    private val attempts = mutableMapOf<Pair<AuthSession, String>, Order?>()

    override suspend fun resolveAttempt(attemptId: String, session: AuthSession): Order? =
        attempts.getOrPut(session to attemptId) { null }

    override suspend fun refresh() = Unit

    override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession, attemptId: String, beforeSend: suspend () -> Boolean): PlaceOrderResult {
        if (!beforeSend()) return PlaceOrderResult.CartChanged
        if (attempts.containsKey(session to attemptId)) {
            return attempts[session to attemptId]?.let { PlaceOrderResult.Placed(it) } ?: PlaceOrderResult.Unavailable
        }
        require(lines.isNotEmpty()) { "Пустой заказ" }
        val order = Order(
            number = nextOrderNumber++,
            // Заказ ко времени готовят не сразу — пока он только принят
            status = if (pickupAtMillis != null) OrderStatus.ACCEPTED else OrderStatus.PREPARING,
            lines = lines.map { it.toOrderItem() },
            shop = shop,
            placedAtMillis = now(),
            pickupAtMillis = pickupAtMillis
        )
        attempts[session to attemptId] = order
        _orders.update { listOf(order) + it }
        return PlaceOrderResult.Placed(order)
    }

    override suspend fun cancel(number: Int): CancelOrderResult {
        val order = find(number)?.takeIf { it.canCancel } ?: return CancelOrderResult.TooLate
        val cancelled = order.copy(status = OrderStatus.CANCELLED)
        _orders.update { list -> list.map { if (it.number == number) cancelled else it } }
        return CancelOrderResult.Cancelled(cancelled)
    }

    override suspend fun load(number: Int): Order? = find(number)

    override fun find(number: Int): Order? = _orders.value.find { it.number == number }
}

private fun demoHistory(now: Long): List<Order> {
    val regular = DrinkOptions(DrinkSize.MEDIUM, MilkType.REGULAR, isIced = false, extras = emptySet())
    return listOf(
        Order(
            number = 1037,
            status = OrderStatus.PICKED_UP,
            lines = listOf(CartLine(DemoMenu.LATTE, unitPrice = 240, quantity = 2, options = regular).toOrderItem()),
            shop = Shop.MOSKOVSKAYA,
            placedAtMillis = now - 2 * DAY_MILLIS
        ),
        Order(
            number = 1029,
            status = OrderStatus.PICKED_UP,
            lines = listOf(
                CartLine(DemoMenu.RAF, unitPrice = 300, options = regular.copy(size = DrinkSize.LARGE)).toOrderItem(),
                CartLine(DemoMenu.ESPRESSO, unitPrice = 150).toOrderItem()
            ),
            shop = Shop.STROITELEY,
            placedAtMillis = now - 6 * DAY_MILLIS
        )
    )
}
