package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DEFAULT_DRINK_SIZE
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop

/** Состояние загрузки меню. */
sealed interface MenuUiState {
    data object Loading : MenuUiState
    data class Success(val products: List<Product>) : MenuUiState
    data object Error : MenuUiState
}

/** Меню. Выбранная категория — чисто UI-состояние и остаётся в экране. */
class MenuViewModel(
    private val menu: MenuRepository,
    private val cart: CartRepository,
    private val shops: ShopRepository
) : ViewModel() {
    val shop: StateFlow<Shop> = shops.selected

    fun selectShop(shop: Shop) = shops.select(shop)


    private val _uiState = MutableStateFlow<MenuUiState>(MenuUiState.Loading)
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadMenu()
    }

    /** Первая загрузка и «Повторить» после ошибки. Ловим только [IOException]: остальное — баг. */
    fun loadMenu() {
        loadJob?.cancel() // повторное «Повторить» не должно гоняться с прошлой загрузкой
        _uiState.value = MenuUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                MenuUiState.Success(menu.getMenu())
            } catch (e: IOException) {
                MenuUiState.Error
            }
        }
    }

    /** «+» в меню: размер по умолчанию и обычное молоко — с явными опциями, чтобы бариста видел размер. */
    fun quickAdd(product: Product) = cart.add(
        CartLine(
            product = product,
            unitPrice = product.unitPrice(DEFAULT_DRINK_SIZE, MilkType.REGULAR, emptySet()),
            options = product.defaultOptions()
        )
    )

    companion object {
        val Factory = viewModelFactory { initializer { MenuViewModel(container.menu, container.cart, container.shops) } }
    }
}
