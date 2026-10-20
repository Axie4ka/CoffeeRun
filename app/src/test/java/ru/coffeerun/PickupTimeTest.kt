package ru.coffeerun

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

class PickupTimeTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun today(hour: Int, minute: Int = 0): Long =
        LocalDate.of(2026, 10, 20).atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun vmAt(hour: Int, minute: Int = 0, second: Int = 0) = CheckoutViewModel(
        cart, orders, InMemoryShopRepository(),
        now = { today(hour, minute) + second * 1000L }
    )

    private val cart = InMemoryCartRepository()
    private val orders = InMemoryOrderRepository()
    private val vm = CheckoutViewModel(cart, orders, InMemoryShopRepository(), now = { today(12) })

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
    fun tooEarlyOrAfterClosing_isRejected() {
        assertFalse(vm.setPickupTime(12, 10))
        assertFalse(vm.setPickupTime(22, 30))
        assertNull(vm.pickupAtMillis.value)
    }

    @Test
    fun staleTime_fallsBackToAsapOnPlaceOrder() {
        var nowMillis = today(12)
        val clocked = CheckoutViewModel(cart, orders, InMemoryShopRepository(), now = { nowMillis })
        assertTrue(clocked.setPickupTime(12, 20))
        nowMillis = today(12, 10)
        cart.add(CartLine(LocalMenu.CROISSANT, unitPrice = 180))
        clocked.placeOrder()
        assertNull(orders.orders.value.first().pickupAtMillis)
        assertNull(clocked.pickupAtMillis.value)
    }

    @Test
    fun asap_clearsPickupTime() {
        vm.setPickupTime(13, 0)
        vm.pickupAsap()
        assertNull(vm.pickupAtMillis.value)
    }

    @Test
    fun boundaries_nowPlus15AndClosingAccepted() {
        assertTrue(vm.setPickupTime(12, 15))
        assertTrue(vm.setPickupTime(22, 0))
        assertFalse(vm.setPickupTime(22, 1))
        assertEquals(today(22), vm.pickupAtMillis.value)
    }

    @Test
    fun earliestPickup_roundsUpAndClosesInEvening() {
        assertEquals(LocalTime.of(12, 15), vm.earliestPickupTime())
        assertEquals(LocalTime.of(12, 20), vmAt(12, 3).earliestPickupTime())
        assertEquals(LocalTime.of(22, 0), vmAt(21, 45).earliestPickupTime())
        assertNull(vmAt(21, 50).earliestPickupTime())
        assertNull(vmAt(23, 50).earliestPickupTime())
    }
}
