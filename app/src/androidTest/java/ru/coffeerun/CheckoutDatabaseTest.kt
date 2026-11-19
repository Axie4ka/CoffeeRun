package ru.coffeerun

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
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

@RunWith(AndroidJUnit4::class)
class CheckoutDatabaseTest {
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
