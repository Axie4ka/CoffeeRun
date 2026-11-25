package ru.coffeerun

import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.coffeerun.data.local.room.CheckoutAttemptDao
import ru.coffeerun.data.local.room.CheckoutAttemptEntity
import ru.coffeerun.data.local.room.CheckoutAttemptLineEntity
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomCheckoutAttemptStore
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.local.datastore.TokenStore
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.local.room.RoomCartRepository
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.domain.checkout.CheckoutResult
import ru.coffeerun.domain.checkout.CheckoutAttemptStatus
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.domain.checkout.PlaceOrderUseCase
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

@RunWith(AndroidJUnit4::class)
class CheckoutDatabaseTest {
    @Test
    fun lostResponse_reopensDatabase_recoversOriginalOrderAndConsumesCartOnce(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "checkout-test-${UUID.randomUUID()}.db"
        val tokenFile = File(context.cacheDir, "checkout-test-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sessions = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { tokenFile })
        val server = InMemoryOrders()
        val item = CartLine(DemoMenu.CROISSANT, 180, quantity = 2)
        val policy = PickupPolicy(now = { Instant.parse("2026-11-19T09:00:00Z").toEpochMilli() })
        var posts = 0
        suspend fun checkout(action: suspend (PlaceOrderUseCase, RoomCartRepository, CoffeeRunDatabase) -> Unit) {
            val database = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            val cartScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val cart = RoomCartRepository(database, sessions, cartScope)
                val dao = database.checkoutAttempts()
                RoomOwnerScope(database).reconcile(sessions.currentSession().localOwner)
                val orders = object : Orders by server {
                    override suspend fun place(
                        lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession, attemptId: String, beforeSend: suspend () -> Boolean
                    ): PlaceOrderResult {
                        if (!beforeSend()) return PlaceOrderResult.CartChanged
                        val stored = dao.active(checkNotNull(session.localOwner))!!
                        assertEquals(lines.single().id, stored.lines.single().cartLineId)
                        assertEquals(2, stored.lines.single().quantity)
                        assertEquals(stored.attempt.id, attemptId)
                        assertTrue(server.place(lines, shop, pickupAtMillis, session, attemptId) is PlaceOrderResult.Placed)
                        posts++
                        return PlaceOrderResult.OutcomeUnknown
                    }
                }
                action(PlaceOrderUseCase(cart, orders, sessions, policy, DemoMenuRepository(), RoomCheckoutAttemptStore(database)), cart, database)
            } finally {
                cartScope.coroutineContext[Job]?.cancelAndJoin()
                database.close()
            }
        }
        try {
            sessions.save("checkout-test-token")
            val session = sessions.currentSession()
            checkout { place, cart, _ ->
                cart.addLine(item)
                assertEquals(CheckoutResult.AttemptPending, place(Shop.MOSKOVSKAYA, null))
                cart.addLine(item.copy(quantity = 1))
            }
            checkout { place, cart, database ->
                assertEquals(CheckoutAttemptStatus.OutcomeUnknown, place.attemptStatus.first())
                assertEquals(CheckoutResult.AttemptPending, place(Shop.MOSKOVSKAYA, null))
                assertFalse(place.dismissUnknownAttempt())
                database.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER reject_order BEFORE INSERT ON saved_order BEGIN SELECT RAISE(ABORT, 'write failed'); END"
                )
                val failure = runCatching { place.recoverOrder() }.exceptionOrNull()
                assertTrue(failure is java.io.IOException)
                assertEquals(3, cart.snapshot(session).single().quantity)
                assertEquals(CheckoutAttemptStatus.OutcomeUnknown, place.attemptStatus.first())
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_order")
                assertEquals(1042, place.recoverOrder()?.number)
                assertNull(place.recoverOrder())
                assertEquals(1, cart.snapshot(session).single().quantity)
                assertEquals(1, posts)
            }
            checkout { place, cart, database ->
                assertEquals(CheckoutAttemptStatus.Idle, place.attemptStatus.first())
                assertEquals(1, cart.snapshot(session).single().quantity)
                assertEquals(1042, database.orderHistory().orders(checkNotNull(session.localOwner)).single().number)
                assertEquals(1042, server.orders.value.first().number)
            }
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            context.deleteDatabase(name)
            tokenFile.delete()
        }
    }

    @Test
    fun activeAttemptAndExactLinesSurviveReopen_withoutAppearingForAnotherOwner(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "checkout-test-${UUID.randomUUID()}.db"
        val attempt = CheckoutAttemptEntity("attempt-a", "owner-a", "MOSKOVSKAYA", null)
        val lines = listOf(
            CheckoutAttemptLineEntity(attempt.id, 0, 25, "latte", 2, 240, """{"size":"MEDIUM","note":"без сахара"}"""),
            CheckoutAttemptLineEntity(attempt.id, 1, 26, "latte", 1, 270, """{"size":"LARGE"}""")
        )
        suspend fun withDatabase(action: suspend (CheckoutAttemptDao) -> Unit) {
            val database = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            try {
                action(database.checkoutAttempts())
            } finally {
                database.close()
            }
        }
        try {
            withDatabase { dao ->
                assertTrue(dao.begin(attempt, lines))
            }
            withDatabase { dao ->
                val restored = dao.active("owner-a")!!
                assertFalse(dao.begin(attempt.copy(id = "another-a"), lines.map { line -> line.copy(attemptId = "another-a") }))
                assertEquals(attempt, restored.attempt)
                assertEquals(lines, restored.lines.sortedBy { line -> line.position })
                assertNull(dao.active("owner-b"))
                assertEquals(0, dao.complete("owner-b", attempt.id, 1042))
                assertEquals(1, dao.complete("owner-a", attempt.id, 1042))
                assertEquals(0, dao.complete("owner-a", attempt.id, 1042))
                assertNull(dao.active("owner-a"))
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
