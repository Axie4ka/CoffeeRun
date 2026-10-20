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
import ru.coffeerun.ui.screens.FavoritesUiState
import ru.coffeerun.ui.screens.FavoritesViewModel
import ru.coffeerun.ui.screens.ProductUiState
import ru.coffeerun.ui.screens.ProductViewModel

/** Избранное: репозиторий, экран списка и признак на экране позиции. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val favorites = InMemoryFavoritesRepository()

    @Test
    fun toggle_addsAndRemoves() {
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
    fun product_isFavoriteReflectsRepo() = runTest {
        val vm = ProductViewModel(LocalMenu.LATTE.id, LocalMenuRepository(), InMemoryCartRepository(), favorites, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.toggleFavorite()
        assertTrue((vm.uiState.value as ProductUiState.Success).isFavorite)
    }
}
