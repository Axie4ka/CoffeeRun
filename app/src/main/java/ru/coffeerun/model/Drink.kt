package ru.coffeerun.model

import androidx.annotation.StringRes
import ru.coffeerun.R

// Все цены — в рублях. Итоговая цена напитка складывается из цены размера,
// доплаты за молоко и доплат за выбранные добавки.

/** Размер напитка. [price] — базовая цена напитка в этом размере. */
enum class DrinkSize(@StringRes val labelRes: Int, val price: Int) {
    SMALL(R.string.size_small, 220),
    MEDIUM(R.string.size_medium, 260),
    LARGE(R.string.size_large, 300)
}

/** Размер по умолчанию и у «+» в меню, и на экране позиции — иначе одинаковые напитки не сольются в одну строку. */
val DEFAULT_DRINK_SIZE = DrinkSize.MEDIUM

/**
 * Вид молока. [price] — доплата к базовой цене (0 — без доплаты).
 * Варианты с [isAvailable] = false показываются в списке, но выбрать их нельзя.
 */
enum class MilkType(
    @StringRes val labelRes: Int,
    val price: Int,
    val isAvailable: Boolean = true
) {
    REGULAR(R.string.milk_regular, 0),
    LACTOSE_FREE(R.string.milk_lactose_free, 0),
    OAT(R.string.milk_oat, 40),
    ALMOND(R.string.milk_almond, 50),
    COCONUT(R.string.milk_coconut, 50, isAvailable = false)
}

/**
 * Добавка к напитку. Можно выбрать несколько; [price] — доплата за каждую.
 * Какие добавки доступны конкретной позиции, задаёт [Product.extras].
 */
enum class Extra(@StringRes val labelRes: Int, val price: Int) {
    VANILLA(R.string.extra_vanilla, 40),
    CARAMEL(R.string.extra_caramel, 40),
    HAZELNUT(R.string.extra_hazelnut, 40),
    CREAM(R.string.extra_cream, 50),
    EXTRA_SHOT(R.string.extra_shot, 60),
    CINNAMON(R.string.extra_cinnamon, 20),
    HONEY(R.string.extra_honey, 30),
    MARSHMALLOW(R.string.extra_marshmallow, 40),
    LEMON(R.string.extra_lemon, 20),
    MINT(R.string.extra_mint, 20),
    GINGER(R.string.extra_ginger, 30)
}