package ru.coffeerun.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.coffeerun.model.Shop

/** Выбранная кофейня — единый источник правды для меню, профиля и оформления. */
interface ShopRepository {
    val selected: StateFlow<Shop>

    suspend fun saveSelection(shop: Shop)
    suspend fun current(): Shop = selected.value
}

/** Выбор в памяти; по умолчанию — [Shop.MOSKOVSKAYA]. */
class InMemoryShopRepository : ShopRepository {
    private val _selected = MutableStateFlow(Shop.MOSKOVSKAYA)
    override val selected: StateFlow<Shop> = _selected.asStateFlow()

    override suspend fun saveSelection(shop: Shop) = select(shop)

    fun select(shop: Shop) {
        _selected.value = shop
    }
}
