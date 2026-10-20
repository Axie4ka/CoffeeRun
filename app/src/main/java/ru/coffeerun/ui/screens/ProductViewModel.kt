package ru.coffeerun.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.model.MAX_NOTE_LENGTH
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Customization
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.defaultOptions
import ru.coffeerun.model.unitPrice
import ru.coffeerun.ui.navigation.ProductRoute
import java.io.Serializable

private const val FORM_KEY = "form"

/**
 * Выбор пользователя на экране позиции. Serializable — чтобы лечь в [SavedStateHandle]
 * и пережить не только поворот, но и смерть процесса.
 */
data class ProductForm(
    val size: DrinkSize = DrinkSize.MEDIUM,
    val milk: MilkType = MilkType.REGULAR,
    val isIced: Boolean = false,
    val extras: Set<Extra> = emptySet(),
    val note: String = "",
    val quantity: Int = MIN_QUANTITY
) : Serializable

/** Экран позиции: товар из аргумента маршрута, форма напитка, добавление в корзину. */
class ProductViewModel(
    val product: Product,
    private val cart: CartRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val form: StateFlow<ProductForm> = savedState.getStateFlow(FORM_KEY, ProductForm())
    val cartLines: StateFlow<List<CartLine>> = cart.lines

    /** Цена одной порции с выбранными опциями. Производное — в состоянии не хранится. */
    fun unitPrice(form: ProductForm) = product.unitPrice(form.size, form.milk, form.extras)

    fun selectSize(size: DrinkSize) = update { copy(size = size) }
    fun selectMilk(milk: MilkType) = update { copy(milk = milk) }
    fun setIced(isIced: Boolean) = update { copy(isIced = isIced) }
    fun toggleExtra(extra: Extra) = update { copy(extras = if (extra in extras) extras - extra else extras + extra) }

    /** Текст длиннее лимита отклоняется целиком: слишком длинная вставка просто не применится. */
    fun setNote(note: String) {
        if (note.length <= MAX_NOTE_LENGTH) update { copy(note = note) }
    }

    fun changeQuantity(delta: Int) = update { copy(quantity = (quantity + delta).coerceIn(MIN_QUANTITY, MAX_QUANTITY)) }

    fun addToCart() {
        val form = form.value
        val features = product.customizations
        cart.add(
            CartLine(
                product = product,
                unitPrice = unitPrice(form),
                quantity = form.quantity,
                // Те же опции по умолчанию, что у «+» в меню, — иначе одинаковые строки не сольются
                options = product.defaultOptions()?.copy(
                    size = form.size.takeIf { Customization.SIZE in features },
                    milk = form.milk.takeIf { Customization.MILK in features },
                    isIced = form.isIced,
                    extras = form.extras,
                    note = form.note.trim()
                )
            )
        )
    }

    private fun update(transform: ProductForm.() -> ProductForm) {
        savedState[FORM_KEY] = form.value.transform()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val savedState = createSavedStateHandle()
                ProductViewModel(savedState.toRoute<ProductRoute>().product, container.cart, savedState)
            }
        }
    }
}
