package ru.coffeerun.domain.promotion

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.Promotion
import ru.coffeerun.model.PromotionType

class PromotionPricingTest {
    private val snapshot = MenuSnapshot(
        DemoMenu.products, DemoMenu.prices,
        promotions = listOf(
            Promotion("medium", PromotionType.CAPPUCCINO_MEDIUM, 1000, 2000, productId = "cappuccino"),
            Promotion("pastry", PromotionType.PASTRY_2_PLUS_1, 1000, 2000, category = ProductCategory.PASTRY)
        )
    )

    @Test fun mediumCappuccinoDiscountLeavesMilkAndExtrasAtFullPrice() {
        val product = DemoMenu.CAPPUCCINO
        val options = product.defaultOptions()!!.copy(size = DrinkSize.MEDIUM, milk = MilkType.OAT, extras = setOf(Extra.VANILLA))
        val cart = listOf(
            CartLine(product, 340, 2, options),
            CartLine(product, 220, options = product.defaultOptions()!!.copy(size = DrinkSize.SMALL))
        )
        val priced = priceCart(cart, snapshot, nowMillis = 1000)
        assertEquals(listOf(104, 0), priced.map { it.discount })
        assertEquals(listOf(576, 220), priced.map { it.total })
    }

    @Test fun mediumCappuccinoRoundsDiscountDownForEachPortion() {
        val product = DemoMenu.CAPPUCCINO.copy(price = 223)
        val medium = product.defaultOptions()!!.copy(size = DrinkSize.MEDIUM)
        val priced = priceCart(listOf(CartLine(product, 263, 3, medium)), snapshot, nowMillis = 1000)
        assertEquals(156, priced.single().discount)
    }

    @Test fun twoCheapestPastriesAreFreeWhenCartContainsTwoTrios() {
        val cart = listOf(
            CartLine(DemoMenu.CROISSANT, 180, 3), CartLine(DemoMenu.CHOCOLATE_MUFFIN, 150, 2),
            CartLine(DemoMenu.CINNAMON_ROLL, 170), CartLine(DemoMenu.ESPRESSO, 130, 3)
        )
        val priced = priceCart(cart, snapshot, nowMillis = 1000)
        assertEquals(listOf(0, 300, 0, 0), priced.map { it.discount })
        assertEquals(0, priced[1].total)
    }

    @Test fun repricingAnExpiredPromotionClearsItsFormerDiscount() {
        val cart = listOf(CartLine(DemoMenu.CROISSANT, 180, 4))
        val priced = priceCart(cart, snapshot, nowMillis = 1999)
        assertEquals(540, priced.single().total)
        assertEquals(720, priceCart(priced, snapshot, nowMillis = 2000).single().total)
    }
}
