package ru.coffeerun

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.coffeerun.data.NetworkMenuRepository
import ru.coffeerun.data.local.CoffeeRunDatabase
import ru.coffeerun.data.network.MenuDto
import ru.coffeerun.data.network.ProductDto
import ru.coffeerun.data.network.SizeOptionDto
import ru.coffeerun.model.DrinkSize

@RunWith(AndroidJUnit4::class)
class RoomMenuCacheTest {
    @Test
    fun reopeningDatabaseKeepsBothLanguages_andCheckoutRefreshStillRequiresNetwork(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "menu-test-${UUID.randomUUID()}.db"
        var language = "ru"
        var offline = false
        val api = object : StubApi() {
            override suspend fun getMenu(language: String?): MenuDto {
                if (offline) throw IOException("offline")
                val title = if (language == "ru") "Латте" else "Latte"
                val product = ProductDto("latte", "COFFEE", title, "", "", "images/latte.jpg",
                    240, true, 190, emptyList(), listOf("SIZE"), emptyList())
                return MenuDto(listOf(product), listOf(SizeOptionDto("SMALL", 240)), emptyList(), emptyList())
            }
        }
        suspend fun withRepository(action: suspend (NetworkMenuRepository) -> Unit) {
            val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            try { action(NetworkMenuRepository(api, "http://host/", { language }, db.menuSnapshots())) }
            finally { db.close() }
        }
        try {
            withRepository {
                assertEquals("Латте", it.getMenu().single().name)
                language = "en"
                assertEquals("Latte", it.getMenu().single().name)
            }
            offline = true
            withRepository {
                assertEquals("Latte", it.observeSnapshot().first().products.single().name)
                language = "ru"
                val cached = it.observeSnapshot().first()
                assertEquals("Латте", cached.products.single().name)
                assertEquals(240, cached.prices.sizePrice(DrinkSize.SMALL))
                assertTrue(cached.isCached)
                assertTrue(runCatching { it.refresh() }.exceptionOrNull() is IOException)
                assertEquals("Латте", it.getMenu().single().name)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
