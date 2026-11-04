package ru.coffeerun.ui.menu

import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.FakeMenuRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.Product

class MenuViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun menu_loadsSuccess() {
        val vm = MenuViewModel(FakeMenuRepository(), InMemoryCartRepository(), InMemoryShopRepository())
        assertEquals(MenuUiState.Success(DemoMenu.products, DemoMenu.prices), vm.uiState.value)
    }

    @Test
    fun menu_networkError_isError() {
        val vm = MenuViewModel(FakeMenuRepository(fail = true), InMemoryCartRepository(), InMemoryShopRepository())
        assertEquals(MenuUiState.Error, vm.uiState.value)
    }

    @Test
    fun retry_afterError_loadsMenu() {
        val fake = FakeMenuRepository(fail = true)
        val vm = MenuViewModel(fake, InMemoryCartRepository(), InMemoryShopRepository())
        assertEquals(MenuUiState.Error, vm.uiState.value)
        fake.fail = false
        vm.loadMenu()
        assertEquals(MenuUiState.Success(DemoMenu.products, DemoMenu.prices), vm.uiState.value)
    }

    @Test
    fun menu_loadsOnce() = runTest {
        val fake = FakeMenuRepository()
        val vm = MenuViewModel(fake, InMemoryCartRepository(), InMemoryShopRepository())
        vm.uiState.first()
        vm.uiState.first()
        assertEquals(1, fake.calls)
    }

    @Test
    fun retry_cancelsPreviousLoad() {
        val first = CompletableDeferred<List<Product>>()
        var call = 0
        val repo = object : MenuRepository {
            override suspend fun getMenu(): List<Product> =
                if (call++ == 0) first.await() else DemoMenu.products

            override suspend fun getProduct(id: String): Product? = DemoMenu.byId(id)
            override suspend fun getOptionPrices() = DemoMenu.prices
        }
        val vm = MenuViewModel(repo, InMemoryCartRepository(), InMemoryShopRepository())
        vm.loadMenu()
        assertEquals(MenuUiState.Success(DemoMenu.products, DemoMenu.prices), vm.uiState.value)
        // Поздняя ошибка первой загрузки не должна затереть Success
        first.completeExceptionally(IOException("поздно"))
        assertEquals(MenuUiState.Success(DemoMenu.products, DemoMenu.prices), vm.uiState.value)
    }
}
