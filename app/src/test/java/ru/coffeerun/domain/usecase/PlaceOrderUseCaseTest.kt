package ru.coffeerun.domain.usecase

import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.coffeerun.data.*
import ru.coffeerun.data.session.*
import ru.coffeerun.domain.checkout.*
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.model.*

class PlaceOrderUseCaseTest {
    private val cart = InMemoryCartRepository()
    private val sessions = SessionStateStore("token")
    private val policy = PickupPolicy(now = { Instant.parse("2026-11-10T09:00:00Z").toEpochMilli() })
    private class ControlledOrders(private val delegate: InMemoryOrders = InMemoryOrders()) : Orders by delegate {
        var calls = 0
        var receivedSession: AuthSession? = null
        var submitted = emptyList<CartLine>()
        var gate: CompletableDeferred<Unit>? = null
        var result: PlaceOrderResult? = null
        var uncooperative = false
        override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession): PlaceOrderResult {
            calls++
            receivedSession = session
            submitted = lines
            if (uncooperative) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return result ?: delegate.place(lines, shop, pickupAtMillis, session)
        }
    }
    private val orders = ControlledOrders()
    private fun useCase() = PlaceOrderUseCase(cart, orders, sessions, policy)
    private fun add(quantity: Int = 1) = cart.add(CartLine(DemoMenu.CROISSANT, 180, quantity))

    @Test fun emptyCart_doesNotSendOrder() = runTest {
        assertEquals(CheckoutResult.EmptyCart, useCase()(Shop.MOSKOVSKAYA, null))
        assertEquals(0, orders.calls)
    }

    @Test fun invalidPickup_keepsCartAndDoesNotSend() = runTest {
        add()
        val before = cart.lines.value
        val stale = Instant.parse("2026-11-10T09:10:00Z").toEpochMilli()
        assertEquals(CheckoutResult.InvalidPickupTime, useCase()(Shop.MOSKOVSKAYA, stale))
        assertEquals(before, cart.lines.value)
        assertEquals(0, orders.calls)
    }

    @Test fun success_submitsSnapshotAndRemovesOnlyItsQuantities() = runTest {
        add(2)
        val expectedSession = sessions.currentSession()
        orders.gate = CompletableDeferred()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { useCase()(Shop.STROITELEY, null) }
        add()
        cart.add(CartLine(DemoMenu.LATTE, 240))
        assertEquals(2, orders.submitted.single().quantity)
        assertEquals(expectedSession, orders.receivedSession)
        orders.gate!!.complete(Unit)
        val result = pending.await() as CheckoutResult.Placed
        assertEquals(Shop.STROITELEY, result.order.shop)
        assertEquals(listOf(1, 1), cart.lines.value.map { it.quantity })
    }

    @Test fun readdedLine_survivesOldSuccess() = runTest {
        add()
        orders.gate = CompletableDeferred()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { useCase()(Shop.MOSKOVSKAYA, null) }
        cart.clear()
        add()
        val replacement = cart.lines.value.single()
        orders.gate!!.complete(Unit)
        assertTrue(pending.await() is CheckoutResult.Placed)
        assertEquals(listOf(replacement), cart.lines.value)
    }

    @Test fun rejectedOrUnavailableOrder_keepsCart() = runTest {
        add()
        val before = cart.lines.value
        orders.result = PlaceOrderResult.Unavailable
        assertEquals(CheckoutResult.Unavailable, useCase()(Shop.MOSKOVSKAYA, null))
        orders.result = PlaceOrderResult.NetworkError
        assertEquals(CheckoutResult.NetworkError, useCase()(Shop.MOSKOVSKAYA, null))
        assertEquals(before, cart.lines.value)
    }

    @Test fun changedSession_doesNotConsumeNewCartOrReturnSuccess() = runTest {
        add()
        orders.uncooperative = true
        orders.gate = CompletableDeferred()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { useCase()(Shop.MOSKOVSKAYA, null) } }
        sessions.replaceToken("token") // даже та же строка означает новый вход
        cart.clear()
        add(3)
        val newCart = cart.lines.value
        orders.gate!!.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(newCart, cart.lines.value)
    }

    @Test fun cancelledCaller_keepsCart() = runTest {
        add()
        val before = cart.lines.value
        orders.gate = CompletableDeferred()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { useCase()(Shop.MOSKOVSKAYA, null) }
        pending.cancelAndJoin()
        assertEquals(before, cart.lines.value)
    }

    @Test fun guest_doesNotSendPersonalOrder() = runTest {
        add()
        sessions.replaceToken(null)
        assertTrue(runCatching { useCase()(Shop.MOSKOVSKAYA, null) }.exceptionOrNull() is CancellationException)
        assertEquals(0, orders.calls)
    }
}
