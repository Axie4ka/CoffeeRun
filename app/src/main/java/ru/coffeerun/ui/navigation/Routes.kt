package ru.coffeerun.ui.navigation
import kotlinx.serialization.Serializable
import ru.coffeerun.model.Product

@Serializable
data object MenuRoute
@Serializable data object CartRoute
@Serializable data object OrdersRoute
@Serializable data object ProfileRoute
@Serializable data class ProductRoute(val product: Product)
@Serializable data object CheckoutRoute
@Serializable data class PlacedRoute(val orderNumber: Int)