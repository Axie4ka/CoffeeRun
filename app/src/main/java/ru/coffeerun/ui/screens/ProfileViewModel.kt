package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.container
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.Shop

/** Профиль: пока только выбранная кофейня; избранное и счётчики добавятся позже. */
class ProfileViewModel(
    private val shops: ShopRepository,
    @Suppress("unused") private val orders: OrderRepository
) : ViewModel() {
    val shop: StateFlow<Shop> = shops.selected

    fun selectShop(shop: Shop) = shops.select(shop)

    companion object {
        val Factory = viewModelFactory { initializer { ProfileViewModel(container.shops, container.orders) } }
    }
}
