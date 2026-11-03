package ru.coffeerun.model

import androidx.annotation.StringRes
import ru.coffeerun.R

// Коды опций понимает приложение, подписи к ним — в ресурсах.
// Цены и наличие приходят с сервера в GET /menu — см. [OptionPrices].

/** Размер напитка. */
enum class DrinkSize(@StringRes val labelRes: Int) {
    SMALL(R.string.size_small),
    MEDIUM(R.string.size_medium),
    LARGE(R.string.size_large)
}

/** Размер по умолчанию и у «+» в меню, и на экране позиции — иначе одинаковые напитки не сольются в одну строку. */
val DEFAULT_DRINK_SIZE = DrinkSize.MEDIUM

/** Вид молока. */
enum class MilkType(@StringRes val labelRes: Int) {
    REGULAR(R.string.milk_regular),
    LACTOSE_FREE(R.string.milk_lactose_free),
    OAT(R.string.milk_oat),
    ALMOND(R.string.milk_almond),
    COCONUT(R.string.milk_coconut)
}

/** Добавка к напитку. Можно выбрать несколько; какие доступны позиции, задаёт [Product.extras]. */
enum class Extra(@StringRes val labelRes: Int) {
    VANILLA(R.string.extra_vanilla),
    CARAMEL(R.string.extra_caramel),
    HAZELNUT(R.string.extra_hazelnut),
    CREAM(R.string.extra_cream),
    EXTRA_SHOT(R.string.extra_shot),
    CINNAMON(R.string.extra_cinnamon),
    HONEY(R.string.extra_honey),
    MARSHMALLOW(R.string.extra_marshmallow),
    LEMON(R.string.extra_lemon),
    MINT(R.string.extra_mint),
    GINGER(R.string.extra_ginger)
}

/** [price] — цена капучино в этом размере; доплата за размер считается от [DrinkSize.SMALL]. */
data class SizeOption(val size: DrinkSize, val price: Int)

/** [price] — доплата (0 — без доплаты). Недоступное молоко показывается в списке, но выбрать его нельзя. */
data class MilkOption(val milk: MilkType, val price: Int, val isAvailable: Boolean)

/**
 * Цены и наличие опций из GET /menu, в рублях. Списки — в порядке показа; опции, которой нет
 * в списке, сейчас в кофейне нет вовсе. [extras] — доплата за каждую добавку.
 */
data class OptionPrices(
    val sizes: List<SizeOption>,
    val milks: List<MilkOption>,
    val extras: Map<Extra, Int>
) {
    fun sizePrice(size: DrinkSize): Int? = sizes.firstOrNull { it.size == size }?.price
    fun milk(milk: MilkType): MilkOption? = milks.firstOrNull { it.milk == milk }

    /** [preferred], если такой размер есть в кофейне, иначе первый из списка — чтобы не заказать несуществующий. */
    fun fitSize(preferred: DrinkSize): DrinkSize =
        if (sizePrice(preferred) != null) preferred else sizes.firstOrNull()?.size ?: preferred

    /** [preferred], если это молоко есть в наличии, иначе первое доступное. */
    fun fitMilk(preferred: MilkType): MilkType =
        if (milk(preferred)?.isAvailable == true) preferred else milks.firstOrNull { it.isAvailable }?.milk ?: preferred
}
