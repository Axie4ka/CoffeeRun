package ru.coffeerun.domain.usecase

import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
        override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession, attemptId: String, beforeSend: suspend () -> Boolean): PlaceOrderResult {
            if (!beforeSend()) return PlaceOrderResult.CartChanged
            calls++
            receivedSession = session
            submitted = lines
            if (uncooperative) withContext(NonCancellable) { gate?.await() } else gate?.await()
            return result ?: delegate.place(lines, shop, pickupAtMillis, session, attemptId)
        }
    }
    private val orders = ControlledOrders()
    private fun useCase(menu: MenuRepository = DemoMenuRepository()) = PlaceOrderUseCase(cart, orders, sessions, policy, menu)
    private fun add(quantity: Int = 1) = cart.add(CartLine(DemoMenu.CROISSANT, 180, quantity))

    @Test fun changedPrice_doesNotSendOldCartWithoutConfirmation() = runTest {
        cart.add(CartLine(DemoMenu.CROISSANT, 150, 2))
        val before = cart.lines.value

        val place = useCase()
        val result = place(Shop.MOSKOVSKAYA, null) as CheckoutResult.PricesChanged

        assertEquals(0, orders.calls)
        assertEquals(before, cart.lines.value)
        assertEquals(180, result.quote.updatedLines.single().unitPrice)
        assertTrue(place(Shop.MOSKOVSKAYA, null, result.quote) is CheckoutResult.Placed)
        assertEquals(180, orders.submitted.single().unitPrice)
        assertTrue(cart.lines.value.isEmpty())
    }

    @Test fun priceChangesAgain_requiresAnotherConfirmation() = runTest {
        add()
        var price = 200
        val menu = object : MenuRepository by DemoMenuRepository() {
            override suspend fun refresh() = MenuSnapshot(listOf(DemoMenu.CROISSANT.copy(price = price)), DemoMenu.prices)
        }
        val place = useCase(menu)
        val first = (place(Shop.MOSKOVSKAYA, null) as CheckoutResult.PricesChanged).quote
        price = 180
        val second = (place(Shop.MOSKOVSKAYA, null, first) as CheckoutResult.PricesChanged).quote
        assertEquals(180, second.updatedLines.single().unitPrice)
        assertEquals(0, orders.calls)
    }

    @Test fun catalogUnavailable_orProductRemoved_doesNotSubmitOrConsumeCart() = runTest {
        add()
        val before = cart.lines.value
        val menu = object : MenuRepository by DemoMenuRepository() {
            override suspend fun refresh(): MenuSnapshot = throw java.io.IOException("offline")
        }
        assertEquals(CheckoutResult.CatalogUnavailable, useCase(menu)(Shop.MOSKOVSKAYA, null))
        val emptyMenu = object : MenuRepository by DemoMenuRepository() {
            override suspend fun refresh() = MenuSnapshot(emptyList(), DemoMenu.prices)
        }
        assertEquals(CheckoutResult.UnavailableLines(listOf(DemoMenu.CROISSANT.name)),
            useCase(emptyMenu)(Shop.MOSKOVSKAYA, null))
        assertEquals(0, orders.calls)
        assertEquals(before, cart.lines.value)
    }

    @Test fun cartChangedDuringCatalogRefresh_keepsNewCartAndDoesNotSubmit() = runTest {
        add()
        val gate = CompletableDeferred<Unit>()
        val menu = object : MenuRepository by DemoMenuRepository() {
            override suspend fun refresh(): MenuSnapshot {
                gate.await()
                return MenuSnapshot(DemoMenu.products, DemoMenu.prices)
            }
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { useCase(menu)(Shop.MOSKOVSKAYA, null) }
        add()
        gate.complete(Unit)
        assertEquals(CheckoutResult.CartChanged, pending.await())
        assertEquals(0, orders.calls)
        assertEquals(2, cart.lines.value.single().quantity)
    }

    @Test fun beginFailureAllowsRetryWithoutPostingOrRequiringRecovery() = runTest {
        sessions.replaceToken("token", "owner")
        var broken = true
        val store = object : CheckoutAttemptStore {
            override fun observeKeyedAttempt(owner: String) = kotlinx.coroutines.flow.flowOf<Boolean?>(null)
            override suspend fun activeId(owner: String): String? = null
            override suspend fun isLegacy(owner: String, id: String) = false
            override suspend fun begin(owner: String, id: String, lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?): Boolean {
                if (broken) throw java.io.IOException("storage full")
                return true
            }
            override suspend fun finish(owner: String, id: String, order: ru.coffeerun.model.Order?) = true
            override suspend fun dismiss(owner: String, id: String) = false
        }
        val place = PlaceOrderUseCase(cart, orders, sessions, policy, DemoMenuRepository(), store)
        add()
        assertEquals(CheckoutResult.LocalStorageUnavailable, place(Shop.MOSKOVSKAYA, null))
        assertEquals(0, orders.calls)
        assertEquals(CheckoutAttemptStatus.Idle, place.attemptStatus.first())
        broken = false
        assertTrue(place(Shop.MOSKOVSKAYA, null) is CheckoutResult.Placed)
        assertEquals(1, orders.calls)
    }

    @Test fun emptyCart_doesNotSendOrder() = runTest {
        assertEquals(CheckoutResult.EmptyCart, useCase()(Shop.MOSKOVSKAYA, null))
        assertEquals(0, orders.calls)
    }

    @Test fun cancelledWaiter_afterSubmission_blocksSecondCheckout() = runTest {
        add()
        val place = useCase()
        orders.gate = CompletableDeferred()
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { place(Shop.MOSKOVSKAYA, null) }
        assertEquals(1, orders.calls)
        pending.cancelAndJoin()
        orders.gate!!.complete(Unit)

        assertEquals(CheckoutResult.AttemptPending, place(Shop.MOSKOVSKAYA, null))

        assertEquals(1, orders.calls)
        assertEquals(1, cart.lines.value.single().quantity)
    }

    @Test fun unknownOutcome_requiresExplicitReleaseWithoutConsumingCart() = runTest {
        add()
        val place = useCase()
        orders.result = PlaceOrderResult.OutcomeUnknown
        assertEquals(CheckoutResult.AttemptPending, place(Shop.MOSKOVSKAYA, null))
        assertEquals(CheckoutResult.AttemptPending, place(Shop.MOSKOVSKAYA, null))
        assertEquals(1, orders.calls)
        assertNull(place.recoverOrder())
        assertEquals(1, orders.calls)
        assertEquals(1, cart.lines.value.single().quantity)
        orders.result = null
        assertTrue(place(Shop.MOSKOVSKAYA, null) is CheckoutResult.Placed)
        assertEquals(2, orders.calls)
    }

    @Test fun newSession_doesNotInheritUnknownAttemptFromPreviousLogin() = runTest {
        add()
        val place = useCase()
        orders.result = PlaceOrderResult.OutcomeUnknown
        assertEquals(CheckoutResult.AttemptPending, place(Shop.MOSKOVSKAYA, null))
        sessions.replaceToken("token")
        cart.clear()
        add()
        orders.result = null

        assertTrue(place(Shop.MOSKOVSKAYA, null) is CheckoutResult.Placed)
        assertEquals(2, orders.calls)
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
