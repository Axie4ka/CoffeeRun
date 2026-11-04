package ru.coffeerun.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private fun store(): TokenStore {
        scopes.forEach { it.cancel() }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return TokenStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

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
}
