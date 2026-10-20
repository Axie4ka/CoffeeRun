package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import ru.coffeerun.container
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.model.Order
import ru.coffeerun.ui.navigation.PlacedRoute

/**
 * Заказ берётся по номеру из маршрута. null — заказа нет: после смерти процесса стек
 * восстановился, а заказы в памяти — нет.
 */
class OrderPlacedViewModel(orderNumber: Int, orders: OrderRepository) : ViewModel() {
    val order: Order? = orders.find(orderNumber)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                OrderPlacedViewModel(createSavedStateHandle().toRoute<PlacedRoute>().orderNumber, container.orders)
            }
        }
    }
}
