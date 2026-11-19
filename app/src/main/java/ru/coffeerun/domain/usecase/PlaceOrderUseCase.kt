package ru.coffeerun.domain.usecase

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession
import ru.coffeerun.domain.checkout.CheckoutResult
import ru.coffeerun.domain.checkout.CheckoutQuote
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.model.Shop
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.model.MAX_QUANTITY

/** Одна покупка: снимок корзины, проверка времени, отправка и применение подтверждённого результата. */
class PlaceOrderUseCase(
    private val cart: CartRepository,
    private val orders: Orders,
    private val sessions: SessionProvider,
    private val pickup: PickupPolicy,
    private val menu: MenuRepository
) {
    suspend operator fun invoke(shop: Shop, pickupAtMillis: Long?, confirmed: CheckoutQuote? = null): CheckoutResult {
        val session = sessions.currentSession()
        return sessions.runInSession(session) {
            val lines = sessions.withSession(session) { cart.lines.value.toList() }
            if (lines.isEmpty()) return@runInSession CheckoutResult.EmptyCart
            if (confirmed != null && (confirmed.session != session || !confirmed.matches(lines, shop, pickupAtMillis))) {
                return@runInSession CheckoutResult.CartChanged
            }
            if (!pickup.isValid(shop, pickupAtMillis)) return@runInSession CheckoutResult.InvalidPickupTime
            val snapshot = try {
                menu.refresh()
            } catch (_: IOException) {
                return@runInSession CheckoutResult.CatalogUnavailable
            }
            currentCoroutineContext().ensureActive()
            if (sessions.withSession(session) { cart.lines.value != lines }) return@runInSession CheckoutResult.CartChanged
            if (!pickup.isValid(shop, pickupAtMillis)) return@runInSession CheckoutResult.InvalidPickupTime
            val products = snapshot.products.associateBy { it.id }
            val unavailable = mutableListOf<String>()
            val updated = lines.mapNotNull { line ->
                val product = products[line.product.id]
                val price = product?.recipePrice(line.options, snapshot.prices)
                if (product == null || price == null || line.quantity !in MIN_QUANTITY..MAX_QUANTITY) {
                    unavailable += line.product.name
                    null
                } else line.copy(product = product, unitPrice = price)
            }
            if (unavailable.isNotEmpty()) return@runInSession CheckoutResult.UnavailableLines(unavailable.distinct())
            if (updated.sumOf { it.unitPrice.toLong() * it.quantity } > Int.MAX_VALUE) {
                return@runInSession CheckoutResult.InvalidTotal
            }
            val shown = confirmed?.updatedLines ?: lines
            if (updated.map { it.unitPrice } != shown.map { it.unitPrice }) {
                return@runInSession CheckoutResult.PricesChanged(CheckoutQuote(session, lines, updated, shop, pickupAtMillis))
            }
            when (val result = orders.place(updated, shop, pickupAtMillis, session)) {
                is PlaceOrderResult.Placed -> {
                    currentCoroutineContext().ensureActive()
                    sessions.withSession(session) { cart.removeOrdered(lines) }
                    CheckoutResult.Placed(result.order)
                }
                PlaceOrderResult.InvalidPickupTime -> CheckoutResult.InvalidPickupTime
                PlaceOrderResult.Unavailable -> CheckoutResult.Unavailable
                PlaceOrderResult.NetworkError -> CheckoutResult.NetworkError
            }
        }
    }
}
