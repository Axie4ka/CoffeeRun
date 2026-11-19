package ru.coffeerun

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.domain.usecase.PlaceOrderUseCase
import ru.coffeerun.ui.checkout.CheckoutViewModel

fun testCheckout(
    cart: CartRepository,
    orders: Orders,
    shops: ShopRepository,
    now: () -> Long = { Instant.parse("2026-11-10T09:00:00Z").toEpochMilli() },
    ticks: Flow<Unit> = flowOf(Unit)
): CheckoutViewModel {
    val policy = testPickupPolicy(now)
    return CheckoutViewModel(cart, PlaceOrderUseCase(cart, orders, InMemoryAuthRepository(loggedIn = true), policy, DemoMenuRepository()), shops, policy, ticks)
}
