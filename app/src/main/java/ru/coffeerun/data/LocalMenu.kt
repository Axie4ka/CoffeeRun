package ru.coffeerun.data

import ru.coffeerun.BuildConfig
import ru.coffeerun.model.Allergen
import ru.coffeerun.model.Customization
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkOption
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.SizeOption

/**
 * Меню без сети: для превью, тестов и демо-истории заказов. [Product.id] — стабильный ключ навигации.
 * Тексты — как в ответе GET /menu на русском, фото — с того же сервера.
 */
object LocalMenu {
    private fun image(id: String) = "${BuildConfig.BASE_URL}images/$id.jpg"

    val CAPPUCCINO = Product(
        id = "cappuccino",
        category = ProductCategory.COFFEE,
        name = "Капучино",
        description = "Эспрессо и нежная молочная пена",
        details = "Классический эспрессо с нежной молочной пеной. Готовим на зерне свежей обжарки.",
        imageUrl = image("cappuccino"), price = 220, calories = 120,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.CREAM, Extra.EXTRA_SHOT, Extra.CINNAMON),
        isPriceFrom = true
    )

    val LATTE = Product(
        id = "latte",
        category = ProductCategory.COFFEE,
        name = "Латте",
        description = "Мягкий кофе с большим количеством молока",
        details = "Мягкий кофе с большим количеством горячего молока и тонким слоем пены.",
        imageUrl = image("latte"), price = 240, calories = 190,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.CREAM, Extra.EXTRA_SHOT, Extra.CINNAMON, Extra.HONEY),
        isPriceFrom = true
    )

    // Ваниль и сливки уже в составе рафа, поэтому как добавки не предлагаются
    val RAF = Product(
        id = "raf",
        category = ProductCategory.COFFEE,
        name = "Раф ванильный",
        description = "Сливки, эспрессо и ванильный сахар",
        details = "Эспрессо, взбитый со сливками и ванильным сахаром. Сладкий и бархатный.",
        imageUrl = image("raf"), price = 280, calories = 320,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.CARAMEL, Extra.HAZELNUT, Extra.EXTRA_SHOT, Extra.CINNAMON),
        isPriceFrom = true
    )

    // Эспрессо и американо — без молока; у эспрессо нет и выбора размера, а из добавок только шот
    val ESPRESSO = Product(
        id = "espresso",
        category = ProductCategory.COFFEE,
        name = "Эспрессо",
        description = "Крепкий и насыщенный, 40 мл",
        details = "Короткий и крепкий кофе, 40 мл. Подаётся без молока.",
        imageUrl = image("espresso"), price = 150, calories = 5,
        customizations = setOf(Customization.NOTE),
        extras = setOf(Extra.EXTRA_SHOT)
    )

    val AMERICANO = Product(
        id = "americano",
        category = ProductCategory.COFFEE,
        name = "Американо",
        description = "Эспрессо, разбавленный горячей водой",
        details = "Эспрессо, разбавленный горячей водой. Чистый вкус, без молока.",
        imageUrl = image("americano"), price = 160, calories = 10,
        customizations = setOf(Customization.SIZE, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.EXTRA_SHOT, Extra.VANILLA, Extra.CARAMEL, Extra.HAZELNUT, Extra.HONEY),
        isPriceFrom = true
    )

    val HOT_CHOCOLATE = Product(
        id = "hot_chocolate",
        category = ProductCategory.NOT_COFFEE,
        name = "Горячий шоколад",
        description = "Густой и сладкий, на какао",
        details = "Густой напиток из настоящего шоколада и какао. Тёплый и сладкий.",
        imageUrl = image("hot_chocolate"), price = 220, calories = 280,
        allergens = setOf(Allergen.MILK),
        customizations = setOf(Customization.SIZE, Customization.MILK, Customization.NOTE),
        extras = setOf(Extra.CREAM, Extra.MARSHMALLOW, Extra.CARAMEL, Extra.HAZELNUT, Extra.CINNAMON),
        isPriceFrom = true
    )

    // К чаю подходят лимон, мёд, мята и имбирь, а не сиропы и сливки
    val TEA = Product(
        id = "tea",
        category = ProductCategory.NOT_COFFEE,
        name = "Чёрный чай",
        description = "Заварной, с лимоном",
        details = "Чёрный чай, заваренный в чайнике, подаётся с долькой лимона.",
        imageUrl = image("tea"), price = 140, calories = 2,
        customizations = setOf(Customization.SIZE, Customization.ICE, Customization.NOTE),
        extras = setOf(Extra.LEMON, Extra.HONEY, Extra.MINT, Extra.GINGER),
        isPriceFrom = true
    )

    // Выпечка: без настроек и добавок, только описание
    val CROISSANT = Product(
        id = "croissant",
        category = ProductCategory.PASTRY,
        name = "Круассан",
        description = "Классический, сливочный",
        details = "Классический сливочный круассан, выпекаем каждое утро.",
        imageUrl = image("croissant"), price = 180, calories = 310,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK)
    )

    val CINNAMON_ROLL = Product(
        id = "cinnamon_roll",
        category = ProductCategory.PASTRY,
        name = "Булочка с корицей",
        description = "Мягкое тесто с корицей и сахаром",
        details = "Мягкая булочка с начинкой из корицы и сахара и лёгкой глазурью.",
        imageUrl = image("cinnamon_roll"), price = 170, calories = 340,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    )

    val CHOCOLATE_MUFFIN = Product(
        id = "chocolate_muffin",
        category = ProductCategory.PASTRY,
        name = "Шоколадный маффин",
        description = "С шоколадной крошкой внутри",
        details = "Влажный шоколадный маффин с шоколадной крошкой внутри.",
        imageUrl = image("chocolate_muffin"), price = 150, calories = 360,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    )

    val APPLE_PIE = Product(
        id = "apple_pie",
        category = ProductCategory.PASTRY,
        name = "Яблочный пирог",
        description = "Яблоки, корица и рассыпчатое тесто",
        details = "Пирог с печёными яблоками и корицей на рассыпчатой основе.",
        imageUrl = image("apple_pie"), price = 190, calories = 290,
        allergens = setOf(Allergen.GLUTEN, Allergen.EGGS)
    )

    val CHEESECAKE = Product(
        id = "cheesecake",
        category = ProductCategory.PASTRY,
        name = "Чизкейк",
        description = "Нью-Йорк",
        details = "Чизкейк Нью-Йорк на песочной основе, с нежной сливочной текстурой.",
        imageUrl = image("cheesecake"), price = 240, calories = 380,
        allergens = setOf(Allergen.GLUTEN, Allergen.MILK, Allergen.EGGS)
    )

    val ALMOND_COOKIE = Product(
        id = "almond_cookie",
        category = ProductCategory.PASTRY,
        name = "Миндальное печенье",
        description = "Хрустящее, с поджаренным миндалём",
        details = "Хрустящее печенье с поджаренными миндальными лепестками.",
        imageUrl = image("almond_cookie"), price = 110, calories = 220,
        allergens = setOf(Allergen.GLUTEN, Allergen.EGGS, Allergen.NUTS)
    )

    /** Цены опций — как в ответе GET /menu. */
    val prices = OptionPrices(
        sizes = listOf(SizeOption(DrinkSize.SMALL, 220), SizeOption(DrinkSize.MEDIUM, 260), SizeOption(DrinkSize.LARGE, 300)),
        milks = listOf(
            MilkOption(MilkType.REGULAR, 0, isAvailable = true),
            MilkOption(MilkType.LACTOSE_FREE, 0, isAvailable = true),
            MilkOption(MilkType.OAT, 40, isAvailable = true),
            MilkOption(MilkType.ALMOND, 50, isAvailable = true),
            MilkOption(MilkType.COCONUT, 50, isAvailable = false)
        ),
        extras = mapOf(
            Extra.VANILLA to 40, Extra.CARAMEL to 40, Extra.HAZELNUT to 40, Extra.CREAM to 50,
            Extra.EXTRA_SHOT to 60, Extra.CINNAMON to 20, Extra.HONEY to 30, Extra.MARSHMALLOW to 40,
            Extra.LEMON to 20, Extra.MINT to 20, Extra.GINGER to 30
        )
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
