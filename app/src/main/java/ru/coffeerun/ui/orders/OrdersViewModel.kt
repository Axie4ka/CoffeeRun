package ru.coffeerun.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.model.Order

/** Как прошла последняя загрузка заказов с сервера. */
enum class OrdersLoad { LOADING, DONE, FAILED }

/**
 * Заказы. При открытии экрана и после входа список берётся с сервера заново — статусы меняются.
 * Ошибку загрузки экран показывает, только если показать нечего.
 */
class OrdersViewModel(
    private val repository: OrderRepository,
    private val cart: CartRepository,
    isLoggedIn: Flow<Boolean>
) : ViewModel() {
    val orders: StateFlow<List<Order>> = repository.orders

    private val _load = MutableStateFlow(OrdersLoad.LOADING)
    val load: StateFlow<OrdersLoad> = _load.asStateFlow()
    private var loadJob: Job? = null

    init {
        // Гостю грузить нечего: экран всё равно просит войти
        viewModelScope.launch { isLoggedIn.distinctUntilChanged().filter { it }.collect { retry() } }
    }

    /** Загрузка при открытии и «Повторить» после ошибки. Ловим только [IOException]: остальное — баг. */
    fun retry() {
        loadJob?.cancel()
        _load.value = OrdersLoad.LOADING
        loadJob = viewModelScope.launch {
            _load.value = try {
                repository.refresh()
                OrdersLoad.DONE
            } catch (e: IOException) {
                OrdersLoad.FAILED
            }
        }
    }

    /** Кладёт позиции прошлого заказа обратно в корзину. */
    fun repeat(order: Order) = order.lines.forEach { cart.add(it.copy(id = 0)) }

    companion object {
        val Factory = viewModelFactory {
            initializer { OrdersViewModel(container.orders, container.cart, container.auth.isLoggedIn) }
        }
    }
}
