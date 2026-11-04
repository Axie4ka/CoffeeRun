package ru.coffeerun.data.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Одна строка на запрос: `POST /orders → 201 (84 мс)` или `GET /orders ✗ ConnectException (12 мс)`.
 * Тела и заголовки не пишет — в них токен и телефон. [log] — куда писать: в приложении Logcat, в тестах список.
 */
class RequestLogInterceptor(private val log: (String) -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val call = "${request.method} ${request.url.encodedPath}"
        val start = System.nanoTime()
        fun took() = "${(System.nanoTime() - start) / 1_000_000} мс"
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            log("$call ✗ ${e::class.simpleName} (${took()})")
            throw e
        }
        log("$call → ${response.code} (${took()})")
        return response
    }
}
