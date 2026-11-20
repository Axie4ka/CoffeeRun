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
import ru.coffeerun.data.CartRead
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.itemCount

/** Состояние корзины для экрана и бейджа нижней панели: позиции, число штук и итог. */
data class CartUiState(
    val lines: List<CartLine>, val count: Int, val total: Int,
    val isLoading: Boolean = false, val readFailed: Boolean = false
)

private fun toCartUiState(readState: CartRead): CartUiState {
    val lines = (readState as? CartRead.Ready)?.lines.orEmpty()
    val total = lines.sumOf { it.unitPrice.toLong() * it.quantity }
    return CartUiState(lines, lines.itemCount, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        isLoading = readState == CartRead.Loading, readFailed = readState == CartRead.Failed)
}

/** Корзина: состояние и действия над позициями. Нижняя панель берёт отсюда же счётчик. */
class CartViewModel(private val cart: CartRepository, private val menu: MenuRepository) : ViewModel() {
    // Начальное значение из текущих позиций — чтобы бейдж не мигал нулём
    val uiState: StateFlow<CartUiState> = cart.readState
        .map(::toCartUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            toCartUiState((cart.readState as? StateFlow<CartRead>)?.value ?: CartRead.Ready(cart.lines.value)))

    fun retryRead() = cart.retryRead()

    fun changeQuantity(id: Int, delta: Int) { viewModelScope.launch { updateCart { cart.changeLineQuantity(id, delta) } } }
    fun remove(id: Int) { viewModelScope.launch { updateCart { cart.removeLine(id) } } }

    private suspend fun updateCart(action: suspend () -> Unit) {
        try { action() } catch (_: IOException) { _storageFailed.value = true }
    }

    private val _storageFailed = kotlinx.coroutines.flow.MutableStateFlow(false)
    val storageFailed: StateFlow<Boolean> = _storageFailed
    fun onStorageErrorShown() { _storageFailed.value = false }

    // В строках корзины — товары на языке, на котором их добавили; сменился язык — берём из меню заново
    private val languageWatch = ReloadOnLocaleChange {
        viewModelScope.launch {
            try { cart.updateProducts(menu.getMenu()) } catch (e: IOException) { } // нет сети — останутся старые названия
        }
    }

    fun onLocale(locale: Locale) = languageWatch.onLocale(locale)

    companion object {
        val Factory = viewModelFactory { initializer { CartViewModel(container.cart, container.menu) } }
    }
}
