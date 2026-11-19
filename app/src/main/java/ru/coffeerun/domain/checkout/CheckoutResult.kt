package ru.coffeerun.domain.checkout

import ru.coffeerun.model.Order
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import ru.coffeerun.data.session.AuthSession

data class CheckoutQuote internal constructor(
    internal val session: AuthSession,
    val originalLines: List<CartLine>,
    val updatedLines: List<CartLine>,
    val shop: Shop,
    val pickupAtMillis: Long?
) {
    fun matches(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?): Boolean =
        originalLines == lines && this.shop == shop && this.pickupAtMillis == pickupAtMillis
}

/** Результат всего сценария покупки, без текстов и навигации. */
sealed interface CheckoutResult {
    data class Placed(val order: Order) : CheckoutResult
    data class PricesChanged(val quote: CheckoutQuote) : CheckoutResult
    data class UnavailableLines(val names: List<String>) : CheckoutResult
    data object CartChanged : CheckoutResult
    data object InvalidTotal : CheckoutResult
    data object CatalogUnavailable : CheckoutResult
    data object EmptyCart : CheckoutResult
    data object InvalidPickupTime : CheckoutResult
    data object Unavailable : CheckoutResult
    data object NetworkError : CheckoutResult
}
