package ru.coffeerun.domain.usecase

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession
import ru.coffeerun.domain.checkout.CheckoutResult
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.model.Shop

/** Одна покупка: снимок корзины, проверка времени, отправка и применение подтверждённого результата. */
class PlaceOrderUseCase(
    private val cart: CartRepository,
    private val orders: Orders,
    private val sessions: SessionProvider,
    private val pickup: PickupPolicy
) {
    suspend operator fun invoke(shop: Shop, pickupAtMillis: Long?): CheckoutResult {
        val session = sessions.currentSession()
        return sessions.runInSession(session) {
            val lines = sessions.withSession(session) { cart.lines.value.toList() }
            if (lines.isEmpty()) return@runInSession CheckoutResult.EmptyCart
            if (!pickup.isValid(shop, pickupAtMillis)) return@runInSession CheckoutResult.InvalidPickupTime
            when (val result = orders.place(lines, shop, pickupAtMillis, session)) {
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
