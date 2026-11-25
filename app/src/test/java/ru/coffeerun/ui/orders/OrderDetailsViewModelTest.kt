package ru.coffeerun.ui.orders

import ru.coffeerun.testRepeatOrder

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.R
import ru.coffeerun.data.CancelOrderResult
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.Shop
import ru.coffeerun.model.OrderItem
import ru.coffeerun.model.toOrderItem

/** Экран заказа: сначала то, что есть в списке, потом свежий заказ с сервера; ошибки, 404, отмена и повтор. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val accepted = Order(
        1042, OrderStatus.ACCEPTED, listOf(CartLine(DemoMenu.CROISSANT, unitPrice = 180, quantity = 2).toOrderItem()),
        Shop.MOSKOVSKAYA, placedAtMillis = 0L, pickupAtMillis = 1L
    )

    /** Список уже содержит [listed]; сервер отвечает [onLoad]. */
    private class FakeOrders(listed: List<Order>, var onLoad: suspend () -> Order?) : Orders {
        override val isLoaded = MutableStateFlow(true)
        override val orders = MutableStateFlow(listed)
        var onCancel: (() -> CancelOrderResult)? = null
        override suspend fun refresh() = Unit
        override suspend fun resolveAttempt(attemptId: String, session: AuthSession): Order? = error("unused")
        override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession, attemptId: String, beforeSend: suspend () -> Boolean) = PlaceOrderResult.NetworkError
        override suspend fun cancel(number: Int): CancelOrderResult {
            val result = onCancel?.invoke()
                ?: CancelOrderResult.Cancelled(orders.value.first { it.number == number }.copy(status = OrderStatus.CANCELLED))
            if (result is CancelOrderResult.Cancelled) save(result.order)
            return result
        }
        override suspend fun load(number: Int): Order? = onLoad().also { loaded ->
            if (loaded != null) save(loaded) else orders.update { list -> list.filterNot { it.number == number } }
        }
        override fun find(number: Int): Order? = orders.value.find { it.number == number }

        private fun save(order: Order) {
            orders.update { list -> list.filterNot { it.number == order.number } + order }
        }
    }

    private val cart = InMemoryCartRepository()

    private fun TestScope.openDetails(number: Int, orders: Orders): OrderDetailsViewModel {
        val vm = OrderDetailsViewModel(number, orders, testRepeatOrder(DemoMenuRepository(), cart))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.refreshWhileVisible() }
        runCurrent()
        return vm
    }

    @Test
    fun pollsOnlyWhileVisibleAndActive() = runTest {
        var calls = 0
        var status = OrderStatus.PREPARING
        val orders = FakeOrders(listOf(accepted)) { calls++; accepted.copy(status = status) }
        val vm = OrderDetailsViewModel(1042, orders, testRepeatOrder(DemoMenuRepository(), cart))
        val visible = backgroundScope.launch { vm.refreshWhileVisible() }
        runCurrent()
        assertEquals(OrderStatus.PREPARING, vm.order.value?.status)
        status = OrderStatus.READY
        advanceTimeBy(OrderDetailsViewModel.STATUS_REFRESH_MILLIS)
        runCurrent()
        assertEquals(OrderStatus.READY, vm.order.value?.status)
        visible.cancel()
        advanceTimeBy(OrderDetailsViewModel.STATUS_REFRESH_MILLIS * 2)
        assertEquals(2, calls)
        status = OrderStatus.PICKED_UP
        backgroundScope.launch { vm.refreshWhileVisible() }
        runCurrent()
        assertEquals(OrderStatus.PICKED_UP, vm.order.value?.status)
        advanceTimeBy(OrderDetailsViewModel.STATUS_REFRESH_MILLIS * 2)
        assertEquals(3, calls)
    }

    @Test
    fun freshStatusFromServer_replacesListed() = runTest {
        val vm = openDetails(1042, FakeOrders(listOf(accepted)) { accepted.copy(status = OrderStatus.READY) })
        assertEquals(OrderStatus.READY, vm.order.value!!.status)
        assertEquals(OrdersLoad.DONE, vm.load.value)
        assertNull(vm.message.value)
    }

    @Test
    fun offline_withListed_showsItAndExplains() = runTest {
        val vm = openDetails(1042, FakeOrders(listOf(accepted)) { throw IOException("нет сети") })
        assertEquals("заказ из списка остаётся на экране", accepted, vm.order.value)
        assertEquals(R.string.orders_refresh_error, vm.message.value)
    }

    @Test
    fun offline_nothingListed_retryLoads() = runTest {
        val orders = FakeOrders(emptyList()) { throw IOException("нет сети") }
        val vm = openDetails(1042, orders)
        assertNull(vm.order.value)
        assertEquals("показывать нечего — экран просит повторить", OrdersLoad.FAILED, vm.load.value)
        assertNull("снэкбар не нужен: на экране и так ошибка", vm.message.value)
        orders.onLoad = { accepted }
        vm.retry()
        runCurrent()
        assertEquals(accepted, vm.order.value)
    }

    @Test
    fun unknownOrder_closesScreen() = runTest {
        val vm = openDetails(9999, FakeOrders(emptyList()) { null })
        assertTrue(vm.notFound.value)
    }

    @Test
    fun cancel_showsCancelled_andMessage() = runTest {
        val vm = openDetails(1042, FakeOrders(listOf(accepted)) { accepted })
        vm.cancel()
        assertEquals(OrderStatus.CANCELLED, vm.order.value!!.status)
        assertEquals(R.string.order_cancelled, vm.message.value)
        assertFalse(vm.cancelling.value)
    }

    @Test
    fun repeat_putsLinesBackToCart() = runTest {
        val vm = openDetails(1042, FakeOrders(listOf(accepted)) { accepted })
        assertEquals(OrderRepeatResult(added = true), vm.repeat())
        assertEquals(listOf(DemoMenu.CROISSANT), cart.lines.value.map { it.product })
        assertEquals(2, cart.lines.value.single().quantity)
    }

    @Test
    fun repeat_skipsUnavailableLine() = runTest {
        val past = accepted.copy(lines = accepted.lines + OrderItem("pumpkin_latte", null, 320))
        val vm = openDetails(1042, FakeOrders(listOf(past)) { past })
        assertEquals(OrderRepeatResult(added = true, message = R.string.order_repeat_partial), vm.repeat())
        assertEquals(listOf("croissant"), cart.lines.value.map { it.product.id })
    }

    @Test
    fun repositoryStatusChange_afterLoad_updatesOpenDetails() = runTest {
        val orders = FakeOrders(listOf(accepted)) { accepted }
        val vm = openDetails(1042, orders)
        orders.orders.value = listOf(accepted.copy(status = OrderStatus.READY))
        assertEquals(OrderStatus.READY, vm.order.value?.status)
        assertEquals(orders.find(1042), vm.order.value)
    }

    @Test
    fun repositoryCleared_afterLoad_removesOrderFromDetails() = runTest {
        val orders = FakeOrders(listOf(accepted)) { accepted }
        val vm = openDetails(1042, orders)
        orders.orders.value = emptyList()
        assertNull(vm.order.value)
    }

    @Test
    fun cancelTooLate_usesUpdatedRepositoryStatus() = runTest {
        val orders = FakeOrders(listOf(accepted)) { accepted }
        val vm = openDetails(1042, orders)
        orders.onCancel = {
            orders.orders.value = listOf(accepted.copy(status = OrderStatus.PREPARING))
            CancelOrderResult.TooLate
        }
        vm.cancel()
        assertEquals(OrderStatus.PREPARING, vm.order.value?.status)
        assertEquals(R.string.order_cancel_too_late, vm.message.value)
        assertFalse(vm.cancelling.value)
    }

    @Test
    fun retry_cancelsPreviousLoad_andKeepsLatestResult() = runTest {
        val firstReply = CompletableDeferred<Unit>()
        var firstCancelled = false
        var calls = 0
        val orders = FakeOrders(listOf(accepted)) {
            if (++calls == 1) {
                try {
                    firstReply.await()
                    accepted
                } finally {
                    firstCancelled = !firstReply.isCompleted
                }
            } else accepted.copy(status = OrderStatus.READY)
        }
        val vm = openDetails(1042, orders)
        vm.retry()
        runCurrent()
        assertTrue("предыдущий запрос должен быть отменён", firstCancelled)
        firstReply.complete(Unit)
        assertEquals(OrderStatus.READY, vm.order.value?.status)
        assertEquals(OrdersLoad.DONE, vm.load.value)
    }
}
