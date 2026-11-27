package ru.coffeerun.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryFavorites
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.RecipeRepository
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.Promotion
import ru.coffeerun.model.PromotionType
import ru.coffeerun.model.Recipe
import ru.coffeerun.testCheckout
import ru.coffeerun.ui.cart.CartViewModel
import ru.coffeerun.ui.product.ProductUiState
import ru.coffeerun.ui.product.ProductViewModel
import ru.coffeerun.ui.profile.RecipesViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class PromotionJourneyTest {
    @get:Rule val main = MainDispatcherRule()
    private val cart = InMemoryCartRepository()
    private val catalogue = MutableStateFlow(MenuSnapshot(DemoMenu.products, DemoMenu.prices))
    private val menu = object : MenuRepository by DemoMenuRepository() {
        override fun observeSnapshot() = catalogue
    }

    private fun TestScope.observe(state: StateFlow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
    }

    private fun TestScope.assertCartAndCheckoutTotal(expected: Int) {
        val basket = CartViewModel(cart, menu)
        basket.onShown()
        observe(basket.uiState)
        val checkout = testCheckout(cart, InMemoryOrders(), InMemoryShopRepository(), menu = menu)
        observe(checkout.uiState)
        runCurrent()
        assertEquals(expected, basket.uiState.value.total)
        assertEquals(expected, checkout.uiState.value.total)
    }

    @Test fun threeCroissantsCostTheSameFromProductToCheckout() = runTest {
        catalogue.value = catalogue.value.copy(promotions = listOf(
            Promotion("pastry", PromotionType.PASTRY_2_PLUS_1, 0, Long.MAX_VALUE, category = ProductCategory.PASTRY)
        ))
        val product = ProductViewModel(DemoMenu.CROISSANT.id, menu, cart, InMemoryFavorites(), SavedStateHandle())
        observe(product.uiState)
        product.changeQuantity(2)
        runCurrent()
        assertEquals(360L, (product.uiState.value as ProductUiState.Success).totalPrice)
        product.addToCart()
        assertCartAndCheckoutTotal(360)
    }

    @Test fun savedMediumCappuccinoUsesTheCurrentOfferThroughCheckout() = runTest {
        val drink = DemoMenu.CAPPUCCINO
        val recipe = Recipe("morning", "Morning", drink.id, drink.defaultOptions())
        val saved = object : RecipeRepository {
            override fun observe(session: AuthSession) = flowOf(listOf(recipe))
            override suspend fun find(id: String, session: AuthSession) = recipe.takeIf { it.id == id }
            override suspend fun refresh(session: AuthSession) = Unit
            override suspend fun save(recipe: Recipe, session: AuthSession) = error("unused")
            override suspend fun rename(id: String, name: String, session: AuthSession) = error("unused")
            override suspend fun remove(id: String, session: AuthSession) = error("unused")
        }
        val auth = InMemoryAuthRepository(loggedIn = true)
        val recipes = RecipesViewModel(saved, auth, menu, cart)
        observe(recipes.list)
        val offer = Promotion("medium", PromotionType.CAPPUCCINO_MEDIUM, 0, Long.MAX_VALUE, drink.id)
        for ((end, price) in listOf(Long.MAX_VALUE to 208, 1L to 260)) {
            catalogue.value = catalogue.value.copy(promotions = listOf(offer.copy(endsAtMillis = end)))
            runCurrent()
            val entry = recipes.list.value.recipes.single()
            assertEquals(price.toLong(), recipes.recipeMenu.value!!.portionPrice(entry))
            recipes.add(entry, auth.currentSession())
            assertCartAndCheckoutTotal(price)
            cart.clear()
        }
    }
}
