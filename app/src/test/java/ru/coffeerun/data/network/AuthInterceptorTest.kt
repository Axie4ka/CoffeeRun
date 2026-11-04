package ru.coffeerun.data.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.coffeerun.data.local.TokenStore

/** Токен в запросах и выход по 401 — на настоящем HTTP-сервере и настоящем клиенте приложения. */
class AuthInterceptorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tokens by lazy { TokenStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "auth.preferences_pb") }) }
    private val api: CoffeeRunApi by lazy { createCoffeeRunApi(server.url("/").toString(), language = { "ru" }, tokens = tokens) }

    private val emptyMenu = """{"products":[],"sizes":[],"milks":[],"extras":[]}"""
    private fun respond(code: Int, body: String = "{}") =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        server.close()
        scope.cancel()
    }

    @Test
    fun withToken_requestHasBearerHeader() = runTest {
        tokens.save("demo-token")
        respond(200, emptyMenu)
        api.getMenu()
        assertEquals("Bearer demo-token", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun withoutToken_noHeader() = runTest {
        respond(200, emptyMenu)
        api.getMenu()
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun loginRequest_neverCarriesToken_and401KeepsToken() = runTest {
        tokens.save("demo-token")
        respond(401, """{"message":"Wrong phone or password"}""")
        runCatching { api.login(LoginRequest("+79990000000", "wrong12")) }
        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals("неверный пароль при входе — не повод выйти", "demo-token", tokens.token.first())
    }

    @Test
    fun unauthorized_clearsToken() = runTest {
        tokens.save("demo-token")
        respond(401, """{"message":"Unauthorized"}""")
        runCatching { api.getMenu() }
        assertNull(tokens.token.first())
    }

    @Test
    fun clearIfStill_keepsNewerToken() = runTest {
        tokens.save("new-token")
        tokens.clearIfStill("old-token")
        assertEquals("new-token", tokens.token.first())
    }
}
