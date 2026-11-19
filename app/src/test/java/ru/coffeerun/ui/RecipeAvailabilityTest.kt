package ru.coffeerun.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryFavorites
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.ui.menu.MenuViewModel
import ru.coffeerun.ui.product.ProductViewModel

class RecipeAvailabilityTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun latteWithNoAvailableMilk_cannotBeAddedFromMenu() {
        val cart = InMemoryCartRepository()
        MenuViewModel(menuWithoutMilk(), cart, InMemoryShopRepository()).quickAdd(DemoMenu.LATTE)
        assertTrue(cart.lines.value.isEmpty())
    }

    @Test
    fun latteWithNoAvailableMilk_cannotBeAddedFromProductScreen() {
        val cart = InMemoryCartRepository()
        ProductViewModel(DemoMenu.LATTE.id, menuWithoutMilk(), cart, InMemoryFavorites(), SavedStateHandle()).addToCart()
        assertTrue(cart.lines.value.isEmpty())
    }

    private fun menuWithoutMilk() = object : MenuRepository by DemoMenuRepository() {
        override suspend fun getOptionPrices() = DemoMenu.prices.copy(milks = emptyList())
        override fun observeSnapshot() = kotlinx.coroutines.flow.flowOf(
            ru.coffeerun.model.MenuSnapshot(DemoMenu.products, getPrices())
        )
        private fun getPrices() = DemoMenu.prices.copy(milks = emptyList())
    }
}
