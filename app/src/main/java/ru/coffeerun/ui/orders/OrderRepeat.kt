package ru.coffeerun.ui.orders

import androidx.annotation.StringRes
import ru.coffeerun.R
import ru.coffeerun.domain.orders.RepeatOrderResult

/** UI решает, показывать ли корзину и какое локализованное сообщение вывести. */
data class OrderRepeatResult(val added: Boolean, @StringRes val message: Int? = null)

internal fun RepeatOrderResult.toUiResult(): OrderRepeatResult = when (this) {
    is RepeatOrderResult.Added -> OrderRepeatResult(true, when {
        skipped.isNotEmpty() && pricesChanged -> R.string.order_repeat_partial_prices
        skipped.isNotEmpty() -> R.string.order_repeat_partial
        pricesChanged -> R.string.order_repeat_updated_prices
        else -> null
    })
    is RepeatOrderResult.Unavailable -> OrderRepeatResult(false, R.string.order_repeat_unavailable)
    RepeatOrderResult.NetworkError -> OrderRepeatResult(false, R.string.order_repeat_error)
}
