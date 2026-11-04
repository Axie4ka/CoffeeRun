package ru.coffeerun.ui.orders

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
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
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop

/** Экран заказа: сначала то, что есть в списке, потом свежий заказ с сервера; ошибки, 404, отмена и повтор. */
class OrderDetailsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val accepted = Order(
        1042, OrderStatus.ACCEPTED, listOf(CartLine(DemoMenu.CROISSANT, unitPrice = 180, quantity = 2, id = 1)),
        Shop.MOSKOVSKAYA, placedAtMillis = 0L, pickupAtMillis = 1L
    )

    /** Список уже содержит [listed]; сервер отвечает [onLoad]. */
    private class FakeOrders(listed: List<Order>, var onLoad: () -> Order?) : OrderRepository {
        override val orders = MutableStateFlow(listed)
        override suspend fun refresh() = Unit
        override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?) = PlaceOrderResult.NetworkError
        override suspend fun cancel(number: Int): CancelOrderResult =
            CancelOrderResult.Cancelled(orders.value.first { it.number == number }.copy(status = OrderStatus.CANCELLED))
        override suspend fun load(number: Int): Order? = onLoad()
        override fun find(number: Int): Order? = orders.value.find { it.number == number }
    }

    private val cart = InMemoryCartRepository()

    @Test
    fun freshStatusFromServer_replacesListed() {
        val vm = OrderDetailsViewModel(1042, FakeOrders(listOf(accepted)) { accepted.copy(status = OrderStatus.READY) }, cart)
        assertEquals(OrderStatus.READY, vm.order.value!!.status)
        assertEquals(OrdersLoad.DONE, vm.load.value)
        assertNull(vm.message.value)
    }

    @Test
    fun offline_withListed_showsItAndExplains() {
        val vm = OrderDetailsViewModel(1042, FakeOrders(listOf(accepted)) { throw IOException("нет сети") }, cart)
        assertEquals("заказ из списка остаётся на экране", accepted, vm.order.value)
        assertEquals(R.string.orders_refresh_error, vm.message.value)
    }

    @Test
    fun offline_nothingListed_retryLoads() {
        val orders = FakeOrders(emptyList()) { throw IOException("нет сети") }
        val vm = OrderDetailsViewModel(1042, orders, cart)
        assertNull(vm.order.value)
        assertEquals("показывать нечего — экран просит повторить", OrdersLoad.FAILED, vm.load.value)
        assertNull("снэкбар не нужен: на экране и так ошибка", vm.message.value)
        orders.onLoad = { accepted }
        vm.retry()
        assertEquals(accepted, vm.order.value)
    }

    @Test
    fun unknownOrder_closesScreen() {
        val vm = OrderDetailsViewModel(9999, FakeOrders(emptyList()) { null }, cart)
        assertTrue(vm.notFound.value)
    }

    @Test
    fun cancel_showsCancelled_andMessage() {
        val vm = OrderDetailsViewModel(1042, FakeOrders(listOf(accepted)) { accepted }, cart)
        vm.cancel()
        assertEquals(OrderStatus.CANCELLED, vm.order.value!!.status)
        assertEquals(R.string.order_cancelled, vm.message.value)
        assertFalse(vm.cancelling.value)
    }

    @Test
    fun repeat_putsLinesBackToCart() {
        val vm = OrderDetailsViewModel(1042, FakeOrders(listOf(accepted)) { accepted }, cart)
        vm.repeat()
        assertEquals(listOf(DemoMenu.CROISSANT), cart.lines.value.map { it.product })
        assertEquals(2, cart.lines.value.single().quantity)
    }
}
