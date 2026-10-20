package ru.coffeerun.data

import ru.coffeerun.model.Allergen
import ru.coffeerun.model.Customization
import ru.coffeerun.model.Extra
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.R

/** Каталог позиций меню. [Product.id] — стабильный ключ: по нему позиция передаётся в навигации. */
object LocalMenu {
    val CAPPUCCINO = Product(
        id = "cappuccino",
        category = ProductCategory.COFFEE, R.string.product_cappuccino, R.string.product_cappuccino_description,
        R.string.product_cappuccino_details, R.drawable.cappuccino, price = 220, calories = 120,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.CREAM, Extra.EXTRA_SHOT, Extra.CINNAMON),
        isPriceFrom = true
    )

    val LATTE = Product(
        id = "latte",
        category = ProductCategory.COFFEE, R.string.product_latte, R.string.product_latte_description,
        R.string.product_latte_details, R.drawable.product_latte, price = 240, calories = 190,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.CREAM, Extra.EXTRA_SHOT, Extra.CINNAMON, Extra.HONEY),
        isPriceFrom = true
    )

    // Ваниль и сливки уже в составе рафа, поэтому как добавки не предлагаются
    val RAF = Product(
        id = "raf",
        category = ProductCategory.COFFEE, R.string.product_raf, R.string.product_raf_description,
        R.string.product_raf_details, R.drawable.product_raf, price = 280, calories = 320,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.CARAMEL, Extra.HAZELNUT, Extra.EXTRA_SHOT, Extra.CINNAMON),
        isPriceFrom = true
    )

    // Эспрессо и американо — без молока; у эспрессо нет и выбора размера, а из добавок только шот
    val ESPRESSO = Product(
        id = "espresso",
        category = ProductCategory.COFFEE, R.string.product_espresso, R.string.product_espresso_description,
        R.string.product_espresso_details, R.drawable.product_espresso, price = 150, calories = 5,
        customizations = setOf(Customization.NOTE),
        extras = setOf(Extra.EXTRA_SHOT)
    )

    val AMERICANO = Product(
        id = "americano",
        category = ProductCategory.COFFEE, R.string.product_americano, R.string.product_americano_description,
        R.string.product_americano_details, R.drawable.product_americano, price = 160, calories = 10,
        customizations = setOf(Customization.SIZE, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.EXTRA_SHOT, Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.HONEY),
        isPriceFrom = true
    )

    val HOT_CHOCOLATE = Product(
        id = "hot_chocolate",
        category = ProductCategory.NOT_COFFEE, R.string.product_hot_chocolate, R.string.product_hot_chocolate_description,
        R.string.product_hot_chocolate_details, R.drawable.product_hot_chocolate, price = 220, calories = 280,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.NOTE),
        extras = setOf(Extra.CREAM, Extra.MARSHMALLOW, Extra.CARAMEL, Extra.HAZELNUT, Extra.CINNAMON),
        isPriceFrom = true
    )

    // К чаю подходят лимон, мёд, мята и имбирь, а не сиропы и сливки
    val TEA = Product(
        id = "tea",
        category = ProductCategory.NOT_COFFEE, R.string.product_tea, R.string.product_tea_description,
        R.string.product_tea_details, R.drawable.product_tea, price = 140, calories = 2,
        customizations = setOf(Customization.SIZE, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.LEMON, Extra.HONEY, Extra.MINT, Extra.GINGER),
        isPriceFrom = true
    )

    // Выпечка: без настроек и добавок, только описание
    val CROISSANT = Product(
        id = "croissant",
        category = ProductCategory.PASTRY, R.string.product_croissant, R.string.product_croissant_description,
        R.string.product_croissant_details, R.drawable.product_croissant, price = 180, calories = 310,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK)
    )

    val CINNAMON_ROLL = Product(
        id = "cinnamon_roll",
        category = ProductCategory.PASTRY, R.string.product_cinnamon_roll, R.string.product_cinnamon_roll_description,
        R.string.product_cinnamon_roll_details, R.drawable.product_cinnamon_roll, price = 170, calories = 340,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    )

    val CHOCOLATE_MUFFIN = Product(
        id = "chocolate_muffin",
        category = ProductCategory.PASTRY, R.string.product_muffin, R.string.product_muffin_description,
        R.string.product_muffin_details, R.drawable.product_muffin, price = 150, calories = 360,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    )

    val APPLE_PIE = Product(
        id = "apple_pie",
        category = ProductCategory.PASTRY, R.string.product_apple_pie, R.string.product_apple_pie_description,
        R.string.product_apple_pie_details, R.drawable.product_apple_pie, price = 190, calories = 290,
        allergens = setOf(Allergen.GLUTEN, Allergen.EGGS)
    )

    val CHEESECAKE = Product(
        id = "cheesecake",
        category = ProductCategory.PASTRY, R.string.product_cheesecake, R.string.product_cheesecake_description,
        R.string.product_cheesecake_details, R.drawable.product_cheesecake, price = 240, calories = 380,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    )

    val ALMOND_COOKIE = Product(
        id = "almond_cookie",
        category = ProductCategory.PASTRY, R.string.product_cookie, R.string.product_cookie_description,
        R.string.product_cookie_details, R.drawable.product_cookie, price = 110, calories = 220,
        allergens = setOf(Allergen.GLUTEN, Allergen.EGGS, Allergen.NUTS)
    )

    val products: List<Product> = listOf(
        CAPPUCCINO,
        LATTE,
        RAF,
        ESPRESSO,
        AMERICANO,
        HOT_CHOCOLATE,
        TEA,
        CROISSANT,
        CINNAMON_ROLL,
        CHOCOLATE_MUFFIN,
        APPLE_PIE,
        CHEESECAKE,
        ALMOND_COOKIE
    )

    fun byId(id: String): Product? = products.firstOrNull { it.id == id }
}
