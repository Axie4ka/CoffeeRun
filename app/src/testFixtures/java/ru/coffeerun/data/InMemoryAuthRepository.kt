package ru.coffeerun.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.coffeerun.model.User
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException

/** Вход без сервера — для UI-тестов и превью. Принимает только демо-аккаунт, как мок. */
class InMemoryAuthRepository(loggedIn: Boolean = false) : AuthRepository {
    private val guard = Any()
    private val token = MutableStateFlow(if (loggedIn) DEMO_TOKEN else null)
    private val session = MutableStateFlow(AuthSession(0, loggedIn))
    override val sessions: Flow<AuthSession> = session
    override val isLoggedIn: Flow<Boolean> = token.map { it != null }

    override suspend fun login(phone: String, password: String): AuthResult =
        if (phone == DEMO.phone && password == DEMO_PASSWORD) success(DEMO) else AuthResult.WrongCredentials

    override suspend fun register(name: String, phone: String, password: String): AuthResult =
        if (phone == DEMO.phone) AuthResult.PhoneTaken else success(User(name, phone))

    override suspend fun currentSession(): AuthSession = synchronized(guard) { session.value }

    override fun <T> withSession(session: AuthSession, action: () -> T): T = synchronized(guard) {
        if (this.session.value != session) throw SessionChangedException()
        action()
    }

    override suspend fun logout() = synchronized(guard) {
        token.value = null
        session.value = AuthSession(session.value.generation + 1, false)
    }

    private fun success(user: User): AuthResult = synchronized(guard) {
        token.value = DEMO_TOKEN
        session.value = AuthSession(session.value.generation + 1, true)
        AuthResult.Success(user)
    }

    companion object {
        val DEMO = User("Денис", "+79990000000")
        const val DEMO_PASSWORD = "coffee123"
        private const val DEMO_TOKEN = "demo-token"
    }
}
