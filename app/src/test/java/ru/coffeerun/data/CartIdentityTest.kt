package ru.coffeerun.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.coffeerun.model.CartLine

class CartIdentityTest {
    @Test
    fun sameRecipeAndPrice_mergeDespiteDifferentProductTextAndPhoto() {
        val cart = InMemoryCartRepository()
        val latte = DemoMenu.LATTE
        cart.add(CartLine(latte, unitPrice = 240, options = latte.defaultOptions()))
        val originalId = cart.lines.value.single().id

        val translated = latte.copy(name = "Latte", imageUrl = "images/new-latte.jpg")
        cart.add(CartLine(translated, unitPrice = 240, options = latte.defaultOptions()))

        assertEquals(1, cart.lines.value.size)
        val merged = cart.lines.value.single()
        assertEquals(originalId, merged.id)
        assertEquals(2, merged.quantity)
    }
}
