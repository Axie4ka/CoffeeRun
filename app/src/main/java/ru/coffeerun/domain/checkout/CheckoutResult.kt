package ru.coffeerun.domain.checkout

import ru.coffeerun.model.Order

/** Результат всего сценария покупки, без текстов и навигации. */
sealed interface CheckoutResult {
    data class Placed(val order: Order) : CheckoutResult
    data object EmptyCart : CheckoutResult
    data object InvalidPickupTime : CheckoutResult
    data object Unavailable : CheckoutResult
    data object NetworkError : CheckoutResult
}
