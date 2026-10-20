package ru.coffeerun.model

/** Опции, выбранные на экране позиции. null — у позиции нет такого выбора (например, молока у эспрессо). */
data class DrinkOptions(
    val size: DrinkSize?,
    val milk: MilkType?,
    val isIced: Boolean,
    val extras: Set<Extra>,
    /** Примечание для бариста; пустая строка — примечания нет. */
    val note: String = ""
)

/**
 * Позиция корзины. [unitPrice] — цена одной порции с учётом опций (в рублях).
 * У позиции, добавленной кнопкой «+» из меню, [options] = null.
 * [id] выдаёт корзина при добавлении; до этого он равен 0.
 */
data class CartLine(
    val product: Product,
    val unitPrice: Int,
    val quantity: Int = 1,
    val options: DrinkOptions? = null,
    val id: Int = 0
) {
    val total: Int get() = unitPrice * quantity

    /** Та же позиция с теми же опциями — такие строки в корзине объединяются. */
    fun isSameItem(other: CartLine) =
        product == other.product && unitPrice == other.unitPrice && options == other.options
}

/** Сколько порций в корзине — число на бейдже. */
val List<CartLine>.itemCount: Int get() = sumOf { it.quantity }
