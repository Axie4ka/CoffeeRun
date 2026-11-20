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
import ru.coffeerun.data.local.CartLineEntity
import ru.coffeerun.data.local.CheckoutAttemptEntity
import ru.coffeerun.data.local.CheckoutAttemptLineEntity
import ru.coffeerun.data.local.CoffeeRunDatabase
import ru.coffeerun.data.local.RoomOwnerScope

@RunWith(AndroidJUnit4::class)
class RoomOwnerScopeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val migrations = MigrationTestHelper(instrumentation, CoffeeRunDatabase::class.java)

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
