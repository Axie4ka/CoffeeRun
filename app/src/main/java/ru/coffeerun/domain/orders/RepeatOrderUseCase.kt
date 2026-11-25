package ru.coffeerun.domain.orders

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession
import ru.coffeerun.domain.orders.RepeatOrderResult
import ru.coffeerun.domain.orders.RepeatSkippedItem
import ru.coffeerun.domain.orders.RepeatSkipReason
import ru.coffeerun.model.*

/** Повторяет точный доступный рецепт по свежим ценам; оплаченный заказ не меняет. */
class RepeatOrderUseCase(
    private val menu: MenuRepository,
    private val cart: CartRepository,
    private val sessions: SessionProvider
) {
    suspend operator fun invoke(order: Order): RepeatOrderResult {
        val session = sessions.currentSession()
        return sessions.runInSession(session) {
            val snapshot = try { menu.refresh() } catch (_: IOException) {
                return@runInSession RepeatOrderResult.NetworkError
            }
            val products = snapshot.products.associateBy { it.id }
            currentCoroutineContext().ensureActive()
            sessions.withSession(session) { }
            try {
                val skipped = mutableListOf<RepeatSkippedItem>()
                val candidates = mutableListOf<Pair<OrderItem, CartLine>>()
                for (item in order.lines) {
                    val product = products[item.productId]
                    val price = product?.recipePrice(item.options, snapshot.prices)
                    val reason = when {
                        product == null -> RepeatSkipReason.ProductUnavailable
                        item.unknownOptionCodes.isNotEmpty() || price == null -> RepeatSkipReason.OptionsUnavailable
                        item.quantity !in MIN_QUANTITY..MAX_QUANTITY -> RepeatSkipReason.QuantityUnavailable
                        else -> null
                    }
                    if (reason != null) {
                        skipped += RepeatSkippedItem(item.productId, reason)
                        continue
                    }
                    val line = CartLine(requireNotNull(product), requireNotNull(price), item.quantity, item.options)
                    candidates += item to line
                }
                val accepted = cart.addWithinLimit(candidates.map { it.second }, session)
                val added = accepted.count { it }
                var pricesChanged = false
                candidates.zip(accepted).forEach { (entry, ok) ->
                    if (!ok) skipped += RepeatSkippedItem(entry.first.productId, RepeatSkipReason.QuantityUnavailable)
                    else if (entry.first.unitPrice != entry.second.unitPrice) pricesChanged = true
                }
                if (added == 0) RepeatOrderResult.Unavailable(skipped)
                else RepeatOrderResult.Added(added, skipped, pricesChanged)
            } catch (_: IOException) {
                RepeatOrderResult.LocalStorageUnavailable
            }
        }
    }

}
