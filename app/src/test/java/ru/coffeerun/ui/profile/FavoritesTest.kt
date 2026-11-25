package ru.coffeerun.ui.profile

import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.FakeMenuRepository
import ru.coffeerun.R
import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.domain.favorites.Favorites
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryFavorites
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.ui.product.ProductUiState
import ru.coffeerun.ui.product.ProductViewModel

/** Избранное: репозиторий, экран списка и признак на экране позиции. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val favorites = InMemoryFavorites()

    @Test
    fun toggle_addsAndRemoves() = runTest {
        favorites.toggle("latte")
        assertEquals(setOf("latte"), favorites.ids.value)
        favorites.toggle("latte")
        assertEquals(emptySet<String>(), favorites.ids.value)
    }

    @Test
    fun unknownIds_canBeRemovedFromFavorites() = runTest {
        favorites.toggle("latte")
        favorites.toggle("gone")
        val vm = FavoritesViewModel(DemoMenuRepository(), favorites)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(FavoritesUiState.Success(listOf(DemoMenu.LATTE), missingIds = setOf("gone")), vm.uiState.value)
        vm.removeMissing("gone") { throw AssertionError("remove failed") }
        assertEquals(setOf("latte"), favorites.ids.value)
    }

    @Test
    fun networkError_isError_thenRetryLoads() = runTest {
        favorites.toggle("latte")
        val repo = FakeMenuRepository(fail = true)
        val vm = FavoritesViewModel(repo, favorites)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(FavoritesUiState.CatalogError, vm.uiState.value)
        repo.fail = false
        vm.retry()
        assertEquals(FavoritesUiState.Success(listOf(DemoMenu.LATTE)), vm.uiState.value)
    }

    @Test
    fun addingFirstFavoriteLoadsItsCatalogCard() = runTest {
        val vm = FavoritesViewModel(DemoMenuRepository(), favorites)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(FavoritesUiState.Success(emptyList()), vm.uiState.value)
        favorites.toggle("latte")
        assertEquals(FavoritesUiState.Success(listOf(DemoMenu.LATTE)), vm.uiState.value)
    }

    @Test
    fun confirmedEmptyFavoritesAreVisibleWhenOffline() = runTest {
        val saved = object : Favorites by favorites {
            override val hasSnapshot = true
            override suspend fun refresh() { throw IOException("offline") }
        }
        val vm = FavoritesViewModel(FakeMenuRepository(fail = true), saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(FavoritesUiState.Success(emptyList(), isCached = true), vm.uiState.value)
    }

    @Test
    fun productInUnsupportedCategoryCannotBeRemovedAsMissing() = runTest {
        favorites.toggle("latte")
        favorites.toggle("new-category")
        val catalog = object : ru.coffeerun.data.MenuRepository by DemoMenuRepository() {
            override suspend fun refresh() = ru.coffeerun.model.MenuSnapshot(
                listOf(DemoMenu.LATTE), DemoMenu.prices, catalogProductIds = setOf("latte", "new-category")
            )
        }
        val vm = FavoritesViewModel(catalog, favorites)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(FavoritesUiState.Success(listOf(DemoMenu.LATTE)), vm.uiState.value)
        vm.removeMissing("new-category") { throw AssertionError() }
        assertTrue("new-category" in favorites.ids.value)
    }

    @Test
    fun staleCatalogCannotAuthorizeRemovingAnUnknownId() = runTest {
        favorites.toggle("latte")
        favorites.toggle("gone")
        val catalog = object : ru.coffeerun.data.MenuRepository by DemoMenuRepository() {
            override suspend fun refresh(): ru.coffeerun.model.MenuSnapshot = throw IOException("offline")
            override fun observeSnapshot() = kotlinx.coroutines.flow.flowOf(
                ru.coffeerun.model.MenuSnapshot(listOf(DemoMenu.LATTE), DemoMenu.prices, isCached = true)
            )
        }
        val vm = FavoritesViewModel(catalog, favorites)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(FavoritesUiState.Success(listOf(DemoMenu.LATTE), isCached = true), vm.uiState.value)
        vm.removeMissing("gone") { throw AssertionError() }
        assertTrue("gone" in favorites.ids.value)
    }

    @Test
    fun product_networkError_isError_thenRetryLoads() = runTest {
        val repo = FakeMenuRepository(fail = true)
        val vm = ProductViewModel(DemoMenu.LATTE.id, repo, InMemoryCartRepository(), favorites, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(ProductUiState.Error, vm.uiState.value)
        vm.addToCart() // без загруженной позиции ничего не происходит
        repo.fail = false
        vm.retry()
        assertEquals(DemoMenu.LATTE, (vm.uiState.value as ProductUiState.Success).product)
    }

    @Test
    fun product_isFavoriteReflectsRepo() = runTest {
        val vm = ProductViewModel(DemoMenu.LATTE.id, DemoMenuRepository(), InMemoryCartRepository(), favorites, SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.toggleFavorite()
        assertTrue((vm.uiState.value as ProductUiState.Success).isFavorite)
    }

    /** Сервер избранного: refresh и toggle падают, пока [fail]. */
    private class FlakyFavorites(var fail: Boolean) : Favorites {
        override val ids = MutableStateFlow(setOf("latte"))
        override suspend fun refresh() {
            if (fail) throw IOException("нет сети")
        }
        override suspend fun toggle(productId: String) = !fail
    }

    @Test
    fun favoritesNotLoaded_isError_thenRetryLoads() = runTest {
        val flaky = FlakyFavorites(fail = true)
        val vm = FavoritesViewModel(DemoMenuRepository(), flaky)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals("меню есть, а избранное с сервера не пришло — тоже ошибка", FavoritesUiState.Error, vm.uiState.value)
        flaky.fail = false
        vm.retry()
        assertEquals(FavoritesUiState.Success(listOf(DemoMenu.LATTE)), vm.uiState.value)
    }

    @Test
    fun product_heartRejected_showsMessage() = runTest {
        val vm = ProductViewModel(DemoMenu.LATTE.id, DemoMenuRepository(), InMemoryCartRepository(), FlakyFavorites(fail = true), SavedStateHandle())
        vm.toggleFavorite()
        assertEquals(R.string.favorites_toggle_error, vm.message.value)
        vm.onMessageShown()
        assertNull(vm.message.value)
    }
}
