package ru.coffeerun.ui.menu

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.MenuSnapshot

class MenuSnapshotUiTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun openMenu_reflectsUpdatedProductsAndPricesTogether() {
        val snapshots = MutableStateFlow(MenuSnapshot(DemoMenu.products, DemoMenu.prices))
        val menu = object : MenuRepository by DemoMenuRepository() {
            override fun observeSnapshot() = snapshots
        }
        val vm = MenuViewModel(menu, InMemoryCartRepository(), InMemoryShopRepository())
        val products = listOf(DemoMenu.LATTE.copy(price = 345))
        val prices = DemoMenu.prices.copy(sizes = emptyList())
        snapshots.value = MenuSnapshot(products, prices)

        assertEquals(MenuUiState.Success(products, prices), vm.uiState.value)
    }
}
