package ru.coffeerun.data.local.room

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import java.io.IOException
import kotlinx.coroutines.flow.catch
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.network.mapper.toDto
import ru.coffeerun.domain.checkout.CheckoutAttemptStore
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import ru.coffeerun.model.Order

class RoomCheckoutAttemptStore(private val db: CoffeeRunDatabase) : CheckoutAttemptStore {
    private val dao = db.checkoutAttempts()
    override val consumesCart = true
    override fun observeKeyedAttempt(owner: String) = dao.observeKeyedAttempt(owner).catch {
        if (it is SQLiteException) throw IOException("Cannot read checkout attempts", it)
        throw it
    }

    override suspend fun activeId(owner: String) = database { dao.active(owner)?.attempt?.id }

    override suspend fun isLegacy(owner: String, id: String) = database {
        dao.active(owner)?.attempt?.let { it.id == id && !it.keyed } == true
    }

    override suspend fun begin(owner: String, id: String, lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?) =
        database {
            val snapshot = lines.mapIndexed { index, line ->
                CheckoutAttemptLineEntity(
                    id, index, line.id, line.product.id, line.quantity, line.unitPrice,
                    line.toDto().options?.let { CoffeeRunJson.encodeToString(it) }, line.discount
                )
            }
            db.withTransaction {
                if (db.localOwner().current() != owner) false
                else dao.begin(CheckoutAttemptEntity(id, owner, shop.name, pickupAtMillis, keyed = true), snapshot)
            }
        }

    override suspend fun finish(owner: String, id: String, order: Order?) = database {
        if (order == null) dao.reject(owner, id) == 1
        else db.withTransaction {
            val active = dao.active(owner)
            if (db.localOwner().current() != owner || active?.attempt?.id != id || dao.complete(owner, id, order.number) != 1) false
            else {
                val cart = db.cart()
                for (line in active.lines) {
                    // Room assigns a new row ID when an item is removed and added again.
                    val current = cart.find(owner, line.cartLineId) ?: continue
                    val remaining = current.quantity - line.quantity
                    if (remaining > 0) cart.setQuantity(owner, current.id, remaining)
                    else cart.delete(owner, current.id)
                }
                val history = db.orderHistory()
                val cached = history.find(owner, order.number)
                val cachedAt = cached?.confirmedAtMillis
                if (cachedAt == null || cachedAt < (order.confirmedAtMillis ?: Long.MIN_VALUE)) {
                    history.save(listOf(order.saved(owner)))
                }
                dao.clearClosed(owner)
                true
            }
        }
    }

    override suspend fun dismiss(owner: String, id: String) = database { dao.reject(owner, id) == 1 }

    private inline fun <T> database(action: () -> T): T = try {
        action()
    } catch (error: SQLiteException) {
        throw IOException("Cannot access checkout attempts", error)
    }
}
