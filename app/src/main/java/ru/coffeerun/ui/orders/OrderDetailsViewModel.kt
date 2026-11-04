package ru.coffeerun.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.CancelOrderResult
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.model.Order
import ru.coffeerun.ui.navigation.OrderDetailsRoute

/**
 * Один заказ. Сразу показывает то, что уже есть в списке «Заказы», и тут же берёт свежий заказ
 * с сервера (GET /orders/{number}): статус мог смениться.
 */
class OrderDetailsViewModel(
    private val number: Int,
    private val repository: OrderRepository,
    private val cart: CartRepository
) : ViewModel() {
    private val fresh = MutableStateFlow<Order?>(null)

    /** null — заказа пока нет: в списке его не было, а сервер ещё не ответил или ответил ошибкой. */
    val order: StateFlow<Order?> = combine(fresh, repository.orders) { fresh, list -> fresh ?: list.find { it.number == number } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.find(number))

    private val _load = MutableStateFlow(OrdersLoad.LOADING)
    val load: StateFlow<OrdersLoad> = _load.asStateFlow()

    private val _notFound = MutableStateFlow(false)
    /** Сервер ответил 404 — экран закрывается. */
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    private val _cancelling = MutableStateFlow(false)
    val cancelling: StateFlow<Boolean> = _cancelling.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        _load.value = OrdersLoad.LOADING
        viewModelScope.launch {
            _load.value = try {
                val loaded = repository.load(number)
                if (loaded == null) _notFound.value = true else fresh.value = loaded
                OrdersLoad.DONE
            } catch (e: IOException) {
                // Заказ уже на экране — показываем его и говорим, что статус мог устареть
                if (order.value != null) _message.value = R.string.orders_refresh_error
                OrdersLoad.FAILED
            }
        }
    }

    fun cancel() {
        val current = order.value ?: return
        if (_cancelling.value) return
        _cancelling.value = true
        viewModelScope.launch {
            val result = repository.cancel(current.number)
            if (result is CancelOrderResult.Cancelled) fresh.value = result.order
            _message.value = result.messageRes()
            _cancelling.value = false
        }
    }

    /** Кладёт позиции заказа обратно в корзину. */
    fun repeat() = order.value?.lines?.forEach { cart.add(it.copy(id = 0)) }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val number = createSavedStateHandle().toRoute<OrderDetailsRoute>().number
                OrderDetailsViewModel(number, container.orders, container.cart)
            }
        }
    }
}
