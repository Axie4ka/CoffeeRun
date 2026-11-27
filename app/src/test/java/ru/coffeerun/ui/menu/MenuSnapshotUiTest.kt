package ru.coffeerun.ui.menu

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
        snapshots.value = MenuSnapshot(products, prices, isCached = true, fetchedAtMillis = 123L)

        assertEquals(MenuUiState.Success(products, prices, isCached = true, fetchedAtMillis = 123L), vm.uiState.value)
    }

    @Test
    fun refreshFreshMenuKeepsTheSnapshotOnFailureAndCanBeRetried() {
        val initial = MenuSnapshot(DemoMenu.products, DemoMenu.prices, fetchedAtMillis = 123L)
        val updated = initial.copy(products = listOf(DemoMenu.LATTE), fetchedAtMillis = 456L)
        val snapshots = MutableStateFlow(initial)
        val finishFirst = CompletableDeferred<Unit>()
        var requests = 0
        val menu = object : MenuRepository by DemoMenuRepository() {
            override fun observeSnapshot() = snapshots
            override suspend fun refresh(): MenuSnapshot {
                if (++requests == 1) {
                    finishFirst.await()
                    throw IOException("offline")
                }
                snapshots.value = updated
                return updated
            }
        }
        val vm = MenuViewModel(menu, InMemoryCartRepository(), InMemoryShopRepository())
        val original = vm.uiState.value
        vm.retry()
        vm.retry()
        assertEquals(1, requests)
        assertEquals(MenuRefresh.RUNNING, vm.refreshState.value)
        assertEquals(original, vm.uiState.value)
        finishFirst.complete(Unit)
        assertEquals(MenuRefresh.FAILED, vm.refreshState.value)
        assertEquals(original, vm.uiState.value)
        vm.retry()
        assertEquals(MenuRefresh.IDLE, vm.refreshState.value)
        assertEquals(MenuUiState.Success(updated.products, updated.prices, fetchedAtMillis = 456L), vm.uiState.value)
    }
}
