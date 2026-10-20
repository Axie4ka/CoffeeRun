package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.coffeerun.container
import ru.coffeerun.data.FavoritesRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.Shop

/** Профиль: выбранная кофейня и число избранных позиций. */
class ProfileViewModel(
    private val shops: ShopRepository,
    @Suppress("unused") private val orders: OrderRepository,
    favorites: FavoritesRepository
) : ViewModel() {
    val shop: StateFlow<Shop> = shops.selected

    val favoritesCount: StateFlow<Int> = favorites.ids
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), favorites.ids.value.size)

    fun selectShop(shop: Shop) = shops.select(shop)

    companion object {
        val Factory = viewModelFactory { initializer { ProfileViewModel(container.shops, container.orders, container.favorites) } }
    }
}
