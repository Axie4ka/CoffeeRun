package ru.coffeerun.domain.orders

import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.data.CancelOrderResult

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.Shop
import ru.coffeerun.data.session.AuthSession

/** Заказы пользователя, от новых к старым. */
interface Orders {
    val orders: StateFlow<List<Order>>

    /** Полный список успешно загружен для текущей сессии; load/place не делают кеш полным. */
    val isLoaded: StateFlow<Boolean>

    /** Один заказ из общего хранилища; null — записи в нём нет. */
    fun observeOrder(number: Int): Flow<Order?> =
        orders.map { list -> list.find { it.number == number } }.distinctUntilChanged()

    /** Загрузить с сервера заново. Ошибка — [java.io.IOException], как у меню. */
    suspend fun refresh()

    /** Оформляет заказ из [lines]; [lines] не должен быть пустым. Принятый заказ сразу попадает в [orders]. */
    suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long? = null, session: AuthSession): PlaceOrderResult

    /** Отменяет заказ [number]; отменённый заказ сразу меняется в [orders]. */
    suspend fun cancel(number: Int): CancelOrderResult

    /**
     * Свежий заказ с сервера: вставляется или обновляется в [orders].
     * null — такого заказа нет; устаревшая запись удаляется из хранилища.
     * Ошибка загрузки — [java.io.IOException], как у меню.
     */
    suspend fun load(number: Int): Order?

    /** Ищет среди загруженных [orders]. */
    fun find(number: Int): Order?
}
