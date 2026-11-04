package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.flow.StateFlow

/** Избранное — id товаров. Наружу только чтение через [ids], менять можно лишь [toggle]. */
interface FavoritesRepository {
    val ids: StateFlow<Set<String>>

    /** Загрузить с сервера заново. Ошибка — [IOException], как у меню. */
    suspend fun refresh()

    /**
     * Добавляет товар в избранное, а если он уже там — убирает. [ids] меняется сразу;
     * false — сервер не принял изменение, и оно откачено.
     */
    suspend fun toggle(productId: String): Boolean
}
