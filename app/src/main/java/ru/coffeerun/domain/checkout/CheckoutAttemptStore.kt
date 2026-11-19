package ru.coffeerun.domain.checkout

import kotlinx.coroutines.flow.Flow
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

interface CheckoutAttemptStore {
    fun observeActiveId(owner: String): Flow<String?>
    suspend fun activeId(owner: String): String?
    suspend fun begin(owner: String, id: String, lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?): Boolean
    suspend fun finish(owner: String, id: String, orderNumber: Int?): Boolean
    suspend fun dismiss(owner: String, id: String): Boolean
}
