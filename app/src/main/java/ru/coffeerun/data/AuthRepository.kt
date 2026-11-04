package ru.coffeerun.data

import kotlinx.coroutines.flow.Flow
import ru.coffeerun.model.User

/**
 * Исход входа или регистрации. Ошибки формы — ожидаемые исходы, а не сбой, поэтому значение,
 * а не исключение: экран показывает каждому своё сообщение.
 */
sealed interface AuthResult {
    data class Success(val user: User) : AuthResult

    /** 401: неверный телефон или пароль. */
    data object WrongCredentials : AuthResult

    /** 409: телефон уже зарегистрирован. */
    data object PhoneTaken : AuthResult

    /** 400: сервер не принял поля — приложение проверяет их раньше, так что это редкость. */
    data object InvalidInput : AuthResult

    /** Нет сети, ошибка сервера или ответ не по контракту — «попробуйте ещё раз». */
    data object Unavailable : AuthResult
}

/** Вход в аккаунт. Вошёл ли пользователь — есть ли сохранённый токен. */
interface AuthRepository {
    val isLoggedIn: Flow<Boolean>
    suspend fun login(phone: String, password: String): AuthResult
    suspend fun register(name: String, phone: String, password: String): AuthResult
    suspend fun logout()
}
