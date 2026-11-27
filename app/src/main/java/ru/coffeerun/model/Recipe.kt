package ru.coffeerun.model

import kotlinx.serialization.Serializable

const val MAX_RECIPE_NAME_LENGTH = 60

@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val productId: String,
    val options: DrinkOptions?
) {
    fun toCartLine(menu: MenuSnapshot): CartLine? {
        val product = menu.products.firstOrNull { it.id == productId } ?: return null
        if (product.category == ProductCategory.PASTRY) return null
        val price = product.recipePrice(options, menu.prices) ?: return null
        return CartLine(product, price, MIN_QUANTITY, options)
    }
}
