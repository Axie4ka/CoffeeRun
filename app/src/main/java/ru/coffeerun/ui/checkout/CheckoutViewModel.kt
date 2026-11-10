package ru.coffeerun.ui.checkout

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.domain.usecase.PlaceOrderUseCase
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.checkout.CheckoutResult
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import java.time.LocalTime
import java.time.ZoneId

/**
 * Состояние экрана оформления. [pickupAtMillis] = null — «как можно скорее»;
 * [earliestPickup] = null — заказ ко времени сегодня уже невозможен;
 * [placedOrderNumber] — номер только что оформленного заказа (событие как состояние, см. [CheckoutViewModel.onPlacedOrderShown]);
 * [isPlacing] — заказ отправлен, ждём сервер; [message] — разовое сообщение для снэкбара, почему заказ не принят.
 */
data class CheckoutUiState(
    val lines: List<CartLine>,
    val total: Int,
    val shop: Shop,
    val pickupAtMillis: Long?,
    val earliestPickup: LocalTime?,
    val pickupZone: ZoneId,
    val placedOrderNumber: Int?,
    val isPlacing: Boolean = false,
    @StringRes val message: Int? = null
)

/** Ход отправки заказа — одним потоком, чтобы три поля менялись вместе. */
private data class Submit(val isPlacing: Boolean = false, val placedOrderNumber: Int? = null, @StringRes val message: Int? = null)

/** «Прошла минута»: пока экран открыт, по нему пересчитывается самое раннее время самовывоза. */
private fun everyMinute(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(60_000)
    }
}

class CheckoutViewModel(
    private val cart: CartRepository,
    private val placeOrderUseCase: PlaceOrderUseCase,
    private val shops: ShopRepository,
    private val pickupPolicy: PickupPolicy,
    ticks: Flow<Unit> = everyMinute()
) : ViewModel() {
    private val _pickupAtMillis = MutableStateFlow<Long?>(null)
    private val submit = MutableStateFlow(Submit())

    val uiState: StateFlow<CheckoutUiState> =
        combine(cart.lines, shops.selected, _pickupAtMillis, submit, ticks) { lines, shop, pickupAt, submit, _ ->
            stateOf(lines, shop, pickupAt, submit)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Начальное значение из текущих данных — чтобы экран не мигнул пустым заказом
            stateOf(cart.lines.value, shops.selected.value, _pickupAtMillis.value, submit.value)
        )

    private fun stateOf(lines: List<CartLine>, shop: Shop, pickupAt: Long?, submit: Submit) = CheckoutUiState(
        lines = lines,
        total = lines.sumOf { it.total },
        shop = shop,
        pickupAtMillis = pickupAt,
        earliestPickup = earliestPickupTime(),
        pickupZone = pickupPolicy.zone(shop),
        placedOrderNumber = submit.placedOrderNumber,
        isPlacing = submit.isPlacing,
        message = submit.message
    )

    /** Кофейня общая с меню и профилем: выбор здесь меняет её и там. */
    fun selectShop(shop: Shop) = shops.select(shop)

    /** Выбирает допустимое время в часовом поясе выбранной кофейни. */
    fun setPickupTime(hour: Int, minute: Int): Boolean {
        val selected = pickupPolicy.selectTime(shops.selected.value, hour, minute) ?: return false
        _pickupAtMillis.value = selected
        return true
    }

    fun earliestPickupTime(): LocalTime? = pickupPolicy.earliestTime(shops.selected.value)

    fun pickupAsap() {
        _pickupAtMillis.value = null
    }

    /**
     * Запускает сценарий покупки; удалением отправленных количеств занимается use case.
     * Пустая корзина или заказ уже отправлен — нового заказа не будет.
     * Сервер не принял заказ — корзина остаётся как была, а в [CheckoutUiState.message] — почему.
     */
    fun placeOrder() {
        if (submit.value.isPlacing) return
        val shop = shops.selected.value
        val pickupAt = _pickupAtMillis.value
        submit.value = Submit(isPlacing = true)
        viewModelScope.launch {
            try {
                submit.value = when (val result = placeOrderUseCase(shop, pickupAt)) {
                    is CheckoutResult.Placed -> Submit(placedOrderNumber = result.order.number)
                    CheckoutResult.EmptyCart -> Submit()
                    CheckoutResult.InvalidPickupTime -> Submit(message = R.string.pickup_time_unavailable)
                    CheckoutResult.Unavailable -> Submit(message = R.string.order_unavailable)
                    CheckoutResult.NetworkError -> Submit(message = R.string.order_network_error)
                }
            } finally {
                submit.update { it.copy(isPlacing = false) }
            }
        }
    }

    /** View перешла на «Заказ принят» — событие обработано, повторного перехода не будет. */
    fun onPlacedOrderShown() = submit.update { it.copy(placedOrderNumber = null) }

    /** Снэкбар показан — второй раз его не будет. */
    fun onMessageShown() = submit.update { it.copy(message = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer { CheckoutViewModel(container.cart, container.placeOrder, container.shops, container.pickupPolicy) }
        }
    }
}
