package ru.coffeerun.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.coffeerun.R

/** Раздел меню — соответствует чипам на экране меню. */
enum class ProductCategory(@StringRes val labelRes: Int) {
    COFFEE(R.string.category_coffee),
    NOT_COFFEE(R.string.category_not_coffee),
    PASTRY(R.string.category_pastry)
}

/**
 * Что можно настроить на экране позиции, кроме добавок (их список — [Product.extras]).
 * Позиции без настроек показывают только описание.
 */
enum class Customization { SIZE, MILK, ICE, NOTE }

enum class Allergen(@StringRes val labelRes: Int) {
    MILK(R.string.allergen_milk),
    GLUTEN(R.string.allergen_gluten),
    EGGS(R.string.allergen_eggs),
    NUTS(R.string.allergen_nuts)
}

/**
 * Позиция меню. [price] — цена в рублях; если [isPriceFrom] = true, это цена
 * самого маленького размера («от 220 ₽»), а итог зависит от выбранных опций.
 * [descriptionRes] — короткое описание для списка, [detailsRes] — для экрана позиции.
 * [extras] — добавки, подходящие именно этой позиции (порядок сохраняется);
 * пустой набор — блока добавок нет.
 */
enum class Product(
    val category: ProductCategory,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val detailsRes: Int,
    @DrawableRes val imageRes: Int,
    val price: Int,
    val calories: Int,
    val allergens: Set<Allergen> = emptySet(),
    val customizations: Set<Customization> = emptySet(),
    val extras: Set<Extra> = emptySet(),
    val isPriceFrom: Boolean = false
) {
    CAPPUCCINO(
        ProductCategory.COFFEE, R.string.product_cappuccino, R.string.product_cappuccino_description,
        R.string.product_cappuccino_details, R.drawable.cappuccino, price = 220, calories = 120,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.CREAM, Extra.EXTRA_SHOT, Extra.CINNAMON),
        isPriceFrom = true
    ),
    LATTE(
        ProductCategory.COFFEE, R.string.product_latte, R.string.product_latte_description,
        R.string.product_latte_details, R.drawable.product_latte, price = 240, calories = 190,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.CREAM, Extra.EXTRA_SHOT, Extra.CINNAMON, Extra.HONEY),
        isPriceFrom = true
    ),
    // Ваниль и сливки уже в составе рафа, поэтому как добавки не предлагаются
    RAF(
        ProductCategory.COFFEE, R.string.product_raf, R.string.product_raf_description,
        R.string.product_raf_details, R.drawable.product_raf, price = 280, calories = 320,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.CARAMEL, Extra.HAZELNUT, Extra.EXTRA_SHOT, Extra.CINNAMON),
        isPriceFrom = true
    ),
    // Эспрессо и американо — без молока; у эспрессо нет и выбора размера, а из добавок только шот
    ESPRESSO(
        ProductCategory.COFFEE, R.string.product_espresso, R.string.product_espresso_description,
        R.string.product_espresso_details, R.drawable.product_espresso, price = 150, calories = 5,
        customizations = setOf(Customization.NOTE),
        extras = setOf(Extra.EXTRA_SHOT)
    ),
    AMERICANO(
        ProductCategory.COFFEE, R.string.product_americano, R.string.product_americano_description,
        R.string.product_americano_details, R.drawable.product_americano, price = 160, calories = 10,
        customizations = setOf(Customization.SIZE, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.EXTRA_SHOT, Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.HONEY),
        isPriceFrom = true
    ),
    HOT_CHOCOLATE(
        ProductCategory.NOT_COFFEE, R.string.product_hot_chocolate, R.string.product_hot_chocolate_description,
        R.string.product_hot_chocolate_details, R.drawable.product_hot_chocolate, price = 220, calories = 280,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.NOTE),
        extras = setOf(Extra.CREAM, Extra.MARSHMALLOW, Extra.CARAMEL, Extra.HAZELNUT, Extra.CINNAMON),
        isPriceFrom = true
    ),
    // К чаю подходят лимон, мёд, мята и имбирь, а не сиропы и сливки
    TEA(
        ProductCategory.NOT_COFFEE, R.string.product_tea, R.string.product_tea_description,
        R.string.product_tea_details, R.drawable.product_tea, price = 140, calories = 2,
        customizations = setOf(Customization.SIZE, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.LEMON, Extra.HONEY, Extra.MINT, Extra.GINGER),
        isPriceFrom = true
    ),
    // Выпечка: без настроек и добавок, только описание
    CROISSANT(
        ProductCategory.PASTRY, R.string.product_croissant, R.string.product_croissant_description,
        R.string.product_croissant_details, R.drawable.product_croissant, price = 180, calories = 310,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK)
    ),
    CINNAMON_ROLL(
        ProductCategory.PASTRY, R.string.product_cinnamon_roll, R.string.product_cinnamon_roll_description,
        R.string.product_cinnamon_roll_details, R.drawable.product_cinnamon_roll, price = 170, calories = 340,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    ),
    CHOCOLATE_MUFFIN(
        ProductCategory.PASTRY, R.string.product_muffin, R.string.product_muffin_description,
        R.string.product_muffin_details, R.drawable.product_muffin, price = 150, calories = 360,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    ),
    APPLE_PIE(
        ProductCategory.PASTRY, R.string.product_apple_pie, R.string.product_apple_pie_description,
        R.string.product_apple_pie_details, R.drawable.product_apple_pie, price = 190, calories = 290,
        allergens = setOf(Allergen.GLUTEN, Allergen.EGGS)
    ),
    CHEESECAKE(
        ProductCategory.PASTRY, R.string.product_cheesecake, R.string.product_cheesecake_description,
        R.string.product_cheesecake_details, R.drawable.product_cheesecake, price = 240, calories = 380,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    ),
    ALMOND_COOKIE(
        ProductCategory.PASTRY, R.string.product_cookie, R.string.product_cookie_description,
        R.string.product_cookie_details, R.drawable.product_cookie, price = 110, calories = 220,
        allergens = setOf(Allergen.GLUTEN, Allergen.EGGS, Allergen.NUTS)
    )
}
