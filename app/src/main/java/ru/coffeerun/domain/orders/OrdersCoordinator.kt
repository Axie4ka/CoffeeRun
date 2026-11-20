package ru.coffeerun.domain.orders

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.data.CancelOrderResult
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.Shop

/** Согласует пересекающиеся запросы; загрузку явно запрашивают потребители. Один на приложение. */
class OrdersCoordinator(
    private val repository: OrderRepository,
    private val sessions: SessionProvider,
    private val scope: CoroutineScope,
    private val pickup: PickupPolicy
) : Orders {
    override val orders: StateFlow<List<Order>> = repository.orders
    override val isLoaded: StateFlow<Boolean> = repository.isLoaded
    // Состояние очереди читается и меняется только под withSession.
    private var owner: AuthSession? = null
    private var operations = Mutex()
    private var loadingRequest: Deferred<Result<Unit>>? = null

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessions.sessions.collect { session ->
                try { sessions.withSession(session) { selectOwner(session) } }
                catch (_: SessionChangedException) { /* Уже пришёл следующий вход. */ }
            }
        }
    }

    /** Общая загрузка не отменяется при закрытии одного из ожидающих экранов. */
    override suspend fun refresh() {
        val session = authorizedSession()
        val request = sessions.withSession(session) {
            selectOwner(session)
            val running = loadingRequest
            if (running != null && !running.isCompleted && !running.isCancelled) running
            else scope.async(start = CoroutineStart.LAZY) {
                runCatching { inSession(session) { repository.refresh(session) } }
            }.also { loadingRequest = it }
        }
        request.start()
        request.await().getOrThrow()
        sessions.withSession(session) { }
    }

    override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, session: AuthSession): PlaceOrderResult {
        return inSession(session) {
            // Ожидание очереди могло сделать время недопустимым после проверки use case.
            if (!pickup.isValid(shop, pickupAtMillis)) PlaceOrderResult.InvalidPickupTime
            else repository.place(lines, shop, pickupAtMillis, session)
        }
    }

    override suspend fun cancel(number: Int): CancelOrderResult {
        val session = authorizedSession()
        return inSession(session) {
            repository.cancel(number, session).also { result ->
                if (result == CancelOrderResult.TooLate) {
                    // Уже под operations: повторный вход через refresh() привёл бы к deadlock.
                    try { repository.refresh(session) } catch (_: IOException) { }
                }
            }
        }
    }

    override suspend fun load(number: Int): Order? {
        val session = authorizedSession()
        return inSession(session) { repository.load(number, session) }
    }

    override fun find(number: Int): Order? = repository.find(number)

    private suspend fun authorizedSession(): AuthSession = sessions.currentSession().also {
        if (!it.isLoggedIn) throw SessionChangedException()
    }

    private suspend fun <T> inSession(session: AuthSession, action: suspend () -> T): T =
        sessions.runInSession(session) {
            val serial = sessions.withSession(session) { selectOwner(session); operations }
            serial.withLock {
                sessions.withSession(session) { }
                action()
            }
        }

    private fun selectOwner(session: AuthSession) {
        if (owner != session) {
            owner = session
            loadingRequest?.cancel()
            loadingRequest = null
            operations = Mutex() // Новый вход не ждёт неотменяемый запрос предыдущего.
            repository.clear(session)
        }
    }
}
