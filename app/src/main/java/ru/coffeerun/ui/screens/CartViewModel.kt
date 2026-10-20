package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.model.CartLine

/** Корзина: список позиций и действия над ними. Нижняя панель берёт отсюда же счётчик. */
class CartViewModel(private val cart: CartRepository) : ViewModel() {
    val lines: StateFlow<List<CartLine>> = cart.lines

    fun changeQuantity(id: Int, delta: Int) = cart.changeQuantity(id, delta)
    fun remove(id: Int) = cart.remove(id)

    companion object {
        val Factory = viewModelFactory { initializer { CartViewModel(container.cart) } }
    }
}
