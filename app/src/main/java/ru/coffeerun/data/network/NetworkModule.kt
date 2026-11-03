package ru.coffeerun.data.network

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.coffeerun.data.local.TokenStore

/**
 * Язык текстов меню для сервера: «ru» для русского интерфейса, иначе «en» — других у сервера нет.
 * Берётся из ресурсов приложения, а не Locale.getDefault(): так меню на том же языке, что и кнопки.
 */
fun Context.serverLanguage(): String = if (resources.configuration.locales[0].language == "ru") "ru" else "en"

/**
 * Retrofit-клиент сервера. [language] спрашивается на каждый запрос: язык телефона могут сменить на ходу.
 * Токен из [tokens] добавляется к запросам, а при ответе 401 стирается — см. [AuthInterceptor].
 * [log] — строка на каждый запрос (см. [RequestLogInterceptor]); null — не логировать.
 */
fun createCoffeeRunApi(baseUrl: String, language: () -> String, tokens: TokenStore, log: ((String) -> Unit)? = null): CoffeeRunApi {
    val builder = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Accept-Language", language()).build())
        }
        .addInterceptor(AuthInterceptor(tokens))
    // Последним: видит запрос уже с токеном, а время — только сеть и сервер
    if (log != null) builder.addInterceptor(RequestLogInterceptor(log))
    val client = builder.build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(CoffeeRunJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CoffeeRunApi::class.java)
}
