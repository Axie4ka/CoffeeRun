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
import ru.coffeerun.ui.screens.CheckoutViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PickupTimeTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun today(hour: Int, minute: Int = 0): Long =
        LocalDate.now().atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
    }

    @Test
    fun tooEarlyOrAfterClosing_isRejected() {
        assertFalse(vm.setPickupTime(12, 10))
        assertFalse(vm.setPickupTime(22, 30))
        assertNull(vm.pickupAtMillis.value)
    }

    @Test
    fun asap_clearsPickupTime() {
        vm.setPickupTime(13, 0)
        vm.pickupAsap()
        assertNull(vm.pickupAtMillis.value)
    }
}
