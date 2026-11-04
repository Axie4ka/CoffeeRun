package ru.coffeerun

import java.io.IOException
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.Product

/** Меню из [DemoMenu]; при [fail] = true каждый запрос падает, как без сети. Общий для тестов ViewModel. */
class FakeMenuRepository(var fail: Boolean = false) : MenuRepository {
    var calls = 0

    override suspend fun getMenu(): List<Product> {
        calls++
        if (fail) throw IOException("нет сети")
        return DemoMenu.products
    }

    override suspend fun getProduct(id: String): Product? {
        if (fail) throw IOException("нет сети")
        return DemoMenu.byId(id)
    }

    override suspend fun getOptionPrices() = DemoMenu.prices
}
