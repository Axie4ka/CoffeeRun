package ru.coffeerun.domain.usecase

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private class Attempt(val id: String, val session: AuthSession, val status: CheckoutAttemptStatus)
    private val activeAttempt = MutableStateFlow<Attempt?>(null)
    val attemptStatus = sessions.sessions.flatMapLatest { session ->
        val stored = session.localOwner?.let { store?.observeActiveId(it) } ?: flowOf(null)
        combine(stored.catch { if (it is IOException) emit(null) else throw it }, activeAttempt) { id, attempt ->
            attempt?.takeIf { it.session == session }?.status
                ?: if (id != null) CheckoutAttemptStatus.OutcomeUnknown else CheckoutAttemptStatus.Idle
        }
    }.distinctUntilChanged()

    /** После ручной сверки пользователь может разрешить отдельную покупку. */
    suspend fun dismissUnknownAttempt(): Boolean {
        val expected = activeAttempt.value
        val session = sessions.currentSession()
        return sessions.runInSession(session) {
            if (expected?.session == session && expected.status != CheckoutAttemptStatus.OutcomeUnknown) return@runInSession false
            val owner = session.localOwner
            if (owner != null && store != null) {
                val id = store.activeId(owner)
                val canDismiss = sessions.withSession(session) { activeAttempt.value === expected }
                if (!canDismiss || (id != null && !store.dismiss(owner, id))) return@runInSession false
            }
            sessions.withSession(session) { activeAttempt.compareAndSet(expected, null) }
        }
    }

    suspend operator fun invoke(shop: Shop, pickupAtMillis: Long?, confirmed: CheckoutQuote? = null): CheckoutResult {
        val session = sessions.currentSession()
        return sessions.runInSession(session) {
            val owner = session.localOwner
            if (store != null) {
                if (owner == null) return@runInSession CheckoutResult.LocalStorageUnavailable
                try {
                    val id = store.activeId(owner)
                    if (id != null) {
                        sessions.withSession(session) {
                            if (activeAttempt.value?.session != session) {
                                activeAttempt.value = Attempt(id, session, CheckoutAttemptStatus.OutcomeUnknown)
                            }
                        }
                        return@runInSession CheckoutResult.AttemptPending
                    }
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
            if (unavailable.isNotEmpty()) return@runInSession CheckoutResult.UnavailableLines(unavailable.distinct())
            if (updated.sumOf { it.unitPrice.toLong() * it.quantity } > Int.MAX_VALUE) {
                return@runInSession CheckoutResult.InvalidTotal
            }
            val shown = confirmed?.updatedLines ?: lines
            if (updated.map { it.unitPrice } != shown.map { it.unitPrice }) {
                return@runInSession CheckoutResult.PricesChanged(CheckoutQuote(session, lines, updated, shop, pickupAtMillis))
            }
            val attempt = sessions.withSession(session) {
                if (activeAttempt.value?.session == session) null
                else Attempt(UUID.randomUUID().toString(), session, CheckoutAttemptStatus.Sending).also { activeAttempt.value = it }
            } ?: return@runInSession CheckoutResult.AttemptPending
            var outcomeKnown = false
            try {
                if (owner != null && store != null && !store.begin(owner, attempt.id, updated, shop, pickupAtMillis)) {
                    outcomeKnown = true
                    return@runInSession CheckoutResult.AttemptPending
                }
                currentCoroutineContext().ensureActive()
                if (cart.snapshot(session) != lines) {
                    outcomeKnown = owner == null || store == null || store.finish(owner, attempt.id, null)
                    return@runInSession CheckoutResult.CartChanged
                }
                val result = orders.place(updated, shop, pickupAtMillis, session)
                if (owner != null && store != null && result != PlaceOrderResult.OutcomeUnknown) {
                    val number = (result as? PlaceOrderResult.Placed)?.order?.number
                    if (!store.finish(owner, attempt.id, number, lines)) return@runInSession CheckoutResult.AttemptPending
                }
                outcomeKnown = result != PlaceOrderResult.OutcomeUnknown && result !is PlaceOrderResult.Placed
                when (result) {
                    is PlaceOrderResult.Placed -> {
                        currentCoroutineContext().ensureActive()
                        sessions.withSession(session) { }
                        if (store?.consumesCart != true) cart.removeOrderedLines(lines, session)
                        outcomeKnown = true
                        CheckoutResult.Placed(result.order)
                    }
                    PlaceOrderResult.OutcomeUnknown -> CheckoutResult.AttemptPending
                    PlaceOrderResult.InvalidPickupTime -> CheckoutResult.InvalidPickupTime
                    PlaceOrderResult.Unavailable -> CheckoutResult.Unavailable
                    PlaceOrderResult.NetworkError -> CheckoutResult.NetworkError
                }
            } catch (_: IOException) {
                CheckoutResult.LocalStorageUnavailable
            } finally {
                activeAttempt.update {
                    if (it !== attempt) it
                    else if (outcomeKnown) null
                    else Attempt(attempt.id, session, CheckoutAttemptStatus.OutcomeUnknown)
                }
            }
        }
    }
}
