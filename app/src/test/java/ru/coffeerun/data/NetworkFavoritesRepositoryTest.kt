package ru.coffeerun.data

import ru.coffeerun.data.repository.NetworkFavoritesRepository

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import ru.coffeerun.StubApi
import ru.coffeerun.data.session.SessionStateStore

/** Подтверждённые сервером данные; оптимистические сценарии проверяются отдельно. */
class NetworkFavoritesRepositoryTest {
    private class FakeApi : StubApi() {
        val server = mutableSetOf("latte")
        var reads = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getFavorites(): List<String> {
            reads++
            val snapshot = server.toList()
            withContext(NonCancellable) { gate?.await() }
            if (fail) throw IOException("offline")
            return snapshot
        }
        override suspend fun addFavorite(productId: String) {
            withContext(NonCancellable) { gate?.await() }
            if (fail) throw IOException("offline")
            server += productId
        }
        override suspend fun removeFavorite(productId: String) {
            if (fail) throw IOException("offline")
            server -= productId
        }
    }

    private val sessions = SessionStateStore("token")

    @Test fun constructor_doesNotLoad() = runTest {
        val api = FakeApi()
        val repo = NetworkFavoritesRepository(api, sessions)
        assertEquals(0, api.reads)
        assertTrue(repo.confirmedIds.value.isEmpty())
    }

    @Test fun explicitSelection_isIdempotent() = runTest {
        val api = FakeApi()
        val repo = NetworkFavoritesRepository(api, sessions)
        val session = sessions.currentSession()
        repo.refresh(session)
        repo.setFavorite("croissant", true, session)
        repo.setFavorite("croissant", true, session)
        assertEquals(setOf("latte", "croissant"), repo.confirmedIds.value)
        assertEquals(setOf("latte", "croissant"), api.server)
        repo.setFavorite("latte", false, session)
        assertEquals(setOf("croissant"), repo.confirmedIds.value)
        assertEquals(setOf("croissant"), api.server)
    }

    @Test fun pendingWrite_doesNotChangeConfirmedIds() = runTest {
        val api = FakeApi()
        val repo = NetworkFavoritesRepository(api, sessions)
        val session = sessions.currentSession()
        repo.refresh(session)
        api.gate = CompletableDeferred()
        val write = async(start = CoroutineStart.UNDISPATCHED) { repo.setFavorite("croissant", true, session) }
        assertEquals(setOf("latte"), repo.confirmedIds.value)
        api.gate!!.complete(Unit)
        write.await()
        assertEquals(setOf("latte", "croissant"), repo.confirmedIds.value)
    }

    @Test fun failedRefresh_keepsConfirmedIds() = runTest {
        val api = FakeApi()
        val repo = NetworkFavoritesRepository(api, sessions)
        val session = sessions.currentSession()
        repo.refresh(session)
        api.fail = true
        assertTrue(runCatching { repo.refresh(session) }.exceptionOrNull() is IOException)
        assertEquals(setOf("latte"), repo.confirmedIds.value)
    }

    @Test fun failedWrite_keepsConfirmedIds() = runTest {
        val api = FakeApi()
        val repo = NetworkFavoritesRepository(api, sessions)
        val session = sessions.currentSession()
        repo.refresh(session)
        api.fail = true
        assertTrue(runCatching { repo.setFavorite("latte", false, session) }.exceptionOrNull() is IOException)
        assertEquals(setOf("latte"), repo.confirmedIds.value)
    }

    @Test fun lateRefresh_doesNotOverwriteNewSession() = runTest {
        val api = FakeApi()
        val repo = NetworkFavoritesRepository(api, sessions)
        val old = sessions.currentSession()
        val reply = CompletableDeferred<Unit>().also { api.gate = it }
        val read = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.refresh(old) } }
        sessions.replaceToken("token")
        val current = sessions.currentSession()
        repo.clear(current)
        api.gate = null
        api.server.clear()
        api.server += "raf"
        repo.refresh(current)
        reply.complete(Unit)
        assertTrue(read.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(setOf("raf"), repo.confirmedIds.value)
    }

    @Test fun lateWrite_doesNotRestoreClearedData() = runTest {
        val api = FakeApi()
        val repo = NetworkFavoritesRepository(api, sessions)
        val old = sessions.currentSession()
        repo.refresh(old)
        val reply = CompletableDeferred<Unit>().also { api.gate = it }
        val write = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.setFavorite("croissant", true, old) } }
        sessions.replaceToken(null)
        repo.clear(sessions.currentSession())
        reply.complete(Unit)
        assertTrue(write.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertTrue(repo.confirmedIds.value.isEmpty())
    }

    @Test fun guest_doesNotSendPersonalRequest() = runTest {
        sessions.replaceToken(null)
        val api = FakeApi()
        val repo = NetworkFavoritesRepository(api, sessions)
        val guest = sessions.currentSession()
        assertTrue(runCatching { repo.refresh(guest) }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertTrue(runCatching { repo.setFavorite("croissant", true, guest) }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(0, api.reads)
        assertEquals(setOf("latte"), api.server)
    }
}
