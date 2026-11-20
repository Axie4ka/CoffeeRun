package ru.coffeerun.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.container
import ru.coffeerun.data.AuthRepository

/**
 * Вошёл ли пользователь — для навигации: куда вести с «Оформить» и сердечка, что показать во вкладках.
 * null — токен ещё читается из DataStore.
 */
class SessionViewModel(private val auth: AuthRepository) : ViewModel() {
    val sessions: StateFlow<AuthSession?> = auth.sessions.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val isLoggedIn: StateFlow<Boolean?> = sessions.map { it?.isLoggedIn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val readFailed = auth.sessionReadFailed.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun retrySessionRead() = auth.retrySessionRead()

    companion object {
        val Factory = viewModelFactory { initializer { SessionViewModel(container.auth) } }
    }
}
