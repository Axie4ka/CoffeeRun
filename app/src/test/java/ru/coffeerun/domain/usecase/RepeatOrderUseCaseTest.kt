package ru.coffeerun.domain.usecase

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.coffeerun.data.*
import ru.coffeerun.data.session.SessionStateStore
import ru.coffeerun.data.network.dto.DrinkOptionsDto
import ru.coffeerun.data.network.dto.OrderDto
import ru.coffeerun.data.network.dto.OrderLineDto
import ru.coffeerun.data.network.mapper.toModel
import ru.coffeerun.domain.orders.*
import ru.coffeerun.model.*

class RepeatOrderUseCaseTest {
    private val cart = InMemoryCartRepository()
    private val sessions = SessionStateStore("token")
    private val recipe = DrinkOptions(DrinkSize.MEDIUM, MilkType.OAT, false, setOf(Extra.VANILLA), "Без сахара")
    private class FreshMenu : MenuRepository by DemoMenuRepository() {
        var snapshot = MenuSnapshot(DemoMenu.products, DemoMenu.prices)
        var calls = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        var uncooperative = false
        override suspend fun refresh(): MenuSnapshot {
            calls++
            if (uncooperative) withContext(NonCancellable) { gate?.await() } else gate?.await()
            if (fail) throw IOException("offline")
            return snapshot
        }
    }
    private val menu = FreshMenu()
    private fun repeat() = RepeatOrderUseCase(menu, cart, sessions)
    private fun past(vararg items: OrderItem) = Order(1049, OrderStatus.PICKED_UP, items.toList(), Shop.MOSKOVSKAYA, 0L)

    @Test fun freshPriceAndOptions_areUsed_historyIsUnchanged() = runTest {
        val prices = DemoMenu.prices.copy(
            sizes = listOf(SizeOption(DrinkSize.SMALL, 220), SizeOption(DrinkSize.MEDIUM, 280)),
            milks = listOf(MilkOption(MilkType.OAT, 50, true)), extras = mapOf(Extra.VANILLA to 60))
        menu.snapshot = MenuSnapshot(listOf(DemoMenu.LATTE.copy(price = 300)), prices)
        val history = past(OrderItem("latte", "Старое название", 200, 2, recipe))
        val result = repeat()(history) as RepeatOrderResult.Added
        assertEquals(1, result.addedLines)
        assertTrue(result.pricesChanged)
        assertTrue(result.skipped.isEmpty())
        assertEquals(470, cart.lines.value.single().unitPrice)
        assertEquals(recipe, cart.lines.value.single().options)
        assertEquals(400, history.total)
        assertEquals(200, history.lines.single().unitPrice)
        assertEquals(1, menu.calls)
    }

    @Test fun missingProduct_isSkippedWithReason() = runTest {
        val result = repeat()(past(OrderItem("croissant", null, 180, 2), OrderItem("retired", null, 300))) as RepeatOrderResult.Added
        assertEquals(listOf("croissant"), cart.lines.value.map { it.product.id })
        assertEquals(listOf(RepeatSkippedItem("retired", RepeatSkipReason.ProductUnavailable)), result.skipped)
        assertFalse(result.pricesChanged)
    }

    @Test fun unavailableRecipes_areSkippedWithoutSubstitution() = runTest {
        val cases = listOf(
            MenuSnapshot(DemoMenu.products, DemoMenu.prices.copy(sizes = emptyList())),
            MenuSnapshot(DemoMenu.products, DemoMenu.prices.copy(milks = listOf(MilkOption(MilkType.OAT, 40, false)))),
            MenuSnapshot(DemoMenu.products, DemoMenu.prices.copy(extras = emptyMap())),
            MenuSnapshot(listOf(DemoMenu.LATTE.copy(extras = emptySet())), DemoMenu.prices),
            MenuSnapshot(listOf(DemoMenu.LATTE.copy(customizations = setOf(Customization.SIZE, Customization.MILK))), DemoMenu.prices)
        )
        for (snapshot in cases) {
            menu.snapshot = snapshot
            val result = repeat()(past(OrderItem("latte", null, 240, options = recipe))) as RepeatOrderResult.Unavailable
            assertEquals(RepeatSkipReason.OptionsUnavailable, result.skipped.single().reason)
            assertTrue(cart.lines.value.isEmpty())
        }
    }

