package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.User

/** Исход сохранения профиля: экрану формы нужно различать причины, поэтому значение, а не исключение. */
sealed interface ProfileSaveResult {
    data class Success(val user: User) : ProfileSaveResult

    /** 400: сервер не принял поля — приложение проверяет их раньше, так что это редкость. */
    data object InvalidInput : ProfileSaveResult

    /** Нет сети, ошибка сервера или ответ не по контракту. */
    data object Unavailable : ProfileSaveResult
    data class OutcomeUnknown(val confirmed: User?) : ProfileSaveResult
}

/** Профиль вошедшего пользователя (GET и PUT /me). Ответ 401 обрабатывает AuthInterceptor: токен стирается. */
interface ProfileRepository {
    fun onSessionChanged(session: AuthSession) = Unit
    fun confirmed(session: AuthSession): User? = null
    fun observeConfirmed(session: AuthSession): Flow<User> = emptyFlow()
    suspend fun getForEditing(): User = getMe()
    /** Загрузка для экрана: любая ошибка — [IOException], как у меню. */
    suspend fun getMe(): User
    suspend fun updateMe(name: String, phone: String): ProfileSaveResult
}
