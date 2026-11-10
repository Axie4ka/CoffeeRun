package ru.coffeerun.domain.favorites

import ru.coffeerun.data.NetworkFavoritesRepository

import ru.coffeerun.StubApi
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.coffeerun.data.session.SessionStateStore
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

/** Оптимистический сценарий с настоящим сетевым репозиторием: гонки, откат и смена сессии. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesCoordinatorTest {
    /** Сервер с избранным [server]; [fail] — следующие запросы падают. */
    private class FakeApi(var server: MutableSet<String> = mutableSetOf("latte")) : StubApi() {
        var fail = false
        val sent = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        var readGate: CompletableDeferred<Unit>? = null
        var addReply: (suspend (String) -> Unit)? = null
        var removeReply: (suspend (String) -> Unit)? = null

        override suspend fun getFavorites(): List<String> {
            val snapshot = server.toList()
            withContext(NonCancellable) { readGate?.await() }
            if (fail) throw IOException("нет сети")
            return snapshot
        }

        override suspend fun addFavorite(productId: String) {
            addReply?.let { return it(productId) }
            gate?.await()
            if (fail) throw HttpException(Response.error<Unit>(500, "".toResponseBody()))
            sent += "PUT $productId"
            server += productId
        }

        override suspend fun removeFavorite(productId: String) {
            removeReply?.let { return it(productId) }
            if (fail) throw IOException("нет сети")
            sent += "DELETE $productId"
            server -= productId
        }
    }

    private val sessions = SessionStateStore("demo-token")

    private fun TestScope.repo(api: FakeApi) =
        FavoritesCoordinator(NetworkFavoritesRepository(api, sessions), sessions, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun login_loadsFromServer() = runTest {
        sessions.replaceToken(null)
        val repo = repo(FakeApi())
        assertEquals(emptySet<String>(), repo.ids.value)
        sessions.replaceToken("demo-token")
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
        sessions.replaceToken(null)
        assertEquals(emptySet<String>(), repo.ids.value)
    }

    @Test
    fun lateRefresh_afterLogout_doesNotRestoreIds() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>().also { api.readGate = it }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.refresh() } }
        sessions.replaceToken(null)
        assertTrue(repo.ids.value.isEmpty())
        reply.complete(Unit)
        refresh.await()
        assertTrue(repo.ids.value.isEmpty())
    }

    @Test
    fun refresh_doesNotEraseNewToggle() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>().also { api.readGate = it }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        val toggle = async(start = CoroutineStart.UNDISPATCHED) { repo.toggle("croissant") }
        assertTrue("намерение видно во время загрузки", "croissant" in repo.ids.value)
        reply.complete(Unit)
        refresh.await()
        toggle.await()
        assertEquals(setOf("latte", "croissant"), repo.ids.value)
    }

    @Test
    fun oldFailure_doesNotUndoLatestToggle() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>()
        var first = true
        api.addReply = { id ->
            if (first) {
                first = false
                reply.await()
                throw IOException("старый запрос")
            }
            api.server += id
        }
        val add = async(start = CoroutineStart.UNDISPATCHED) { repo.toggle("croissant") }
        val remove = async(start = CoroutineStart.UNDISPATCHED) { repo.toggle("croissant") }
        val addAgain = async(start = CoroutineStart.UNDISPATCHED) { repo.toggle("croissant") }
        reply.complete(Unit)
        add.await(); remove.await(); addAgain.await()
        assertTrue("последнее намерение подтверждено сервером", "croissant" in api.server)
        assertTrue("старая ошибка не стирает последнее сердечко", "croissant" in repo.ids.value)
    }

    @Test
    fun sameTokenNewLogin_reloadsIds() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.server = mutableSetOf("croissant")
        sessions.replaceToken("demo-token")
        assertEquals(setOf("croissant"), repo.ids.value)
    }

    @Test
    fun cancelledCaller_doesNotLoseToggle() = runTest {
        val api = FakeApi().apply { gate = CompletableDeferred() }
        val repo = repo(api)
        val caller = async(start = CoroutineStart.UNDISPATCHED) { repo.toggle("croissant") }
        caller.cancel()
        api.gate!!.complete(Unit)
        assertTrue("изменение отправляется после закрытия экрана", "croissant" in api.server)
        assertTrue("croissant" in repo.ids.value)
    }

    @Test
    fun lateToggle_afterNewLogin_doesNotRestorePreviousIds() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>()
        api.addReply = { withContext(NonCancellable) { reply.await() } }
        val caller = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.toggle("croissant") } }
        sessions.replaceToken(null)
        api.server = mutableSetOf("raf")
        sessions.replaceToken("demo-token")
        assertEquals(setOf("raf"), repo.ids.value)
        reply.complete(Unit)
        assertTrue(caller.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(setOf("raf"), repo.ids.value)
    }

    @Test
    fun differentProducts_keepIndependentIntentions() = runTest {
        val api = FakeApi().apply { gate = CompletableDeferred() }
        val repo = repo(api)
        val first = async(start = CoroutineStart.UNDISPATCHED) { repo.toggle("croissant") }
        val second = async(start = CoroutineStart.UNDISPATCHED) { repo.toggle("raf") }
        assertEquals(setOf("latte", "croissant", "raf"), repo.ids.value)
        api.gate!!.complete(Unit)
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(setOf("latte", "croissant", "raf"), api.server)
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
    @Test
    fun oldSuccess_doesNotShowItemHiddenByLatestIntent() = runTest {
        val api = FakeApi()
        val coordinator = repo(api)
        val added = CompletableDeferred<Unit>()
        val removed = CompletableDeferred<Unit>()
        val removing = CompletableDeferred<Unit>()
        api.addReply = { id -> added.await(); api.server += id }
        api.removeReply = { id -> removing.complete(Unit); removed.await(); api.server -= id }
        val add = async(start = CoroutineStart.UNDISPATCHED) { coordinator.toggle("croissant") }
        val remove = async(start = CoroutineStart.UNDISPATCHED) { coordinator.toggle("croissant") }
        assertEquals(setOf("latte"), coordinator.ids.value)
        added.complete(Unit)
        removing.await()
        assertTrue(add.await())
        assertEquals("старый успех подтверждён сервером", setOf("latte", "croissant"), api.server)
        assertEquals("последнее намерение остаётся поверх подтверждённых ID", setOf("latte"), coordinator.ids.value)
        removed.complete(Unit)
        assertTrue(remove.await())
        assertEquals(setOf("latte"), coordinator.ids.value)
        assertEquals(setOf("latte"), api.server)
    }

}
