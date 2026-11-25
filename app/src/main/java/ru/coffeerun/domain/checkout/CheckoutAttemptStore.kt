package ru.coffeerun.domain.checkout

import kotlinx.coroutines.flow.Flow
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import ru.coffeerun.model.Order

interface CheckoutAttemptStore {
    val consumesCart: Boolean get() = false
    fun observeKeyedAttempt(owner: String): Flow<Boolean?>
    suspend fun activeId(owner: String): String?
    suspend fun isLegacy(owner: String, id: String): Boolean
    suspend fun begin(owner: String, id: String, lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?): Boolean
    suspend fun finish(owner: String, id: String, order: Order?): Boolean
    suspend fun dismiss(owner: String, id: String): Boolean
}
