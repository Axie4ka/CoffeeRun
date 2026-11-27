package ru.coffeerun.model
import kotlinx.serialization.Serializable

/** Порций одной позиции — от [MIN_QUANTITY] до [MAX_QUANTITY], и на экране позиции, и в корзине. */
const val MIN_QUANTITY = 1
const val MAX_QUANTITY = 10

/** Длина примечания для бариста. */
const val MAX_NOTE_LENGTH = 100

/** Опции, выбранные на экране позиции. null — у позиции нет такого выбора (например, молока у эспрессо). */
@Serializable
data class DrinkOptions(
    val size: DrinkSize?,
    val milk: MilkType?,
    val isIced: Boolean,
    val extras: Set<Extra>,
    /** Примечание для бариста; пустая строка — примечания нет. */
    val note: String = ""
) {
    internal fun normalized() = if (note == note.trim()) this else copy(note = note.trim())
}

/**
 * Позиция корзины. [unitPrice] — цена одной порции с учётом опций (в рублях).
 * [discount] — скидка на все порции строки, рассчитывается для конкретной корзины.
 * [id] выдаёт корзина при добавлении; до этого он равен 0.
 */
data class CartLine(
    val product: Product,
    val unitPrice: Int,
    val quantity: Int = 1,
    val options: DrinkOptions? = null,
    val id: Int = 0,
    val discount: Int = 0
) {
    val totalAmount: Long get() = unitPrice.toLong() * quantity - discount
    val total: Int get() = totalAmount.toInt()

    /** Та же позиция с теми же опциями — такие строки в корзине объединяются. */
    fun isSameItem(other: CartLine) =
        product.id == other.product.id &&
        unitPrice == other.unitPrice &&
        options?.normalized() == other.options?.normalized()
}

/** Сколько порций в корзине — число на бейдже. */
val List<CartLine>.itemCount: Int get() = sumOf { it.quantity }
