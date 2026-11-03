package ru.coffeerun.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.FavoritesRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.Shop

/**
 * Состояние профиля: выбранная кофейня, число заказов и избранных позиций.
 * [isLoggedIn] — null, пока не прочитан токен: экран ничего не рисует, чтобы не мигнуть чужим видом.
 */
data class ProfileUiState(val shop: Shop, val ordersCount: Int, val favoritesCount: Int, val isLoggedIn: Boolean? = true)

/** Профиль: выбранная кофейня, число заказов и избранных позиций. Гостю — только приглашение войти. */
class ProfileViewModel(
    private val shops: ShopRepository,
    orders: OrderRepository,
    favorites: FavoritesRepository,
    private val auth: AuthRepository
) : ViewModel() {
    val uiState: StateFlow<ProfileUiState> =
        combine(shops.selected, orders.orders, favorites.ids, auth.isLoggedIn) { shop, orderList, ids, loggedIn ->
            ProfileUiState(shop, orderList.size, ids.size, loggedIn)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Начальное значение из текущих данных — чтобы счётчики не мигали нулём
            ProfileUiState(shops.selected.value, orders.orders.value.size, favorites.ids.value.size, isLoggedIn = null)
        )

    fun selectShop(shop: Shop) = shops.select(shop)

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ProfileViewModel(container.shops, container.orders, container.favorites, container.auth) }
        }
    }
}
