package ru.coffeerun.data

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
import ru.coffeerun.model.Shop

private const val FIRST_ORDER_NUMBER = 1042
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Заказы пользователя, от новых к старым. */
interface OrderRepository {
    val orders: StateFlow<List<Order>>

    /** Оформляет заказ из [lines]; [lines] не должен быть пустым. */
    fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long? = null): Order

    fun find(number: Int): Order?
}

/** Заказы в памяти, стартуют с демо-истории. */
class InMemoryOrderRepository(private val now: () -> Long = System::currentTimeMillis) : OrderRepository {
    private val _orders = MutableStateFlow(demoHistory(now()))
    override val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    private var nextOrderNumber = FIRST_ORDER_NUMBER

    override fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?): Order {
        require(lines.isNotEmpty()) { "Пустой заказ" }
        val order = Order(
            number = nextOrderNumber++,
            // Заказ ко времени готовят не сразу — пока он только принят
            status = if (pickupAtMillis != null) OrderStatus.ACCEPTED else OrderStatus.PREPARING,
            lines = lines,
            shop = shop,
            placedAtMillis = now(),
            pickupAtMillis = pickupAtMillis
        )
        _orders.update { listOf(order) + it }
        return order
    }

    override fun find(number: Int): Order? = _orders.value.find { it.number == number }
}

private fun demoHistory(now: Long): List<Order> {
    val regular = DrinkOptions(DrinkSize.MEDIUM, MilkType.REGULAR, isIced = false, extras = emptySet())
    return listOf(
        Order(
            number = 1037,
            status = OrderStatus.PICKED_UP,
            lines = listOf(CartLine(LocalMenu.LATTE, unitPrice = 240, quantity = 2, options = regular, id = 1)),
            shop = Shop.MOSKOVSKAYA,
            placedAtMillis = now - 2 * DAY_MILLIS
        ),
        Order(
            number = 1029,
            status = OrderStatus.PICKED_UP,
            lines = listOf(
                CartLine(LocalMenu.RAF, unitPrice = 300, options = regular.copy(size = DrinkSize.LARGE), id = 1),
                CartLine(LocalMenu.ESPRESSO, unitPrice = 150, id = 2)
            ),
            shop = Shop.STROITELEY,
            placedAtMillis = now - 6 * DAY_MILLIS
        )
    )
}
