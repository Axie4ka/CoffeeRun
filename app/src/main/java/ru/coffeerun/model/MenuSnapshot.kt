package ru.coffeerun.model

/** Товары и цены опций одного ответа каталога. */
data class MenuSnapshot(val products: List<Product>, val prices: OptionPrices, val isCached: Boolean = false,
    val catalogProductIds: Set<String> = products.mapTo(mutableSetOf()) { it.id },
    val fetchedAtMillis: Long? = null,
    val promotions: List<Promotion> = emptyList()
) {
    fun activePromotions(nowMillis: Long = System.currentTimeMillis()): List<Promotion> = promotions
        .filter { nowMillis >= it.startsAtMillis && nowMillis < it.endsAtMillis }
        .distinctBy { it.type }
}
