package ru.coffeerun.ui.profile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.testCheckout
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.domain.promotion.pricingTimes
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Promotion
import ru.coffeerun.model.PromotionType
import ru.coffeerun.model.Recipe
import ru.coffeerun.ui.cart.CartViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class RecipePricingTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @Test fun recipeCartAndCheckoutAgreeOnPromotionAndRequireAvailableOptions() = runTest {
        val product = DemoMenu.CAPPUCCINO
        val recipe = Recipe("morning", "Morning", product.id, product.defaultOptions()!!.copy(
            milk = MilkType.OAT, extras = setOf(Extra.VANILLA)
        ))
        val menu = MutableStateFlow(MenuSnapshot(listOf(product), DemoMenu.prices))
        val repository = object : MenuRepository by DemoMenuRepository() {
            override fun observeSnapshot() = menu
        }
        val cart = InMemoryCartRepository()
        val vm = RecipesViewModel(null, InMemoryAuthRepository(), repository, cart)
        val regular = recipe.copy(options = product.defaultOptions())
        cart.add(checkNotNull(regular.toCartLine(menu.value)))
        val cartVm = CartViewModel(cart, repository).apply { onShown() }
        val checkout = testCheckout(cart, InMemoryOrders(), InMemoryShopRepository(), menu = repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cartVm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checkout.uiState.collect {} }
        fun price() = vm.recipeMenu.value!!.portionPrice(recipe)
        fun assertRegularPrice(expected: Int) {
            assertEquals(expected.toLong(), vm.recipeMenu.value!!.portionPrice(regular))
            assertEquals(expected, cartVm.uiState.value.total)
            assertEquals(expected, checkout.uiState.value.total)
        }
        runCurrent()
        assertEquals(340L, price())
        assertRegularPrice(260)
        menu.value = menu.value.copy(promotions = listOf(
            Promotion("medium", PromotionType.CAPPUCCINO_MEDIUM, 0L, Long.MAX_VALUE, product.id)
        ))
        runCurrent()
        assertEquals(288L, price())
        assertRegularPrice(208)
        menu.value = menu.value.copy(promotions = emptyList())
        runCurrent()
        assertEquals(340L, price())
        assertRegularPrice(260)
        menu.value = menu.value.copy(prices = DemoMenu.prices.copy(extras = emptyMap()))
        runCurrent()
        assertNull(price())
    }

    @Test fun promotionClockRechecksBoundariesAndSkipsAnOfferThatEndedDuringSleep() = runTest {
        val product = DemoMenu.CAPPUCCINO
        val menu = MenuSnapshot(listOf(product), DemoMenu.prices, promotions = listOf(
            Promotion("medium", PromotionType.CAPPUCCINO_MEDIUM, 100L, 200L, product.id)
        ))
        val resume = MutableStateFlow(0L)
        var wallTime = 0L
        val emitted = mutableListOf<Long>()
        backgroundScope.launch {
            menu.pricingTimes(now = { wallTime }, resume = resume).take(4).toList(emitted)
        }
        runCurrent()
        advanceTimeBy(100)
        wallTime = 100
        runCurrent()
        advanceTimeBy(100)
        wallTime = 200
        runCurrent()
        assertEquals(listOf(0L, 100L, 200L), emitted)

        wallTime = 50
        resume.value++
        runCurrent()
        assertEquals(listOf(0L, 100L, 200L, 50L), emitted)

        val resumedTimes = mutableListOf<Long>()
        backgroundScope.launch {
            menu.pricingTimes(now = { wallTime }, resume = resume).take(2).toList(resumedTimes)
        }
        runCurrent()
        wallTime = 300
        resume.value++
        runCurrent()
        assertEquals(listOf(50L, 300L), resumedTimes)
    }
}
