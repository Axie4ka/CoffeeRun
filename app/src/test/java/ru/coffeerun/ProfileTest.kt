package ru.coffeerun

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryFavoritesRepository
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.screens.ProfileViewModel

/** Профиль: счётчики заказов и избранного следуют за репозиториями. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val orders = InMemoryOrderRepository()
    private val favorites = InMemoryFavoritesRepository()
    // viewModelScope привязывается к Main при создании, поэтому VM строим после установки правила
    private fun newVm() = ProfileViewModel(InMemoryShopRepository(), orders, favorites)

    @Test
    fun profile_ordersCountFollowsRepository() = runTest {
        val vm = newVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(2, vm.uiState.value.ordersCount)
        orders.place(listOf(CartLine(LocalMenu.CROISSANT, unitPrice = 180)), Shop.MOSKOVSKAYA)
        assertEquals(3, vm.uiState.value.ordersCount)
    }

    @Test
    fun profile_favoritesCountFollowsRepository() = runTest {
        val vm = newVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(0, vm.uiState.value.favoritesCount)
        favorites.toggle("latte")
        assertEquals(1, vm.uiState.value.favoritesCount)
        // Выбор кофейни — в том же состоянии
        vm.selectShop(Shop.STROITELEY)
        assertEquals(Shop.STROITELEY, vm.uiState.value.shop)
    }
}
