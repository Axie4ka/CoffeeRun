package ru.coffeerun.domain.promotion

import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.PromotionType

internal fun MenuSnapshot.promotionProductIds(category: ProductCategory?): Set<String> =
    products.filter { it.category == category }.mapTo(hashSetOf()) { it.id }

/** Applies the current catalogue's discounts to the displayed unit prices without changing cart identity. */
fun priceCart(
    lines: List<CartLine>,
    snapshot: MenuSnapshot?,
    nowMillis: Long = System.currentTimeMillis()
): List<CartLine> {
    if (snapshot == null) return lines.map { if (it.discount == 0) it else it.copy(discount = 0) }
    val discounts = IntArray(lines.size)
    for (promotion in snapshot.activePromotions(nowMillis)) {
        when (promotion.type) {
            PromotionType.CAPPUCCINO_MEDIUM -> {
                val small = snapshot.prices.sizePrice(DrinkSize.SMALL) ?: continue
                val medium = snapshot.prices.sizePrice(DrinkSize.MEDIUM) ?: continue
                val product = snapshot.products.firstOrNull { it.id == promotion.productId } ?: continue
                lines.forEachIndexed { index, line ->
                    if (line.product.id == product.id && line.options?.size == DrinkSize.MEDIUM) {
                        val base = (line.product.price.toLong() + medium - small).coerceIn(0L, line.unitPrice.toLong().coerceAtLeast(0L))
                        discounts[index] = (base / 5 * line.quantity).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    }
                }
            }
            PromotionType.PASTRY_2_PLUS_1 -> {
                val eligible = snapshot.promotionProductIds(promotion.category)
                val cheapest = lines.indices.filter { lines[it].product.id in eligible }.sortedBy { lines[it].unitPrice }
                var free = cheapest.sumOf { lines[it].quantity } / 3
                for (index in cheapest) {
                    if (free == 0) break
                    val line = lines[index]
                    val freeOnLine = minOf(free, line.quantity)
                    discounts[index] = (line.unitPrice.toLong() * freeOnLine).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    free -= freeOnLine
                }
            }
        }
    }
    return lines.mapIndexed { index, line ->
        if (discounts[index] == line.discount) line else line.copy(discount = discounts[index])
    }
}
