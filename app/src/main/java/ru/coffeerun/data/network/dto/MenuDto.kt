package ru.coffeerun.data.network.dto

import kotlinx.serialization.Serializable

// Ответ GET /menu — поля один в один со схемами docs/openapi.yaml.
// Коды (категория, аллергены, опции) — строки: незнакомый код с сервера не должен ронять разбор.

@Serializable
data class MenuDto(
    val products: List<ProductDto>,
    val sizes: List<SizeOptionDto>,
    val milks: List<MilkOptionDto>,
    val extras: List<ExtraOptionDto>,
    val promotions: List<PromotionDto> = emptyList(),
    val shopSchedules: List<ShopScheduleDto> = emptyList()
)

@Serializable
data class ShopScheduleDto(
    val shop: String, val timeZone: String, val preparationMinutes: Long,
    val hours: List<ShopHoursDto>
)

@Serializable
data class ShopHoursDto(val days: List<Int>, val opensAt: String, val closesAt: String)

@Serializable
data class PromotionDto(
    val id: String,
    val type: String,
    val startsAt: String,
    val endsAt: String,
    val productId: String? = null,
    val category: String? = null
)

@Serializable
data class ProductDto(
    val id: String,
    val category: String,
    val name: String,
    val description: String,
    val details: String,
    /** Путь относительно адреса сервера, например `images/latte.jpg`. */
    val imageUrl: String,
    val price: Int,
    val priceFrom: Boolean,
    val calories: Int,
    val allergens: List<String>,
    val customizations: List<String>,
    val extras: List<String>
)

@Serializable
data class SizeOptionDto(val code: String, val price: Int)

@Serializable
data class MilkOptionDto(val code: String, val price: Int, val available: Boolean)

@Serializable
data class ExtraOptionDto(val code: String, val price: Int)
