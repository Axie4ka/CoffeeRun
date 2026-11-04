package ru.coffeerun.ui.checkout

import ru.coffeerun.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

@OptIn(ExperimentalCoroutinesApi::class)
class ShopTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun defaultShop_isMoskovskaya() {
        assertEquals(Shop.MOSKOVSKAYA, InMemoryShopRepository().selected.value)
    }

    @Test
    fun placeOrder_usesSelectedShop() {
        val cart = InMemoryCartRepository()
        val orders = InMemoryOrderRepository()
        val shops = InMemoryShopRepository()
        shops.select(Shop.STROITELEY)
        cart.add(CartLine(DemoMenu.CROISSANT, unitPrice = 180))
        CheckoutViewModel(cart, orders, shops).placeOrder()
        assertEquals(Shop.STROITELEY, orders.orders.value.first().shop)
    }

    @Test
    fun checkout_selectShop_changesItForMenuAndOrder() = runTest {
        val cart = InMemoryCartRepository().apply { add(CartLine(DemoMenu.CROISSANT, unitPrice = 180)) }
        val orders = InMemoryOrderRepository()
        val shops = InMemoryShopRepository()
        val checkout = CheckoutViewModel(cart, orders, shops, ticks = flowOf(Unit))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checkout.uiState.collect {} }
        checkout.selectShop(Shop.STROITELEY)
        assertEquals(Shop.STROITELEY, checkout.uiState.value.shop)
        assertEquals("выбор общий с меню", Shop.STROITELEY, shops.selected.value)
        checkout.placeOrder()
        assertEquals(Shop.STROITELEY, orders.orders.value.first().shop)
    }
}
