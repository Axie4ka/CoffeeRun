package ru.coffeerun.data.local

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import java.io.IOException
import kotlinx.coroutines.flow.catch
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.network.toDto
import ru.coffeerun.domain.checkout.CheckoutAttemptStore
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

class RoomCheckoutAttemptStore(private val db: CoffeeRunDatabase) : CheckoutAttemptStore {
    private val dao = db.checkoutAttempts()
    override val consumesCart = true
    override fun observeActiveId(owner: String) = dao.observeActiveId(owner).catch {
        if (it is SQLiteException) throw IOException("Cannot read checkout attempts", it)
        throw it
    }

    override suspend fun activeId(owner: String) = database { dao.active(owner)?.attempt?.id }

    override suspend fun begin(owner: String, id: String, lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?) =
        database {
            val snapshot = lines.mapIndexed { index, line ->
                CheckoutAttemptLineEntity(
                    id, index, line.id, line.product.id, line.quantity, line.unitPrice,
                    line.toDto().options?.let { CoffeeRunJson.encodeToString(it) }
                )
            }
            db.withTransaction {
                if (db.localOwner().current() != owner) false
                else dao.begin(CheckoutAttemptEntity(id, owner, shop.name, pickupAtMillis), snapshot)
            }
        }

    override suspend fun finish(owner: String, id: String, orderNumber: Int?, submitted: List<CartLine>) = database {
        if (orderNumber == null) dao.reject(owner, id) == 1
        else db.withTransaction {
            if (db.localOwner().current() != owner || dao.complete(owner, id, orderNumber) != 1) false
            else {
                db.cart().consume(owner, submitted)
                true
            }
        }
    }

    override suspend fun dismiss(owner: String, id: String) = database { dao.dismiss(owner, id) == 1 }

    private inline fun <T> database(action: () -> T): T = try {
        action()
    } catch (error: SQLiteException) {
        throw IOException("Cannot access checkout attempts", error)
    }
}
