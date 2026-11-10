package ru.coffeerun.ui.orders

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.coffeerun.FakeMenuRepository
import ru.coffeerun.R
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderItem
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop

class OrderRepeatTest {
    private val cart = InMemoryCartRepository()

    private fun order(vararg items: OrderItem) =
        Order(1049, OrderStatus.PICKED_UP, items.toList(), Shop.MOSKOVSKAYA, 0L)

    @Test
    fun partialRepeat_addsAvailableById_andExplainsSkipped() = runTest {
        val past = order(
            OrderItem("croissant", "Старое название", 150, quantity = 2),
            OrderItem("retired", "Снятый товар", 300)
        )
        val result = repeatOrder(past, DemoMenuRepository(), cart)
        assertEquals(OrderRepeatResult(added = true, message = R.string.order_repeat_partial), result)
        val line = cart.lines.value.single()
        assertEquals("croissant", line.product.id)
        assertEquals("Круассан", line.product.name)
        assertEquals(2, line.quantity)
        assertEquals(150, line.unitPrice) // Актуализация цены — следующий отдельный шаг.
        assertEquals(2, past.lines.size) // История не меняется при повторе.
        assertEquals(600, past.total)
    }

    @Test
    fun noAvailableItems_keepsExistingCart_andExplains() = runTest {
        cart.add(CartLine(DemoMenu.CROISSANT, 180))
        val before = cart.lines.value
        val result = repeatOrder(order(OrderItem("retired", null, 300)), DemoMenuRepository(), cart)
        assertFalse(result.added)
        assertEquals(R.string.order_repeat_unavailable, result.message)
        assertEquals(before, cart.lines.value)
    }

    @Test
    fun menuFailure_keepsExistingCart_andReturnsRetryableError() = runTest {
        cart.add(CartLine(DemoMenu.CROISSANT, 180))
        val before = cart.lines.value
        val result = repeatOrder(order(OrderItem("latte", "Латте", 240)), FakeMenuRepository(fail = true), cart)
        assertFalse(result.added)
        assertEquals(R.string.order_repeat_error, result.message)
        assertEquals(before, cart.lines.value)
    }

    @Test
    fun knownIdWithoutHistoricalName_canBeRepeated() = runTest {
        val result = repeatOrder(order(OrderItem("croissant", null, 180)), DemoMenuRepository(), cart)
        assertEquals(OrderRepeatResult(added = true), result)
        assertEquals(listOf("croissant"), cart.lines.value.map { it.product.id })
    }
}
