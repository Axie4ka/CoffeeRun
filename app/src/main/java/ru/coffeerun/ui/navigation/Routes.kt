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
/** [toCheckout] — после входа открыть оформление: гость нажал «Оформить» в корзине. */
@Serializable data class LoginRoute(val toCheckout: Boolean = false)
@Serializable data class RegisterRoute(val toCheckout: Boolean = false)
/** Редактирование профиля: текущие имя и телефон (+7XXXXXXXXXX) приходят из профиля — повторно /me не запрашиваем. */
@Serializable data class EditProfileRoute(val name: String, val phone: String)
