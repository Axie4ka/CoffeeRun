package ru.coffeerun.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Recipe

class RecipeTest {
    private val product = DemoMenu.CAPPUCCINO
    private val options = checkNotNull(product.defaultOptions()).copy(
        size = DrinkSize.LARGE, milk = MilkType.OAT, extras = setOf(Extra.VANILLA), note = "Меньше пены"
    )
    private val recipe = Recipe("recipe", "Утренний", product.id, options)

    @Test
    fun repriceOnePortionFromCachedMenu() {
        val updatedProduct = product.copy(price = product.price + 30)
        val snapshot = MenuSnapshot(listOf(updatedProduct), DemoMenu.prices, isCached = true)
        val line = checkNotNull(recipe.toCartLine(snapshot))

        assertEquals(updatedProduct, line.product)
        assertEquals(options, line.options)
        assertEquals(1, line.quantity)
        assertEquals(410, line.unitPrice)
    }

    @Test
    fun refusesRecipeWhenProductOrExactExtraIsMissing() {
        assertNull(recipe.toCartLine(MenuSnapshot(emptyList(), DemoMenu.prices)))
        val prices = DemoMenu.prices.copy(extras = DemoMenu.prices.extras - Extra.VANILLA)
        assertNull(recipe.toCartLine(MenuSnapshot(listOf(product), prices)))
    }
}
