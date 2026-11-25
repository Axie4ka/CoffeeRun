package ru.coffeerun

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.coffeerun.data.repository.NetworkFavoritesRepository
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.local.datastore.TokenStore

@RunWith(AndroidJUnit4::class)
class RoomFavoritesCacheTest {
    @Test
    fun confirmedEmptyFavoritesRemainKnownOffline_andNewOwnerHasNoSnapshot(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "favorites-test-${UUID.randomUUID()}.db"
        val prefs = File(context.cacheDir, "favorites-test-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val tokens = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { prefs })
        var offline = false
        val api = object : StubApi() {
            override suspend fun getFavorites(): List<String> {
                if (offline) throw IOException("offline")
                return emptyList()
            }
        }
        suspend fun withRepository(action: suspend (NetworkFavoritesRepository) -> Unit) {
            val database = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            try {
                RoomOwnerScope(database).reconcile(tokens.currentSession().localOwner)
                action(NetworkFavoritesRepository(api, tokens, database))
            } finally {
                database.close()
            }
        }
        try {
            tokens.save("test-a")
            withRepository { it.refresh(tokens.currentSession()) }
            offline = true
            withRepository {
                assertTrue(runCatching { it.refresh(tokens.currentSession()) }.exceptionOrNull() is IOException)
                assertEquals(emptySet<String>(), it.confirmedIds.value)
                assertTrue(it.hasSnapshot)
            }
            tokens.save("test-b")
            withRepository {
                assertTrue(runCatching { it.refresh(tokens.currentSession()) }.exceptionOrNull() is IOException)
                assertFalse(it.hasSnapshot)
            }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            context.deleteDatabase(name)
            prefs.delete()
        }
    }
}
