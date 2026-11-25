package ru.coffeerun.data.repository

import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.ProfileSaveResult

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.dto.UserDto
import ru.coffeerun.model.User
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.runInSession

class NetworkProfileRepository(private val api: CoffeeRunApi, private val sessions: SessionProvider) : ProfileRepository {
    private class Account(val session: AuthSession, val mutex: Mutex = Mutex(), val user: User? = null) {
        fun withUser(user: User) = Account(session, mutex, user)
    }
    private val current = MutableStateFlow<Account?>(null)

    override fun onSessionChanged(session: AuthSession) {
        try {
            sessions.withSession(session) { if (current.value?.session != session) current.value = null }
        } catch (_: SessionChangedException) {
            // Более новый вход уже заменил эту сессию.
        }
    }

    override fun confirmed(session: AuthSession): User? = current.value?.takeIf { it.session == session }?.user
    override fun observeConfirmed(session: AuthSession) = current.mapNotNull { it?.takeIf { account -> account.session == session }?.user }

    private suspend fun <T> forAccount(action: suspend (AuthSession) -> T): T {
        val session = sessions.currentSession()
        return sessions.runInSession(session) {
            val mutex = sessions.withSession(session) {
                (current.value?.takeIf { it.session == session } ?: Account(session).also { current.value = it }).mutex
            }
            mutex.withLock {
                sessions.withSession(session) { }
                action(session)
            }
        }
    }

    private fun remember(session: AuthSession, user: User): User = sessions.withSession(session) {
        current.value = checkNotNull(current.value).withUser(user)
        user
    }

    override suspend fun getMe(): User = forAccount { remember(it, getCurrentUser()) }
    override suspend fun getForEditing(): User = forAccount { confirmed(it) ?: remember(it, getCurrentUser()) }

    private suspend fun getCurrentUser(): User = try {
        api.getMe().toModel()
    } catch (e: HttpException) {
        throw IOException("HTTP ${e.code()}", e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }

    override suspend fun updateMe(name: String, phone: String): ProfileSaveResult = forAccount { session ->
        val result = saveCurrentUser(name, phone)
        when (result) {
            is ProfileSaveResult.Success -> ProfileSaveResult.Success(remember(session, result.user))
            is ProfileSaveResult.OutcomeUnknown -> {
                sessions.withSession(session) { }
                val server = try { remember(session, getCurrentUser()) } catch (_: IOException) { null }
                if (server == User(name, phone)) ProfileSaveResult.Success(server)
                else ProfileSaveResult.OutcomeUnknown(server)
            }
            else -> result
        }
    }

    private suspend fun saveCurrentUser(name: String, phone: String): ProfileSaveResult = try {
        ProfileSaveResult.Success(api.updateMe(UserDto(name, phone)).toModel())
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> ProfileSaveResult.InvalidInput
            401, 403 -> ProfileSaveResult.Unavailable
            else -> ProfileSaveResult.OutcomeUnknown(null)
        }
    } catch (e: IOException) {
        ProfileSaveResult.OutcomeUnknown(null)
    } catch (e: SerializationException) {
        ProfileSaveResult.OutcomeUnknown(null)
    }

    private fun UserDto.toModel() = User(name, phone)
}
