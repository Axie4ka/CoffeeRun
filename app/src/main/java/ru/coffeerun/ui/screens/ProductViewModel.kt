package ru.coffeerun.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
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

/** Состояние экрана позиции: товар грузится по id из маршрута. [unitPrice] — производное от товара и формы. */
sealed interface ProductUiState {
    data object Loading : ProductUiState
    data class Success(val product: Product, val form: ProductForm, val unitPrice: Int) : ProductUiState
    data object NotFound : ProductUiState
}

/** Результат загрузки товара; вместе с формой собирается в [ProductUiState]. */
private sealed interface Loaded {
    data object Loading : Loaded
    data class Found(val product: Product) : Loaded
    data object Missing : Loaded
}

/** Экран позиции: товар по id из маршрута, форма напитка, добавление в корзину. */
class ProductViewModel(
    private val productId: String,
    private val menu: MenuRepository,
    private val cart: CartRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val form: StateFlow<ProductForm> = savedState.getStateFlow(FORM_KEY, ProductForm())
    val cartLines: StateFlow<List<CartLine>> = cart.lines

    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)

    val uiState: StateFlow<ProductUiState> = combine(loaded, form) { loaded, form ->
        when (loaded) {
            Loaded.Loading -> ProductUiState.Loading
            Loaded.Missing -> ProductUiState.NotFound
            is Loaded.Found -> ProductUiState.Success(
                loaded.product, form, loaded.product.unitPrice(form.size, form.milk, form.extras)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductUiState.Loading)

    init {
        viewModelScope.launch {
            loaded.value = menu.getProduct(productId)?.let(Loaded::Found) ?: Loaded.Missing
        }
    }

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
        val product = (loaded.value as? Loaded.Found)?.product ?: return
        val form = form.value
        val features = product.customizations
        cart.add(
            CartLine(
                product = product,
                unitPrice = product.unitPrice(form.size, form.milk, form.extras),
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
                ProductViewModel(savedState.toRoute<ProductRoute>().productId, container.menu, container.cart, savedState)
            }
        }
    }
}
