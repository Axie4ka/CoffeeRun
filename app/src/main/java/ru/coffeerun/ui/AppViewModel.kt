package ru.coffeerun.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkOptions
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop

/** Больше порций одной позиции не положить — ни с экрана позиции, ни в корзине. */
const val MAX_QUANTITY = 10

private const val FIRST_ORDER_NUMBER = 1042
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Общее состояние приложения: корзина и заказы. Живёт в ViewModel, чтобы
 * переживать поворот экрана. Данные хранятся только в памяти.
 */
class AppViewModel : ViewModel() {
    val cart = mutableStateListOf<CartLine>()

    /** Заказы от новых к старым. */
    val orders = mutableStateListOf<Order>()

    /** Последний оформленный заказ — его показывает экран «Заказ принят». */
    var lastOrder by mutableStateOf<Order?>(null)
        private set

    private var nextLineId = 1
    private var nextOrderNumber = FIRST_ORDER_NUMBER

    init {
        // Демонстрационная история заказов
        val now = System.currentTimeMillis()
        val regular = DrinkOptions(DrinkSize.MEDIUM, MilkType.REGULAR, isIced = false, extras = emptySet<Extra>())
        orders += Order(
            number = 1037,
            status = OrderStatus.PICKED_UP,
            lines = listOf(CartLine(Product.LATTE, unitPrice = 240, quantity = 2, options = regular.copy(), id = nextLineId++)),
            shop = Shop.MOSKOVSKAYA,
            placedAtMillis = now - 2 * DAY_MILLIS
        )
        orders += Order(
            number = 1029,
            status = OrderStatus.PICKED_UP,
            lines = listOf(
                CartLine(Product.RAF, unitPrice = 300, options = regular.copy(size = DrinkSize.LARGE), id = nextLineId++),
                CartLine(Product.ESPRESSO, unitPrice = 150, id = nextLineId++)
            ),
            shop = Shop.STROITELEY,
            placedAtMillis = now - 6 * DAY_MILLIS
        )
    }

    val cartCount: Int get() = cart.sumOf { it.quantity }

    /** Одинаковые позиции сливаются в одну строку; количество не превышает [MAX_QUANTITY]. */
    fun addToCart(line: CartLine) {
        val index = cart.indexOfFirst { it.isSameItem(line) }
        if (index >= 0) {
            cart[index] = cart[index].copy(quantity = (cart[index].quantity + line.quantity).coerceAtMost(MAX_QUANTITY))
        } else {
            cart += line.copy(id = nextLineId++, quantity = line.quantity.coerceAtMost(MAX_QUANTITY))
        }
    }

    /** Меняет количество в пределах 1…[MAX_QUANTITY]; удаляет позицию [remove]. */
    fun changeQuantity(id: Int, delta: Int) {
        val index = cart.indexOfFirst { it.id == id }
        if (index >= 0) {
            cart[index] = cart[index].copy(quantity = (cart[index].quantity + delta).coerceIn(1, MAX_QUANTITY))
        }
    }

    fun remove(id: Int) {
        cart.removeAll { it.id == id }
    }

    /** Оформляет заказ из корзины и очищает её. Для пустой корзины возвращает null. */
    fun placeOrder(shop: Shop): Order? {
        if (cart.isEmpty()) return null
        val order = Order(
            number = nextOrderNumber++,
            status = OrderStatus.PREPARING,
            lines = cart.toList(),
            shop = shop,
            placedAtMillis = System.currentTimeMillis()
        )
        orders.add(0, order)
        cart.clear()
        lastOrder = order
        return order
    }

    /** Кладёт позиции прошлого заказа обратно в корзину. */
    fun repeat(order: Order) {
        order.lines.forEach { addToCart(it.copy(id = 0)) }
    }
}
