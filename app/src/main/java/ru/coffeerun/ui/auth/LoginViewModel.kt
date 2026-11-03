package ru.coffeerun.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.AuthResult

/**
 * Экран входа. [phone] — 10 цифр без +7. Ошибки полей появляются после первого нажатия «Войти».
 * [message] — разовое сообщение для снэкбара, [isDone] — вход выполнен, экран закрывается.
 */
data class LoginUiState(
    val phone: String = "",
    val password: String = "",
    @StringRes val phoneError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val isLoading: Boolean = false,
    @StringRes val message: Int? = null,
    val isDone: Boolean = false
)

private const val PHONE_KEY = "phone"

/** Вход по телефону и паролю. Телефон переживает смерть процесса, пароль — нет: на диск его не пишем. */
class LoginViewModel(private val auth: AuthRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState(phone = savedState[PHONE_KEY] ?: ""))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var submitted = false

    fun onPhoneChange(input: String) {
        val digits = phoneDigits(input, _uiState.value.phone)
        savedState[PHONE_KEY] = digits
        edit { copy(phone = digits) }
    }

    fun onPasswordChange(password: String) = edit { copy(password = password) }

    fun submit() {
        if (_uiState.value.isLoading) return
        submitted = true
        edit { this }
        val form = _uiState.value
        if (form.phoneError != null || form.passwordError != null) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = auth.login(fullPhone(form.phone), form.password)
            _uiState.update {
                when (result) {
                    is AuthResult.Success -> it.copy(isLoading = false, isDone = true)
                    AuthResult.WrongCredentials -> it.copy(isLoading = false, passwordError = R.string.auth_wrong_credentials)
                    AuthResult.Unavailable -> it.copy(isLoading = false, message = R.string.auth_unavailable)
                    AuthResult.InvalidInput, AuthResult.PhoneTaken -> it.copy(isLoading = false, message = R.string.auth_invalid)
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /** Любая правка пересчитывает ошибки заново — заодно стирает ответ сервера «неверный пароль». */
    private fun edit(change: LoginUiState.() -> LoginUiState) = _uiState.update {
        val form = it.change()
        form.copy(
            phoneError = if (submitted && !isValidPhone(form.phone)) R.string.auth_phone_invalid else null,
            passwordError = if (submitted && form.password.isEmpty()) R.string.auth_password_empty else null
        )
    }

    companion object {
        val Factory = viewModelFactory { initializer { LoginViewModel(container.auth, createSavedStateHandle()) } }
    }
}
