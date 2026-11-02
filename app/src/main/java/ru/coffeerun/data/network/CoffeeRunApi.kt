package ru.coffeerun.data.network

import kotlinx.serialization.json.Json
import retrofit2.http.GET

/** Запросы к серверу CoffeeRun. Контракт — docs/openapi.yaml. */
interface CoffeeRunApi {
    @GET("menu")
    suspend fun getMenu(): MenuDto
}

/** Разбор ответов сервера. Новые поля в ответе не ломают старое приложение. */
val CoffeeRunJson = Json { ignoreUnknownKeys = true }
