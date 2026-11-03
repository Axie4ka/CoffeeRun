package ru.coffeerun

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import ru.coffeerun.data.AuthResult
import ru.coffeerun.data.NetworkAuthRepository
import ru.coffeerun.data.local.TokenStore
import ru.coffeerun.data.network.AuthResponse
import ru.coffeerun.data.network.LoginRequest
import ru.coffeerun.data.network.RegisterRequest
import ru.coffeerun.data.network.UserDto
import ru.coffeerun.model.User

/** Вход и регистрация: ответ сервера → [AuthResult], токен сохраняется только при успехе. */
class NetworkAuthRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tokens by lazy { TokenStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "auth.preferences_pb") }) }

    @After
    fun tearDown() = scope.cancel()

    private val ok = AuthResponse("demo-token", UserDto("Денис", "+79990000000"))

    /** API, которое на любой вход и регистрацию отвечает [answer]. */
    private class FakeApi(val answer: () -> AuthResponse) : StubApi() {
        var lastLogin: LoginRequest? = null
        override suspend fun login(request: LoginRequest): AuthResponse = answer().also { lastLogin = request }
        override suspend fun register(request: RegisterRequest): AuthResponse = answer()
    }

    private fun http(code: Int): () -> AuthResponse = { throw HttpException(Response.error<AuthResponse>(code, "".toResponseBody())) }

    @Test
    fun login_success_savesToken() = runTest {
        val api = FakeApi { ok }
        val repo = NetworkAuthRepository(api, tokens)
        assertFalse(repo.isLoggedIn.first())
        assertEquals(AuthResult.Success(User("Денис", "+79990000000")), repo.login("+79990000000", "coffee123"))
        assertEquals(LoginRequest("+79990000000", "coffee123"), api.lastLogin)
        assertEquals("demo-token", tokens.token.first())
        assertTrue(repo.isLoggedIn.first())
    }

    @Test
    fun logout_clearsToken() = runTest {
        val repo = NetworkAuthRepository(FakeApi { ok }, tokens)
        repo.register("Денис", "+79990000000", "coffee123")
        repo.logout()
        assertNull(tokens.token.first())
        assertFalse(repo.isLoggedIn.first())
    }

    @Test
    fun httpErrors_mapToResults_tokenNotSaved() = runTest {
        val cases = mapOf(
            401 to AuthResult.WrongCredentials,
            409 to AuthResult.PhoneTaken,
            400 to AuthResult.InvalidInput,
            500 to AuthResult.Unavailable
        )
        for ((code, expected) in cases) {
            assertEquals("HTTP $code", expected, NetworkAuthRepository(FakeApi(http(code)), tokens).login("+79990000000", "x"))
        }
        assertNull(tokens.token.first())
    }

    @Test
    fun noNetwork_isUnavailable() = runTest {
        val repo = NetworkAuthRepository(FakeApi { throw IOException("нет сети") }, tokens)
        assertEquals(AuthResult.Unavailable, repo.register("Аня", "+79991112233", "secret1"))
        assertNull(tokens.token.first())
    }
}
