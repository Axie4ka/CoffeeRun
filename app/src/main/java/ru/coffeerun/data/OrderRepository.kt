package ru.coffeerun.data

import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.Shop

/** Чем закончилось оформление — результат-значение, как у форм входа и профиля. */
sealed interface PlaceOrderResult {
    data class Placed(val order: Order) : PlaceOrderResult

    /** 409: чего-то из заказа сейчас нет в наличии. */
    data object Unavailable : PlaceOrderResult

    /** Время стало недопустимым до отправки из очереди координатора. */
    data object InvalidPickupTime : PlaceOrderResult
    data object CartChanged : PlaceOrderResult

    /** Нет сети или сервер ответил не так. */
    data object NetworkError : PlaceOrderResult
    data object OutcomeUnknown : PlaceOrderResult
}

/** Чем закончилась отмена заказа. */
sealed interface CancelOrderResult {
    data class Cancelled(val order: Order) : CancelOrderResult

    /** 409: заказ уже готовят или выдали. */
    data object TooLate : CancelOrderResult

    /** Нет сети или сервер ответил не так. */
    data object NetworkError : CancelOrderResult
}

/** Подтверждённые заказы с сервера. Последовательность операций задаёт OrdersCoordinator. */
interface OrderRepository {
    val orders: StateFlow<List<Order>>
    val isLoaded: StateFlow<Boolean>
    suspend fun refresh(session: AuthSession)
    suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession, attemptId: String = java.util.UUID.randomUUID().toString()): PlaceOrderResult
    suspend fun resolveAttempt(attemptId: String, session: AuthSession): Order?

    suspend fun publishConfirmed(order: Order, session: AuthSession)
    suspend fun cancel(number: Int, session: AuthSession): CancelOrderResult
    suspend fun load(number: Int, session: AuthSession): Order?
    fun find(number: Int): Order?
    suspend fun findSaved(number: Int, session: AuthSession): Order? = find(number)
    fun clear(session: AuthSession)
}
