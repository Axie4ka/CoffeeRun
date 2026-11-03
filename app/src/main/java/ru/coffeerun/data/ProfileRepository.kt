package ru.coffeerun.data

import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.UserDto
import ru.coffeerun.model.User

/** Исход сохранения профиля: экрану формы нужно различать причины, поэтому значение, а не исключение. */
sealed interface ProfileSaveResult {
    data class Success(val user: User) : ProfileSaveResult

    /** 400: сервер не принял поля — приложение проверяет их раньше, так что это редкость. */
    data object InvalidInput : ProfileSaveResult

    /** Нет сети, ошибка сервера или ответ не по контракту. */
    data object Unavailable : ProfileSaveResult
}

/** Профиль вошедшего пользователя (GET и PUT /me). Ответ 401 обрабатывает AuthInterceptor: токен стирается. */
interface ProfileRepository {
    /** Загрузка для экрана: любая ошибка — [IOException], как у меню. */
    suspend fun getMe(): User
    suspend fun updateMe(name: String, phone: String): ProfileSaveResult
}

class NetworkProfileRepository(private val api: CoffeeRunApi) : ProfileRepository {
    override suspend fun getMe(): User = try {
        api.getMe().toModel()
    } catch (e: HttpException) {
        throw IOException("HTTP ${e.code()}", e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }

    override suspend fun updateMe(name: String, phone: String): ProfileSaveResult = try {
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

/** Профиль без сервера — для UI-тестов и превью; начинается с демо-пользователя, как мок. */
class InMemoryProfileRepository(private var user: User = InMemoryAuthRepository.DEMO) : ProfileRepository {
    override suspend fun getMe(): User = user
    override suspend fun updateMe(name: String, phone: String): ProfileSaveResult =
        ProfileSaveResult.Success(User(name, phone).also { user = it })
}
