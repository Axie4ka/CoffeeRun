package ru.coffeerun.data

import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.Shop

/** Чем закончилось оформление — результат-значение, как у форм входа и профиля. */
sealed interface PlaceOrderResult {
    data class Placed(val order: Order) : PlaceOrderResult

    /** 409: чего-то из заказа сейчас нет в наличии. */
    data object Unavailable : PlaceOrderResult

    /** Нет сети или сервер ответил не так. */
    data object NetworkError : PlaceOrderResult
}

/** Заказы пользователя, от новых к старым. */
interface OrderRepository {
    val orders: StateFlow<List<Order>>

    /** Загрузить с сервера заново. Ошибка — [java.io.IOException], как у меню. */
    suspend fun refresh()

    /** Оформляет заказ из [lines]; [lines] не должен быть пустым. Принятый заказ сразу попадает в [orders]. */
    suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long? = null): PlaceOrderResult

    /** Ищет среди загруженных [orders]. */
    fun find(number: Int): Order?
}
