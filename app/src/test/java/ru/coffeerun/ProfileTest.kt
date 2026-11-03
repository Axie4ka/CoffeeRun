package ru.coffeerun

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.ProfileSaveResult
import ru.coffeerun.model.User
import ru.coffeerun.ui.profile.AccountState
import ru.coffeerun.data.InMemoryFavoritesRepository
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.profile.ProfileViewModel

/** Профиль: счётчики заказов и избранного следуют за репозиториями. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val orders = InMemoryOrderRepository()
    private val favorites = InMemoryFavoritesRepository()
    private val auth = InMemoryAuthRepository(loggedIn = true)

    /** Профиль с сервера: считает запросы, при [fail] — «нет сети». */
    private class FakeProfile(var fail: Boolean = false) : ProfileRepository {
        var calls = 0
        override suspend fun getMe(): User {
            calls++
            if (fail) throw IOException("нет сети")
            return InMemoryAuthRepository.DEMO
        }
        override suspend fun updateMe(name: String, phone: String) = ProfileSaveResult.Unavailable
    }
    private val profile = FakeProfile()

    // viewModelScope привязывается к Main при создании, поэтому VM строим после установки правила
    private fun newVm(auth: InMemoryAuthRepository = this.auth) = ProfileViewModel(InMemoryShopRepository(), orders, favorites, auth, profile)

    @Test
    fun loggedIn_loadsNameAndPhone() = runTest {
        val vm = newVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(AccountState.Loaded(InMemoryAuthRepository.DEMO), vm.uiState.value.account)
    }

    @Test
    fun loadError_thenRetry_loads() = runTest {
        profile.fail = true
        val vm = newVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(AccountState.Error, vm.uiState.value.account)
        profile.fail = false
        vm.retry()
        assertEquals(AccountState.Loaded(InMemoryAuthRepository.DEMO), vm.uiState.value.account)
    }

    @Test
    fun guest_doesNotRequestProfile_untilLogin() = runTest {
        val guestAuth = InMemoryAuthRepository(loggedIn = false)
        val vm = newVm(guestAuth)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(0, profile.calls)
        guestAuth.login(InMemoryAuthRepository.DEMO.phone, InMemoryAuthRepository.DEMO_PASSWORD)
        assertEquals(1, profile.calls)
        assertEquals(AccountState.Loaded(InMemoryAuthRepository.DEMO), vm.uiState.value.account)
    }

    @Test
    fun logout_turnsProfileIntoGuest() = runTest {
        val vm = newVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(true, vm.uiState.value.isLoggedIn)
        vm.logout()
        assertEquals(false, vm.uiState.value.isLoggedIn)
    }

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
