package ru.coffeerun.ui.profile

import ru.coffeerun.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import kotlinx.coroutines.CompletableDeferred
import ru.coffeerun.domain.orders.Orders
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.ProfileSaveResult
import ru.coffeerun.model.User
import ru.coffeerun.data.InMemoryFavorites
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

/** Профиль: счётчики заказов и избранного следуют за репозиториями. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val orders = InMemoryOrders()
    private val favorites = InMemoryFavorites()
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
    fun logoutFailure_canBeRetriedWithoutShowingSuccess() = runTest {
        var fail = true
        val repository = object : AuthRepository by auth {
            override suspend fun logout() {
                if (fail) throw IOException("storage unavailable")
                auth.logout()
            }
        }
        val vm = ProfileViewModel(InMemoryShopRepository(), orders, favorites, repository, profile)
        vm.logout()
        assertEquals(LogoutState.Failed, vm.logoutState.value)
        assertTrue(auth.currentSession().isLoggedIn)
        fail = false
        vm.logout()
        assertFalse(auth.currentSession().isLoggedIn)
        assertEquals(LogoutState.Idle, vm.logoutState.value)
    }

    @Test
    fun loggedIn_loadsNameAndPhone() = runTest {
        val vm = newVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(AccountState.Loaded(InMemoryAuthRepository.DEMO), vm.uiState.value.account)
    }

    @Test
    fun sameTokenNewLogin_reloadsProfile() = runTest {
        val vm = newVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(1, profile.calls)
        auth.login(InMemoryAuthRepository.DEMO.phone, InMemoryAuthRepository.DEMO_PASSWORD)
        assertEquals(2, profile.calls)
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
        orders.place(listOf(CartLine(DemoMenu.CROISSANT, unitPrice = 180)), Shop.MOSKOVSKAYA, session = auth.currentSession())
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
    private class LoadingOrders : Orders by InMemoryOrders() {
        override val orders = MutableStateFlow(InMemoryOrders().orders.value)
        override val isLoaded = MutableStateFlow(false)
        var calls = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun refresh() {
            calls++
            gate?.await()
            if (fail) throw IOException("offline")
            isLoaded.value = true
        }
    }

    @Test
    fun incompleteHistory_countIsUnknownUntilExplicitProfileLoad() = runTest {
        val loading = LoadingOrders().apply { gate = CompletableDeferred() }
        val vm = ProfileViewModel(InMemoryShopRepository(), loading, favorites, auth, profile)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(1, loading.calls)
        assertNull(vm.uiState.value.ordersCount)
        loading.gate!!.complete(Unit)
        assertEquals(2, vm.uiState.value.ordersCount)
    }

    @Test
    fun loadedEmptyHistory_countIsZero() = runTest {
        val loading = LoadingOrders().apply { orders.value = emptyList() }
        val vm = ProfileViewModel(InMemoryShopRepository(), loading, favorites, auth, profile)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(1, loading.calls)
        assertEquals(0, vm.uiState.value.ordersCount)
    }

    @Test
    fun historyLoadFailure_keepsUnknownCount_retryLoads() = runTest {
        val loading = LoadingOrders().apply { fail = true }
        val vm = ProfileViewModel(InMemoryShopRepository(), loading, favorites, auth, profile)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertNull(vm.uiState.value.ordersCount)
        assertEquals(1, loading.calls)
        assertTrue(vm.uiState.value.ordersLoadFailed)
        loading.fail = false
        vm.retry()
        assertEquals(2, loading.calls)
        assertEquals(2, vm.uiState.value.ordersCount)
        assertFalse(vm.uiState.value.ordersLoadFailed)
    }

    @Test
    fun guest_doesNotLoadHistory_untilLogin() = runTest {
        val loading = LoadingOrders()
        val guest = InMemoryAuthRepository(loggedIn = false)
        val vm = ProfileViewModel(InMemoryShopRepository(), loading, favorites, guest, profile)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(0, loading.calls)
        guest.login(InMemoryAuthRepository.DEMO.phone, InMemoryAuthRepository.DEMO_PASSWORD)
        assertEquals(1, loading.calls)
        assertEquals(2, vm.uiState.value.ordersCount)
    }

    @Test
    fun historyInvalidatedAfterSessionNotification_triggersLoad() = runTest {
        val loading = LoadingOrders().apply { isLoaded.value = true }
        val vm = ProfileViewModel(InMemoryShopRepository(), loading, favorites, auth, profile)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        auth.login(InMemoryAuthRepository.DEMO.phone, InMemoryAuthRepository.DEMO_PASSWORD)
        assertEquals(0, loading.calls)
        // Coordinator может очистить кеш после того, как ViewModel уже получила новую сессию.
        loading.isLoaded.value = false
        assertEquals(1, loading.calls)
        assertEquals(2, vm.uiState.value.ordersCount)
    }

}
