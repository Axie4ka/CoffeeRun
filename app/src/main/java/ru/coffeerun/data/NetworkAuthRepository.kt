package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.local.TokenStore
import ru.coffeerun.data.network.AuthResponse
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.LoginRequest
import ru.coffeerun.data.network.RegisterRequest
import ru.coffeerun.model.User

/** POST /auth/login и /auth/register; токен из ответа — в [TokenStore]. */
class NetworkAuthRepository(private val api: CoffeeRunApi, private val tokens: TokenStore) : AuthRepository {
    override val isLoggedIn: Flow<Boolean> = tokens.token.map { it != null }

    override suspend fun login(phone: String, password: String) = auth { api.login(LoginRequest(phone, password)) }

    override suspend fun register(name: String, phone: String, password: String) =
        auth { api.register(RegisterRequest(name, phone, password)) }

    override suspend fun logout() = tokens.clear()

    /** Коды HTTP переводятся в [AuthResult] только здесь: ViewModel не знают о Retrofit. */
    private suspend fun auth(request: suspend () -> AuthResponse): AuthResult = try {
        val response = request()
        tokens.save(response.token)
        AuthResult.Success(User(response.user.name, response.user.phone))
    } catch (e: HttpException) {
        when (e.code()) {
            401 -> AuthResult.WrongCredentials
            409 -> AuthResult.PhoneTaken
            400 -> AuthResult.InvalidInput
            else -> AuthResult.Unavailable
        }
    } catch (e: IOException) {
        AuthResult.Unavailable
    } catch (e: SerializationException) {
        AuthResult.Unavailable
    }
}
