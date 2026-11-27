package ru.coffeerun.model

enum class PromotionType { CAPPUCCINO_MEDIUM, PASTRY_2_PLUS_1 }

data class Promotion(
    val id: String,
    val type: PromotionType,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val productId: String? = null,
    val category: ProductCategory? = null
)
