package ru.coffeerun.data.network

import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Запросы к серверу CoffeeRun. Контракт — docs/openapi.yaml. */
interface CoffeeRunApi {
    @GET("menu")
    suspend fun getMenu(): MenuDto

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @GET("me")
    suspend fun getMe(): UserDto

    @PUT("me")
    suspend fun updateMe(@Body user: UserDto): UserDto

    @GET("me/favorites")
    suspend fun getFavorites(): List<String>

    /** 204 без тела; повторный PUT ничего не меняет. */
    @PUT("me/favorites/{productId}")
    suspend fun addFavorite(@Path("productId") productId: String)

    /** 204 без тела; если позиции в избранном не было — тоже 204. */
    @DELETE("me/favorites/{productId}")
    suspend fun removeFavorite(@Path("productId") productId: String)

    /** От новых к старым — сортирует сервер. */
    @GET("orders")
    suspend fun getOrders(): List<OrderDto>

    /** 201 — заказ принят; 409 — чего-то из заказа нет в наличии. */
    @POST("orders")
    suspend fun placeOrder(@Body request: OrderRequest): OrderDto

    /** 200 — заказ отменён (статус CANCELLED); 409 — его уже готовят или выдали. */
    @POST("orders/{number}/cancel")
    suspend fun cancelOrder(@Path("number") number: Int): OrderDto
}

/** Разбор ответов сервера. Новые поля в ответе не ломают старое приложение. */
val CoffeeRunJson = Json { ignoreUnknownKeys = true }
