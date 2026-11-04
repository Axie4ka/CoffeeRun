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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.CancelOrderResult
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.model.Order

/** Как прошла последняя загрузка заказов с сервера. */
enum class OrdersLoad { LOADING, DONE, FAILED }

/**
 * Заказы. Каждый раз, когда вкладку показывают, список берётся с сервера заново — статусы меняются.
 * После входа заказы загружает сам репозиторий. Ошибку загрузки экран показывает, только если показать нечего.
 */
class OrdersViewModel(
    private val repository: OrderRepository,
    private val cart: CartRepository,
    private val isLoggedIn: Flow<Boolean>
) : ViewModel() {
    val orders: StateFlow<List<Order>> = repository.orders

    private val _load = MutableStateFlow(OrdersLoad.LOADING)
    val load: StateFlow<OrdersLoad> = _load.asStateFlow()
    private var loadJob: Job? = null

    /** Вкладку показали. Гостю грузить нечего: экран всё равно просит войти. */
    fun onShown() {
        viewModelScope.launch { if (isLoggedIn.first()) retry() }
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

    private val _cancelling = MutableStateFlow<Int?>(null)
    /** Номер заказа, который сейчас отменяется: его кнопка занята, второе нажатие ничего не шлёт. */
    val cancelling: StateFlow<Int?> = _cancelling.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    /** Итог отмены или обновления для снэкбара — строковый ресурс. */
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun cancel(order: Order) {
        if (_cancelling.value != null) return
        _cancelling.value = order.number
        viewModelScope.launch {
            _message.value = when (repository.cancel(order.number)) {
                is CancelOrderResult.Cancelled -> R.string.order_cancelled
                CancelOrderResult.TooLate -> R.string.order_cancel_too_late
                CancelOrderResult.NetworkError -> R.string.order_cancel_error
            }
            _cancelling.value = null
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    private val _refreshing = MutableStateFlow(false)
    /** Идёт обновление, которое пользователь запросил свайпом: крутится индикатор над списком. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Свайп вниз. Список уже на экране, поэтому ошибка — снэкбар, а список остаётся. */
    fun refresh() {
        loadJob?.cancel()
        _refreshing.value = true
        loadJob = viewModelScope.launch {
            try {
                repository.refresh()
                _load.value = OrdersLoad.DONE
            } catch (e: IOException) {
                _load.value = OrdersLoad.FAILED
                _message.value = R.string.orders_refresh_error
            } finally {
                _refreshing.value = false
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
