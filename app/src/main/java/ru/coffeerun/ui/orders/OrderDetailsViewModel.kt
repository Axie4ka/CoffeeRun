package ru.coffeerun.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.domain.orders.RepeatOrderUseCase
import ru.coffeerun.model.Order
import ru.coffeerun.ui.navigation.OrderDetailsRoute

/**
 * Один заказ. Сразу показывает то, что уже есть в списке «Заказы», и тут же берёт свежий заказ
 * с сервера (GET /orders/{number}): статус мог смениться.
 */
class OrderDetailsViewModel(
    private val number: Int,
    private val repository: Orders,
    private val repeatOrderUseCase: RepeatOrderUseCase
) : ViewModel() {
    /** null — заказа пока нет: в списке его не было, а сервер ещё не ответил или ответил ошибкой. */
    val order: StateFlow<Order?> = repository.observeOrder(number)
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

    private val refreshRequest = MutableStateFlow(0)

    fun retry() = refreshRequest.update { it + 1 }

    /** Вызывается в RESUMED. Уход с экрана отменяет и задержку, и выполняющийся запрос. */
    suspend fun refreshWhileVisible() {
        refreshRequest.collectLatest {
            _notFound.value = false
            _load.value = OrdersLoad.LOADING
            do {
                _load.value = try {
                    _notFound.value = repository.load(number) == null
                    OrdersLoad.DONE
                } catch (_: IOException) {
                    if (repository.find(number) != null) _message.value = R.string.orders_refresh_error
                    OrdersLoad.FAILED
                }
                if (repository.find(number)?.isActive != true || _notFound.value) return@collectLatest
                delay(STATUS_REFRESH_MILLIS)
            } while (repository.find(number)?.isActive == true)
        }
    }

    fun cancel() {
        val current = order.value ?: return
        if (_cancelling.value) return
        _cancelling.value = true
        viewModelScope.launch {
            try {
                _message.value = repository.cancel(current.number).messageRes()
            } finally {
                _cancelling.value = false
            }
        }
    }

    private var isRepeating = false

    suspend fun repeat(): OrderRepeatResult? {
        val current = order.value ?: return null
        if (isRepeating) return null
        isRepeating = true
        return try {
            repeatOrderUseCase(current).toUiResult()
        } finally {
            isRepeating = false
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        internal const val STATUS_REFRESH_MILLIS = 30_000L

        val Factory = viewModelFactory {
            initializer {
                val number = createSavedStateHandle().toRoute<OrderDetailsRoute>().number
                OrderDetailsViewModel(number, container.orders, container.repeatOrder)
            }
        }
    }
}
