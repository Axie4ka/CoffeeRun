package ru.coffeerun

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.preferencesDataStore
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryProfileRepository
import ru.coffeerun.data.NetworkFavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.coffeerun.data.NetworkAuthRepository
import ru.coffeerun.data.NetworkProfileRepository
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.local.AUTH_DATASTORE
import ru.coffeerun.data.local.TokenStore
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.FavoritesRepository
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryFavoritesRepository
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.data.LocalMenuRepository
import ru.coffeerun.data.NetworkMenuRepository
import ru.coffeerun.data.network.createCoffeeRunApi
import ru.coffeerun.data.network.serverLanguage
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.ShopRepository

/**
 * Репозитории в одном экземпляре на процесс — так у всех ViewModel одна корзина и одни заказы.
 * Сетевые репозитории передаются снаружи: приложение даёт настоящие, UI-тесты — [LocalMenuRepository],
 * [InMemoryAuthRepository], [InMemoryProfileRepository] и [InMemoryFavoritesRepository], без сервера и DataStore.
 */
class AppContainer(
    val menu: MenuRepository,
    val auth: AuthRepository,
    val profile: ProfileRepository,
    val favorites: FavoritesRepository
) {
    val cart: CartRepository = InMemoryCartRepository()
    val orders: OrderRepository = InMemoryOrderRepository()
    val shops: ShopRepository = InMemoryShopRepository()
}

/** Один DataStore на файл на весь процесс — это гарантирует делегат. */
private val Context.authDataStore by preferencesDataStore(AUTH_DATASTORE)

class CoffeeRunApplication : Application() {
    /** Живёт, пока жив процесс: в нём репозитории следят за входом и выходом, пока экраны меняются. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // var — только чтобы UI-тесты начинали с пустой корзиной: процесс у них общий.
    // Создаётся в onCreate: DataStore нужен готовый Context, при создании объекта Application его ещё нет
    @VisibleForTesting
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        val language = { serverLanguage() } // спрашивается на каждый запрос: язык могут сменить на ходу
        val tokens = TokenStore(authDataStore) // один на процесс: им пользуются и запросы, и вход
        val api = createCoffeeRunApi(BuildConfig.BASE_URL, language, tokens)
        val auth = NetworkAuthRepository(api, tokens)
        container = AppContainer(
            menu = NetworkMenuRepository(api, BuildConfig.BASE_URL, language),
            auth = auth,
            profile = NetworkProfileRepository(api),
            favorites = NetworkFavoritesRepository(api, auth.isLoggedIn, applicationScope)
        )
    }
}

/** Доступ к [AppContainer] из фабрики ViewModel (`viewModelFactory { initializer { … } }`). */
val CreationExtras.container: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as CoffeeRunApplication).container
