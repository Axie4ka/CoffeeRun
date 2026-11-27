package ru.coffeerun.data.network.mapper

import ru.coffeerun.data.network.dto.DrinkOptionsDto
import ru.coffeerun.data.network.dto.OrderDto
import ru.coffeerun.data.network.dto.OrderLineDto
import ru.coffeerun.data.network.dto.OrderRequest

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
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderItem
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
    options = options?.let { DrinkOptionsDto(it.size?.name, it.milk?.name, it.isIced, it.extras.map(Extra::name), it.note) },
    discount = discount
)

/**
 * Сервер пока не присылает исторические названия. Берём их из [knownItems] при обновлении
 * статуса или из [products] при загрузке. Неизвестный товар остаётся отдельной строкой.
 * null — заказ не показать: незнакомая кофейня или время не по контракту.
 */
fun OrderDto.toModel(products: Map<String, Product>, knownItems: List<OrderItem> = emptyList()): Order? {
    val shop = enumOrNull<Shop>(shop) ?: return null
    val placed = parseMillis(placedAt) ?: return null
    var total = 0L
    for (line in lines) {
        if (line.quantity !in MIN_QUANTITY..MAX_QUANTITY || line.unitPrice < 0) return null
        val gross = line.unitPrice.toLong() * line.quantity
        if (line.discount.toLong() !in 0L..gross) return null
        total += gross - line.discount
        if (total > Int.MAX_VALUE) return null
    }
    val knownById = knownItems.associateBy { it.productId }
    val shown = lines.map { line ->
        val known = knownById[line.productId]
        val product = products[line.productId]
        OrderItem(
            productId = line.productId,
            name = known?.name ?: product?.name,
            unitPrice = line.unitPrice,
            quantity = line.quantity,
            options = line.options?.toModel(),
            description = known?.description ?: product?.description.orEmpty(),
            unknownOptionCodes = line.options?.unknownCodes().orEmpty(),
            discount = line.discount
        )
    }
    return Order(
        number = number,
        status = enumOrNull<OrderStatus>(status) ?: OrderStatus.UNKNOWN,
        lines = shown,
        shop = shop,
        placedAtMillis = placed,
        pickupAtMillis = pickupAt?.let(::parseMillis),
        promisedReadyAtMillis = promisedReadyAt?.let(::parseMillis),
        total = total.toInt()
    )
}

private fun DrinkOptionsDto.toModel() = DrinkOptions(
    size = size?.let { enumOrNull<DrinkSize>(it) },
    milk = milk?.let { enumOrNull<MilkType>(it) },
    isIced = iced,
    extras = extras.mapNotNull { enumOrNull<Extra>(it) }.toSet(),
    note = note
)

private fun DrinkOptionsDto.unknownCodes(): List<String> = buildList {
    this@unknownCodes.size?.takeIf { enumOrNull<DrinkSize>(it) == null }?.let { add(it) }
    milk?.takeIf { enumOrNull<MilkType>(it) == null }?.let { add(it) }
    addAll(extras.filter { enumOrNull<Extra>(it) == null })
}

private fun parseMillis(iso: String): Long? = try {
    OffsetDateTime.parse(iso).toInstant().toEpochMilli()
} catch (e: DateTimeParseException) {
    null
}

private inline fun <reified T : Enum<T>> enumOrNull(code: String): T? = enumValues<T>().firstOrNull { it.name == code }
