package ru.coffeerun.data.session

import kotlinx.coroutines.CancellationException

/** Поколение защищает текущий процесс, localOwner связывает локальные данные с одним входом. */
data class AuthSession(val generation: Long, val isLoggedIn: Boolean, val localOwner: String? = null)

/** Старый запрос не должен выдавать результат или ошибку для нового входа. */
class SessionChangedException : CancellationException("Auth session changed")
