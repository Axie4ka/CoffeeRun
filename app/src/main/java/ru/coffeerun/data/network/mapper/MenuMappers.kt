package ru.coffeerun.data.network.mapper

import ru.coffeerun.data.network.dto.MenuDto
import ru.coffeerun.data.network.dto.ProductDto

import java.net.URI
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import ru.coffeerun.model.Allergen
import ru.coffeerun.model.Customization
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkOption
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.OptionPrices
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.Promotion
import ru.coffeerun.model.PromotionType
import ru.coffeerun.model.SizeOption

/**
 * Позиция для приложения. null — категория незнакома: показать позицию не на какой вкладке.
 * Незнакомые настройки и добавки отбрасываются — у них нет подписи в ресурсах.
 * Незнакомый аллерген становится [Allergen.OTHER] и идёт последним.
 * [baseUrl] — адрес сервера: путь к фото из ответа превращается в полный адрес.
 */
fun ProductDto.toModel(baseUrl: String): Product? = Product(
    id = id,
    category = enumOrNull<ProductCategory>(category) ?: return null,
    name = name,
    description = description,
    details = details,
    imageUrl = resolveMenuImage(baseUrl, imageUrl),
    price = price,
    calories = calories,
    allergens = allergens.map { enumOrNull<Allergen>(it) ?: Allergen.OTHER }.sortedBy { it == Allergen.OTHER }.toSet(),
    customizations = customizations.mapNotNull { enumOrNull<Customization>(it) }.toSet(),
    extras = extras.mapNotNull { enumOrNull<Extra>(it) }.toSet(),
    isPriceFrom = priceFrom
)

private fun resolveMenuImage(baseUrl: String, value: String): String {
    if (value.isBlank()) return ""
    return try {
        URI(baseUrl).resolve(value)
            .takeIf { it.host != null && (it.scheme.equals("https", true) || it.scheme.equals("http", true)) }
            ?.toString().orEmpty()
    } catch (_: IllegalArgumentException) {
        ""
    }
}

/** Цены опций из ответа /menu. Незнакомые коды отбрасываются: подписи для них нет, выбрать их нельзя. */
fun MenuDto.toOptionPrices(): OptionPrices = OptionPrices(
    sizes = sizes.mapNotNull { dto -> enumOrNull<DrinkSize>(dto.code)?.let { SizeOption(it, dto.price) } },
    milks = milks.mapNotNull { dto -> enumOrNull<MilkType>(dto.code)?.let { MilkOption(it, dto.price, dto.available) } },
    extras = extras.mapNotNull { dto -> enumOrNull<Extra>(dto.code)?.let { it to dto.price } }.toMap()
)

fun MenuDto.toPromotions(): List<Promotion> = promotions.mapNotNull { dto ->
    val type = enumOrNull<PromotionType>(dto.type) ?: return@mapNotNull null
    val starts = parsePromotionDate(dto.startsAt) ?: return@mapNotNull null
    val ends = parsePromotionDate(dto.endsAt) ?: return@mapNotNull null
    if (dto.id.isBlank() || starts >= ends) return@mapNotNull null
    when (type) {
        PromotionType.CAPPUCCINO_MEDIUM -> {
            if (dto.productId != "cappuccino" || products.none { it.id == dto.productId } ||
                sizes.none { it.code == DrinkSize.MEDIUM.name }) return@mapNotNull null
            Promotion(dto.id, type, starts, ends, productId = dto.productId)
        }
        PromotionType.PASTRY_2_PLUS_1 -> {
            val category = ProductCategory.PASTRY
            if (dto.category != category.name || products.none { it.category == category.name }) return@mapNotNull null
            Promotion(dto.id, type, starts, ends, category = category)
        }
    }
}.distinctBy { it.id }

private fun parsePromotionDate(value: String): Long? = try {
    OffsetDateTime.parse(value).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) {
    null
} catch (_: ArithmeticException) {
    null
}

private inline fun <reified T : Enum<T>> enumOrNull(code: String): T? = enumValues<T>().firstOrNull { it.name == code }
