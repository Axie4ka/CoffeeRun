package ru.coffeerun.ui.profile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import ru.coffeerun.R
import ru.coffeerun.ui.auth.AuthField
import ru.coffeerun.ui.auth.AuthScaffold
import ru.coffeerun.ui.auth.PhoneField
import ru.coffeerun.ui.auth.SubmitButton
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
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
        SubmitButton(R.string.profile_save, uiState.isSaving, onSave)
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
