package ru.coffeerun.domain.orders

enum class RepeatSkipReason { ProductUnavailable, OptionsUnavailable, QuantityUnavailable }

data class RepeatSkippedItem(val productId: String, val reason: RepeatSkipReason)

sealed interface RepeatOrderResult {
    data class Added(val addedLines: Int, val skipped: List<RepeatSkippedItem>, val pricesChanged: Boolean) : RepeatOrderResult
    data class Unavailable(val skipped: List<RepeatSkippedItem>) : RepeatOrderResult
    data object NetworkError : RepeatOrderResult
}
