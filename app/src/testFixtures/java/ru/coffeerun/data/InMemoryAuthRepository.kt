package ru.coffeerun.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.coffeerun.model.User

/** Вход без сервера — для UI-тестов и превью. Принимает только демо-аккаунт, как мок. */
class InMemoryAuthRepository(loggedIn: Boolean = false) : AuthRepository {
    private val token = MutableStateFlow(if (loggedIn) DEMO_TOKEN else null)
    override val isLoggedIn: Flow<Boolean> = token.map { it != null }

    override suspend fun login(phone: String, password: String): AuthResult =
        if (phone == DEMO.phone && password == DEMO_PASSWORD) success(DEMO) else AuthResult.WrongCredentials

    override suspend fun register(name: String, phone: String, password: String): AuthResult =
        if (phone == DEMO.phone) AuthResult.PhoneTaken else success(User(name, phone))

    override suspend fun logout() {
        token.value = null
    }

    private fun success(user: User): AuthResult {
        token.value = DEMO_TOKEN
        return AuthResult.Success(user)
    }

    companion object {
        val DEMO = User("Денис", "+79990000000")
        const val DEMO_PASSWORD = "coffee123"
        private const val DEMO_TOKEN = "demo-token"
    }
}
