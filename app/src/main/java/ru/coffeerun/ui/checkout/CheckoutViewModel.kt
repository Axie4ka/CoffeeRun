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
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.CLOSING_HOUR
import ru.coffeerun.model.READY_IN_MINUTES
import ru.coffeerun.model.Shop
import java.time.Instant
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
    private val orders: OrderRepository,
    private val shops: ShopRepository,
    private val now: () -> Long = System::currentTimeMillis,
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
        placedOrderNumber = submit.placedOrderNumber,
        isPlacing = submit.isPlacing,
        message = submit.message
    )

    /** Кофейня общая с меню и профилем: выбор здесь меняет её и там. */
    fun selectShop(shop: Shop) = shops.select(shop)

    /**
     * Выбирает время самовывоза сегодня. false (состояние не меняется), если оно
     * раньше чем через [READY_IN_MINUTES] минут или позже закрытия.
     */
    fun setPickupTime(hour: Int, minute: Int): Boolean {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        val millis = today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        if (!isPickupValid(millis)) return false
        _pickupAtMillis.value = millis
        return true
    }

    /** Время допустимо: не раньше now + [READY_IN_MINUTES] и не позже закрытия сегодня. */
    private fun isPickupValid(millis: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        val closing = today.atTime(CLOSING_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        return millis >= now() + READY_IN_MINUTES * 60_000L && millis <= closing
    }

    /**
     * Самое раннее время самовывоза: now + [READY_IN_MINUTES], округлённое вверх до 5 минут.
     * null — сегодня уже нельзя (позже закрытия или за полночь).
     */
    fun earliestPickupTime(): LocalTime? {
        val step = 5 * 60_000L
        val millis = (now() + READY_IN_MINUTES * 60_000L + step - 1) / step * step
        val zone = ZoneId.systemDefault()
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        val closing = today.atTime(CLOSING_HOUR, 0)
        return at.toLocalDateTime().takeIf { it.toLocalDate() == today && !it.isAfter(closing) }?.toLocalTime()
    }

    fun pickupAsap() {
        _pickupAtMillis.value = null
    }

    /**
     * Оформляет заказ из корзины и очищает её. Пустая корзина или заказ уже отправлен — ничего не происходит.
     * Сервер не принял заказ — корзина остаётся как была, а в [CheckoutUiState.message] — почему.
     */
    fun placeOrder() {
        val lines = cart.lines.value
        if (lines.isEmpty() || submit.value.isPlacing) return
        // Выбранное время могло устареть, пока открыт экран: тогда — «как можно скорее».
        if (_pickupAtMillis.value?.let(::isPickupValid) == false) _pickupAtMillis.value = null
        submit.value = Submit(isPlacing = true)
        viewModelScope.launch {
            submit.value = when (val result = orders.place(lines, shops.selected.value, _pickupAtMillis.value)) {
                is PlaceOrderResult.Placed -> {
                    cart.clear()
                    Submit(placedOrderNumber = result.order.number)
                }
                PlaceOrderResult.Unavailable -> Submit(message = R.string.order_unavailable)
                PlaceOrderResult.NetworkError -> Submit(message = R.string.order_network_error)
            }
        }
    }

    /** View перешла на «Заказ принят» — событие обработано, повторного перехода не будет. */
    fun onPlacedOrderShown() = submit.update { it.copy(placedOrderNumber = null) }

    /** Снэкбар показан — второй раз его не будет. */
    fun onMessageShown() = submit.update { it.copy(message = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer { CheckoutViewModel(container.cart, container.orders, container.shops) }
        }
    }
}
