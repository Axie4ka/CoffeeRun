package ru.coffeerun.data

import java.io.IOException
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
