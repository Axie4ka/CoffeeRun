package ru.coffeerun.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY

/**
 * Корзина — единый источник правды для всех экранов. Наружу только чтение через [lines],
 * менять можно лишь методами ниже. Хранится в памяти.
 */
class CartRepository {
    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    private var nextLineId = 1

    /** Одинаковые позиции сливаются в одну строку; количество не превышает [MAX_QUANTITY]. */
    fun add(line: CartLine) {
        // id выдаём до update: его лямбда может выполниться повторно
        val id = nextLineId++
        _lines.update { cart ->
            val index = cart.indexOfFirst { it.isSameItem(line) }
            if (index >= 0) {
                cart.replaceAt(index) { it.copy(quantity = (it.quantity + line.quantity).coerceAtMost(MAX_QUANTITY)) }
            } else {
                cart + line.copy(id = id, quantity = line.quantity.coerceAtMost(MAX_QUANTITY))
            }
        }
    }

    /** Меняет количество в пределах [MIN_QUANTITY]…[MAX_QUANTITY]; удаляет позицию [remove]. */
    fun changeQuantity(id: Int, delta: Int) = _lines.update { cart ->
        val index = cart.indexOfFirst { it.id == id }
        if (index < 0) cart
        else cart.replaceAt(index) { it.copy(quantity = (it.quantity + delta).coerceIn(MIN_QUANTITY, MAX_QUANTITY)) }
    }

    fun remove(id: Int) = _lines.update { cart -> cart.filterNot { it.id == id } }

    fun clear() {
        _lines.value = emptyList()
    }
}

private fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    toMutableList().also { it[index] = transform(it[index]) }
