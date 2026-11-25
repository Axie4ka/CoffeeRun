package ru.coffeerun.model

/** Товары и цены опций одного ответа каталога. */
data class MenuSnapshot(val products: List<Product>, val prices: OptionPrices, val isCached: Boolean = false,
    val catalogProductIds: Set<String> = products.mapTo(mutableSetOf()) { it.id }
)
