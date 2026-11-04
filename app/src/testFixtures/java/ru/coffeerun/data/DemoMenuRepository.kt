package ru.coffeerun.data

import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product

/** Меню из [DemoMenu]; никогда не падает. */
class DemoMenuRepository : MenuRepository {
    override suspend fun getMenu(): List<Product> = DemoMenu.products
    override suspend fun getProduct(id: String): Product? = DemoMenu.byId(id)
    override suspend fun getOptionPrices(): OptionPrices = DemoMenu.prices
}
