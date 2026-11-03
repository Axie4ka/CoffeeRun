package ru.coffeerun.data.network

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.DrinkOptions
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop

/** Тело POST /orders из корзины. Время — ISO 8601 со смещением пояса телефона, как ждёт контракт. */
fun orderRequest(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?, zone: ZoneId = ZoneId.systemDefault()) = OrderRequest(
    shop = shop.name,
    pickupAt = pickupAtMillis?.let { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) },
    lines = lines.map { it.toDto() }
)

fun CartLine.toDto() = OrderLineDto(
    productId = product.id,
    quantity = quantity,
    unitPrice = unitPrice,
    options = options?.let { DrinkOptionsDto(it.size?.name, it.milk?.name, it.isIced, it.extras.map(Extra::name), it.note) }
)

/**
 * Заказ с сервера. Строка нужна целиком с позицией, а сервер присылает только productId —
 * поэтому нужен [products] из меню. Позиции, которой больше нет в меню, строка не показывается,
 * но итог считается по всем строкам ответа: за неё заплачено.
 * null — заказ не показать: незнакомая кофейня или время не по контракту.
 */
fun OrderDto.toModel(products: Map<String, Product>): Order? {
    val shop = enumOrNull<Shop>(shop) ?: return null
    val placed = parseMillis(placedAt) ?: return null
    val shown = lines.mapIndexedNotNull { index, line ->
        val product = products[line.productId] ?: return@mapIndexedNotNull null
        CartLine(product, line.unitPrice, line.quantity, line.options?.toModel(), id = index + 1)
    }
    return Order(
        number = number,
        // Новый статус, которого приложение не знает, — заказ хотя бы «принят», а не исчез
        status = enumOrNull<OrderStatus>(status) ?: OrderStatus.ACCEPTED,
        lines = shown,
        shop = shop,
        placedAtMillis = placed,
        pickupAtMillis = pickupAt?.let(::parseMillis),
        total = lines.sumOf { it.unitPrice * it.quantity }
    )
}

private fun DrinkOptionsDto.toModel() = DrinkOptions(
    size = size?.let { enumOrNull<DrinkSize>(it) },
    milk = milk?.let { enumOrNull<MilkType>(it) },
    isIced = iced,
    extras = extras.mapNotNull { enumOrNull<Extra>(it) }.toSet(),
    note = note
)

private fun parseMillis(iso: String): Long? = try {
    OffsetDateTime.parse(iso).toInstant().toEpochMilli()
} catch (e: DateTimeParseException) {
    null
}

private inline fun <reified T : Enum<T>> enumOrNull(code: String): T? = enumValues<T>().firstOrNull { it.name == code }
