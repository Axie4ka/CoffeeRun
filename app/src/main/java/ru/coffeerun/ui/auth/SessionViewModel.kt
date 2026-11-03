package ru.coffeerun.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.coffeerun.container
import ru.coffeerun.data.AuthRepository

/**
 * Вошёл ли пользователь — для навигации: куда вести с «Оформить» и сердечка, что показать во вкладках.
 * null — токен ещё читается из DataStore.
 */
class SessionViewModel(auth: AuthRepository) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean?> = auth.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    companion object {
        val Factory = viewModelFactory { initializer { SessionViewModel(container.auth) } }
    }
}
