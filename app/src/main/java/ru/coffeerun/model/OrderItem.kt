package ru.coffeerun.model

/**
 * Позиция оформленного заказа: оплаченная цена и выбранные опции не зависят от текущего каталога.
 * Пока сервер возвращает только productId, название и описание берутся из доступного меню.
 * null в [name] означает, что название неизвестно; локализованную заглушку выбирает UI.
 */
data class OrderItem(
    val productId: String,
    val name: String?,
    val unitPrice: Int,
    val quantity: Int = 1,
    val options: DrinkOptions? = null,
    val description: String = "",
    val unknownOptionCodes: List<String> = emptyList()
) {
    val total: Int get() = unitPrice * quantity
}

/** Фиксирует данные позиции корзины в момент оформления. Локальный ID корзины заказу не нужен. */
fun CartLine.toOrderItem() = OrderItem(
    productId = product.id,
    name = product.name,
    unitPrice = unitPrice,
    quantity = quantity,
    options = options,
    description = product.description
)
