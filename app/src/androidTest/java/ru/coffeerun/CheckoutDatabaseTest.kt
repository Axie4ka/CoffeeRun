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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.coffeerun.data.local.CheckoutAttemptDao
import ru.coffeerun.data.local.CheckoutAttemptEntity
import ru.coffeerun.data.local.CheckoutAttemptLineEntity
import ru.coffeerun.data.local.CoffeeRunDatabase
import ru.coffeerun.data.local.RoomCheckoutAttemptStore
import ru.coffeerun.data.local.RoomOwnerScope
import ru.coffeerun.data.local.TokenStore
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.domain.checkout.CheckoutResult
import ru.coffeerun.domain.checkout.CheckoutAttemptStatus
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.domain.usecase.PlaceOrderUseCase
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

@RunWith(AndroidJUnit4::class)
class CheckoutDatabaseTest {
    @Test
    fun lostResponse_savedBeforePost_blocksFreshCheckoutAfterDatabaseReopen(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "checkout-test-${UUID.randomUUID()}.db"
        val tokenFile = File(context.cacheDir, "checkout-test-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sessions = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { tokenFile })
        val cart = InMemoryCartRepository()
        cart.add(CartLine(DemoMenu.CROISSANT, 180, quantity = 2))
        val policy = PickupPolicy(now = { Instant.parse("2026-11-19T09:00:00Z").toEpochMilli() })
        var posts = 0
        suspend fun checkout(action: suspend (PlaceOrderUseCase) -> Unit) {
            val database = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            try {
                val dao = database.checkoutAttempts()
                RoomOwnerScope(database).reconcile(sessions.currentSession().localOwner)
                val orders = object : Orders by InMemoryOrders() {
                    override suspend fun place(
                        lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession
                    ): PlaceOrderResult {
                        val stored = dao.active(checkNotNull(session.localOwner))!!
                        assertEquals(lines.single().id, stored.lines.single().cartLineId)
                        assertEquals(2, stored.lines.single().quantity)
                        posts++
                        return PlaceOrderResult.OutcomeUnknown
                    }
                }
                action(PlaceOrderUseCase(cart, orders, sessions, policy, DemoMenuRepository(), RoomCheckoutAttemptStore(database)))
            } finally {
                database.close()
            }
        }
        try {
            sessions.save("checkout-test-token")
            checkout { assertEquals(CheckoutResult.AttemptPending, it(Shop.MOSKOVSKAYA, null)) }
            checkout {
                assertEquals(CheckoutAttemptStatus.OutcomeUnknown, it.attemptStatus.first())
                assertEquals(CheckoutResult.AttemptPending, it(Shop.MOSKOVSKAYA, null))
                assertEquals(1, posts)
                assertTrue(it.dismissUnknownAttempt())
                assertEquals(2, cart.lines.value.single().quantity)
            }
            checkout { assertEquals(CheckoutAttemptStatus.Idle, it.attemptStatus.first()) }
        } finally {
            scope.cancel()
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
