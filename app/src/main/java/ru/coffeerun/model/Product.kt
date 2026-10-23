package ru.coffeerun.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.coffeerun.R

/** Раздел меню — соответствует вкладкам на экране меню. */
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
data class Product(
    val id: String,
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
    /** Есть ли у позиции что выбирать. У выпечки нет — её строка корзины без опций. */
    private val hasOptions: Boolean get() = customizations.isNotEmpty() || extras.isNotEmpty()

    /**
     * Опции по умолчанию: размер [DEFAULT_DRINK_SIZE], обычное молоко, без добавок.
     * Их получает и «+» в меню, и экран позиции, поэтому одинаковые строки корзины сливаются.
     */
    fun defaultOptions(): DrinkOptions? = if (!hasOptions) null else DrinkOptions(
        size = DEFAULT_DRINK_SIZE.takeIf { Customization.SIZE in customizations },
        milk = MilkType.REGULAR.takeIf { Customization.MILK in customizations },
        isIced = false,
        extras = emptySet()
    )

    /**
     * Цена одной порции с выбранными опциями, в рублях. [DrinkSize.price] — цена капучино
     * в этом размере, поэтому для остальных позиций берётся только доплата к размеру S.
     */
    fun unitPrice(size: DrinkSize, milk: MilkType, selectedExtras: Set<Extra>): Int {
        val sizeSurcharge = if (Customization.SIZE in customizations) size.price - DrinkSize.SMALL.price else 0
        val milkSurcharge = if (Customization.MILK in customizations) milk.price else 0
        return price + sizeSurcharge + milkSurcharge + selectedExtras.sumOf { it.price }
    }
}
