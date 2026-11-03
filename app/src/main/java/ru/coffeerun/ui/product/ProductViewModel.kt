package ru.coffeerun.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.FavoritesRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.MAX_NOTE_LENGTH
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Customization
import ru.coffeerun.model.DEFAULT_DRINK_SIZE
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product
import ru.coffeerun.model.itemCount
import ru.coffeerun.ui.navigation.ProductRoute
import java.io.Serializable

private const val FORM_KEY = "form"

/**
 * Выбор пользователя на экране позиции. Serializable — чтобы лечь в [SavedStateHandle]
 * и пережить не только поворот, но и смерть процесса.
 */
data class ProductForm(
    val size: DrinkSize = DEFAULT_DRINK_SIZE,
    val milk: MilkType = MilkType.REGULAR,
    val isIced: Boolean = false,
    val extras: Set<Extra> = emptySet(),
    val note: String = "",
    val quantity: Int = MIN_QUANTITY
) : Serializable

/**
 * Состояние экрана позиции: товар грузится по id из маршрута. [Success.unitPrice] — производное от товара и формы,
 * [Success.cartCount] — порций в корзине, для бейджа в шапке.
 */
sealed interface ProductUiState {
    data object Loading : ProductUiState
    data class Success(
        val product: Product,
        val form: ProductForm,
        val unitPrice: Int,
        val prices: OptionPrices,
        val isFavorite: Boolean,
        val cartCount: Int
    ) : ProductUiState
    data object NotFound : ProductUiState

    /** Не удалось загрузить: нет сети или сервер ответил ошибкой. Выход — [ProductViewModel.retry]. */
    data object Error : ProductUiState
}

/**
 * Форма, в которой выбрано только то, что сейчас есть в кофейне: размер по умолчанию или
 * сохранённый выбор мог пропасть из меню, молоко — закончиться, добавка — исчезнуть.
 */
private fun ProductForm.fitTo(prices: OptionPrices) = copy(
    size = prices.fitSize(size),
    milk = prices.fitMilk(milk),
    extras = extras.filterTo(mutableSetOf()) { it in prices.extras }
)

/** Результат загрузки товара; вместе с формой собирается в [ProductUiState]. */
private sealed interface Loaded {
    data object Loading : Loaded
    data class Found(val product: Product, val prices: OptionPrices) : Loaded
    data object Missing : Loaded
    data object Failed : Loaded
}

/** Экран позиции: товар по id из маршрута, форма напитка, добавление в корзину. */
class ProductViewModel(
    private val productId: String,
    private val menu: MenuRepository,
    private val cart: CartRepository,
    private val favorites: FavoritesRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val form: StateFlow<ProductForm> = savedState.getStateFlow(FORM_KEY, ProductForm())
    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)

    val uiState: StateFlow<ProductUiState> = combine(loaded, form, favorites.ids, cart.lines) { loaded, form, favoriteIds, lines ->
        when (loaded) {
            Loaded.Loading -> ProductUiState.Loading
            Loaded.Missing -> ProductUiState.NotFound
            Loaded.Failed -> ProductUiState.Error
            is Loaded.Found -> {
                val fitted = form.fitTo(loaded.prices)
                ProductUiState.Success(
                    loaded.product, fitted, loaded.product.unitPrice(fitted.size, fitted.milk, fitted.extras, loaded.prices),
                    prices = loaded.prices,
                    isFavorite = productId in favoriteIds,
                    cartCount = lines.itemCount
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductUiState.Loading)

    private var loadJob: Job? = null

    init {
        retry()
    }

    /** Первая загрузка и «Повторить» после ошибки. Ловим только [IOException]: остальное — баг. */
    fun retry() {
        loadJob?.cancel() // повторное «Повторить» не должно гоняться с прошлой загрузкой
        loaded.value = Loaded.Loading
        loadJob = viewModelScope.launch {
            loaded.value = try {
                val product = menu.getProduct(productId)
                if (product == null) Loaded.Missing else Loaded.Found(product, menu.getOptionPrices())
            } catch (e: IOException) {
                Loaded.Failed
            }
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

    /** Разовое сообщение для снэкбара: сервер не принял сердечко, и оно откатилось. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun toggleFavorite() {
        viewModelScope.launch {
            if (!favorites.toggle(productId)) _message.value = R.string.favorites_toggle_error
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    fun addToCart() {
        val (product, prices) = loaded.value as? Loaded.Found ?: return
        val form = form.value.fitTo(prices)
        val features = product.customizations
        cart.add(
            CartLine(
                product = product,
                unitPrice = product.unitPrice(form.size, form.milk, form.extras, prices),
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
                ProductViewModel(savedState.toRoute<ProductRoute>().productId, container.menu, container.cart, container.favorites, savedState)
            }
        }
    }
}
