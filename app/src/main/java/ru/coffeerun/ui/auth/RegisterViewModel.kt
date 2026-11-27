package ru.coffeerun.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.AuthResult
import ru.coffeerun.data.AvatarRepository
import ru.coffeerun.data.AvatarUploadDraft
import ru.coffeerun.data.session.SessionChangedException

/** Экран регистрации; поля и ошибки — как у [LoginUiState], плюс имя. */
data class RegisterUiState(
    val name: String = "",
    val phone: String = "",
    val password: String = "",
    @StringRes val nameError: Int? = null,
    @StringRes val phoneError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val isLoading: Boolean = false,
    @StringRes val message: Int? = null,
    val isDone: Boolean = false,
    val avatarPreview: ByteArray? = null,
    val isPreparingAvatar: Boolean = false,
    val avatarUploadFailed: Boolean = false
)

private const val NAME_KEY = "name"
private const val PHONE_KEY = "phone"

/** Регистрация: имя, телефон, пароль от 6 символов. Имя и телефон переживают смерть процесса, пароль — нет. */
class RegisterViewModel(
    private val auth: AuthRepository,
    private val savedState: SavedStateHandle,
    private val avatars: AvatarRepository = AvatarRepository.Unavailable,
    private val avatarDraft: AvatarUploadDraft = AvatarUploadDraft()
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RegisterUiState(name = savedState[NAME_KEY] ?: "", phone = savedState[PHONE_KEY] ?: "")
    )
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    private var submitted = false

    fun onNameChange(name: String) {
        savedState[NAME_KEY] = name
        edit { copy(name = name) }
    }

    fun onPhoneChange(input: String) {
        val digits = phoneDigits(input, _uiState.value.phone)
        savedState[PHONE_KEY] = digits
        edit { copy(phone = digits) }
    }

    fun onPasswordChange(password: String) = edit { copy(password = password) }

    fun onAvatarPicked(uri: String) {
        if (_uiState.value.isLoading || _uiState.value.isPreparingAvatar || _uiState.value.isDone) return
        _uiState.update { it.copy(isPreparingAvatar = true) }
        viewModelScope.launch {
            try {
                val jpeg = avatars.prepare(uri)
                _uiState.update { it.copy(avatarPreview = jpeg) }
            } catch (_: IOException) {
                _uiState.update { it.copy(message = R.string.avatar_read_failed) }
            } finally {
                _uiState.update { it.copy(isPreparingAvatar = false) }
            }
        }
    }

    fun onAvatarRemoved() {
        if (_uiState.value.isLoading || _uiState.value.isPreparingAvatar) return
        _uiState.update { it.copy(avatarPreview = null) }
    }

    fun submit() {
        if (_uiState.value.isLoading || _uiState.value.isPreparingAvatar || _uiState.value.isDone) return
        submitted = true
        edit { this }
        val form = _uiState.value
        if (form.nameError != null || form.phoneError != null || form.passwordError != null) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = auth.register(form.name.trim(), fullPhone(form.phone), form.password)
            val avatarUploadFailed = if (result is AuthResult.Success && form.avatarPreview != null) {
                val session = auth.currentSession()
                try {
                    avatars.upload(session, form.avatarPreview)
                    false
                } catch (_: IOException) {
                    try { auth.withSession(session) { avatarDraft.save(session, form.avatarPreview) } }
                    catch (_: SessionChangedException) { }
                    true
                } catch (_: SessionChangedException) {
                    true
                }
            } else false
            _uiState.update {
                when (result) {
                    is AuthResult.Success -> it.copy(
                        isLoading = false, isDone = true, avatarUploadFailed = avatarUploadFailed, avatarPreview = null
                    )
                    AuthResult.PhoneTaken -> it.copy(isLoading = false, phoneError = R.string.auth_phone_taken)
                    AuthResult.Unavailable -> it.copy(isLoading = false, message = R.string.auth_unavailable)
                    AuthResult.InvalidInput, AuthResult.WrongCredentials -> it.copy(isLoading = false, message = R.string.auth_invalid)
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /** Любая правка пересчитывает ошибки заново — заодно стирает ответ сервера «номер занят». */
    private fun edit(change: RegisterUiState.() -> RegisterUiState) = _uiState.update {
        val form = it.change()
        form.copy(
            nameError = if (submitted && form.name.isBlank()) R.string.auth_name_empty else null,
            phoneError = if (submitted && !isValidPhone(form.phone)) R.string.auth_phone_invalid else null,
            passwordError = if (submitted && form.password.length < MIN_PASSWORD_LENGTH) R.string.auth_password_short else null
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RegisterViewModel(container.auth, createSavedStateHandle(), container.avatars, container.avatarUploadDraft) }
        }
    }
}
