package ru.coffeerun.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.ProfileSaveResult
import ru.coffeerun.ui.auth.fullPhone
import ru.coffeerun.ui.auth.isValidPhone
import ru.coffeerun.ui.auth.phoneDigits
import ru.coffeerun.ui.navigation.EditProfileRoute

/** Экран «Изменить профиль»: [phone] — 10 цифр без +7; [isDone] — сохранено, экран закрывается. */
data class EditProfileUiState(
    val name: String = "",
    val phone: String = "",
    @StringRes val nameError: Int? = null,
    @StringRes val phoneError: Int? = null,
    val isSaving: Boolean = false,
    @StringRes val message: Int? = null,
    val isDone: Boolean = false
)

private const val NAME_KEY = "editName"
private const val PHONE_KEY = "editPhone"

/**
 * Изменение имени и телефона (PUT /me). Начальные значения — из профиля ([initialName], [initialPhone]);
 * правки переживают смерть процесса. Проверки — те же, что при регистрации.
 */
class EditProfileViewModel(
    private val profile: ProfileRepository,
    private val savedState: SavedStateHandle,
    initialName: String,
    initialPhone: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditProfileUiState(
            name = savedState[NAME_KEY] ?: initialName,
            phone = savedState[PHONE_KEY] ?: initialPhone.removePrefix("+7")
        )
    )
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()
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

    fun save() {
        if (_uiState.value.isSaving) return
        submitted = true
        edit { this }
        val form = _uiState.value
        if (form.nameError != null || form.phoneError != null) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = profile.updateMe(form.name.trim(), fullPhone(form.phone))
            _uiState.update {
                when (result) {
                    is ProfileSaveResult.Success -> it.copy(isSaving = false, isDone = true)
                    ProfileSaveResult.InvalidInput -> it.copy(isSaving = false, message = R.string.auth_invalid)
                    ProfileSaveResult.Unavailable -> it.copy(isSaving = false, message = R.string.auth_unavailable)
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun edit(change: EditProfileUiState.() -> EditProfileUiState) = _uiState.update {
        val form = it.change()
        form.copy(
            nameError = if (submitted && form.name.isBlank()) R.string.auth_name_empty else null,
            phoneError = if (submitted && !isValidPhone(form.phone)) R.string.auth_phone_invalid else null
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val savedState = createSavedStateHandle()
                val route = savedState.toRoute<EditProfileRoute>()
                EditProfileViewModel(container.profile, savedState, route.name, route.phone)
            }
        }
    }
}
