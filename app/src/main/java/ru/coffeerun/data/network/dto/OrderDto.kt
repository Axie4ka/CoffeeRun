package ru.coffeerun.data.network.dto

import kotlinx.serialization.Serializable

// Заказы — поля один в один со схемами docs/openapi.yaml. Коды — строками, как в MenuDto.

@Serializable
data class DrinkOptionsDto(
    val size: String? = null,
    val milk: String? = null,
    val iced: Boolean,
    val extras: List<String>,
    val note: String
)

@Serializable
data class OrderLineDto(
    val productId: String,
    val quantity: Int,
    /** Цена одной порции с опциями до скидки в рублях. */
    val unitPrice: Int,
    val options: DrinkOptionsDto? = null,
    /** Скидка в рублях на все порции строки. */
    val discount: Int = 0
)

@Serializable
data class OrderRequest(
    val shop: String,
    /** ISO 8601 со смещением; null — как можно скорее. */
    val pickupAt: String?,
    val lines: List<OrderLineDto>
)

@Serializable
data class OrderDto(
    val number: Int,
    val status: String,
    val shop: String,
    val placedAt: String,
    val pickupAt: String? = null,
    val lines: List<OrderLineDto>,
    val promisedReadyAt: String? = null
)
