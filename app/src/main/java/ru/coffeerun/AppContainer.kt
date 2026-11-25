package ru.coffeerun

import ru.coffeerun.data.AuthRepository
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.checkout.PlaceOrderUseCase
import ru.coffeerun.domain.orders.RepeatOrderUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.domain.checkout.CheckoutAttemptStore
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import ru.coffeerun.data.CartRepository
import ru.coffeerun.domain.favorites.Favorites
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.ShopRepository

/**
 * Репозитории в одном экземпляре на процесс — так у всех ViewModel одна корзина и одни заказы.
 * Сетевые репозитории передаются снаружи: приложение даёт настоящие, UI-тесты — фейки без сервера
 * и DataStore из src/testFixtures (DemoMenuRepository, InMemoryAuthRepository и др.). У orders нет значения по умолчанию:
 * забыть передать настоящий репозиторий — ошибка компиляции, а не тихие демо-заказы.
 */
class AppContainer(
    val menu: MenuRepository,
    val auth: AuthRepository,
    val profile: ProfileRepository,
    val favorites: Favorites,
    val orders: Orders,
    val pickupPolicy: PickupPolicy = PickupPolicy(),
    checkoutAttemptStore: CheckoutAttemptStore? = null,
    val cart: CartRepository = InMemoryCartRepository(),
    val shops: ShopRepository = InMemoryShopRepository()
) {
    val placeOrder = PlaceOrderUseCase(cart, orders, auth, pickupPolicy, menu, checkoutAttemptStore)
    val repeatOrder = RepeatOrderUseCase(menu, cart, auth)

    internal fun observeSession(scope: CoroutineScope): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        var previous: AuthSession? = null
        auth.sessions.collect { current ->
            profile.onSessionChanged(current)
            if (previous?.isLoggedIn == true && previous != current) cart.onSessionEnded()
            previous = current
        }
    }
}

/** Доступ к [AppContainer] из фабрики ViewModel (`viewModelFactory { initializer { … } }`). */
val CreationExtras.container: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as CoffeeRunApplication).container
