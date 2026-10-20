package ru.coffeerun

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.screens.CheckoutViewModel

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
        cart.add(CartLine(LocalMenu.CROISSANT, unitPrice = 180))
        CheckoutViewModel(cart, orders, shops).placeOrder()
        assertEquals(Shop.STROITELEY, orders.orders.value.first().shop)
    }
}
