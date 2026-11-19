package ru.coffeerun.data.local

import ru.coffeerun.testPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Токен в DataStore: сохраняется, стирается и переживает «перезапуск» — новый DataStore на том же файле. */
class TokenStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File by lazy { File(folder.root, "auth.preferences_pb") }

    /** DataStore на временном файле. На один файл — один активный DataStore, поэтому старый scope гасим. */
    private fun store() = TokenStore(openDisk())

    private fun openDisk(): DataStore<Preferences> {
        scopes.forEach { it.cancel() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return testPreferences(scope, file)
    }

    private suspend fun persistedOwner(): String? =
        openDisk().data.first()[androidx.datastore.preferences.core.stringPreferencesKey("local_owner")]

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun saveAndClear() = runTest {
        val store = store()
        assertNull(store.token.first())
        store.save("demo-token")
        assertEquals("demo-token", store.token.first())
        store.clear()
        assertNull(store.token.first())
    }

    @Test
    fun token_survivesRestart() = runTest {
        store().save("demo-token")
        // Как после закрытия приложения: новый DataStore читает тот же файл
        assertEquals("demo-token", store().token.first())
    }

    @Test
    fun sameToken_savedAgain_startsAnotherSession() = runTest {
        val store = store()
        store.save("demo-token")
        val previous = store.currentSession()
        store.save("demo-token")
        val current = store.currentSession()
        assertTrue(current.isLoggedIn)
        assertTrue(current.generation > previous.generation)
        assertEquals("demo-token", store.token.first())
    }

    @Test
    fun localOwner_survivesRestartButChangesOnSameTokenLogin() = runTest {
        val first = store()
        first.save("demo-token")
        val owner = persistedOwner()
        assertTrue(!owner.isNullOrBlank())

        val restarted = store()
        assertEquals(owner, restarted.currentSession().localOwner)
        restarted.save("demo-token")
        assertTrue(restarted.currentSession().localOwner != owner)
        assertEquals(restarted.currentSession().localOwner, persistedOwner())
        store().clear()
        assertNull(persistedOwner())
    }

    @Test
    fun existingTokenWithoutOwner_getsOneStableOwner() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val disk = testPreferences(scope, file)
        disk.updateData {
            androidx.datastore.preferences.core.preferencesOf(
                androidx.datastore.preferences.core.stringPreferencesKey("token") to "old-token"
            )
        }
        assertTrue(store().currentSession().isLoggedIn)
        val owner = persistedOwner()
        assertTrue(!owner.isNullOrBlank())
        assertTrue(store().currentSession().isLoggedIn)
        assertEquals(owner, persistedOwner())
    }

    @Test
    fun staleSession_cannotApplyLocalChanges() = runTest {
        val store = store()
        store.save("demo-token")
        val previous = store.currentSession()
        store.clear()
        var applied = false
        val result = runCatching { store.withSession(previous) { applied = true } }
        assertTrue(result.exceptionOrNull() is ru.coffeerun.data.session.SessionChangedException)
        assertFalse(applied)
        assertFalse(store.currentSession().isLoggedIn)
    }

    @Test
    fun failedPersistence_doesNotPublishTokenOrNewSession() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val disk = testPreferences(scope, file)
        val failing = object : DataStore<Preferences> by disk {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw IOException("запись недоступна")
            }
        }
        val store = TokenStore(failing)
        val previous = store.currentSession()
        assertTrue(runCatching { store.save("demo-token") }.exceptionOrNull() is IOException)
        assertEquals(previous, store.currentSession())
        assertNull(store.token.first())
    }

    @Test
    fun cancellationAfterDiskCommit_keepsMemoryInSync() = runTest {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val disk = testPreferences(scope, file)
        val committed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var pause = false
        val delayed = object : DataStore<Preferences> by disk {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                val result = disk.updateData(transform)
                if (pause) {
                    committed.complete(Unit)
                    release.await() // Моделирует ожидание подтверждения после записи на диск.
                }
                return result
            }
        }
        val store = TokenStore(delayed)
        store.save("old-token")
        val previous = store.currentSession()
        pause = true
        val saving = launch { store.save("new-token") }
        committed.await()
        saving.cancel()
        release.complete(Unit)
        saving.join()
        assertEquals("new-token", store.token.first())
        assertTrue(store.currentSession().generation > previous.generation)
    }
}
