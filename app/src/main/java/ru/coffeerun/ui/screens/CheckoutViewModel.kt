package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.CLOSING_HOUR
import ru.coffeerun.model.READY_IN_MINUTES
import ru.coffeerun.model.Shop
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class CheckoutViewModel(
    private val cart: CartRepository,
    private val orders: OrderRepository,
    private val shops: ShopRepository,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {
    val lines: StateFlow<List<CartLine>> = cart.lines

    val shop: StateFlow<Shop> = shops.selected

    private val _pickupAtMillis = MutableStateFlow<Long?>(null)

    /** Выбранное время самовывоза сегодня; null — «Как можно скорее». */
    val pickupAtMillis: StateFlow<Long?> = _pickupAtMillis.asStateFlow()

    /**
     * Выбирает время самовывоза сегодня. false (состояние не меняется), если оно
     * раньше чем через [READY_IN_MINUTES] минут или позже закрытия.
     */
    fun setPickupTime(hour: Int, minute: Int): Boolean {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
        val millis = today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val closing = today.atTime(CLOSING_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        if (millis < now() + READY_IN_MINUTES * 60_000L || millis > closing) return false
        _pickupAtMillis.value = millis
        return true
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

    private val _placedOrderNumber = MutableStateFlow<Int?>(null)

    /**
     * Номер только что оформленного заказа — событие как состояние: View переходит
     * на «Заказ принят» и сообщает об этом через [onPlacedOrderShown].
     */
    val placedOrderNumber: StateFlow<Int?> = _placedOrderNumber.asStateFlow()

    /** Оформляет заказ из корзины и очищает её. Пустая корзина — ничего не происходит. */
    fun placeOrder() {
        val lines = cart.lines.value
        if (lines.isEmpty()) return
        _placedOrderNumber.value = orders.place(lines, shops.selected.value, _pickupAtMillis.value).number
        cart.clear()
    }

    fun onPlacedOrderShown() {
        _placedOrderNumber.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CheckoutViewModel(container.cart, container.orders, container.shops) }
        }
    }
}
