package ru.coffeerun.domain.checkout

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.PlaceOrderResult
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.runInSession
import ru.coffeerun.domain.checkout.CheckoutResult
import ru.coffeerun.domain.checkout.CheckoutQuote
import ru.coffeerun.domain.checkout.CheckoutAttemptStatus
import ru.coffeerun.domain.checkout.CheckoutAttemptStore
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.Shop
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.model.MAX_QUANTITY

/** Одна покупка: снимок корзины, проверка времени, отправка и применение подтверждённого результата. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceOrderUseCase(
    private val cart: CartRepository,
    private val orders: Orders,
    private val sessions: SessionProvider,
    private val pickup: PickupPolicy,
    private val menu: MenuRepository,
    private val store: CheckoutAttemptStore? = null
) {
    private data class Attempt(val id: String, val session: AuthSession, val status: CheckoutAttemptStatus, val lines: List<CartLine>)
    private val activeAttempt = MutableStateFlow<Attempt?>(null)
    val attemptStatus = sessions.sessions.flatMapLatest { session ->
        val stored = session.localOwner?.let { store?.observeKeyedAttempt(it) } ?: flowOf(null)
        combine(stored.catch { if (it is IOException) emit(null) else throw it }, activeAttempt) { keyed, attempt ->
            attempt?.takeIf { it.session == session }?.status
                ?: when (keyed) {
                    true -> CheckoutAttemptStatus.OutcomeUnknown
                    false -> CheckoutAttemptStatus.LegacyUnknown
                    null -> CheckoutAttemptStatus.Idle
                }
        }
    }.distinctUntilChanged()

    /** После ручной сверки пользователь может разрешить отдельную покупку. */
    suspend fun dismissUnknownAttempt(): Boolean {
        val session = sessions.currentSession()
        val owner = session.localOwner ?: return false
        val disk = store ?: return false
        return sessions.runInSession(session) {
            val id = disk.activeId(owner) ?: return@runInSession false
            disk.isLegacy(owner, id) && disk.dismiss(owner, id)
        }
    }

    /** null означает, что сервер закрыл неиспользованный ключ. Потеря ответа оставляет попытку активной. */
    suspend fun recoverOrder(): Order? {
        val session = sessions.currentSession()
        return sessions.runInSession(session) {
            val owner = session.localOwner
            val pending = activeAttempt.value?.takeIf { it.session == session }
            val id = if (owner != null && store != null) {
                val stored = store.activeId(owner) ?: return@runInSession null
                if (store.isLegacy(owner, stored)) throw IOException("Legacy attempt has no server key")
                stored
            } else pending?.id ?: return@runInSession null
            val attempt = sessions.withSession(session) {
                val current = activeAttempt.value?.takeIf { it.session == session }
                if (current != null && (current.id != id || current.status == CheckoutAttemptStatus.Sending)) {
                    throw IOException("Checkout in progress")
                }
                Attempt(id, session, CheckoutAttemptStatus.Sending, current?.lines.orEmpty()).also { activeAttempt.value = it }
            }
            var resolved = false
            try {
                val order = orders.resolveAttempt(id, session)
                if (owner != null && store != null && !store.finish(owner, id, order)) {
                    throw IOException("Cannot finish checkout")
                }
                if (order != null && store?.consumesCart != true) cart.removeOrderedLines(attempt.lines, session)
                resolved = true
                if (order != null) orders.publishConfirmed(order, session)
                order
            } finally {
                activeAttempt.update {
                    if (it !== attempt) it else if (resolved) null else attempt.copy(status = CheckoutAttemptStatus.OutcomeUnknown)
                }
            }
        }
    }

    suspend operator fun invoke(shop: Shop, pickupAtMillis: Long?, confirmed: CheckoutQuote? = null): CheckoutResult {
        val session = sessions.currentSession()
        return sessions.runInSession(session) {
            val owner = session.localOwner
            if (store != null) {
                if (owner == null) return@runInSession CheckoutResult.LocalStorageUnavailable
                try {
                    if (store.activeId(owner) != null) return@runInSession CheckoutResult.AttemptPending
                } catch (_: IOException) {
                    return@runInSession CheckoutResult.LocalStorageUnavailable
                }
            }
            if (sessions.withSession(session) { activeAttempt.value?.session == session }) {
                return@runInSession CheckoutResult.AttemptPending
            }
            val lines = try { cart.snapshot(session) } catch (_: IOException) {
                return@runInSession CheckoutResult.LocalStorageUnavailable
            }
            if (lines.isEmpty()) return@runInSession CheckoutResult.EmptyCart
            if (confirmed != null && (confirmed.session != session || !confirmed.matches(lines, shop, pickupAtMillis))) {
                return@runInSession CheckoutResult.CartChanged
            }
            if (!pickup.isValid(shop, pickupAtMillis)) return@runInSession CheckoutResult.InvalidPickupTime
            val snapshot = try {
                menu.refresh()
            } catch (_: IOException) {
                return@runInSession CheckoutResult.CatalogUnavailable
            }
            currentCoroutineContext().ensureActive()
            val currentLines = try { cart.snapshot(session) } catch (_: IOException) {
                return@runInSession CheckoutResult.LocalStorageUnavailable
            }
            if (currentLines != lines) return@runInSession CheckoutResult.CartChanged
            if (!pickup.isValid(shop, pickupAtMillis)) return@runInSession CheckoutResult.InvalidPickupTime
            val updated = when (val quote = calculateQuote(lines, snapshot, session, shop, pickupAtMillis, confirmed)) {
                is QuoteDecision.Ready -> quote.lines
                is QuoteDecision.Blocked -> return@runInSession quote.result
            }
            val attempt = sessions.withSession(session) {
                if (activeAttempt.value?.session == session) null
                else Attempt(UUID.randomUUID().toString(), session, CheckoutAttemptStatus.Sending, lines).also { activeAttempt.value = it }
            } ?: return@runInSession CheckoutResult.AttemptPending
            var outcomeKnown = true
            var recorded = false
            var sending = false
            try {
                if (owner != null && store != null && !store.begin(owner, attempt.id, updated, shop, pickupAtMillis)) {
                    return@runInSession CheckoutResult.AttemptPending
                }
                recorded = owner != null && store != null
                currentCoroutineContext().ensureActive()
                val result = orders.place(updated, shop, pickupAtMillis, session, attempt.id) {
                    if (cart.snapshot(session) != lines) false
                    else {
                        sending = true
                        outcomeKnown = false
                        true
                    }
                }
                if (owner != null && store != null && result != PlaceOrderResult.OutcomeUnknown) {
                    val order = (result as? PlaceOrderResult.Placed)?.order
                    if (!store.finish(owner, attempt.id, order)) return@runInSession CheckoutResult.AttemptPending
                    recorded = false
                }
                outcomeKnown = result != PlaceOrderResult.OutcomeUnknown && result !is PlaceOrderResult.Placed
                when (result) {
                    is PlaceOrderResult.Placed -> {
                        currentCoroutineContext().ensureActive()
                        sessions.withSession(session) { }
                        if (store?.consumesCart != true) cart.removeOrderedLines(lines, session)
                        outcomeKnown = true
                        orders.publishConfirmed(result.order, session)
                        CheckoutResult.Placed(result.order)
                    }
                    PlaceOrderResult.OutcomeUnknown -> CheckoutResult.AttemptPending
                    PlaceOrderResult.CartChanged -> CheckoutResult.CartChanged
                    PlaceOrderResult.InvalidPickupTime -> CheckoutResult.InvalidPickupTime
                    PlaceOrderResult.Unavailable -> CheckoutResult.Unavailable
                    PlaceOrderResult.NetworkError -> CheckoutResult.NetworkError
                }
            } catch (_: IOException) {
                if (sending) CheckoutResult.AttemptPending else CheckoutResult.LocalStorageUnavailable
            } finally {
                if (recorded && !sending && owner != null && store != null) {
                    try {
                        withContext(NonCancellable) { store.finish(owner, attempt.id, null) }
                    } catch (_: IOException) {
                        // A durable record will be resolved when local storage is available again.
                    }
                }
                activeAttempt.update {
                    if (it !== attempt) it
                    else if (outcomeKnown) null
                    else attempt.copy(status = CheckoutAttemptStatus.OutcomeUnknown)
                }
            }
        }
    }
}


