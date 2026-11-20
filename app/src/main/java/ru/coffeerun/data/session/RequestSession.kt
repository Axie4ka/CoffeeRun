package ru.coffeerun.data.session

/**
 * Контекст корутины передаёт версию до синхронного создания Retrofit/OkHttp Call.
 * Дальше версия хранится в request tag: interceptor работает уже в другом потоке.
 */
internal object RequestSession {
    val current = ThreadLocal<AuthSession?>()
}
