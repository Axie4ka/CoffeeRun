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
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomCartRepository
import ru.coffeerun.data.local.room.RoomCheckoutAttemptStore
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.local.datastore.TokenStore
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.Shop
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.data.local.room.saved

@RunWith(AndroidJUnit4::class)
class RoomCartTest {
    @Test
    fun cartPersistsAndConfirmationConsumesOnlySubmittedQuantity(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "cart-test-${UUID.randomUUID()}.db"
        val prefs = File(context.cacheDir, "cart-test-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val tokens = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { prefs })
        suspend fun withCart(action: suspend (RoomCartRepository, RoomCheckoutAttemptStore, CoffeeRunDatabase) -> Unit) {
            val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            val cartScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                RoomOwnerScope(db).reconcile(tokens.currentSession().localOwner)
                action(RoomCartRepository(db, tokens, cartScope), RoomCheckoutAttemptStore(db), db)
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
            withCart { cart, _, _ ->
                cart.addLine(line)
                savedId = cart.snapshot(session).single().id
            }
            withCart { cart, attempts, db ->
                val submitted = cart.snapshot(session)
                assertEquals(line.copy(id = savedId), submitted.single())
                assertTrue(attempts.begin(owner, "first", submitted, Shop.MOSKOVSKAYA, null))
                cart.addLine(line.copy(product = latte.copy(name = "Latte"), quantity = 1))
                assertEquals(listOf(false), cart.addWithinLimit(listOf(line.copy(quantity = MAX_QUANTITY)), session))
                val order = Order(1042, OrderStatus.PREPARING, emptyList(), Shop.MOSKOVSKAYA, 0, confirmedAtMillis = 1)
                val newer = order.copy(status = OrderStatus.READY, confirmedAtMillis = 2)
                db.orderHistory().save(listOf(newer.saved(owner)))
                assertTrue(attempts.finish(owner, "first", order))
                assertFalse(attempts.finish(owner, "first", order))
                assertEquals(newer.saved(owner), db.orderHistory().find(owner, order.number))
                assertEquals(line.copy(id = savedId, quantity = 1), cart.snapshot(session).single())
            }
            withCart { cart, _, _ ->
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
