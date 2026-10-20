package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.Shop

class CheckoutViewModel(
    private val cart: CartRepository,
    private val orders: OrderRepository
) : ViewModel() {
    val lines: StateFlow<List<CartLine>> = cart.lines

    // shortcut: магазин один на всех, пока нет экрана выбора магазина
    val shop = Shop.MOSKOVSKAYA

    /** Оформляет заказ из корзины и очищает её. Для пустой корзины возвращает null. */
    fun placeOrder(): Order? {
        val lines = cart.lines.value
        if (lines.isEmpty()) return null
        return orders.place(lines, shop).also { cart.clear() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CheckoutViewModel(container.cart, container.orders) }
        }
    }
}
