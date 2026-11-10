package ru.coffeerun.data

import ru.coffeerun.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY
import java.util.concurrent.atomic.AtomicInteger

/**
 * Корзина — единый источник правды для всех экранов. Наружу только чтение через [lines],
 * менять можно лишь методами ниже.
 */
interface CartRepository {
    val lines: StateFlow<List<CartLine>>

    /** Одинаковые позиции сливаются в одну строку; количество не превышает [MAX_QUANTITY]. */
    fun add(line: CartLine)

    /** Меняет количество в пределах [MIN_QUANTITY]…[MAX_QUANTITY]; удаляет позицию [remove]. */
    fun changeQuantity(id: Int, delta: Int)

    fun remove(id: Int)

    fun clear()

    /** Удаляет отправленные количества по ID строк, сохраняя добавления во время запроса. */
    fun removeOrdered(submitted: List<CartLine>)

    /** Подменяет товары в строках на [products] с тем же id — например, на названия на новом языке. */
    fun replaceProducts(products: List<Product>)
}

/** Корзина в памяти. */
class InMemoryCartRepository : CartRepository {
    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    override val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    private val nextLineId = AtomicInteger(1)

    override fun add(line: CartLine) {
        // id выдаём до update: его лямбда может выполниться повторно
        val id = nextLineId.getAndIncrement()
        _lines.update { cart ->
            val index = cart.indexOfFirst { it.isSameItem(line) }
            if (index >= 0) {
                cart.replaceAt(index) { it.copy(quantity = (it.quantity + line.quantity).coerceAtMost(MAX_QUANTITY)) }
            } else {
                cart + line.copy(id = id, quantity = line.quantity.coerceAtMost(MAX_QUANTITY))
            }
        }
    }

    override fun changeQuantity(id: Int, delta: Int) = _lines.update { cart ->
        val index = cart.indexOfFirst { it.id == id }
        if (index < 0) cart
        else cart.replaceAt(index) { it.copy(quantity = (it.quantity + delta).coerceIn(MIN_QUANTITY, MAX_QUANTITY)) }
    }

    override fun remove(id: Int) = _lines.update { cart -> cart.filterNot { it.id == id } }

    override fun clear() {
        _lines.value = emptyList()
    }

    override fun removeOrdered(submitted: List<CartLine>) {
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

    override fun replaceProducts(products: List<Product>) {
        val byId = products.associateBy { it.id }
        _lines.update { cart -> cart.map { line -> byId[line.product.id]?.let { line.copy(product = it) } ?: line } }
    }
}

private fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    toMutableList().also { it[index] = transform(it[index]) }
