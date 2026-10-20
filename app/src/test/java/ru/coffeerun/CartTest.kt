package ru.coffeerun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop
import ru.coffeerun.model.defaultOptions
import ru.coffeerun.model.unitPrice
import ru.coffeerun.ui.AppViewModel
import ru.coffeerun.ui.MAX_QUANTITY

/** Цена, слияние строк корзины и оформление заказа — логика, где ошибка стоит денег. */
class CartTest {

    @Test
    fun unitPrice_sumsSizeMilkAndExtras() {
        // 240 база + 80 доплата за L + 40 овсяное + 40 ваниль
        assertEquals(400, Product.LATTE.unitPrice(DrinkSize.LARGE, MilkType.OAT, setOf(Extra.VANILLA)))
        // У эспрессо нет выбора размера и молока — они не влияют на цену
        assertEquals(150, Product.ESPRESSO.unitPrice(DrinkSize.LARGE, MilkType.OAT, emptySet()))
    }

    @Test
    fun quickAdd_hasSizeAndMergesWithSameDrinkFromProductScreen() {
        val vm = AppViewModel()
        val quick = quickAdd(Product.ESPRESSO)
        // Эспрессо с экрана позиции без добавок и примечания — те же опции, что у «+»
        val fromScreen = CartLine(Product.ESPRESSO, unitPrice = 150, options = Product.ESPRESSO.defaultOptions()?.copy(note = ""))
        vm.addToCart(quick)
        vm.addToCart(fromScreen)
        assertEquals(1, vm.cart.size)
        assertEquals(2, vm.cart[0].quantity)
        assertEquals(DrinkSize.SMALL, quickAdd(Product.CAPPUCCINO).options?.size)
        assertNull(Product.CROISSANT.defaultOptions())
    }

    @Test
    fun differentNotes_stayDifferentLines() {
        val vm = AppViewModel()
        val base = quickAdd(Product.LATTE)
        vm.addToCart(base.copy(options = base.options?.copy(note = "без пенки")))
        vm.addToCart(base)
        assertEquals(2, vm.cart.size)
    }

    @Test
    fun quantity_neverExceedsMax() {
        val vm = AppViewModel()
        vm.addToCart(quickAdd(Product.LATTE).copy(quantity = MAX_QUANTITY))
        vm.addToCart(quickAdd(Product.LATTE).copy(quantity = MAX_QUANTITY))
        assertEquals(MAX_QUANTITY, vm.cart[0].quantity)
        vm.changeQuantity(vm.cart[0].id, +1)
        assertEquals(MAX_QUANTITY, vm.cart[0].quantity)
        repeat(MAX_QUANTITY + 1) { vm.changeQuantity(vm.cart[0].id, -1) }
        assertEquals(1, vm.cart[0].quantity)
    }

    @Test
    fun placeOrder_movesCartToOrders() {
        val vm = AppViewModel()
        assertNull(vm.placeOrder(Shop.MOSKOVSKAYA))
        vm.addToCart(quickAdd(Product.CROISSANT).copy(quantity = 2))
        val order = vm.placeOrder(Shop.MOSKOVSKAYA)
        assertEquals(360, order?.total)
        assertTrue(vm.cart.isEmpty())
        assertEquals(order, vm.orders.first())
        assertEquals(order, vm.lastOrder)
    }

    // Так строку собирает «+» в меню (CoffeeRunApp)
    private fun quickAdd(p: Product) = CartLine(
        product = p,
        unitPrice = p.unitPrice(DrinkSize.SMALL, MilkType.REGULAR, emptySet()),
        options = p.defaultOptions()
    )
}
