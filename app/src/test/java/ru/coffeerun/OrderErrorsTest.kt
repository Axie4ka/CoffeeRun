package ru.coffeerun

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.checkout.CheckoutViewModel
import ru.coffeerun.ui.orders.OrdersLoad
import ru.coffeerun.ui.orders.OrdersViewModel

/** Экраны заказов, когда сервер отвечает не «да»: оформление и загрузка истории. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderErrorsTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    /** Сервер, который отвечает [result]; пока [gate] не открыт — «ещё думает». */
    private class FakeOrders(var result: PlaceOrderResult = PlaceOrderResult.NetworkError) : OrderRepository {
        override val orders = MutableStateFlow<List<Order>>(emptyList())
        var gate: CompletableDeferred<Unit>? = null
        var placeCalls = 0
        var refreshFails = false
        var refreshCalls = 0

        override suspend fun refresh() {
            refreshCalls++
            if (refreshFails) throw IOException("нет сети")
            orders.value = listOf(Order(1037, OrderStatus.PICKED_UP, emptyList(), Shop.MOSKOVSKAYA, 0L, total = 480))
        }

        override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?): PlaceOrderResult {
            placeCalls++
            gate?.await()
            return result
        }

        override fun find(number: Int): Order? = null
    }

    private val cart = InMemoryCartRepository().apply { add(CartLine(LocalMenu.CROISSANT, unitPrice = 180)) }
    private val orders = FakeOrders()
    private val checkout by lazy { CheckoutViewModel(cart, orders, InMemoryShopRepository(), ticks = flowOf(Unit)) }

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
        orders.result = PlaceOrderResult.Placed(Order(1042, OrderStatus.PREPARING, cart.lines.value, Shop.MOSKOVSKAYA, 0L))
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
    fun ordersLoadFails_thenRetry_loads() = runTest {
        orders.refreshFails = true
        val vm = OrdersViewModel(orders, cart, flowOf(true))
        assertEquals(OrdersLoad.FAILED, vm.load.value)
        orders.refreshFails = false
        vm.retry()
        assertEquals(OrdersLoad.DONE, vm.load.value)
        assertEquals(listOf(1037), vm.orders.value.map { it.number })
    }

    @Test
    fun guest_doesNotLoadOrders_untilLogin() = runTest {
        val loggedIn = MutableStateFlow(false)
        OrdersViewModel(orders, cart, loggedIn)
        assertEquals(0, orders.refreshCalls)
        loggedIn.value = true
        assertEquals(1, orders.refreshCalls)
    }
}
