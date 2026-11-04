package ru.coffeerun.data

import ru.coffeerun.StubApi
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Избранное на сервере: загрузка после входа, мгновенное переключение с откатом, очистка при выходе. */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkFavoritesRepositoryTest {
    /** Сервер с избранным [server]; [fail] — следующие запросы падают. */
    private class FakeApi(var server: MutableSet<String> = mutableSetOf("latte")) : StubApi() {
        var fail = false
        val sent = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getFavorites(): List<String> {
            if (fail) throw IOException("нет сети")
            return server.toList()
        }

        override suspend fun addFavorite(productId: String) {
            gate?.await()
            if (fail) throw HttpException(Response.error<Unit>(500, "".toResponseBody()))
            sent += "PUT $productId"
            server += productId
        }

        override suspend fun removeFavorite(productId: String) {
            if (fail) throw IOException("нет сети")
            sent += "DELETE $productId"
            server -= productId
        }
    }

    private val loggedIn = MutableStateFlow(true)

    private fun TestScope.repo(api: FakeApi) =
        NetworkFavoritesRepository(api, loggedIn, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun login_loadsFromServer() = runTest {
        loggedIn.value = false
        val repo = repo(FakeApi())
        assertEquals(emptySet<String>(), repo.ids.value)
        loggedIn.value = true
        assertEquals(setOf("latte"), repo.ids.value)
    }

    @Test
    fun toggle_addsWithPut_removesWithDelete() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        assertTrue(repo.toggle("croissant"))
        assertEquals(setOf("latte", "croissant"), repo.ids.value)
        assertTrue(repo.toggle("latte"))
        assertEquals(setOf("croissant"), repo.ids.value)
        assertEquals(listOf("PUT croissant", "DELETE latte"), api.sent)
        assertEquals(setOf("croissant"), api.server)
    }

    @Test
    fun toggle_changesIdsBeforeServerAnswers() = runTest {
        val api = FakeApi().apply { gate = CompletableDeferred() }
        val repo = repo(api)
        // Сразу запускаем toggle: он дойдёт до запроса и встанет на gate — сервер «ещё не ответил»
        val result = async(start = CoroutineStart.UNDISPATCHED) { repo.toggle("croissant") }
        assertTrue("сердечко меняется сразу", "croissant" in repo.ids.value)
        api.gate!!.complete(Unit)
        assertTrue(result.await())
    }

    @Test
    fun toggle_serverError_rollsBack() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.fail = true
        assertFalse(repo.toggle("croissant"))
        assertEquals(setOf("latte"), repo.ids.value)
        assertFalse(repo.toggle("latte"))
        assertEquals("откат удаления возвращает позицию", setOf("latte"), repo.ids.value)
    }

    @Test
    fun logout_clearsIds() = runTest {
        val repo = repo(FakeApi())
        assertEquals(setOf("latte"), repo.ids.value)
        loggedIn.value = false
        assertEquals(emptySet<String>(), repo.ids.value)
    }

    @Test
    fun refresh_offline_isIOException() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.fail = true
        val error = runCatching { repo.refresh() }.exceptionOrNull()
        assertEquals(IOException::class, error!!::class)
        assertEquals("неудачная загрузка не стирает то, что было", setOf("latte"), repo.ids.value)
    }
}
