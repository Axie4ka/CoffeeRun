package ru.coffeerun

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.local.CoffeeRunDatabase
import ru.coffeerun.data.local.RoomCartRepository
import ru.coffeerun.data.local.RoomCheckoutAttemptStore
import ru.coffeerun.data.local.RoomOwnerScope
import ru.coffeerun.data.local.TokenStore
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.Shop

@RunWith(AndroidJUnit4::class)
class RoomCartTest {
    @Test
    fun cartPersistsAndConfirmationConsumesOnlySubmittedQuantity(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cart-test-${UUID.randomUUID()}.db"
        val prefs = File(context.cacheDir, "cart-test-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val tokens = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { prefs })
        suspend fun withCart(action: suspend (RoomCartRepository, RoomCheckoutAttemptStore) -> Unit) {
            val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            val cartScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                RoomOwnerScope(db).reconcile(tokens.currentSession().localOwner)
                action(RoomCartRepository(db, tokens, cartScope), RoomCheckoutAttemptStore(db))
            } finally {
                cartScope.coroutineContext[Job]?.cancelAndJoin()
                db.close()
            }
        }
        try {
            tokens.save("cart-test-token")
            val session = tokens.currentSession()
            val owner = checkNotNull(session.localOwner)
            val latte = DemoMenu.LATTE
            val options = latte.defaultOptions()!!.copy(note = "без сахара")
            val line = CartLine(latte, 240, quantity = 2, options = options)
            var savedId = 0
            withCart { cart, _ ->
                cart.addLine(line)
                savedId = cart.snapshot(session).single().id
            }
            withCart { cart, attempts ->
                val submitted = cart.snapshot(session)
                assertEquals(line.copy(id = savedId), submitted.single())
                assertTrue(attempts.begin(owner, "first", submitted, Shop.MOSKOVSKAYA, null))
                cart.addLine(line.copy(product = latte.copy(name = "Latte"), quantity = 1))
                assertEquals(listOf(false), cart.addWithinLimit(listOf(line.copy(quantity = MAX_QUANTITY)), session))
                assertTrue(attempts.finish(owner, "first", 1042, submitted))
                assertFalse(attempts.finish(owner, "first", 1042, submitted))
                assertEquals(line.copy(id = savedId, quantity = 1), cart.snapshot(session).single())
            }
            withCart { cart, _ ->
                val remaining = cart.snapshot(session).single()
                assertEquals(1, remaining.quantity)
                cart.removeLine(remaining.id)
                cart.addLine(line)
                assertTrue(cart.snapshot(session).single().id > remaining.id)
            }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            context.deleteDatabase(name)
            prefs.delete()
        }
    }
}