private sealed interface QuoteDecision {
    data class Ready(val lines: List<CartLine>) : QuoteDecision
    data class Blocked(val result: CheckoutResult) : QuoteDecision
}

private fun calculateQuote(
    lines: List<CartLine>, snapshot: MenuSnapshot, session: AuthSession, shop: Shop,
    pickupAtMillis: Long?, confirmed: CheckoutQuote?
): QuoteDecision {
    val products = snapshot.products.associateBy { it.id }
    val unavailable = mutableListOf<String>()
    val updated = lines.mapNotNull { line ->
        val product = products[line.product.id]
        val price = product?.recipePrice(line.options, snapshot.prices)
        if (product == null || price == null || line.quantity !in MIN_QUANTITY..MAX_QUANTITY) {
            unavailable += line.product.name
            null
        } else line.copy(product = product, unitPrice = price)
    }
    if (unavailable.isNotEmpty()) return QuoteDecision.Blocked(CheckoutResult.UnavailableLines(unavailable.distinct()))
    if (updated.sumOf { it.unitPrice.toLong() * it.quantity } > Int.MAX_VALUE) {
        return QuoteDecision.Blocked(CheckoutResult.InvalidTotal)
    }
    val shown = confirmed?.updatedLines ?: lines
    if (updated.map { it.unitPrice } != shown.map { it.unitPrice }) {
        return QuoteDecision.Blocked(CheckoutResult.PricesChanged(CheckoutQuote(session, lines, updated, shop, pickupAtMillis)))
    }
    return QuoteDecision.Ready(updated)
}
