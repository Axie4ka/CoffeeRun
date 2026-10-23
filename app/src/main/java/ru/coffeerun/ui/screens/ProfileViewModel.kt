package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.coffeerun.container
import ru.coffeerun.data.FavoritesRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.Shop

/** Состояние профиля: выбранная кофейня, число заказов и избранных позиций. */
data class ProfileUiState(val shop: Shop, val ordersCount: Int, val favoritesCount: Int)

/** Профиль: выбранная кофейня, число заказов и избранных позиций. */
class ProfileViewModel(
    private val shops: ShopRepository,
    orders: OrderRepository,
    favorites: FavoritesRepository
) : ViewModel() {
    val uiState: StateFlow<ProfileUiState> = combine(shops.selected, orders.orders, favorites.ids) { shop, orderList, ids ->
        ProfileUiState(shop, orderList.size, ids.size)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // Начальное значение из текущих данных — чтобы счётчики не мигали нулём
        ProfileUiState(shops.selected.value, orders.orders.value.size, favorites.ids.value.size)
    )

    fun selectShop(shop: Shop) = shops.select(shop)

    companion object {
        val Factory = viewModelFactory { initializer { ProfileViewModel(container.shops, container.orders, container.favorites) } }
    }
}
