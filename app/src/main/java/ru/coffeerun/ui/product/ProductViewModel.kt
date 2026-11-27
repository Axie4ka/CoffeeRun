package ru.coffeerun.ui.product

import ru.coffeerun.ui.util.ReloadOnLocaleChange
import java.util.Locale
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.domain.favorites.Favorites
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.RecipeRepository
import ru.coffeerun.data.local.room.GUEST_OWNER
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.domain.promotion.priceCart
import ru.coffeerun.domain.promotion.pricingTimes
import ru.coffeerun.model.MAX_NOTE_LENGTH
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Customization
import ru.coffeerun.model.DEFAULT_DRINK_SIZE
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.DrinkOptions
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MAX_RECIPE_NAME_LENGTH
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.Recipe
import ru.coffeerun.model.itemCount
import ru.coffeerun.ui.navigation.ProductRoute
import java.io.Serializable

private const val FORM_KEY = "form"
private const val EDIT_OWNER_KEY = "editOwner"

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
        val totalPrice: Long,
        val prices: OptionPrices,
        val isFavorite: Boolean,
        val cartCount: Int,
        val recipeName: String? = null,
        val optionsAdjusted: Boolean = false,
        val discount: Int = 0
    ) : ProductUiState
    data object NotFound : ProductUiState

    /** Не удалось загрузить: нет сети или сервер ответил ошибкой. Выход — [ProductViewModel.retry]. */
    data object Error : ProductUiState
}

/**
 * Форма, в которой выбрано только то, что сейчас есть в кофейне: размер по умолчанию или
 * сохранённый выбор мог пропасть из меню, молоко — закончиться, добавка — исчезнуть.
 */
private fun ProductForm.fitTo(product: Product, prices: OptionPrices) = copy(
    size = prices.fitSize(size),
    milk = prices.fitMilk(milk),
    extras = extras.filterTo(mutableSetOf()) { it in product.extras && it in prices.extras },
    isIced = isIced && Customization.ICE in product.customizations,
    note = note.takeIf { Customization.NOTE in product.customizations }.orEmpty()
)

private fun ProductForm.toOptions(product: Product) = product.defaultOptions()?.copy(
    size = size.takeIf { Customization.SIZE in product.customizations },
    milk = milk.takeIf { Customization.MILK in product.customizations },
    isIced = isIced,
    extras = extras,
    note = note.trim()
)

private fun DrinkOptions?.toForm(quantity: Int = MIN_QUANTITY) = ProductForm(
    size = this?.size ?: DEFAULT_DRINK_SIZE,
    milk = this?.milk ?: MilkType.REGULAR,
    isIced = this?.isIced == true,
    extras = this?.extras.orEmpty(),
    note = this?.note.orEmpty(),
    quantity = quantity
)

private data class Editing(val session: AuthSession, val options: DrinkOptions?, val quantity: Int, val name: String? = null)

/** Результат загрузки товара; вместе с формой собирается в [ProductUiState]. */
private sealed interface Loaded {
    data object Loading : Loaded
    data class Found(val product: Product, val snapshot: MenuSnapshot, val editing: Editing?, val now: Long) : Loaded {
        val prices: OptionPrices get() = snapshot.prices
    }
    data object Missing : Loaded
    data object Failed : Loaded
}

