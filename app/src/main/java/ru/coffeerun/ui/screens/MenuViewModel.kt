package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.defaultOptions
import ru.coffeerun.model.unitPrice

/** Меню. Выбранная категория — чисто UI-состояние и остаётся в экране. */
class MenuViewModel(private val cart: CartRepository) : ViewModel() {

    /** «+» в меню: размер S и обычное молоко — с явными опциями, чтобы бариста видел размер. */
    fun quickAdd(product: Product) = cart.add(
        CartLine(
            product = product,
            unitPrice = product.unitPrice(DrinkSize.SMALL, MilkType.REGULAR, emptySet()),
            options = product.defaultOptions()
        )
    )

    companion object {
        val Factory = viewModelFactory { initializer { MenuViewModel(container.cart) } }
    }
}
