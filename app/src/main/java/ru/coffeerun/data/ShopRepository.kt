package ru.coffeerun.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.coffeerun.model.Shop

/** Выбранная кофейня — единый источник правды для меню, профиля и оформления. */
interface ShopRepository {
    val selected: StateFlow<Shop>

    fun select(shop: Shop)
}

/** Выбор в памяти; по умолчанию — [Shop.MOSKOVSKAYA]. */
class InMemoryShopRepository : ShopRepository {
    // shortcut: выбор теряется при смерти процесса, в лабе 6 — DataStore
    private val _selected = MutableStateFlow(Shop.MOSKOVSKAYA)
    override val selected: StateFlow<Shop> = _selected.asStateFlow()

    override fun select(shop: Shop) {
        _selected.value = shop
    }
}
