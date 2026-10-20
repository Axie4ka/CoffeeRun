package ru.coffeerun.ui.navigation
import kotlinx.serialization.Serializable

@Serializable
data object MenuRoute
@Serializable data object CartRoute
@Serializable data object OrdersRoute
@Serializable data object ProfileRoute
@Serializable data class ProductRoute(val productId: String)
@Serializable data object FavoritesRoute
@Serializable data object CheckoutRoute
@Serializable data class PlacedRoute(val orderNumber: Int)