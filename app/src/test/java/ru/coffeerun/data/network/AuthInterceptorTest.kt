package ru.coffeerun.data.network

import ru.coffeerun.data.network.dto.LoginRequest

import ru.coffeerun.testPreferences
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.Dispatcher
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.RequestSession
import kotlinx.coroutines.asContextElement
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.coffeerun.data.local.datastore.TokenStore
import ru.coffeerun.data.repository.NetworkMenuRepository

/** Токен в запросах и выход по 401 — на настоящем HTTP-сервере и настоящем клиенте приложения. */
class AuthInterceptorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tokens by lazy { TokenStore(testPreferences(scope, File(folder.root, "auth.preferences_pb"))) }
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
    fun menuRequest_usesLanguageOfCatalogSnapshot() = runTest {
        val baseUrl = server.url("/").toString()
        val client = createCoffeeRunApi(baseUrl, language = { "en" }, tokens = tokens)
        val menu = NetworkMenuRepository(client, baseUrl, language = { "ru" })
        respond(200, emptyMenu)

        menu.getMenu()

        assertEquals("ru", server.takeRequest().headers["Accept-Language"])
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

    @Test
    fun oldUnauthorized_afterSameTokenLogin_keepsNewSession() = runTest {
        tokens.save("demo-token")
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                started.complete(Unit)
                check(release.await(10, TimeUnit.SECONDS)) { "тест не освободил ответ сервера" }
                return MockResponse.Builder().code(401).body("{}").build()
            }
        }
        val pending = async { runCatching { api.getMenu() } }
        try {
            started.await()
            tokens.save("demo-token") // Новый вход, но строка токена осталась прежней.
        } finally {
            release.countDown()
        }
        pending.await()
        assertEquals("старый 401 не сбрасывает новый вход", "demo-token", tokens.token.first())
    }

    @Test
    fun queuedOldSessionRequest_isNotSentWithNewToken() = runTest {
        tokens.save("old-token")
        val session = tokens.currentSession()
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens)).build()
        val queued = client.newCall(Request.Builder().url(server.url("/orders")).tag(AuthSession::class.java, session).build())
        tokens.save("new-token")
        respond(200)
        val outcome = withContext(Dispatchers.IO) { runCatching { queued.execute().close() } }
        assertTrue("старый запрос должен остановиться до отправки", outcome.exceptionOrNull() is IOException)
        assertEquals(0, server.requestCount)
        assertEquals("new-token", tokens.token.first())
    }

    @Test
    fun retrofitCall_carriesCapturedSessionToInterceptor() = runTest {
        tokens.save("old-token")
        val previous = tokens.currentSession()
        tokens.save("new-token")
        respond(200, "[]")
        val result = withContext(RequestSession.current.asContextElement(previous)) {
            runCatching { api.getOrders() }
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(0, server.requestCount)
        assertEquals("new-token", tokens.token.first())
    }
}
