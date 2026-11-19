package ru.coffeerun.ui.auth

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import ru.coffeerun.AppContainer
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryProfileRepository
import ru.coffeerun.data.InMemoryFavorites
import ru.coffeerun.data.InMemoryOrders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.model.CartLine

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun logout_clearsCart() = runTest {
        val auth = InMemoryAuthRepository(loggedIn = true)
        val container = AppContainer(DemoMenuRepository(), auth, InMemoryProfileRepository(), InMemoryFavorites(), InMemoryOrders())
        container.observeSession(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        val cart = container.cart
        val vm = SessionViewModel(auth)
        cart.addLine(CartLine(DemoMenu.CROISSANT, 180))
        auth.logout()
        assertTrue(cart.lines.value.isEmpty())
        assertEquals(false, vm.isLoggedIn.value)
    }

    @Test fun guestLogin_preservesCart_newLoginClearsIt() = runTest {
        val auth = InMemoryAuthRepository()
        val container = AppContainer(DemoMenuRepository(), auth, InMemoryProfileRepository(), InMemoryFavorites(), InMemoryOrders())
        container.observeSession(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        val cart = container.cart
        val vm = SessionViewModel(auth)
        cart.addLine(CartLine(DemoMenu.CROISSANT, 180))
        auth.login(InMemoryAuthRepository.DEMO.phone, InMemoryAuthRepository.DEMO_PASSWORD)
        assertEquals(1, cart.lines.value.size)
        auth.login(InMemoryAuthRepository.DEMO.phone, InMemoryAuthRepository.DEMO_PASSWORD)
        assertTrue(cart.lines.value.isEmpty())
        assertEquals(true, vm.isLoggedIn.value)
    }
}
