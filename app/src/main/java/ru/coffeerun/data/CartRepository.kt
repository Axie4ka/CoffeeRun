package ru.coffeerun.data

import ru.coffeerun.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY
import java.util.concurrent.atomic.AtomicInteger
import ru.coffeerun.data.session.AuthSession

sealed interface CartRead {
    data object Loading : CartRead
    data object Failed : CartRead
    data class Ready(val lines: List<CartLine>) : CartRead
}

/**
 * Корзина — единый источник правды для всех экранов. Наружу только чтение через [lines],
 * менять можно лишь методами ниже.
 */
interface CartRepository {
    val lines: StateFlow<List<CartLine>>
    val readState: Flow<CartRead> get() = lines.map { CartRead.Ready(it) }
    fun retryRead() = Unit

    /** Добавляет всё количество или возвращает false, оставляя корзину без изменений. */
    suspend fun addLine(line: CartLine): Boolean
    suspend fun addWithinLimit(lines: List<CartLine>, session: AuthSession): List<Boolean>
    suspend fun snapshot(session: AuthSession): List<CartLine> = lines.value

    /** Меняет количество в пределах [MIN_QUANTITY]…[MAX_QUANTITY]; удаляет позицию [remove]. */
    suspend fun changeLineQuantity(id: Int, delta: Int)

    suspend fun removeLine(id: Int, session: AuthSession? = null): CartLine?
    suspend fun replaceLine(id: Int, replacement: CartLine, session: AuthSession): Boolean

    suspend fun onSessionEnded() {}

    /** Удаляет отправленные количества по ID строк, сохраняя добавления во время запроса. */
    suspend fun removeOrderedLines(submitted: List<CartLine>, session: AuthSession)

    /** Подменяет товары в строках на [products] с тем же id — например, на названия на новом языке. */
    suspend fun updateProducts(products: List<Product>)
}

/** Корзина в памяти. */
class InMemoryCartRepository : CartRepository {
    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    override val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    private val nextLineId = AtomicInteger(1)

    override suspend fun addLine(line: CartLine) = add(line)
    override suspend fun changeLineQuantity(id: Int, delta: Int) = changeQuantity(id, delta)
    override suspend fun removeLine(id: Int, session: AuthSession?): CartLine? =
        _lines.getAndUpdate { cart -> cart.filterNot { it.id == id } }.firstOrNull { it.id == id }

    override suspend fun replaceLine(id: Int, replacement: CartLine, session: AuthSession): Boolean {
        val newId = nextLineId.getAndIncrement()
        while (true) {
            val before = _lines.value
            val original = before.firstOrNull { it.id == id } ?: return false
            if (original.isSameItem(replacement)) return true
            val target = before.firstOrNull { it.id != id && it.isSameItem(replacement) }
            val quantity = original.quantity + (target?.quantity ?: 0)
            if (quantity > MAX_QUANTITY) return false
            val after = before.filterNot { it.id == id }.let { remaining ->
                if (target == null) remaining + replacement.copy(id = newId, quantity = quantity)
                else remaining.map { if (it.id == target.id) it.copy(quantity = quantity) else it }
            }
            if (_lines.compareAndSet(before, after)) return true
        }
    }
    override suspend fun onSessionEnded() = clear()
    override suspend fun removeOrderedLines(submitted: List<CartLine>, session: AuthSession) = removeOrdered(submitted)
    override suspend fun updateProducts(products: List<Product>) = replaceProducts(products)

    override suspend fun addWithinLimit(lines: List<CartLine>, session: AuthSession): List<Boolean> = lines.map(::add)

    fun add(line: CartLine): Boolean {
        if (line.quantity !in MIN_QUANTITY..MAX_QUANTITY) return false
        val id = nextLineId.getAndIncrement()
        while (true) {
            val before = _lines.value
            val index = before.indexOfFirst { it.isSameItem(line) }
            val quantity = (before.getOrNull(index)?.quantity ?: 0) + line.quantity
            if (quantity > MAX_QUANTITY) return false
            val after = if (index < 0) before + line.copy(id = id)
                else before.replaceAt(index) { it.copy(quantity = quantity) }
            if (_lines.compareAndSet(before, after)) return true
        }
    }

    fun changeQuantity(id: Int, delta: Int) = _lines.update { cart ->
        val index = cart.indexOfFirst { it.id == id }
        if (index < 0) cart
        else cart.replaceAt(index) { it.copy(quantity = (it.quantity + delta).coerceIn(MIN_QUANTITY, MAX_QUANTITY)) }
    }

    fun remove(id: Int) = _lines.update { cart -> cart.filterNot { it.id == id } }

    fun clear() {
        _lines.value = emptyList()
    }

    fun removeOrdered(submitted: List<CartLine>) {
        val byId = submitted.associateBy { it.id }
        _lines.update { cart ->
            cart.mapNotNull { current ->
                val ordered = byId[current.id]
                if (ordered == null || current.product.id != ordered.product.id ||
                    current.unitPrice != ordered.unitPrice || current.options != ordered.options) current
                else (current.quantity - ordered.quantity).takeIf { it > 0 }?.let { current.copy(quantity = it) }
            }
        }
    }

    fun replaceProducts(products: List<Product>) {
        val byId = products.associateBy { it.id }
        _lines.update { cart -> cart.map { line -> byId[line.product.id]?.let { line.copy(product = it) } ?: line } }
    }
}

private fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    toMutableList().also { it[index] = transform(it[index]) }
