package ru.coffeerun

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.Product
import ru.coffeerun.ui.screens.MenuUiState
import ru.coffeerun.ui.screens.MenuViewModel

private class FakeMenuRepository(var fail: Boolean = false) : MenuRepository {
    var calls = 0

    override suspend fun getMenu(): List<Product> {
        calls++
        if (fail) throw IOException("нет сети")
        return LocalMenu.products
    }

    override suspend fun getProduct(id: String): Product? = LocalMenu.byId(id)
}

class MenuViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun menu_loadsSuccess() {
        val vm = MenuViewModel(FakeMenuRepository(), InMemoryCartRepository(), InMemoryShopRepository())
        assertEquals(MenuUiState.Success(LocalMenu.products), vm.uiState.value)
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
        assertEquals(MenuUiState.Success(LocalMenu.products), vm.uiState.value)
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
                if (call++ == 0) first.await() else LocalMenu.products

            override suspend fun getProduct(id: String): Product? = LocalMenu.byId(id)
        }
        val vm = MenuViewModel(repo, InMemoryCartRepository(), InMemoryShopRepository())
        vm.loadMenu()
        assertEquals(MenuUiState.Success(LocalMenu.products), vm.uiState.value)
        // Поздняя ошибка первой загрузки не должна затереть Success
        first.completeExceptionally(IOException("поздно"))
        assertEquals(MenuUiState.Success(LocalMenu.products), vm.uiState.value)
    }
}
