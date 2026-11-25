package ru.coffeerun.data.local

import ru.coffeerun.data.local.datastore.DataStoreShopRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.coffeerun.model.Shop
import ru.coffeerun.testPreferences

class ShopStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun selectedShop_survivesRecreatingTheRepository() = runTest {
        val file = File(folder.root, "shop.preferences_pb")
        suspend fun usingStore(action: suspend (DataStoreShopRepository) -> Unit) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                action(DataStoreShopRepository(testPreferences(scope, file), scope))
            } finally {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
        usingStore { it.saveSelection(Shop.STROITELEY) }
        usingStore { assertEquals(Shop.STROITELEY, it.selected.first { shop -> shop != Shop.MOSKOVSKAYA }) }
    }
}
