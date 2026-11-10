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
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.Order

/** Текст снэкбара об итоге отмены — один и тот же в списке и на экране заказа. */
internal fun CancelOrderResult.messageRes(): Int = when (this) {
    is CancelOrderResult.Cancelled -> R.string.order_cancelled
    CancelOrderResult.TooLate -> R.string.order_cancel_too_late
    CancelOrderResult.NetworkError -> R.string.order_cancel_error
}

/** Как прошла последняя загрузка заказов с сервера. */
enum class OrdersLoad { LOADING, DONE, FAILED }

/**
 * Заказы. Каждый раз, когда вкладку показывают, список берётся с сервера заново — статусы меняются.
 * Загрузку явно запрашивает экран. Ошибку показывает, только если показать нечего.
 */
class OrdersViewModel(
    private val repository: Orders,
    private val cart: CartRepository,
    private val isLoggedIn: Flow<Boolean>,
    private val menu: MenuRepository
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
            try {
                _message.value = repository.cancel(order.number).messageRes()
            } finally {
                _cancelling.value = null
            }
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

    private var isRepeating = false

    /** Не допускает параллельных повторов, пока проверяем товары по каталогу. */
    suspend fun repeat(order: Order): OrderRepeatResult? {
        if (isRepeating) return null
        isRepeating = true
        return try {
            repeatOrder(order, menu, cart)
        } finally {
            isRepeating = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { OrdersViewModel(container.orders, container.cart, container.auth.isLoggedIn, container.menu) }
        }
    }
}
