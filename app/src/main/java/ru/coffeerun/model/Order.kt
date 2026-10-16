package ru.coffeerun.model

import androidx.annotation.StringRes
import ru.coffeerun.R

/** Через сколько минут после оформления заказ готов (пока фиксированно). */
const val READY_IN_MINUTES = 15L

enum class OrderStatus(@StringRes val labelRes: Int) {
    ACCEPTED(R.string.status_accepted),
    PREPARING(R.string.status_preparing),
    READY(R.string.status_ready),
    PICKED_UP(R.string.status_picked_up)
}

enum class Shop(@StringRes val addressRes: Int) {
    MOSKOVSKAYA(R.string.shop_moskovskaya),
    STROITELEY(R.string.shop_stroiteley)
}

data class Order(
    val number: Int,
    val status: OrderStatus,
    val lines: List<CartLine>,
    val shop: Shop,
    val placedAtMillis: Long
) {
    val total: Int get() = lines.sumOf { it.total }
    val readyAtMillis: Long get() = placedAtMillis + READY_IN_MINUTES * 60_000L

    /** Активный заказ — ещё не выдан клиенту. */
    val isActive: Boolean get() = status != OrderStatus.PICKED_UP
}
