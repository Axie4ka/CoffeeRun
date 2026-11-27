package ru.coffeerun

import ru.coffeerun.data.network.dto.AuthResponse
import ru.coffeerun.data.network.dto.AvatarDto
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.dto.LoginRequest
import ru.coffeerun.data.network.dto.MenuDto
import ru.coffeerun.data.network.dto.OrderDto
import ru.coffeerun.data.network.dto.OrderRequest
import ru.coffeerun.data.network.dto.RegisterRequest
import ru.coffeerun.data.network.dto.RenameRecipeRequest
import ru.coffeerun.data.network.dto.UserDto
import ru.coffeerun.model.Recipe

/**
 * Общая основа для unit- и UI-фейков API: неизвестный тесту запрос считается ошибкой.
 * Фейк переопределяет только свои запросы, поэтому новый метод в [CoffeeRunApi] не ломает старые тесты.
 */
open class StubApi : CoffeeRunApi {
    override suspend fun getMenu(language: String?): MenuDto = unused()
    override suspend fun login(request: LoginRequest): AuthResponse = unused()
    override suspend fun register(request: RegisterRequest): AuthResponse = unused()
    override suspend fun getMe(): UserDto = unused()
    override suspend fun updateMe(user: UserDto): UserDto = unused()
    override suspend fun getAvatar(): AvatarDto = unused()
    override suspend fun putAvatar(avatar: AvatarDto): AvatarDto = unused()
    override suspend fun deleteAvatar(): Unit = unused()
    override suspend fun getFavorites(): List<String> = unused()
    override suspend fun addFavorite(productId: String): Unit = unused()
    override suspend fun removeFavorite(productId: String): Unit = unused()
    override suspend fun getRecipes(): List<Recipe> = unused()
    override suspend fun saveRecipe(id: String, recipe: Recipe): Unit = unused()
    override suspend fun renameRecipe(id: String, request: RenameRecipeRequest): Recipe = unused()
    override suspend fun removeRecipe(id: String): Unit = unused()
    override suspend fun getOrders(): List<OrderDto> = unused()
    override suspend fun getOrder(number: Int): OrderDto = unused()
    override suspend fun placeOrder(attemptId: String, request: OrderRequest): OrderDto = unused()
    override suspend fun resolveAttempt(attemptId: String): OrderDto = unused()
    override suspend fun cancelOrder(number: Int): OrderDto = unused()

    private fun unused(): Nothing = error("Этот запрос не используется в тесте")
}
