package ru.coffeerun.data

import ru.coffeerun.model.Product

/** Источник меню. suspend — чтобы подмена на сетевую реализацию не меняла вызывающий код. */
interface MenuRepository {
    suspend fun getMenu(): List<Product>
    suspend fun getProduct(id: String): Product?
}

/** Меню из [LocalMenu]; никогда не падает. */
class LocalMenuRepository : MenuRepository {
    override suspend fun getMenu(): List<Product> = LocalMenu.products
    override suspend fun getProduct(id: String): Product? = LocalMenu.byId(id)
}
