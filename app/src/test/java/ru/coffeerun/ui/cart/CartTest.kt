package ru.coffeerun.ui.cart

import ru.coffeerun.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DEFAULT_DRINK_SIZE
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.itemCount
import ru.coffeerun.ui.checkout.CheckoutViewModel
import ru.coffeerun.ui.menu.MenuViewModel
import ru.coffeerun.ui.checkout.OrderPlacedViewModel
import ru.coffeerun.ui.orders.OrdersViewModel

/** Цена, слияние строк корзины и оформление заказа — логика, где ошибка стоит денег. */
class CartTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val cart = InMemoryCartRepository()
    private val orders = InMemoryOrderRepository()
    private val lines get() = cart.lines.value

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cartUiState_countsItemsAndTotal() = runTest {
        cart.add(CartLine(DemoMenu.CROISSANT, unitPrice = 180, quantity = 2))
        cart.add(CartLine(DemoMenu.LATTE, unitPrice = 240, options = DemoMenu.LATTE.defaultOptions()))
        val vm = CartViewModel(cart)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(3, vm.uiState.value.count)
        assertEquals(2 * 180 + 240, vm.uiState.value.total)
        cart.add(CartLine(DemoMenu.CROISSANT, unitPrice = 180))
        assertEquals(4, vm.uiState.value.count)
        assertEquals(3 * 180 + 240, vm.uiState.value.total)
    }

    @Test
    fun demoMenu_idsAreUniqueAndResolvable() {
        assertEquals(13, DemoMenu.products.size)
        assertEquals(DemoMenu.products.size, DemoMenu.products.map { it.id }.toSet().size)
        assertEquals(DemoMenu.LATTE, DemoMenu.byId("latte"))
        assertNull(DemoMenu.byId("unknown"))
    }

    @Test
    fun unitPrice_sumsSizeMilkAndExtras() {
        // 240 база + 80 доплата за L + 40 овсяное + 40 ваниль
        assertEquals(400, DemoMenu.LATTE.unitPrice(DrinkSize.LARGE, MilkType.OAT, setOf(Extra.VANILLA), DemoMenu.prices))
        // У эспрессо нет выбора размера и молока — они не влияют на цену
        assertEquals(150, DemoMenu.ESPRESSO.unitPrice(DrinkSize.LARGE, MilkType.OAT, emptySet(), DemoMenu.prices))
    }

    @Test
    fun unitPrice_usesPricesFromServer() {
        // Кофейня подняла цену овсяного молока и L — приложение считает по новым ценам без обновления
        val prices = DemoMenu.prices.copy(
            sizes = DemoMenu.prices.sizes.map { if (it.size == DrinkSize.LARGE) it.copy(price = 320) else it },
            milks = DemoMenu.prices.milks.map { if (it.milk == MilkType.OAT) it.copy(price = 70) else it }
        )
        // 240 база + 100 доплата за L + 70 овсяное + 40 ваниль
        assertEquals(450, DemoMenu.LATTE.unitPrice(DrinkSize.LARGE, MilkType.OAT, setOf(Extra.VANILLA), prices))
    }

    @Test
    fun quickAdd_hasSizeAndMergesWithSameDrinkFromProductScreen() {
        // Эспрессо с экрана позиции без добавок и примечания — те же опции, что у «+»
        val fromScreen = CartLine(DemoMenu.ESPRESSO, unitPrice = 150, options = DemoMenu.ESPRESSO.defaultOptions()?.copy(note = ""))
        MenuViewModel(DemoMenuRepository(), cart, InMemoryShopRepository()).quickAdd(DemoMenu.ESPRESSO)
        cart.add(fromScreen)
        assertEquals(1, lines.size)
        assertEquals(2, lines[0].quantity)

        MenuViewModel(DemoMenuRepository(), cart, InMemoryShopRepository()).quickAdd(DemoMenu.CAPPUCCINO)
        assertEquals(DrinkSize.MEDIUM, lines.last().options?.size)
        assertEquals(3, lines.itemCount)
        assertNull(DemoMenu.CROISSANT.defaultOptions())
    }

    @Test
    fun differentNotes_stayDifferentLines() {
        val base = quickAdd(DemoMenu.LATTE)
        cart.add(base.copy(options = base.options?.copy(note = "без пенки")))
        cart.add(base)
        assertEquals(2, lines.size)
    }

    @Test
    fun quantity_neverExceedsMax() {
        cart.add(quickAdd(DemoMenu.LATTE).copy(quantity = MAX_QUANTITY))
        cart.add(quickAdd(DemoMenu.LATTE).copy(quantity = MAX_QUANTITY))
        assertEquals(MAX_QUANTITY, lines[0].quantity)
        cart.changeQuantity(lines[0].id, +1)
        assertEquals(MAX_QUANTITY, lines[0].quantity)
        repeat(MAX_QUANTITY + 1) { cart.changeQuantity(lines[0].id, -1) }
        assertEquals(1, lines[0].quantity)
        cart.remove(lines[0].id)
        assertTrue(lines.isEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun placeOrder_movesCartToOrders() = runTest {
        val checkout = CheckoutViewModel(cart, orders, InMemoryShopRepository(), ticks = flowOf(Unit))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checkout.uiState.collect {} }
        checkout.placeOrder()
        assertNull("пустая корзина — заказа нет", checkout.uiState.value.placedOrderNumber)

        cart.add(quickAdd(DemoMenu.CROISSANT).copy(quantity = 2))
        checkout.placeOrder()
        val order = orders.orders.value.first()
        assertEquals(order.number, checkout.uiState.value.placedOrderNumber)
        assertEquals(360, order.total)
        assertTrue(lines.isEmpty())
        // Событие обработано — повторного перехода не будет
        checkout.onPlacedOrderShown()
        assertNull(checkout.uiState.value.placedOrderNumber)

        // Экран «Заказ принят» находит заказ по номеру из маршрута
        assertEquals(order, OrderPlacedViewModel(order.number, orders).order)
        assertNull(OrderPlacedViewModel(-1, orders).order)
    }

    @Test
    fun repeat_putsOrderLinesBackToCart() {
        val past = orders.orders.value.first { it.lines.size == 1 }
        OrdersViewModel(orders, cart, flowOf(false)).repeat(past)
        OrdersViewModel(orders, cart, flowOf(false)).repeat(past)
        assertEquals(1, lines.size)
        assertEquals(past.lines[0].quantity * 2, lines[0].quantity)
    }

    // Так строку собирает «+» в меню (MenuViewModel.quickAdd)
    private fun quickAdd(p: Product) = CartLine(
        product = p,
        unitPrice = p.unitPrice(DEFAULT_DRINK_SIZE, MilkType.REGULAR, emptySet(), DemoMenu.prices),
        options = p.defaultOptions()
    )
}
