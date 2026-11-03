package ru.coffeerun.data.network

import kotlinx.serialization.Serializable

// Ответ GET /menu — поля один в один со схемами docs/openapi.yaml.
// Коды (категория, аллергены, опции) — строки: незнакомый код с сервера не должен ронять разбор.

@Serializable
data class MenuDto(
    val products: List<ProductDto>,
    val sizes: List<SizeOptionDto>,
    val milks: List<MilkOptionDto>,
    val extras: List<ExtraOptionDto>
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
