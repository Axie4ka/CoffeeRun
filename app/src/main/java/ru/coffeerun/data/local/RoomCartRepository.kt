package ru.coffeerun.data.local

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.CartRead
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkOptions
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.model.Product

@OptIn(ExperimentalCoroutinesApi::class)
class RoomCartRepository(
    private val db: CoffeeRunDatabase,
    private val sessions: SessionProvider,
    scope: CoroutineScope
) : CartRepository {
    private val dao = db.cart()
    private val reload = MutableStateFlow(0)
    override val readState = combine(sessions.sessions, reload) { session, _ -> session }.flatMapLatest { session ->
        dao.observe(session.owner())
            .map<List<CartLineEntity>, CartRead> { CartRead.Ready(it.map(CartLineEntity::toCartLine)) }
            .onStart { emit(CartRead.Loading) }
            .catch { if (it is SQLiteException || it is SerializationException) emit(CartRead.Failed) else throw it }
    }.stateIn(scope, SharingStarted.Eagerly, CartRead.Loading)
    override val lines = readState.map { (it as? CartRead.Ready)?.lines.orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun retryRead() { reload.update { it + 1 } }

    override suspend fun snapshot(session: AuthSession) =
        transaction(session) { owner -> dao.snapshot(owner).map(CartLineEntity::toCartLine) }

    override suspend fun addLine(line: CartLine) = transaction(sessions.currentSession()) { owner ->
        add(owner, line, exact = false)
        Unit
    }

    override suspend fun addWithinLimit(lines: List<CartLine>, session: AuthSession) =
        transaction(session) { owner -> lines.map { add(owner, it, exact = true) } }

    override suspend fun changeLineQuantity(id: Int, delta: Int) = transaction(sessions.currentSession()) { owner ->
        dao.snapshot(owner).find { it.id == id }?.let {
            dao.setQuantity(owner, id, (it.quantity + delta.toLong()).coerceIn(MIN_QUANTITY.toLong(), MAX_QUANTITY.toLong()).toInt())
        }
        Unit
    }

    override suspend fun removeLine(id: Int) = transaction(sessions.currentSession()) { dao.delete(it, id) }

    override suspend fun removeOrderedLines(submitted: List<CartLine>, session: AuthSession) =
        transaction(session) { owner -> dao.consume(owner, submitted) }

    override suspend fun updateProducts(products: List<Product>) = transaction(sessions.currentSession()) { owner ->
        val byId = products.associateBy { it.id }
        dao.snapshot(owner).forEach { row ->
            byId[row.productId]?.let { dao.setProduct(owner, row.id, CoffeeRunJson.encodeToString(it)) }
        }
    }

    private suspend fun add(owner: String, line: CartLine, exact: Boolean): Boolean {
        val recipe = line.options.key()
        val existing = dao.snapshot(owner).find {
            it.productId == line.product.id && it.unitPrice == line.unitPrice && it.optionsJson == recipe
        }
        val quantity = (existing?.quantity ?: 0) + line.quantity.toLong()
        if (line.quantity < MIN_QUANTITY || (exact && quantity > MAX_QUANTITY)) return false
        val capped = quantity.coerceAtMost(MAX_QUANTITY.toLong()).toInt()
        if (existing != null) dao.setQuantity(owner, existing.id, capped)
        else dao.insert(CartLineEntity(owner = owner, productId = line.product.id, unitPrice = line.unitPrice,
            quantity = capped, productJson = CoffeeRunJson.encodeToString(line.product), optionsJson = recipe))
        return true
    }

    private suspend fun <T> transaction(session: AuthSession, action: suspend (String) -> T): T {
        val owner = session.owner()
        return try {
            db.withTransaction {
                sessions.withSession(session) { }
                if (db.localOwner().current() != owner) throw SessionChangedException()
                action(owner)
            }
        } catch (error: SQLiteException) {
            throw IOException("Cannot access the cart", error)
        } catch (error: SerializationException) {
            throw IOException("Cannot decode the cart", error)
        }
    }
}

internal suspend fun CartDao.consume(owner: String, submitted: List<CartLine>) {
    val sent = submitted.associateBy { it.id }
    snapshot(owner).forEach { row ->
        val line = sent[row.id] ?: return@forEach
        if (row.productId != line.product.id || row.unitPrice != line.unitPrice || row.optionsJson != line.options.key()) return@forEach
        val remaining = row.quantity - line.quantity
        if (remaining > 0) setQuantity(owner, row.id, remaining) else delete(owner, row.id)
    }
}

private fun AuthSession.owner() = localOwner ?: GUEST_OWNER

private fun DrinkOptions?.key() =
    this?.let { CoffeeRunJson.encodeToString(it.copy(extras = it.extras.toSortedSet(), note = it.note.trim())) }.orEmpty()

private fun CartLineEntity.toCartLine() = CartLine(
    CoffeeRunJson.decodeFromString(productJson), unitPrice, quantity,
    optionsJson.takeIf(String::isNotEmpty)?.let { CoffeeRunJson.decodeFromString<DrinkOptions>(it) }, id
)
