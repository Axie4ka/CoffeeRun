package ru.coffeerun.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.FavoritesRepository
import ru.coffeerun.data.OrderRepository
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.Shop
import ru.coffeerun.model.User

/** Данные аккаунта с сервера (GET /me). */
sealed interface AccountState {
    data object Loading : AccountState
    data object Error : AccountState
    data class Loaded(val user: User) : AccountState
}

/**
 * Состояние профиля: выбранная кофейня, число заказов и избранных позиций, данные аккаунта.
 * [isLoggedIn] — null, пока не прочитан токен: экран ничего не рисует, чтобы не мигнуть чужим видом.
 */
data class ProfileUiState(
    val shop: Shop,
    val ordersCount: Int,
    val favoritesCount: Int,
    val isLoggedIn: Boolean? = true,
    val account: AccountState = AccountState.Loading
)

/**
 * Профиль: имя и телефон — с сервера, кофейня и счётчики — пока локальные. Гостю — только приглашение войти.
 * /me запрашивается, как только пользователь вошёл (открыл вкладку, вошёл, вошёл заново), и по «Повторить».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val shops: ShopRepository,
    orders: OrderRepository,
    favorites: FavoritesRepository,
    private val auth: AuthRepository,
    private val profile: ProfileRepository
) : ViewModel() {
    private val reload = MutableStateFlow(0)

    private val account: Flow<AccountState> =
        combine(auth.isLoggedIn.distinctUntilChanged(), reload) { loggedIn, _ -> loggedIn }.flatMapLatest { loggedIn ->
            if (!loggedIn) flowOf<AccountState>(AccountState.Loading) // гость в сеть не ходит
            else flow {
                emit(AccountState.Loading)
                emit(
                    try {
                        AccountState.Loaded(profile.getMe())
                    } catch (e: IOException) {
                        AccountState.Error
                    }
                )
            }
        }

    val uiState: StateFlow<ProfileUiState> =
        combine(shops.selected, orders.orders, favorites.ids, auth.isLoggedIn, account) { shop, orderList, ids, loggedIn, account ->
            ProfileUiState(shop, orderList.size, ids.size, loggedIn, account)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Начальное значение из текущих данных — чтобы счётчики не мигали нулём
            ProfileUiState(shops.selected.value, orders.orders.value.size, favorites.ids.value.size, isLoggedIn = null)
        )

    fun selectShop(shop: Shop) = shops.select(shop)

    /** «Повторить» после ошибки загрузки. */
    fun retry() {
        reload.value++
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ProfileViewModel(container.shops, container.orders, container.favorites, container.auth, container.profile) }
        }
    }
}
