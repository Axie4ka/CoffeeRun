package ru.coffeerun

import android.app.Application
import androidx.annotation.VisibleForTesting
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
 * [menu] передаётся снаружи: приложение даёт сетевое меню, UI-тесты — [LocalMenuRepository] без сервера.
 */
class AppContainer(val menu: MenuRepository) {
    val cart: CartRepository = InMemoryCartRepository()
    val orders: OrderRepository = InMemoryOrderRepository()
    val shops: ShopRepository = InMemoryShopRepository()
    val favorites: FavoritesRepository = InMemoryFavoritesRepository()
}

class CoffeeRunApplication : Application() {
    // var — только чтобы UI-тесты начинали с пустой корзиной: процесс у них общий
    @VisibleForTesting
    var container = AppContainer(menu = networkMenu())

    // Язык спрашивается лениво, на каждый запрос: при создании Application ресурсов ещё нет
    private fun networkMenu(): MenuRepository {
        val language = { serverLanguage() }
        return NetworkMenuRepository(createCoffeeRunApi(BuildConfig.BASE_URL, language), BuildConfig.BASE_URL, language)
    }
}

/** Доступ к [AppContainer] из фабрики ViewModel (`viewModelFactory { initializer { … } }`). */
val CreationExtras.container: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as CoffeeRunApplication).container
