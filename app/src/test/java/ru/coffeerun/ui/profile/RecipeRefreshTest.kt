package ru.coffeerun.ui.profile

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.RecipeRepository
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.Recipe

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeRefreshTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun refreshFromEmptyListCoalescesClicksAndKeepsCachedRecipesOnFailure() = runTest {
        val cached = MutableStateFlow(emptyList<Recipe>())
        val server = Recipe("one", "Morning", "latte", null)
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val repository = object : RecipeRepository {
            override fun observe(session: AuthSession) = cached
            override suspend fun refresh(session: AuthSession) {
                when (++requests) {
                    1 -> Unit
                    2 -> { gate.await(); cached.value = listOf(server) }
                    else -> throw IOException("offline")
                }
            }
            override suspend fun find(id: String, session: AuthSession) = error("unused")
            override suspend fun save(recipe: Recipe, session: AuthSession) = error("unused")
            override suspend fun rename(id: String, name: String, session: AuthSession) = error("unused")
            override suspend fun remove(id: String, session: AuthSession) = error("unused")
        }
        val vm = RecipesViewModel(repository, InMemoryAuthRepository(true), DemoMenuRepository(), InMemoryCartRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.list.collect {} }
        runCurrent()
        assertFalse(vm.list.value.loading)
        assertTrue(vm.list.value.recipes.isEmpty())
        vm.retry()
        vm.retry()
        runCurrent()
        assertEquals(2, requests)
        assertTrue(vm.refreshing.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(server), vm.list.value.recipes)
        vm.retry()
        runCurrent()
        assertTrue(vm.refreshFailed.value)
        assertFalse(vm.refreshing.value)
        assertEquals(listOf(server), vm.list.value.recipes)
    }
}
