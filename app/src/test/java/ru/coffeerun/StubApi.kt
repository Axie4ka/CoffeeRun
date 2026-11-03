package ru.coffeerun

import ru.coffeerun.data.network.AuthResponse
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.LoginRequest
import ru.coffeerun.data.network.MenuDto
import ru.coffeerun.data.network.RegisterRequest
import ru.coffeerun.data.network.UserDto

/**
 * Основа для фейков API в тестах: каждый запрос по умолчанию — «не используется в этом тесте».
 * Фейк переопределяет только свои запросы, поэтому новый метод в [CoffeeRunApi] не ломает старые тесты.
 */
open class StubApi : CoffeeRunApi {
    override suspend fun getMenu(): MenuDto = unused()
    override suspend fun login(request: LoginRequest): AuthResponse = unused()
    override suspend fun register(request: RegisterRequest): AuthResponse = unused()
    override suspend fun getMe(): UserDto = unused()
    override suspend fun updateMe(user: UserDto): UserDto = unused()

    private fun unused(): Nothing = error("Этот запрос не используется в тесте")
}
