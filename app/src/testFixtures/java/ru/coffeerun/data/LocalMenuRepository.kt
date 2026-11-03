package ru.coffeerun.data

import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product

/** Меню из [LocalMenu]; никогда не падает. */
class LocalMenuRepository : MenuRepository {
    override suspend fun getMenu(): List<Product> = LocalMenu.products
    override suspend fun getProduct(id: String): Product? = LocalMenu.byId(id)
    override suspend fun getOptionPrices(): OptionPrices = LocalMenu.prices
}
