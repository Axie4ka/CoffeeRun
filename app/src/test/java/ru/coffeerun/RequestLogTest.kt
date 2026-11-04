package ru.coffeerun

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.local.TokenStore
import ru.coffeerun.data.network.createCoffeeRunApi
import ru.coffeerun.data.network.orderRequest
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

/** Настоящий клиент приложения против MockWebServer: путь и тело POST /orders, разбор ответа и строка в логе. */
class RequestLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tokens by lazy { TokenStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "auth.preferences_pb") }) }
    private val lines = mutableListOf<String>()
    private val api by lazy { createCoffeeRunApi(server.url("/").toString(), language = { "ru" }, tokens = tokens, log = { lines += it }) }

    @After
    fun tearDown() {
        server.close()
        scope.cancel()
    }

    @Test
    fun placeOrder_sendsContractBody_parsesAnswer_logsOneLine() = runTest {
        server.start()
        server.enqueue(
            MockResponse.Builder().code(201).addHeader("Content-Type", "application/json").body(
                """{"number":1042,"status":"PREPARING","shop":"STROITELEY","placedAt":"2026-11-03T12:00:00+03:00",
                   "lines":[{"productId":"croissant","quantity":2,"unitPrice":180}]}"""
            ).build()
        )
        val order = api.placeOrder(orderRequest(listOf(CartLine(DemoMenu.CROISSANT, unitPrice = 180, quantity = 2)), Shop.STROITELEY, null))

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/orders", sent.url.encodedPath)
        val body = sent.body!!.utf8()
        assertTrue(body, "\"shop\":\"STROITELEY\"" in body && "\"productId\":\"croissant\"" in body)
        assertEquals(1042, order.number)
        assertEquals(1, lines.size)
        assertTrue(lines.single(), lines.single().startsWith("POST /orders → 201 ("))
    }

    @Test
    fun noServer_logsFailure() = runTest {
        server.start()
        val url = server.url("/").toString()
        server.close() // порт свободен — соединение отклонят, как без сети
        val offline = createCoffeeRunApi(url, language = { "ru" }, tokens = tokens, log = { lines += it })
        val error = runCatching { offline.getOrders() }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(lines.single(), lines.single().startsWith("GET /orders ✗ "))
    }
}
