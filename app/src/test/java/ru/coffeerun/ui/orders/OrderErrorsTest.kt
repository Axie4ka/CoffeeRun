package ru.coffeerun.ui.orders

import ru.coffeerun.testRepeatOrder

import ru.coffeerun.testCheckout

import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.R
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.data.CancelOrderResult
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.Shop
import ru.coffeerun.model.toOrderItem
import ru.coffeerun.ui.checkout.CheckoutViewModel

/** Экраны заказов, когда сервер отвечает не «да»: оформление, загрузка истории и отмена. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderErrorsTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    /** Сервер, который отвечает [result]; пока [gate] не открыт — «ещё думает». */
    private class FakeOrders(var result: PlaceOrderResult = PlaceOrderResult.NetworkError) : Orders {
        override val isLoaded = MutableStateFlow(true)
        override val orders = MutableStateFlow<List<Order>>(emptyList())
        var gate: CompletableDeferred<Unit>? = null
        var placeCalls = 0
        var refreshFails = false
        var refreshCalls = 0
        var cancelResult: CancelOrderResult = CancelOrderResult.NetworkError
        var cancelCalls = 0
        var cancelAfterGate = false
        var placeAfterGate = false

        override suspend fun refresh() {
            refreshCalls++
            if (refreshFails) throw IOException("нет сети")
            orders.value = listOf(Order(1037, OrderStatus.PICKED_UP, emptyList(), Shop.MOSKOVSKAYA, 0L, total = 480))
        }

        override suspend fun resolveAttempt(attemptId: String, session: AuthSession): Order? = error("unused")
        override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession, attemptId: String, beforeSend: suspend () -> Boolean): PlaceOrderResult {
            if (!beforeSend()) return PlaceOrderResult.CartChanged
            placeCalls++
            gate?.await()
            if (placeAfterGate) throw CancellationException("смена сессии")
            return result
        }

        override suspend fun cancel(number: Int): CancelOrderResult {
            cancelCalls++
            gate?.await()
            if (cancelAfterGate) throw CancellationException("смена сессии")
            return cancelResult
        }

        override suspend fun load(number: Int): Order? = find(number)

        override fun find(number: Int): Order? = orders.value.find { it.number == number }
    }

    private val cart = InMemoryCartRepository().apply { add(CartLine(DemoMenu.CROISSANT, unitPrice = 180)) }
    private val orders = FakeOrders()
    private val checkout by lazy { testCheckout(cart, orders, InMemoryShopRepository(), ticks = flowOf(Unit)) }

    @Test
    fun conflict_keepsCart_andExplains() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checkout.uiState.collect {} }
        orders.result = PlaceOrderResult.Unavailable
        checkout.placeOrder()
        assertEquals(R.string.order_unavailable, checkout.uiState.value.message)
        assertNull(checkout.uiState.value.placedOrderNumber)
        assertEquals("корзина не пропала", 1, cart.lines.value.size)
        checkout.onMessageShown()
        assertNull("снэкбар второй раз не покажется", checkout.uiState.value.message)
    }

    @Test
    fun offline_keepsCart_andExplains() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checkout.uiState.collect {} }
        checkout.placeOrder()
        assertEquals(R.string.order_network_error, checkout.uiState.value.message)
        assertEquals(1, cart.lines.value.size)
        assertFalse(checkout.uiState.value.isPlacing)
    }

    @Test
    fun whilePlacing_buttonBusy_secondTapIgnored() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checkout.uiState.collect {} }
        val gate = CompletableDeferred<Unit>().also { orders.gate = it }
        orders.result = PlaceOrderResult.Placed(Order(1042, OrderStatus.PREPARING, cart.lines.value.map { it.toOrderItem() }, Shop.MOSKOVSKAYA, 0L))
        checkout.placeOrder()
        assertTrue(checkout.uiState.value.isPlacing)
        checkout.placeOrder()
        assertEquals("второе нажатие не отправит второй заказ", 1, orders.placeCalls)
        gate.complete(Unit)
        assertFalse(checkout.uiState.value.isPlacing)
        assertEquals(1042, checkout.uiState.value.placedOrderNumber)
        assertTrue(cart.lines.value.isEmpty())
    }

    @Test
    fun success_removesOnlySubmittedQuantities() = runTest {
        val gate = CompletableDeferred<Unit>().also { orders.gate = it }
        orders.result = PlaceOrderResult.Placed(Order(1042, OrderStatus.PREPARING, cart.lines.value.map { it.toOrderItem() }, Shop.MOSKOVSKAYA, 0L))
        checkout.placeOrder()
        cart.add(CartLine(DemoMenu.CROISSANT, unitPrice = 180, quantity = 2))
        cart.add(CartLine(DemoMenu.LATTE, unitPrice = 240))
        gate.complete(Unit)
        assertEquals(listOf("croissant", "latte"), cart.lines.value.map { it.product.id })
        assertEquals(listOf(2, 1), cart.lines.value.map { it.quantity })
    }

    @Test
    fun success_preservesRemovedAndReaddedLine() = runTest {
        val gate = CompletableDeferred<Unit>().also { orders.gate = it }
        orders.result = PlaceOrderResult.Placed(Order(1042, OrderStatus.PREPARING, cart.lines.value.map { it.toOrderItem() }, Shop.MOSKOVSKAYA, 0L))
        checkout.placeOrder()
        cart.clear()
        cart.add(CartLine(DemoMenu.CROISSANT, unitPrice = 180))
        gate.complete(Unit)
        assertEquals(1, cart.lines.value.single().quantity)
    }

    @Test
    fun sessionChangesWhilePlacing_keepsCartAndClearsBusy_withoutSuccessEvent() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checkout.uiState.collect {} }
        val before = cart.lines.value
        val gate = CompletableDeferred<Unit>().also { orders.gate = it }
        orders.placeAfterGate = true
        checkout.placeOrder()
        assertTrue(checkout.uiState.value.isPlacing)
        gate.complete(Unit)
        assertFalse(checkout.uiState.value.isPlacing)
        assertEquals(before, cart.lines.value)
        assertNull(checkout.uiState.value.placedOrderNumber)
        assertNull(checkout.uiState.value.message)
    }

    @Test
    fun sessionChangesWhileCancelling_clearsBusy_withoutOldMessage() = runTest {
        val vm = OrdersViewModel(orders, flowOf(true), testRepeatOrder(DemoMenuRepository(), cart))
        val gate = CompletableDeferred<Unit>().also { orders.gate = it }
        orders.cancelAfterGate = true
        vm.cancel(Order(1042, OrderStatus.ACCEPTED, emptyList(), Shop.MOSKOVSKAYA, 0L))
        assertEquals(1042, vm.cancelling.value)
        gate.complete(Unit)
        assertNull(vm.cancelling.value)
        assertNull(vm.message.value)
    }

    @Test
    fun ordersLoadFails_thenRetry_loads() = runTest {
        orders.refreshFails = true
        val vm = OrdersViewModel(orders, flowOf(true), testRepeatOrder(DemoMenuRepository(), cart))
        vm.onShown()
        assertEquals(OrdersLoad.FAILED, vm.load.value)
        orders.refreshFails = false
        vm.retry()
        assertEquals(OrdersLoad.DONE, vm.load.value)
        assertEquals(listOf(1037), vm.orders.value.map { it.number })
    }

    @Test
    fun eachShow_reloads_guestNever() = runTest {
        val loggedIn = MutableStateFlow(false)
        val vm = OrdersViewModel(orders, loggedIn, testRepeatOrder(DemoMenuRepository(), cart))
        vm.onShown()
        assertEquals("гостю грузить нечего", 0, orders.refreshCalls)
        loggedIn.value = true
        assertEquals("после входа грузит репозиторий, а не экран", 0, orders.refreshCalls)
        vm.onShown()
        vm.onShown()
        assertEquals("каждый показ вкладки — свежие статусы", 2, orders.refreshCalls)
    }

    private val evening = Order(1042, OrderStatus.ACCEPTED, emptyList(), Shop.MOSKOVSKAYA, 0L, pickupAtMillis = 1L)

    @Test
    fun cancel_eachResult_hasItsMessage() = runTest {
        val vm = OrdersViewModel(orders, flowOf(true), testRepeatOrder(DemoMenuRepository(), cart))
        val cases = listOf(
            CancelOrderResult.Cancelled(evening.copy(status = OrderStatus.CANCELLED)) to R.string.order_cancelled,
            CancelOrderResult.TooLate to R.string.order_cancel_too_late,
            CancelOrderResult.NetworkError to R.string.order_cancel_error
        )
        for ((result, message) in cases) {
            orders.cancelResult = result
            vm.cancel(evening)
            assertEquals(message, vm.message.value)
            vm.onMessageShown()
            assertNull(vm.message.value)
        }
    }

    @Test
    fun whileCancelling_buttonBusy_secondTapIgnored() = runTest {
        val vm = OrdersViewModel(orders, flowOf(true), testRepeatOrder(DemoMenuRepository(), cart))
        val gate = CompletableDeferred<Unit>().also { orders.gate = it }
        vm.cancel(evening)
        assertEquals(1042, vm.cancelling.value)
        vm.cancel(evening)
        assertEquals("второе нажатие не шлёт второй запрос", 1, orders.cancelCalls)
        gate.complete(Unit)
        assertNull(vm.cancelling.value)
    }

    @Test
    fun pullToRefresh_failure_keepsList_andExplains() = runTest {
        val vm = OrdersViewModel(orders, flowOf(true), testRepeatOrder(DemoMenuRepository(), cart))
        vm.onShown()
        assertEquals(listOf(1037), vm.orders.value.map { it.number })
        orders.refreshFails = true
        vm.refresh()
        assertFalse("индикатор погас", vm.refreshing.value)
        assertEquals(R.string.orders_refresh_error, vm.message.value)
        assertEquals("неудачное обновление не стирает список", listOf(1037), vm.orders.value.map { it.number })
        vm.onMessageShown()
        orders.refreshFails = false
        vm.refresh()
        assertNull("удачное обновление без снэкбара", vm.message.value)
        assertEquals(OrdersLoad.DONE, vm.load.value)
    }
}
