package ru.coffeerun

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.ui.screens.CheckoutViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PickupTimeTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun today(hour: Int, minute: Int = 0): Long =
        LocalDate.of(2026, 10, 20).atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // Часы и «тики» подменяются: время в тестах не зависит от реального, бесконечного таймера нет
    private fun checkout(now: () -> Long, ticks: Flow<Unit> = flowOf(Unit)) =
        CheckoutViewModel(cart, orders, InMemoryShopRepository(), now = now, ticks = ticks)

    private fun vmAt(hour: Int, minute: Int = 0, second: Int = 0) = checkout({ today(hour, minute) + second * 1000L })

    private val cart = InMemoryCartRepository()
    private val orders = InMemoryOrderRepository()
    // lazy: viewModelScope нужен Dispatchers.Main, а его подменяет правило — уже после создания полей теста
    private val vm by lazy { checkout({ today(12) }) }

    /** uiState живёт только при подписчике (WhileSubscribed), поэтому держим коллектор. */
    private fun TestScope.keepActive(vm: CheckoutViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun validTime_isStoredAndUsedInOrder() {
        cart.add(CartLine(LocalMenu.CROISSANT, unitPrice = 180))
        assertTrue(vm.setPickupTime(13, 0))
        vm.placeOrder()
        val order = orders.orders.value.first()
        assertEquals(today(13), order.pickupAtMillis)
        assertEquals(today(13), order.readyAtMillis)
        // К 13:00 ещё рано готовить — заказ только принят
        assertEquals(OrderStatus.ACCEPTED, order.status)
    }

    @Test
    fun asapOrder_isPreparing() {
        cart.add(CartLine(LocalMenu.CROISSANT, unitPrice = 180))
        vm.placeOrder()
        assertEquals(OrderStatus.PREPARING, orders.orders.value.first().status)
    }

    @Test
    fun tooEarlyOrAfterClosing_isRejected() = runTest {
        keepActive(vm)
        assertFalse(vm.setPickupTime(12, 10))
        assertFalse(vm.setPickupTime(22, 30))
        assertNull(vm.uiState.value.pickupAtMillis)
    }

    @Test
    fun staleTime_fallsBackToAsapOnPlaceOrder() = runTest {
        var nowMillis = today(12)
        val clocked = checkout({ nowMillis })
        keepActive(clocked)
        assertTrue(clocked.setPickupTime(12, 20))
        nowMillis = today(12, 10)
        cart.add(CartLine(LocalMenu.CROISSANT, unitPrice = 180))
        clocked.placeOrder()
        assertNull(orders.orders.value.first().pickupAtMillis)
        assertNull(clocked.uiState.value.pickupAtMillis)
    }

    @Test
    fun asap_clearsPickupTime() = runTest {
        keepActive(vm)
        vm.setPickupTime(13, 0)
        assertEquals(today(13), vm.uiState.value.pickupAtMillis)
        vm.pickupAsap()
        assertNull(vm.uiState.value.pickupAtMillis)
    }

    @Test
    fun boundaries_nowPlus15AndClosingAccepted() = runTest {
        keepActive(vm)
        assertTrue(vm.setPickupTime(12, 15))
        assertTrue(vm.setPickupTime(22, 0))
        assertFalse(vm.setPickupTime(22, 1))
        assertEquals(today(22), vm.uiState.value.pickupAtMillis)
    }

    @Test
    fun earliestPickup_roundsUpAndClosesInEvening() {
        assertEquals(LocalTime.of(12, 15), vm.earliestPickupTime())
        assertEquals(LocalTime.of(12, 20), vmAt(12, 3).earliestPickupTime())
        assertEquals(LocalTime.of(22, 0), vmAt(21, 45).earliestPickupTime())
        assertNull(vmAt(21, 50).earliestPickupTime())
        assertNull(vmAt(23, 50).earliestPickupTime())
    }

    @Test
    fun earliestPickup_followsClockWhileScreenIsOpen() = runTest {
        var nowMillis = today(12)
        val ticks = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val clocked = checkout({ nowMillis }, ticks)
        keepActive(clocked)
        assertEquals(LocalTime.of(12, 15), clocked.uiState.value.earliestPickup)
        nowMillis = today(12, 3)
        ticks.tryEmit(Unit) // прошла минута
        assertEquals(LocalTime.of(12, 20), clocked.uiState.value.earliestPickup)
    }

    @Test
    fun uiState_hasCartAndTotal() = runTest {
        cart.add(CartLine(LocalMenu.CROISSANT, unitPrice = 180, quantity = 2))
        keepActive(vm)
        assertEquals(1, vm.uiState.value.lines.size)
        assertEquals(360, vm.uiState.value.total)
        cart.add(CartLine(LocalMenu.LATTE, unitPrice = 260))
        assertEquals(620, vm.uiState.value.total)
    }
}
