package ru.coffeerun.ui.cart

import ru.coffeerun.data.MenuRepository
import kotlinx.coroutines.launch
import java.io.IOException
import ru.coffeerun.ui.util.ReloadOnLocaleChange
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.itemCount

/** Состояние корзины для экрана и бейджа нижней панели: позиции, число штук и итог. */
data class CartUiState(val lines: List<CartLine>, val count: Int, val total: Int)

private fun toCartUiState(lines: List<CartLine>) =
    CartUiState(lines, count = lines.itemCount, total = lines.sumOf { it.total })

/** Корзина: состояние и действия над позициями. Нижняя панель берёт отсюда же счётчик. */
class CartViewModel(private val cart: CartRepository, private val menu: MenuRepository) : ViewModel() {
    // Начальное значение из текущих позиций — чтобы бейдж не мигал нулём
    val uiState: StateFlow<CartUiState> = cart.lines
        .map(::toCartUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toCartUiState(cart.lines.value))

    fun changeQuantity(id: Int, delta: Int) = cart.changeQuantity(id, delta)
    fun remove(id: Int) = cart.remove(id)

    // В строках корзины — товары на языке, на котором их добавили; сменился язык — берём из меню заново
    private val languageWatch = ReloadOnLocaleChange {
        viewModelScope.launch {
            try { cart.replaceProducts(menu.getMenu()) } catch (e: IOException) { } // нет сети — останутся старые названия
        }
    }

    fun onLocale(locale: Locale) = languageWatch.onLocale(locale)

    companion object {
        val Factory = viewModelFactory { initializer { CartViewModel(container.cart, container.menu) } }
    }
}