/** Экран позиции: товар по id из маршрута, форма напитка, добавление в корзину. */
class ProductViewModel(
    private val productId: String,
    private val menu: MenuRepository,
    private val cart: CartRepository,
    private val favorites: Favorites,
    private val savedState: SavedStateHandle,
    private val recipes: RecipeRepository? = null,
    private val sessions: SessionProvider? = null,
    private val cartLineId: Int? = null,
    private val recipeId: String? = null,
    initialSize: DrinkSize = DEFAULT_DRINK_SIZE
) : ViewModel() {
    val isCartEdit = cartLineId != null
    val isRecipeEdit = recipeId != null
    val recipeSavingEnabled = recipes != null && sessions != null && !isCartEdit
    private val hasRestoredForm = savedState.contains(FORM_KEY)
    private var editSession: AuthSession? = null
    private var formInitialized = false
    val form: StateFlow<ProductForm> = savedState.getStateFlow(FORM_KEY, ProductForm(size = initialSize))
    private val loaded = MutableStateFlow<Loaded>(Loaded.Loading)

    val uiState: StateFlow<ProductUiState> = combine(loaded, form, favorites.ids, cart.lines) { loaded, form, favoriteIds, lines ->
        when (loaded) {
            Loaded.Loading -> ProductUiState.Loading
            Loaded.Missing -> ProductUiState.NotFound
            Loaded.Failed -> ProductUiState.Error
            is Loaded.Found -> {
                val fitted = form.fitTo(loaded.product, loaded.prices)
                val options = fitted.toOptions(loaded.product)
                val rawPrice = loaded.product.recipePrice(options, loaded.prices)
                val unitPrice = rawPrice ?: loaded.product.unitPrice(fitted.size, fitted.milk, fitted.extras, loaded.prices)
                fun pricedLine(quantity: Int): CartLine {
                    val line = CartLine(loaded.product, unitPrice, quantity, options)
                    return if (rawPrice == null) line else priceCart(listOf(line), loaded.snapshot, loaded.now).single()
                }
                val portion = pricedLine(MIN_QUANTITY)
                val selection = if (fitted.quantity == MIN_QUANTITY) portion else pricedLine(fitted.quantity)
                ProductUiState.Success(
                    loaded.product, fitted,
                    unitPrice = unitPrice,
                    totalPrice = selection.totalAmount,
                    prices = loaded.prices,
                    isFavorite = productId in favoriteIds,
                    cartCount = lines.itemCount,
                    recipeName = loaded.editing?.name,
                    optionsAdjusted = loaded.editing?.let {
                        loaded.product.recipePrice(it.options, loaded.prices) == null
                    } == true,
                    discount = portion.discount
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductUiState.Loading)

    private var loadJob: Job? = null

    init {
        retry()
    }

    private val languageWatch = ReloadOnLocaleChange(::retry)

    /** Экран сообщает язык; сменился — данные с сервера загружаются заново, уже на новом языке. */
    fun onLocale(locale: Locale) = languageWatch.onLocale(locale)

    /** Первая загрузка и «Повторить» после ошибки. Ловим только [IOException]: остальное — баг. */
    fun retry() {
        loadJob?.cancel() // повторное «Повторить» не должно гоняться с прошлой загрузкой
        loaded.value = Loaded.Loading
        loadJob = viewModelScope.launch {
            try {
                val editing = if (isCartEdit || isRecipeEdit) {
                    loadEditing() ?: run {
                        loaded.value = Loaded.Missing
                        return@launch
                    }
                } else null
                if (editing != null && !formInitialized) {
                    if (!hasRestoredForm) savedState[FORM_KEY] = editing.options.toForm(editing.quantity)
                    formInitialized = true
                }
                menu.observeSnapshot().collectLatest { snapshot ->
                    val product = snapshot.products.firstOrNull { it.id == productId }
                    if (product == null) loaded.value = Loaded.Missing
                    else snapshot.pricingTimes().collect { time ->
                        loaded.value = Loaded.Found(product, snapshot, editing, time)
                    }
                }
            } catch (e: IOException) {
                loaded.value = Loaded.Failed
            }
        }
    }

    private suspend fun loadEditing(): Editing? {
        val provider = sessions ?: return null
        val session = provider.currentSession()
        if (editSession != null && editSession != session) return null
        val owner = session.localOwner ?: GUEST_OWNER
        if (savedState.get<String>(EDIT_OWNER_KEY)?.let { it != owner } == true) return null
        savedState[EDIT_OWNER_KEY] = owner
        editSession = session
        if (cartLineId != null) {
            val line = cart.snapshot(session).firstOrNull { it.id == cartLineId && it.product.id == productId } ?: return null
            return Editing(session, line.options, line.quantity)
        }
        if (recipeId != null && session.isLoggedIn) {
            val recipe = recipes?.find(recipeId, session)?.takeIf { it.productId == productId } ?: return null
            return Editing(session, recipe.options, MIN_QUANTITY, recipe.name)
        }
        return null
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

    private var addingToCart = false
    private var savingRecipe = false
    private var newRecipeId: String? = null

    fun addToCart(onResult: (Int?) -> Unit = {}) {
        if (addingToCart) return
        val (product, snapshot, editing) = loaded.value as? Loaded.Found ?: return
        val prices = snapshot.prices
        val form = form.value.fitTo(product, prices)
        val options = form.toOptions(product)
        val price = product.recipePrice(options, prices) ?: return onResult(R.string.recipe_unavailable)
        addingToCart = true
        viewModelScope.launch {
            try {
                val line = CartLine(product, price, form.quantity, options)
                if (cartLineId == null) {
                    if (!cart.addLine(line)) return@launch onResult(R.string.cart_add_limit)
                } else if (editing == null || !cart.replaceLine(cartLineId, line, editing.session)) {
                    onResult(R.string.recipe_cart_edit_error)
                    return@launch
                }
                onResult(null)
            } catch (_: IOException) {
                onResult(R.string.cart_storage_unavailable)
            } finally {
                addingToCart = false
            }
        }
    }

    fun saveRecipe(name: String, onSaved: () -> Unit = {}) {
        val repository = recipes ?: return
        val provider = sessions ?: return
        val trimmed = name.trim()
        if (!recipeSavingEnabled || savingRecipe || trimmed.length !in 1..MAX_RECIPE_NAME_LENGTH) return
        val (product, snapshot, editing) = loaded.value as? Loaded.Found ?: return
        val prices = snapshot.prices
        val options = form.value.fitTo(product, prices).toOptions(product)
        if (product.category == ProductCategory.PASTRY || product.recipePrice(options, prices) == null) {
            _message.value = R.string.recipe_unavailable
            return
        }
        val id = recipeId ?: newRecipeId ?: UUID.randomUUID().toString().also { newRecipeId = it }
        val recipe = Recipe(id, trimmed, product.id, options)
        savingRecipe = true
        viewModelScope.launch {
            try {
                val session = editing?.session ?: provider.currentSession()
                if (!session.isLoggedIn) return@launch
                repository.save(recipe, session)
                newRecipeId = null
                if (!isRecipeEdit) _message.value = R.string.recipe_saved
                onSaved()
            } catch (_: IOException) {
                _message.value = R.string.recipe_action_error
            } finally {
                savingRecipe = false
            }
        }
    }

    private fun update(transform: ProductForm.() -> ProductForm) {
        savedState[FORM_KEY] = form.value.transform()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val savedState = createSavedStateHandle()
                val route = savedState.toRoute<ProductRoute>()
                ProductViewModel(
                    route.productId, container.menu, container.cart, container.favorites, savedState,
                    recipes = container.recipes,
                    sessions = container.auth,
                    cartLineId = route.cartLineId,
                    recipeId = route.recipeId,
                    initialSize = DrinkSize.entries.firstOrNull { it.name == route.size } ?: DEFAULT_DRINK_SIZE
                )
            }
        }
    }
}
