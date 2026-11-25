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
import ru.coffeerun.data.repository.NetworkFavoritesRepository
import ru.coffeerun.domain.favorites.FavoritesCoordinator
import ru.coffeerun.data.repository.NetworkOrderRepository
import ru.coffeerun.domain.orders.OrdersCoordinator
import ru.coffeerun.domain.checkout.PickupPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import ru.coffeerun.data.repository.NetworkAuthRepository
import ru.coffeerun.data.repository.NetworkProfileRepository
import ru.coffeerun.data.local.datastore.AUTH_DATASTORE
import ru.coffeerun.data.local.datastore.TokenStore
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomCheckoutAttemptStore
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.local.room.RoomCartRepository
import ru.coffeerun.data.local.datastore.DataStoreShopRepository
import ru.coffeerun.data.repository.NetworkMenuRepository
import ru.coffeerun.data.network.createCoffeeRunApi
import ru.coffeerun.data.network.serverLanguage

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
        val menu = NetworkMenuRepository(api, BuildConfig.BASE_URL, language, database.menuSnapshots())
        val pickupPolicy = PickupPolicy()
        container = AppContainer(
            menu = menu,
            auth = auth,
            profile = NetworkProfileRepository(api, tokens),
            favorites = FavoritesCoordinator(NetworkFavoritesRepository(api, tokens, database), tokens, applicationScope),
            orders = OrdersCoordinator(NetworkOrderRepository(api, menu, tokens, database, applicationScope), tokens, applicationScope, pickupPolicy),
            pickupPolicy = pickupPolicy,
            checkoutAttemptStore = RoomCheckoutAttemptStore(database),
            cart = RoomCartRepository(database, tokens, applicationScope),
            shops = DataStoreShopRepository(shopDataStore, applicationScope)
        )
    }

    /** Один загрузчик фото на всё приложение: фото проявляются плавно, а не появляются рывком. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context).crossfade(true).build()
}
