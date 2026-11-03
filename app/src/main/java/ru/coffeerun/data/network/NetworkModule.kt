package ru.coffeerun.data.network

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Язык текстов меню для сервера: «ru» для русского интерфейса, иначе «en» — других у сервера нет.
 * Берётся из ресурсов приложения, а не Locale.getDefault(): так меню на том же языке, что и кнопки.
 */
fun Context.serverLanguage(): String = if (resources.configuration.locales[0].language == "ru") "ru" else "en"

/** Retrofit-клиент сервера. [language] спрашивается на каждый запрос: язык телефона могут сменить на ходу. */
fun createCoffeeRunApi(baseUrl: String, language: () -> String): CoffeeRunApi {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Accept-Language", language()).build())
        }
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(CoffeeRunJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CoffeeRunApi::class.java)
}
