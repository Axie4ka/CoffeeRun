package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.model.Order

class OrdersViewModel(orders: OrderRepository, private val cart: CartRepository) : ViewModel() {
    val orders: StateFlow<List<Order>> = orders.orders

    /** Кладёт позиции прошлого заказа обратно в корзину. */
    fun repeat(order: Order) = order.lines.forEach { cart.add(it.copy(id = 0)) }

    companion object {
        val Factory = viewModelFactory {
            initializer { OrdersViewModel(container.orders, container.cart) }
        }
    }
}
