package ru.coffeerun

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryFavoritesRepository
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.data.LocalMenuRepository
import ru.coffeerun.ui.profile.FavoritesUiState
import ru.coffeerun.ui.profile.FavoritesViewModel
import ru.coffeerun.ui.product.ProductUiState
import ru.coffeerun.ui.product.ProductViewModel

/** Избранное: репозиторий, экран списка и признак на экране позиции. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val favorites = InMemoryFavoritesRepository()

    @Test
    fun toggle_addsAndRemoves() = runTest {
        favorites.toggle("latte")
        assertEquals(setOf("latte"), favorites.ids.value)
        favorites.toggle("latte")
        assertEquals(emptySet<String>(), favorites.ids.value)
    }

    @Test
    fun unknownIds_areSkipped() = runTest {
        favorites.toggle("latte")
        favorites.toggle("gone")
        val vm = FavoritesViewModel(LocalMenuRepository(), favorites)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(FavoritesUiState.Success(listOf(LocalMenu.LATTE)), vm.uiState.value)
    }

    @Test
    fun networkError_isError_thenRetryLoads() = runTest {
        favorites.toggle("latte")
        val repo = FakeMenuRepository(fail = true)
        val vm = FavoritesViewModel(repo, favorites)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(FavoritesUiState.Error, vm.uiState.value)
        repo.fail = false
        vm.retry()
        assertEquals(FavoritesUiState.Success(listOf(LocalMenu.LATTE)), vm.uiState.value)
    }

    @Test
    fun product_networkError_isError_thenRetryLoads() = runTest {
        val repo = FakeMenuRepository(fail = true)
        val vm = ProductViewModel(LocalMenu.LATTE.id, repo, InMemoryCartRepository(), favorites, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(ProductUiState.Error, vm.uiState.value)
        vm.addToCart() // без загруженной позиции ничего не происходит
        repo.fail = false
        vm.retry()
        assertEquals(LocalMenu.LATTE, (vm.uiState.value as ProductUiState.Success).product)
    }

    @Test
    fun product_isFavoriteReflectsRepo() = runTest {
        val vm = ProductViewModel(LocalMenu.LATTE.id, LocalMenuRepository(), InMemoryCartRepository(), favorites, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.toggleFavorite()
        assertTrue((vm.uiState.value as ProductUiState.Success).isFavorite)
    }
}
