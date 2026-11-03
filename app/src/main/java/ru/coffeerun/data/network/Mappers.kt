package ru.coffeerun.data.network

import java.net.URI
import ru.coffeerun.model.Allergen
import ru.coffeerun.model.Customization
import ru.coffeerun.model.Extra
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory

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
    imageUrl = URI(baseUrl).resolve(imageUrl).toString(), // относительный путь — к адресу сервера, полный — как есть
    price = price,
    calories = calories,
    allergens = allergens.map { enumOrNull<Allergen>(it) ?: Allergen.OTHER }.sortedBy { it == Allergen.OTHER }.toSet(),
    customizations = customizations.mapNotNull { enumOrNull<Customization>(it) }.toSet(),
    extras = extras.mapNotNull { enumOrNull<Extra>(it) }.toSet(),
    isPriceFrom = priceFrom
)

private inline fun <reified T : Enum<T>> enumOrNull(code: String): T? = enumValues<T>().firstOrNull { it.name == code }
