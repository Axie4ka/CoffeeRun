package ru.coffeerun.model

/** Товары и цены опций одного ответа каталога. */
data class MenuSnapshot(val products: List<Product>, val prices: OptionPrices)
