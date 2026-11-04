package ru.coffeerun.data

import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product

/** Источник меню. suspend — чтобы подмена на сетевую реализацию не меняла вызывающий код. */
interface MenuRepository {
    suspend fun getMenu(): List<Product>
    suspend fun getProduct(id: String): Product?

    /** Цены и наличие размеров, молока и добавок — приходят вместе с меню. */
    suspend fun getOptionPrices(): OptionPrices
}
