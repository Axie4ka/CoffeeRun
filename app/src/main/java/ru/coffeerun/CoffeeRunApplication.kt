package ru.coffeerun

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import androidx.datastore.preferences.preferencesDataStore
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.NetworkFavoritesRepository
import ru.coffeerun.domain.favorites.FavoritesCoordinator
import ru.coffeerun.data.NetworkOrderRepository
import ru.coffeerun.domain.orders.OrdersCoordinator
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.usecase.PlaceOrderUseCase
import ru.coffeerun.domain.usecase.RepeatOrderUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.NetworkAuthRepository
import ru.coffeerun.data.NetworkProfileRepository
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.local.AUTH_DATASTORE
import ru.coffeerun.data.local.TokenStore
import ru.coffeerun.data.local.CoffeeRunDatabase
import ru.coffeerun.data.local.RoomCheckoutAttemptStore
import ru.coffeerun.data.local.RoomOwnerScope
import ru.coffeerun.data.local.RoomCartRepository
import ru.coffeerun.data.local.DataStoreShopRepository
import ru.coffeerun.domain.checkout.CheckoutAttemptStore
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import ru.coffeerun.data.CartRepository
import ru.coffeerun.domain.favorites.Favorites
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.NetworkMenuRepository
import ru.coffeerun.data.network.createCoffeeRunApi
import ru.coffeerun.data.network.serverLanguage
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
            if (previous?.isLoggedIn == true && previous != current) cart.onSessionEnded()
            previous = current
        }
    }
}

/** Один DataStore на файл на весь процесс — это гарантирует делегат. */
private val Context.authDataStore by preferencesDataStore(AUTH_DATASTORE)
private val Context.shopDataStore by preferencesDataStore("shop")

class CoffeeRunApplication : Application(), SingletonImageLoader.Factory {
    /** Живёт, пока жив процесс: координаторы согласуют операции и следят за сменой входа. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // var — только чтобы UI-тесты начинали с пустой корзиной: процесс у них общий.
    // Создаётся в onCreate: DataStore нужен готовый Context, при создании объекта Application его ещё нет
    private var installedContainer: AppContainer? = null
    private var containerSessionJob: Job? = null
    @VisibleForTesting
    var container: AppContainer
        get() = checkNotNull(installedContainer)
        set(value) {
            containerSessionJob?.cancel()
            installedContainer = value
            containerSessionJob = value.observeSession(applicationScope)
        }

    override fun onCreate() {
        super.onCreate()
        val language = { serverLanguage() } // спрашивается на каждый запрос: язык могут сменить на ходу
        val database = Room.databaseBuilder(this, CoffeeRunDatabase::class.java, "coffeerun.db").build()
        val tokens = TokenStore(authDataStore, RoomOwnerScope(database)::reconcile)
        // Запросы в Logcat (тег CoffeeRunHttp) — только в debug-сборке
        val log = if (BuildConfig.DEBUG) { line: String -> Log.d("CoffeeRunHttp", line); Unit } else null
        val api = createCoffeeRunApi(BuildConfig.BASE_URL, language, tokens, log)
        val auth = NetworkAuthRepository(api, tokens)
        val menu = NetworkMenuRepository(api, BuildConfig.BASE_URL, language)
        val pickupPolicy = PickupPolicy()
        container = AppContainer(
            menu = menu,
            auth = auth,
            profile = NetworkProfileRepository(api, tokens),
            favorites = FavoritesCoordinator(NetworkFavoritesRepository(api, tokens), tokens, applicationScope),
            orders = OrdersCoordinator(NetworkOrderRepository(api, menu, tokens), tokens, applicationScope, pickupPolicy),
            pickupPolicy = pickupPolicy,
            checkoutAttemptStore = RoomCheckoutAttemptStore(database),
            cart = RoomCartRepository(database, tokens, applicationScope),
            shops = DataStoreShopRepository(shopDataStore, applicationScope)
        )
    }

    /** Один загрузчик фото на всё приложение: фото проявляются плавно, а не появляются рывком. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context).crossfade(true).build()
}

/** Доступ к [AppContainer] из фабрики ViewModel (`viewModelFactory { initializer { … } }`). */
val CreationExtras.container: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as CoffeeRunApplication).container
