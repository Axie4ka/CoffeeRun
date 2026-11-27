package ru.coffeerun.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.AvatarRepository
import ru.coffeerun.data.AvatarUploadDraft
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.ProfileSaveResult
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.ui.auth.fullPhone
import ru.coffeerun.ui.auth.isValidPhone
import ru.coffeerun.ui.auth.phoneDigits
import ru.coffeerun.model.User

/** Экран «Изменить профиль»: [phone] — 10 цифр без +7; [isDone] — сохранено, экран закрывается. */
data class EditProfileUiState(
    val name: String = "",
    val phone: String = "",
    @StringRes val nameError: Int? = null,
    @StringRes val phoneError: Int? = null,
    val isSaving: Boolean = false,
    @StringRes val message: Int? = null,
    val isDone: Boolean = false,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val lastConfirmed: User? = null,
    val avatar: File? = null,
    val isUpdatingAvatar: Boolean = false,
    val avatarLoadFailed: Boolean = false,
    val avatarUploadPending: Boolean = false
)

private const val NAME_KEY = "editName"
private const val PHONE_KEY = "editPhone"

/**
 * Изменение имени и телефона (PUT /me). Начальные значения берутся из подтверждённого профиля;
 * правки переживают смерть процесса. Проверки — те же, что при регистрации.
 */
class EditProfileViewModel(
    private val profile: ProfileRepository,
    private val savedState: SavedStateHandle,
    private val avatars: AvatarRepository = AvatarRepository.Unavailable,
    auth: AuthRepository? = null,
    private val avatarDraft: AvatarUploadDraft = AvatarUploadDraft()
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditProfileUiState(
            name = savedState[NAME_KEY] ?: "",
            phone = savedState[PHONE_KEY] ?: "",
            isLoading = true
        )
    )
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()
    private var submitted = false
    private var avatarSession: AuthSession? = null

    init {
        load()
        if (auth != null) viewModelScope.launch {
            auth.sessions.collectLatest { session ->
                avatarSession = session.takeIf(AuthSession::isLoggedIn)
                _uiState.update {
                    it.copy(avatar = null, avatarLoadFailed = false, avatarUploadPending = avatarDraft.get(session) != null)
                }
                if (session.isLoggedIn) coroutineScope {
                    launch { refreshAvatar(session) }
                    avatars.observe(session).collect { photo -> _uiState.update { it.copy(avatar = photo) } }
                }
            }
        }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val user = profile.getForEditing()
                _uiState.update {
                    it.copy(name = savedState[NAME_KEY] ?: user.name,
                        phone = savedState[PHONE_KEY] ?: user.phone.removePrefix("+7"), isLoading = false)
                }
            } catch (_: IOException) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun onNameChange(name: String) {
        savedState[NAME_KEY] = name
        edit { copy(name = name) }
    }

    fun onPhoneChange(input: String) {
        val digits = phoneDigits(input, _uiState.value.phone)
        savedState[PHONE_KEY] = digits
        edit { copy(phone = digits) }
    }

    fun onAvatarPicked(uri: String) = updateAvatar(R.string.avatar_upload_failed) { session ->
        val jpeg = try {
            avatars.prepare(uri)
        } catch (_: IOException) {
            _uiState.update { it.copy(message = R.string.avatar_read_failed) }
            return@updateAvatar
        }
        avatars.upload(session, jpeg)
        avatarDraft.clear(session)
        _uiState.update { it.copy(avatarLoadFailed = false, avatarUploadPending = false) }
    }

    fun onAvatarRemoved() = updateAvatar(R.string.avatar_remove_failed) { session ->
        avatars.remove(session)
        avatarDraft.clear(session)
        _uiState.update { it.copy(avatarLoadFailed = false, avatarUploadPending = false) }
    }

    fun retryAvatar() = updateAvatar(R.string.avatar_upload_failed) { session ->
        val pending = avatarDraft.get(session)
        if (pending == null) refreshAvatar(session)
        else {
            avatars.upload(session, pending)
            avatarDraft.clear(session)
            _uiState.update { it.copy(avatarLoadFailed = false, avatarUploadPending = false) }
        }
    }

    private suspend fun refreshAvatar(session: AuthSession) {
        try {
            avatars.refresh(session)
            _uiState.update { it.copy(avatarLoadFailed = false) }
        } catch (_: IOException) {
            _uiState.update { it.copy(avatarLoadFailed = true) }
        }
    }

    private fun updateAvatar(@StringRes error: Int, action: suspend (AuthSession) -> Unit) {
        val session = avatarSession ?: return
        val state = _uiState.value
        if (state.isUpdatingAvatar || state.isSaving || state.isLoading || state.loadFailed || state.isDone) return
        _uiState.update { it.copy(isUpdatingAvatar = true, message = null) }
        viewModelScope.launch {
            try {
                action(session)
            } catch (_: IOException) {
                _uiState.update { it.copy(message = error) }
            } finally {
                _uiState.update { it.copy(isUpdatingAvatar = false) }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.loadFailed || state.isUpdatingAvatar || state.isDone) return
        submitted = true
        edit { this }
        val form = _uiState.value
        if (form.nameError != null || form.phoneError != null) return
        _uiState.update { it.copy(isSaving = true, lastConfirmed = null) }
        viewModelScope.launch {
            try {
                val result = profile.updateMe(form.name.trim(), fullPhone(form.phone))
                _uiState.update {
                    when (result) {
                        is ProfileSaveResult.Success -> it.copy(isDone = true)
                        ProfileSaveResult.InvalidInput -> it.copy(message = R.string.auth_invalid)
                        ProfileSaveResult.Unavailable -> it.copy(message = R.string.auth_unavailable)
                        is ProfileSaveResult.OutcomeUnknown -> it.copy(
                            message = R.string.profile_save_outcome_unknown, lastConfirmed = result.confirmed
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
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
                EditProfileViewModel(
                    container.profile, createSavedStateHandle(), container.avatars, container.auth, container.avatarUploadDraft
                )
            }
        }
    }
}