    @Test fun missingHistoricalOptions_areNotInvented() = runTest {
        val result = repeat()(past(OrderItem("latte", null, 240))) as RepeatOrderResult.Unavailable
        assertEquals(RepeatSkipReason.OptionsUnavailable, result.skipped.single().reason)
        assertTrue(cart.lines.value.isEmpty())
    }

    @Test fun historyWithUnknownExtra_skipsTheRecipeInsteadOfDroppingTheExtra() = runTest {
        val history = OrderDto(1049, "PICKED_UP", "MOSKOVSKAYA", "2026-11-19T09:00:00+03:00",
            lines = listOf(
                OrderLineDto("latte", 1, 280, DrinkOptionsDto("MEDIUM", "REGULAR", false, listOf("CARDAMOM"), "")),
                OrderLineDto("croissant", 1, 180)
            )).toModel(DemoMenu.products.associateBy { it.id })!!

        val result = repeat()(history) as RepeatOrderResult.Added
        assertEquals(listOf(RepeatSkippedItem("latte", RepeatSkipReason.OptionsUnavailable)), result.skipped)
        assertEquals(listOf("croissant"), cart.lines.value.map { it.product.id })
    }

    @Test fun removedIceOrOversizedNote_areNotSilentlyDropped() = runTest {
        menu.snapshot = MenuSnapshot(listOf(DemoMenu.LATTE.copy(customizations = setOf(Customization.SIZE, Customization.MILK))), DemoMenu.prices)
        val result = repeat()(past(OrderItem("latte", null, 240, options = recipe.copy(isIced = true, note = "")))) as RepeatOrderResult.Unavailable
        assertEquals(RepeatSkipReason.OptionsUnavailable, result.skipped.single().reason)
        menu.snapshot = MenuSnapshot(DemoMenu.products, DemoMenu.prices)
        assertTrue(repeat()(past(OrderItem("latte", null, 240, options = recipe.copy(note = "x".repeat(MAX_NOTE_LENGTH + 1))))) is RepeatOrderResult.Unavailable)
    }

    @Test fun quantityLimit_doesNotTruncateRepeatedOrder() = runTest {
        cart.add(CartLine(DemoMenu.CROISSANT, 180, MAX_QUANTITY))
        val before = cart.lines.value
        val result = repeat()(past(OrderItem("croissant", null, 180))) as RepeatOrderResult.Unavailable
        assertEquals(RepeatSkipReason.QuantityUnavailable, result.skipped.single().reason)
        assertEquals(before, cart.lines.value)
        assertTrue(repeat()(past(OrderItem("croissant", null, 180, quantity = 0))) is RepeatOrderResult.Unavailable)
    }

    @Test fun networkFailure_keepsExistingCart() = runTest {
        cart.add(CartLine(DemoMenu.CROISSANT, 180))
        val before = cart.lines.value
        menu.fail = true
        assertEquals(RepeatOrderResult.NetworkError, repeat()(past(OrderItem("croissant", null, 180))))
        assertEquals(before, cart.lines.value)
    }

    @Test fun sessionChangedDuringRefresh_doesNotAddToNewCart() = runTest {
        menu.gate = CompletableDeferred()
        menu.uncooperative = true
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repeat()(past(OrderItem("croissant", null, 180))) } }
        sessions.replaceToken("token")
        cart.add(CartLine(DemoMenu.LATTE, 280))
        val before = cart.lines.value
        menu.gate!!.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(before, cart.lines.value)
    }

    @Test fun cancelledCaller_doesNotAddLateResult() = runTest {
        menu.gate = CompletableDeferred()
        menu.uncooperative = true
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repeat()(past(OrderItem("croissant", null, 180))) }
        pending.cancel()
        menu.gate!!.complete(Unit)
        pending.join()
        assertTrue(cart.lines.value.isEmpty())
    }
    @Test fun missingSizeReference_doesNotUseZeroSurcharge() = runTest {
        menu.snapshot = MenuSnapshot(DemoMenu.products,
            DemoMenu.prices.copy(sizes = listOf(SizeOption(DrinkSize.MEDIUM, 280))))
        val result = repeat()(past(OrderItem("latte", null, 240, options = recipe)))
        assertTrue(result is RepeatOrderResult.Unavailable)
        assertTrue(cart.lines.value.isEmpty())
    }

}
