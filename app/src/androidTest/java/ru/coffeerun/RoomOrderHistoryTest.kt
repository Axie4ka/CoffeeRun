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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.repository.NetworkOrderRepository
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.local.datastore.TokenStore
import ru.coffeerun.data.network.dto.DrinkOptionsDto
import ru.coffeerun.data.network.dto.OrderDto
import ru.coffeerun.data.network.dto.OrderLineDto

@RunWith(AndroidJUnit4::class)
class RoomOrderHistoryTest {
    @Test
    fun offlineHistoryPreservesPaidRecipe_andIsClearedWhenAccountChanges(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-test-${UUID.randomUUID()}.db"
        val file = File(context.cacheDir, "history-test-${UUID.randomUUID()}.preferences_pb")
        val tokenScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val tokens = TokenStore(PreferenceDataStoreFactory.create(scope = tokenScope) { file })
        var offline = false
        var serveEmpty = false
        val api = object : StubApi() {
            override suspend fun getOrders(): List<OrderDto> {
                if (offline) throw IOException("offline")
                if (serveEmpty) return emptyList()
                return listOf(OrderDto(1037, "PICKED_UP", "MOSKOVSKAYA", "2026-11-14T09:30:00+03:00",
                    lines = listOf(OrderLineDto("latte", 2, 195, DrinkOptionsDto("LARGE", "OAT", false,
                        listOf("VANILLA"), "без сахара")))))
            }
        }
        suspend fun withDatabase(action: suspend (NetworkOrderRepository, CoffeeRunDatabase) -> Unit) {
            val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            try {
                RoomOwnerScope(db).reconcile(tokens.currentSession().localOwner)
                action(NetworkOrderRepository(api, DemoMenuRepository(), tokens, db), db)
            } finally { db.close() }
        }
        suspend fun withRepository(action: suspend (NetworkOrderRepository) -> Unit) =
            withDatabase { repository, _ -> action(repository) }
        try {
            tokens.save("first")
            withRepository { it.refresh(tokens.currentSession()) }
            offline = true
            withDatabase { repository, db ->
                assertTrue(runCatching { repository.refresh(tokens.currentSession()) }.exceptionOrNull() is IOException)
                val restored = repository.orders.value.single()
                assertTrue(restored.confirmedAtMillis != null)
                assertEquals(390, restored.total)
                assertEquals(195, restored.lines.single().unitPrice)
                assertEquals("без сахара", restored.lines.single().options?.note)
                assertTrue(repository.isLoaded.value)
                val session = tokens.currentSession()
                assertEquals(restored, repository.findSaved(1037, session))
                assertNull(repository.findSaved(9999, session))
                val row = checkNotNull(db.orderHistory().find(checkNotNull(session.localOwner), 1037))
                db.orderHistory().save(listOf(row.copy(payload = "{")))
                assertTrue(runCatching { repository.findSaved(1037, session) }.exceptionOrNull() is IOException)
                db.orderHistory().save(listOf(row))
                assertEquals(restored, repository.findSaved(1037, session))
            }
            tokens.save("second")
            withRepository {
                assertTrue(runCatching { it.refresh(tokens.currentSession()) }.exceptionOrNull() is IOException)
                assertTrue(it.orders.value.isEmpty())
                assertTrue(!it.isLoaded.value)
            }
            offline = false
            serveEmpty = true
            withRepository { it.refresh(tokens.currentSession()) }
            offline = true
            withRepository {
                assertTrue(runCatching { it.refresh(tokens.currentSession()) }.exceptionOrNull() is IOException)
                assertTrue(it.orders.value.isEmpty())
                assertTrue(it.isLoaded.value)
            }
        } finally {
            tokenScope.coroutineContext[Job]?.cancelAndJoin()
            context.deleteDatabase(name)
            file.delete()
        }
    }
}
