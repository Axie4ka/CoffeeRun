package ru.coffeerun.data.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import ru.coffeerun.data.local.TokenStore
import ru.coffeerun.data.session.AuthSession

/**
 * Добавляет к каждому запросу `Authorization: Bearer <токен>`, если пользователь вошёл.
 * Ответ 401 на запрос с токеном значит, что токен недействителен (истёк или отозван): он стирается,
 * `isLoggedIn` становится false, и экраны сами показывают «Войдите…».
 *
 * Вход и регистрация идут без токена, и их 401 — это «неверный пароль», а не повод выйти.
 */
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if ("auth" in request.url.pathSegments) return chain.proceed(request)
        // Интерцептор работает в потоке OkHttp, не в главном: подождать DataStore здесь можно
        val credentials = runBlocking { tokens.credentials() }
        val expected = request.tag(AuthSession::class.java)
        if (expected != null && credentials.session != expected) {
            throw IOException("Request session changed")
        }
        val token = credentials.token ?: return chain.proceed(request)
        val response = chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        if (response.code == 401) runBlocking { tokens.clearIfStill(token, credentials.session) }
        return response
    }
}
