package ru.coffeerun.ui.profile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.coffeerun.ui.components.RetryMessage
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import ru.coffeerun.R
import ru.coffeerun.ui.auth.AuthField
import ru.coffeerun.ui.auth.AuthScaffold
import ru.coffeerun.ui.auth.PhoneField
import ru.coffeerun.ui.auth.SubmitButton
import ru.coffeerun.ui.components.AvatarEditor
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing

/** «Изменить профиль»: те же поля и каркас, что у регистрации. Stateless — состояние в [EditProfileViewModel]. */
@Composable
fun EditProfileScreen(
    uiState: EditProfileUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSave: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRetryLoad: () -> Unit = {},
    onAvatarPicked: (String) -> Unit = {},
    onAvatarRemoved: () -> Unit = {},
    onAvatarRetry: () -> Unit = {}
) {
    AuthScaffold(
        title = R.string.profile_edit_title,
        subtitle = null,
        message = uiState.message,
        onMessageShown = onMessageShown,
        onRetry = onSave,
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
            return@AuthScaffold
        }
        if (uiState.loadFailed) {
            RetryMessage(stringResource(R.string.load_error), onRetryLoad)
            return@AuthScaffold
        }
        uiState.lastConfirmed?.let {
            Text(stringResource(R.string.profile_confirmed_fields, it.name, it.phone))
        }
        AvatarEditor(
            model = uiState.avatar,
            name = uiState.name,
            onPicked = onAvatarPicked,
            onRemoved = onAvatarRemoved,
            enabled = !uiState.isSaving,
            isWorking = uiState.isUpdatingAvatar
        )
        if (uiState.avatarUploadPending || uiState.avatarLoadFailed) {
            val error = if (uiState.avatarUploadPending) R.string.avatar_upload_failed else R.string.avatar_load_failed
            Text(stringResource(error))
            TextButton(onClick = onAvatarRetry, enabled = !uiState.isUpdatingAvatar && !uiState.isSaving) {
                Text(stringResource(R.string.action_retry))
            }
        }
        AuthField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = R.string.auth_name,
            error = uiState.nameError,
            enabled = !uiState.isSaving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )
        PhoneField(uiState.phone, onPhoneChange, uiState.phoneError, enabled = !uiState.isSaving)
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        SubmitButton(R.string.profile_save, uiState.isSaving, onSave, enabled = !uiState.isUpdatingAvatar)
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun EditProfileScreenPreview() {
    CoffeeRunTheme(dynamicColor = false) {
        EditProfileScreen(
            uiState = EditProfileUiState(name = "Денис", phone = "9990000000"),
            onNameChange = {}, onPhoneChange = {}, onSave = {}, onMessageShown = {}, onBack = {}
        )
    }
}
