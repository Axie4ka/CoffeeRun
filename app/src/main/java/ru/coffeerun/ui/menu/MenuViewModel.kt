package ru.coffeerun.ui.menu

import ru.coffeerun.ui.util.ReloadOnLocaleChange
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.R
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DEFAULT_DRINK_SIZE
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop
import ru.coffeerun.model.Promotion
import ru.coffeerun.domain.promotion.pricingTimes

/** Состояние загрузки меню. */
sealed interface MenuUiState {
    data object Loading : MenuUiState
    data class Success(
        val products: List<Product>,
        val prices: OptionPrices,
        val isCached: Boolean = false,
        val fetchedAtMillis: Long? = null,
        val promotions: List<Promotion> = emptyList()
    ) : MenuUiState
    data object Error : MenuUiState
}

enum class MenuRefresh { IDLE, RUNNING, FAILED }

/** Меню. Выбранная категория — чисто UI-состояние и остаётся в экране. */
class MenuViewModel(
    private val menu: MenuRepository,
    private val cart: CartRepository,
    private val shops: ShopRepository
) : ViewModel() {
    val shop: StateFlow<Shop> = shops.selected

    fun selectShop(shop: Shop, onFailure: () -> Unit = {}) {
        viewModelScope.launch { try { shops.saveSelection(shop) } catch (_: IOException) { onFailure() } }
    }


    private val _uiState = MutableStateFlow<MenuUiState>(MenuUiState.Loading)
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()
    private val _refreshState = MutableStateFlow(MenuRefresh.IDLE)
    val refreshState = _refreshState.asStateFlow()
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    init {
        loadMenu()
    }

    private val languageWatch = ReloadOnLocaleChange(::loadMenu)

    /** Экран сообщает язык; сменился — данные с сервера загружаются заново, уже на новом языке. */
    fun onLocale(locale: Locale) = languageWatch.onLocale(locale)

    fun retry() {
        if (_uiState.value !is MenuUiState.Success) {
            loadMenu()
            return
        }
        if (refreshJob?.isActive == true) return
        _refreshState.value = MenuRefresh.RUNNING
        refreshJob = viewModelScope.launch {
            _refreshState.value = try {
                menu.refresh()
                MenuRefresh.IDLE
            } catch (_: IOException) {
                MenuRefresh.FAILED
            }
        }
    }

    /** Первая загрузка и «Повторить» после ошибки. Ловим только [IOException]: остальное — баг. */
    fun loadMenu() {
        refreshJob?.cancel()
        _refreshState.value = MenuRefresh.IDLE
        loadJob?.cancel() // повторное «Повторить» не должно гоняться с прошлой загрузкой
        _uiState.value = MenuUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                menu.observeSnapshot().collectLatest { snapshot ->
                    snapshot.pricingTimes().collect { now ->
                        _uiState.value = MenuUiState.Success(
                            snapshot.products, snapshot.prices, snapshot.isCached, snapshot.fetchedAtMillis,
                            snapshot.activePromotions(now)
                        )
                    }
                }
            } catch (e: IOException) {
                _uiState.value = MenuUiState.Error
            }
        }
    }

    /**
     * «+» в меню: размер по умолчанию и обычное молоко — с явными опциями, чтобы бариста видел размер.
     * Если их нет в кофейне — то же, что подставит экран позиции, иначе строки корзины не сольются.
     * Кнопка видна только при загруженном меню, поэтому цены уже есть.
     */
    fun quickAdd(product: Product, onResult: (Int?) -> Unit = {}) {
        val snapshot = _uiState.value as? MenuUiState.Success ?: return
        val current = snapshot.products.firstOrNull { it.id == product.id } ?: return onResult(R.string.recipe_unavailable)
        val prices = snapshot.prices
        val size = prices.fitSize(DEFAULT_DRINK_SIZE)
        val milk = prices.fitMilk(MilkType.REGULAR)
        val options = current.defaultOptions()?.let {
            it.copy(size = it.size?.let { size }, milk = it.milk?.let { milk })
        }
        val price = current.recipePrice(options, prices) ?: return onResult(R.string.recipe_unavailable)
        viewModelScope.launch {
            try {
                val added = cart.addLine(CartLine(current, price, options = options))
                onResult(if (added) null else R.string.cart_add_limit)
            } catch (_: IOException) {
                onResult(R.string.cart_storage_unavailable)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { MenuViewModel(container.menu, container.cart, container.shops) } }
    }
}
