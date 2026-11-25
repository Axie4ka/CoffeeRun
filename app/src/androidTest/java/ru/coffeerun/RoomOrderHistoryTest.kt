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
        suspend fun withRepository(action: suspend (NetworkOrderRepository) -> Unit) {
            val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            try {
                RoomOwnerScope(db).reconcile(tokens.currentSession().localOwner)
                action(NetworkOrderRepository(api, DemoMenuRepository(), tokens, db))
            } finally { db.close() }
        }
        try {
            tokens.save("first")
            withRepository { it.refresh(tokens.currentSession()) }
            offline = true
            withRepository {
                assertTrue(runCatching { it.refresh(tokens.currentSession()) }.exceptionOrNull() is IOException)
                val restored = it.orders.value.single()
                assertTrue(restored.confirmedAtMillis != null)
                assertEquals(390, restored.total)
                assertEquals(195, restored.lines.single().unitPrice)
                assertEquals("без сахара", restored.lines.single().options?.note)
                assertTrue(it.isLoaded.value)
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
