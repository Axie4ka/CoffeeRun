package ru.coffeerun.model

import androidx.annotation.StringRes
import ru.coffeerun.R

/** Раздел меню — соответствует чипам на экране меню. */
enum class ProductCategory(@StringRes val labelRes: Int) {
    COFFEE(R.string.category_coffee),
    NOT_COFFEE(R.string.category_not_coffee),
    PASTRY(R.string.category_pastry)
}

/**
 * Позиция меню. [price] — цена в рублях; если [isPriceFrom] = true, это цена
 * самого маленького размера («от 220 ₽»), а итог зависит от выбранных опций.
 */
enum class Product(
    val category: ProductCategory,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val price: Int,
    val isPriceFrom: Boolean = false
) {
    CAPPUCCINO(ProductCategory.COFFEE, R.string.product_cappuccino, R.string.product_cappuccino_description, 220, isPriceFrom = true),
    LATTE(ProductCategory.COFFEE, R.string.product_latte, R.string.product_latte_description, 240, isPriceFrom = true),
    RAF(ProductCategory.COFFEE, R.string.product_raf, R.string.product_raf_description, 280, isPriceFrom = true),
    ESPRESSO(ProductCategory.COFFEE, R.string.product_espresso, R.string.product_espresso_description, 150),
    AMERICANO(ProductCategory.COFFEE, R.string.product_americano, R.string.product_americano_description, 160, isPriceFrom = true),
    HOT_CHOCOLATE(ProductCategory.NOT_COFFEE, R.string.product_hot_chocolate, R.string.product_hot_chocolate_description, 220),
    TEA(ProductCategory.NOT_COFFEE, R.string.product_tea, R.string.product_tea_description, 140),
    CROISSANT(ProductCategory.PASTRY, R.string.product_croissant, R.string.product_croissant_description, 180),
    CHEESECAKE(ProductCategory.PASTRY, R.string.product_cheesecake, R.string.product_cheesecake_description, 240)
}
