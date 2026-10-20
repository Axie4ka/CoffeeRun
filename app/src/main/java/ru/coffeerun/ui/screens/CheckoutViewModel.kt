package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

class CheckoutViewModel(
    private val cart: CartRepository,
    private val orders: OrderRepository
) : ViewModel() {
    val lines: StateFlow<List<CartLine>> = cart.lines

    // shortcut: магазин один на всех, пока нет экрана выбора магазина
    val shop = Shop.MOSKOVSKAYA

    private val _placedOrderNumber = MutableStateFlow<Int?>(null)

    /**
     * Номер только что оформленного заказа — событие как состояние: View переходит
     * на «Заказ принят» и сообщает об этом через [onPlacedOrderShown].
     */
    val placedOrderNumber: StateFlow<Int?> = _placedOrderNumber.asStateFlow()

    /** Оформляет заказ из корзины и очищает её. Пустая корзина — ничего не происходит. */
    fun placeOrder() {
        val lines = cart.lines.value
        if (lines.isEmpty()) return
        _placedOrderNumber.value = orders.place(lines, shop).number
        cart.clear()
    }

    fun onPlacedOrderShown() {
        _placedOrderNumber.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CheckoutViewModel(container.cart, container.orders) }
        }
    }
}
