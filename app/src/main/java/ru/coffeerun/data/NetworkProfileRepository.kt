package ru.coffeerun.data

import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.UserDto
import ru.coffeerun.model.User
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession

class NetworkProfileRepository(private val api: CoffeeRunApi, private val sessions: SessionProvider) : ProfileRepository {
    override suspend fun getMe(): User = sessions.runInSession(sessions.currentSession()) { getCurrentUser() }

    private suspend fun getCurrentUser(): User = try {
        api.getMe().toModel()
    } catch (e: HttpException) {
        throw IOException("HTTP ${e.code()}", e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }

    override suspend fun updateMe(name: String, phone: String): ProfileSaveResult =
        sessions.runInSession(sessions.currentSession()) { saveCurrentUser(name, phone) }

    private suspend fun saveCurrentUser(name: String, phone: String): ProfileSaveResult = try {
        ProfileSaveResult.Success(api.updateMe(UserDto(name, phone)).toModel())
    } catch (e: HttpException) {
        if (e.code() == 400) ProfileSaveResult.InvalidInput else ProfileSaveResult.Unavailable
    } catch (e: IOException) {
        ProfileSaveResult.Unavailable
    } catch (e: SerializationException) {
        ProfileSaveResult.Unavailable
    }

    private fun UserDto.toModel() = User(name, phone)
}
