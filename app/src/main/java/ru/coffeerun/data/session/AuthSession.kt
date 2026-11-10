package ru.coffeerun.data.session

import kotlinx.coroutines.CancellationException

/** Локальная версия входа. Не содержит токен и не является ID пользователя на сервере. */
data class AuthSession(val generation: Long, val isLoggedIn: Boolean)

/** Старый запрос не должен выдавать результат или ошибку для нового входа. */
class SessionChangedException : CancellationException("Auth session changed")
