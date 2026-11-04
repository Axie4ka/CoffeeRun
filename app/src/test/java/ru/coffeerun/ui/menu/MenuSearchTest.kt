package ru.coffeerun.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.coffeerun.data.DemoMenu

class MenuSearchTest {
    private fun ids(query: String) = searchProducts(DemoMenu.products, query).map { it.id }

    @Test
    fun byName_ignoringCase() = assertEquals(listOf("latte"), ids("ЛАТТЕ"))

    @Test
    fun byDescription() = assertEquals(listOf("latte"), ids("молок"))

    @Test
    fun yoEqualsYe() = assertEquals(listOf("tea"), ids("черный"))

    @Test
    fun severalMatches_keepMenuOrder() = assertEquals(listOf("cinnamon_roll", "apple_pie"), ids("кориц"))

    @Test
    fun blank_returnsAll_unknown_returnsNone() {
        assertEquals(DemoMenu.products.size, ids("  ").size)
        assertEquals(emptyList<String>(), ids("пицца"))
    }
}
