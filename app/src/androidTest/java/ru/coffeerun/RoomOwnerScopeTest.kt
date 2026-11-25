package ru.coffeerun

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import ru.coffeerun.data.local.room.CartLineEntity
import ru.coffeerun.data.local.room.CheckoutAttemptEntity
import ru.coffeerun.data.local.room.CheckoutAttemptLineEntity
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.local.room.withOwner
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.session.SessionChangedException

@RunWith(AndroidJUnit4::class)
class RoomOwnerScopeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val migrations = MigrationTestHelper(instrumentation, CoffeeRunDatabase::class.java)

    @Test
    fun sessionChangeRollsBackCartWrite(): Unit = runBlocking {
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CoffeeRunDatabase::class.java).build()
        try {
            val sessions = InMemoryAuthRepository()
            RoomOwnerScope(db).reconcile(null)
            val session = sessions.currentSession()
            val failure = runCatching {
                db.withOwner(sessions, session) { owner ->
                    db.cart().insert(CartLineEntity(owner = owner, productId = "latte", unitPrice = 240,
                        quantity = 1, productJson = "{}", optionsJson = ""))
                    sessions.logout()
                }
            }.exceptionOrNull()
            org.junit.Assert.assertTrue(failure is SessionChangedException)
            assertEquals(emptyList<CartLineEntity>(), db.cart().snapshot("guest"))
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationFromVersionOne_preservesPendingAttemptForCurrentOwner(): Unit = runBlocking {
        val context = instrumentation.targetContext
        val name = "owner-migration-${UUID.randomUUID()}.db"
        try {
            migrations.createDatabase(name, 1).use { db ->
                db.execSQL("INSERT INTO checkout_attempt (id, owner, shop, dismissed) VALUES ('attempt', 'a', 'MOSKOVSKAYA', 0)")
                db.execSQL("INSERT INTO checkout_attempt_line (attemptId, position, cartLineId, productId, quantity, unitPrice) VALUES ('attempt', 0, 25, 'latte', 2, 240)")
            }
            val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            try {
                RoomOwnerScope(db).reconcile("a")
                assertEquals("attempt", db.checkoutAttempts().active("a")?.attempt?.id)
                assertEquals(25, db.checkoutAttempts().active("a")?.lines?.single()?.cartLineId)
                assertEquals(emptyList<CartLineEntity>(), db.cart().snapshot("a"))
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationFromVersionFive_keepsLegacyAttemptAndCachedData(): Unit = runBlocking {
        val context = instrumentation.targetContext
        val name = "owner-v5-${UUID.randomUUID()}.db"
        try {
            migrations.createDatabase(name, 5).use { db ->
                db.execSQL("INSERT INTO local_owner (id, owner) VALUES (1, 'a')")
                db.execSQL("INSERT INTO cart_line (id, owner, productId, unitPrice, quantity, productJson, optionsJson) VALUES (25, 'a', 'latte', 240, 3, '{}', '')")
                db.execSQL("INSERT INTO checkout_attempt (id, owner, shop, dismissed) VALUES ('active', 'a', 'MOSKOVSKAYA', 0)")
                db.execSQL("INSERT INTO checkout_attempt (id, owner, shop, orderNumber, dismissed) VALUES ('closed', 'a', 'MOSKOVSKAYA', 1042, 0)")
                db.execSQL("INSERT INTO checkout_attempt_line (attemptId, position, cartLineId, productId, quantity, unitPrice) VALUES ('active', 0, 25, 'latte', 2, 240), ('closed', 0, 25, 'latte', 1, 240)")
                db.execSQL("INSERT INTO saved_order (owner, number, payload) VALUES ('a', 1042, '{}')")
                db.execSQL("INSERT INTO order_history_sync (owner, fetchedAtMillis) VALUES ('empty-history', 25)")
                db.execSQL("INSERT INTO favorites_snapshot (owner, payload) VALUES ('a', '[]')")
            }
            val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
            try {
                val active = db.checkoutAttempts().active("a")!!
                assertEquals("active", active.attempt.id)
                assertEquals(false, active.attempt.keyed)
                assertEquals(240, active.lines.single().unitPrice)
                assertEquals(3, db.cart().snapshot("a").single().quantity)
                assertNull(db.orderHistory().orders("a").single().confirmedAtMillis)
                assertEquals(emptyList<Any>(), db.orderHistory().orders("empty-history"))
                assertEquals(25L, db.orderHistory().sync("empty-history")?.fetchedAtMillis)
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM checkout_attempt_line").use {
                    it.moveToFirst()
                    assertEquals(1, it.getInt(0))
                }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun unauthorizedAndNewLogin_keepPendingAttemptForItsOriginalAccount(): Unit = runBlocking {
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CoffeeRunDatabase::class.java).build()
        val preferences = java.io.File(context.cacheDir, "reauth-${UUID.randomUUID()}.preferences_pb")
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        val disk = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) { preferences }
        val tokens = ru.coffeerun.data.local.datastore.TokenStore(disk, RoomOwnerScope(db)::reconcile)
        val store = ru.coffeerun.data.local.room.RoomCheckoutAttemptStore(db)
        try {
            tokens.save("first-token", "original")
            val first = tokens.currentSession()
            val owner = checkNotNull(first.localOwner)
            val line = ru.coffeerun.model.CartLine(ru.coffeerun.data.DemoMenu.CROISSANT, 180, quantity = 1)
            org.junit.Assert.assertTrue(store.begin(owner, "pending", listOf(line), ru.coffeerun.model.Shop.MOSKOVSKAYA, null))
            tokens.clearIfStill("first-token", first)
            assertNull(tokens.currentSession().localOwner)
            tokens.save("different-token", "different")
            val different = checkNotNull(tokens.currentSession().localOwner)
            assertNull(store.activeId(different))
            tokens.save("second-token", "original")
            val restored = tokens.currentSession()
            assertEquals(owner, restored.localOwner)
            org.junit.Assert.assertNotEquals(first, restored)
            assertEquals("pending", store.activeId(owner))
            val order = ru.coffeerun.model.Order(1042, ru.coffeerun.model.OrderStatus.PREPARING, emptyList(),
                ru.coffeerun.model.Shop.MOSKOVSKAYA, 0L)
            org.junit.Assert.assertTrue(store.finish(owner, "pending", order))
            assertNull(store.activeId(owner))
            assertEquals(1042, db.orderHistory().orders(owner).single().number)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            db.close()
            preferences.delete()
        }
    }

    @Test
    fun guestCartTransfersOnce_andAccountSwitchRemovesPreviousPrivateData(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "owner-test-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, name).build()
        try {
            val ownerScope = RoomOwnerScope(db)
            ownerScope.reconcile(null)
            val id = db.cart().insert(CartLineEntity(owner = "guest", productId = "latte", unitPrice = 240,
                quantity = 2, productJson = "{}", optionsJson = ""))
            ownerScope.reconcile("a")
            ownerScope.reconcile("a")
            assertEquals(id.toInt(), db.cart().snapshot("a").single().id)
            assertEquals(emptyList<CartLineEntity>(), db.cart().snapshot("guest"))
            db.checkoutAttempts().begin(
                CheckoutAttemptEntity("attempt-a", "a", "MOSKOVSKAYA", null),
                listOf(CheckoutAttemptLineEntity("attempt-a", 0, id.toInt(), "latte", 2, 240, null))
            )

            ownerScope.reconcile("b")
            assertEquals(emptyList<CartLineEntity>(), db.cart().snapshot("a"))
            assertEquals(emptyList<CartLineEntity>(), db.cart().snapshot("b"))
            assertNull(db.checkoutAttempts().active("a"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
