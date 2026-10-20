package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Product
import ru.coffeerun.ui.navigation.ProductRoute

/** Экран позиции: товар берётся из аргумента маршрута, корзина — для бейджа и добавления. */
class ProductViewModel(val product: Product, private val cart: CartRepository) : ViewModel() {
    val cartLines: StateFlow<List<CartLine>> = cart.lines

    fun addToCart(line: CartLine) = cart.add(line)

    companion object {
        val Factory = viewModelFactory {
            initializer { ProductViewModel(createSavedStateHandle().toRoute<ProductRoute>().product, container.cart) }
        }
    }
}
