package ru.coffeerun.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.AvatarRepository
import ru.coffeerun.domain.favorites.Favorites
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.Shop
import ru.coffeerun.model.User

/** Данные аккаунта с сервера (GET /me). */
sealed interface AccountState {
    data object Loading : AccountState
    data object Error : AccountState
    data class Loaded(val user: User, val avatar: File? = null) : AccountState
}

enum class LogoutState { Idle, InProgress, Failed }

/**
 * Состояние профиля: выбранная кофейня, число заказов и избранных позиций, данные аккаунта.
 * [isLoggedIn] — null, пока не прочитан токен: экран ничего не рисует, чтобы не мигнуть чужим видом.
 */
data class ProfileUiState(
    val shop: Shop,
    val ordersCount: Int?,
    val favoritesCount: Int,
    val isLoggedIn: Boolean? = true,
    val account: AccountState = AccountState.Loading,
    val ordersLoadFailed: Boolean = false
)

/**
 * Профиль: имя и телефон — с сервера, кофейня и счётчики — пока локальные. Гостю — только приглашение войти.
 * /me запрашивается, как только пользователь вошёл (открыл вкладку, вошёл, вошёл заново), и по «Повторить».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val shops: ShopRepository,
    orders: Orders,
    favorites: Favorites,
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
    private val avatars: AvatarRepository = AvatarRepository.Unavailable
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val orderLoadFailed = MutableStateFlow(false)

    private val account: Flow<AccountState> =
        combine(auth.sessions, reload) { session, _ -> session }.flatMapLatest { session ->
            if (!session.isLoggedIn) flowOf<AccountState>(AccountState.Loading) // гость в сеть не ходит
            else channelFlow {
                send(AccountState.Loading)
                launch {
                    try { avatars.refresh(session) } catch (_: IOException) { }
                }
                val profileStates = flow {
                    emit(profile.confirmed(session)?.let { AccountState.Loaded(it) } ?: AccountState.Loading)
                    emit(
                        try {
                            AccountState.Loaded(profile.getMe())
                        } catch (_: IOException) {
                            profile.confirmed(session)?.let { AccountState.Loaded(it) } ?: AccountState.Error
                        }
                    )
                    emitAll(profile.observeConfirmed(session).map { AccountState.Loaded(it) })
                }
                combine(profileStates, avatars.observe(session).onStart { emit(null) }) { state, avatar ->
                    if (state is AccountState.Loaded) state.copy(avatar = avatar) else state
                }.collect { send(it) }
            }
        }

    // Загрузка нужна счётчику профиля; частичный кеш после load/place не даёт полного числа.
    init {
        viewModelScope.launch {
            combine(auth.sessions, reload, orders.isLoaded) { session, _, loaded -> session to loaded }
                .collectLatest { (session, loaded) ->
                    orderLoadFailed.value = false
                    if (session.isLoggedIn && !loaded) {
                        try { orders.refresh() } catch (_: IOException) { orderLoadFailed.value = true }
                    }
                }
        }
    }

    private val orderCount = combine(orders.orders, orders.isLoaded, orderLoadFailed) { list, loaded, failed ->
        list.size.takeIf { loaded } to (failed && !loaded)
    }

    val uiState: StateFlow<ProfileUiState> =
        combine(shops.selected, orderCount, favorites.ids, auth.isLoggedIn, account) { shop, countState, ids, loggedIn, account ->
            val (count, failed) = countState
            ProfileUiState(shop, count, ids.size, loggedIn, account, ordersLoadFailed = failed)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Начальное значение из текущих данных — чтобы счётчики не мигали нулём
            ProfileUiState(shops.selected.value, orders.orders.value.size.takeIf { orders.isLoaded.value }, favorites.ids.value.size, isLoggedIn = null)
        )

    fun selectShop(shop: Shop, onFailure: () -> Unit = {}) {
        viewModelScope.launch { try { shops.saveSelection(shop) } catch (_: IOException) { onFailure() } }
    }

    /** «Повторить» после ошибки загрузки. */
    fun retry() {
        reload.value++
    }

    private val _logoutState = MutableStateFlow(LogoutState.Idle)
    val logoutState: StateFlow<LogoutState> = _logoutState

    fun logout() {
        if (_logoutState.value == LogoutState.InProgress) return
        _logoutState.value = LogoutState.InProgress
        viewModelScope.launch {
            try {
                auth.logout()
                _logoutState.value = LogoutState.Idle
            } catch (_: IOException) {
                _logoutState.value = LogoutState.Failed
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ProfileViewModel(container.shops, container.orders, container.favorites, container.auth, container.profile, container.avatars)
            }
        }
    }
}
