package ru.coffeerun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.data.LocalMenuRepository
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.defaultOptions
import ru.coffeerun.model.itemCount
import ru.coffeerun.model.unitPrice
import ru.coffeerun.ui.screens.CartViewModel
import ru.coffeerun.ui.screens.CheckoutViewModel
import ru.coffeerun.ui.screens.MenuViewModel
import ru.coffeerun.ui.screens.OrderPlacedViewModel
import ru.coffeerun.ui.screens.OrdersViewModel

/** Цена, слияние строк корзины и оформление заказа — логика, где ошибка стоит денег. */
class CartTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val cart = InMemoryCartRepository()
    private val orders = InMemoryOrderRepository()
    private val lines get() = cart.lines.value

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cartUiState_countsItemsAndTotal() = runTest {
        cart.add(CartLine(LocalMenu.CROISSANT, unitPrice = 180, quantity = 2))
        cart.add(CartLine(LocalMenu.LATTE, unitPrice = 240, options = LocalMenu.LATTE.defaultOptions()))
        val vm = CartViewModel(cart)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(3, vm.uiState.value.count)
        assertEquals(2 * 180 + 240, vm.uiState.value.total)
    }

    @Test
    fun localMenu_idsAreUniqueAndResolvable() {
        assertEquals(13, LocalMenu.products.size)
        assertEquals(LocalMenu.products.size, LocalMenu.products.map { it.id }.toSet().size)
        assertEquals(LocalMenu.LATTE, LocalMenu.byId("latte"))
        assertNull(LocalMenu.byId("unknown"))
    }

    @Test
    fun unitPrice_sumsSizeMilkAndExtras() {
        // 240 база + 80 доплата за L + 40 овсяное + 40 ваниль
        assertEquals(400, LocalMenu.LATTE.unitPrice(DrinkSize.LARGE, MilkType.OAT, setOf(Extra.VANILLA)))
        // У эспрессо нет выбора размера и молока — они не влияют на цену
        assertEquals(150, LocalMenu.ESPRESSO.unitPrice(DrinkSize.LARGE, MilkType.OAT, emptySet()))
    }

    @Test
    fun quickAdd_hasSizeAndMergesWithSameDrinkFromProductScreen() {
        // Эспрессо с экрана позиции без добавок и примечания — те же опции, что у «+»
        val fromScreen = CartLine(LocalMenu.ESPRESSO, unitPrice = 150, options = LocalMenu.ESPRESSO.defaultOptions()?.copy(note = ""))
        MenuViewModel(LocalMenuRepository(), cart).quickAdd(LocalMenu.ESPRESSO)
        cart.add(fromScreen)
        assertEquals(1, lines.size)
        assertEquals(2, lines[0].quantity)

        MenuViewModel(LocalMenuRepository(), cart).quickAdd(LocalMenu.CAPPUCCINO)
        assertEquals(DrinkSize.SMALL, lines.last().options?.size)
        assertEquals(3, lines.itemCount)
        assertNull(LocalMenu.CROISSANT.defaultOptions())
    }

    @Test
    fun differentNotes_stayDifferentLines() {
        val base = quickAdd(LocalMenu.LATTE)
        cart.add(base.copy(options = base.options?.copy(note = "без пенки")))
        cart.add(base)
        assertEquals(2, lines.size)
    }

    @Test
    fun quantity_neverExceedsMax() {
        cart.add(quickAdd(LocalMenu.LATTE).copy(quantity = MAX_QUANTITY))
        cart.add(quickAdd(LocalMenu.LATTE).copy(quantity = MAX_QUANTITY))
        assertEquals(MAX_QUANTITY, lines[0].quantity)
        cart.changeQuantity(lines[0].id, +1)
        assertEquals(MAX_QUANTITY, lines[0].quantity)
        repeat(MAX_QUANTITY + 1) { cart.changeQuantity(lines[0].id, -1) }
        assertEquals(1, lines[0].quantity)
        cart.remove(lines[0].id)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun placeOrder_movesCartToOrders() {
        val checkout = CheckoutViewModel(cart, orders)
        checkout.placeOrder()
        assertNull("пустая корзина — заказа нет", checkout.placedOrderNumber.value)

        cart.add(quickAdd(LocalMenu.CROISSANT).copy(quantity = 2))
        checkout.placeOrder()
        val order = orders.orders.value.first()
        assertEquals(order.number, checkout.placedOrderNumber.value)
        assertEquals(360, order.total)
        assertTrue(lines.isEmpty())
        // Событие обработано — повторного перехода не будет
        checkout.onPlacedOrderShown()
        assertNull(checkout.placedOrderNumber.value)

        // Экран «Заказ принят» находит заказ по номеру из маршрута
        assertEquals(order, OrderPlacedViewModel(order.number, orders).order)
        assertNull(OrderPlacedViewModel(-1, orders).order)
    }

    @Test
    fun repeat_putsOrderLinesBackToCart() {
        val past = orders.orders.value.first { it.lines.size == 1 }
        OrdersViewModel(orders, cart).repeat(past)
        OrdersViewModel(orders, cart).repeat(past)
        assertEquals(1, lines.size)
        assertEquals(past.lines[0].quantity * 2, lines[0].quantity)
    }

    // Так строку собирает «+» в меню (MenuViewModel.quickAdd)
    private fun quickAdd(p: Product) = CartLine(
        product = p,
        unitPrice = p.unitPrice(DrinkSize.SMALL, MilkType.REGULAR, emptySet()),
        options = p.defaultOptions()
    )
}
