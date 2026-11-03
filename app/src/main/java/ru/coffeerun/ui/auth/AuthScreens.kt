package ru.coffeerun.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing

/** Вход: телефон и пароль. Stateless — состояние в [LoginViewModel]. */
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMessageShown: () -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    AuthScaffold(
        title = R.string.auth_login_title,
        subtitle = R.string.auth_login_subtitle,
        message = uiState.message,
        onMessageShown = onMessageShown,
        onRetry = onSubmit,
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) {
        PhoneField(uiState.phone, onPhoneChange, uiState.phoneError, enabled = !uiState.isLoading)
        PasswordField(uiState.password, onPasswordChange, uiState.passwordError, enabled = !uiState.isLoading, onDone = onSubmit)
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        SubmitButton(R.string.auth_login_button, uiState.isLoading, onSubmit)
        SwitchRow(R.string.auth_no_account, R.string.auth_register_button, onRegister)
    }
}

/** Регистрация: имя, телефон и пароль. Stateless — состояние в [RegisterViewModel]. */
@Composable
fun RegisterScreen(
    uiState: RegisterUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMessageShown: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    AuthScaffold(
        title = R.string.auth_register_title,
        subtitle = R.string.auth_register_subtitle,
        message = uiState.message,
        onMessageShown = onMessageShown,
        onRetry = onSubmit,
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) {
        AuthField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = R.string.auth_name,
            error = uiState.nameError,
            enabled = !uiState.isLoading,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        )
        PhoneField(uiState.phone, onPhoneChange, uiState.phoneError, enabled = !uiState.isLoading)
        // У пароля регистрации правило видно заранее, а не только после ошибки
        PasswordField(
            uiState.password, onPasswordChange, uiState.passwordError, enabled = !uiState.isLoading, onDone = onSubmit,
            hint = R.string.auth_password_short
        )
        Spacer(Modifier.size(MaterialTheme.spacing.small))
        SubmitButton(R.string.auth_register_button, uiState.isLoading, onSubmit)
        SwitchRow(R.string.auth_have_account, R.string.auth_login_button, onLogin)
    }
}

/**
 * Каркас экранов входа: панель «назад» сверху, форма по центру видимой области.
 * С клавиатурой область сжимается, а форма прокручивается — кнопка не прячется под клавиатурой.
 * [message] показывается снэкбаром; у «нет связи» есть действие «Повторить».
 */
@Composable
private fun AuthScaffold(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    @StringRes message: Int?,
    onMessageShown: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val retry = if (message == R.string.auth_unavailable) context.getString(R.string.action_retry) else null
        val result = snackbarHostState.showSnackbar(context.getString(message), actionLabel = retry)
        onMessageShown()
        if (result == SnackbarResult.ActionPerformed) onRetry()
    }
    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = MaterialTheme.spacing.large)
                    // Высота верхней панели: форма по центру экрана, а не области под панелью
                    .padding(bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large, Alignment.CenterVertically)
            ) {
                Text(
                    text = stringResource(subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                content()
            }
        }
    }
}

/** M3 Outlined text field: подпись, ошибка под полем красным, у ошибки — значок справа. */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int?,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    @StringRes hint: Int? = null,
    prefix: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val supporting = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(label)) },
        prefix = prefix,
        isError = error != null,
        supportingText = supporting?.let { { Text(stringResource(it)) } },
        trailingIcon = trailingIcon ?: if (error != null) {
            { Icon(painterResource(R.drawable.ic_error), contentDescription = null) }
        } else null,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true
    )
}

/** Телефон: префикс +7 не стирается, вводятся только 10 цифр, отображаются группами. */
@Composable
private fun PhoneField(digits: String, onChange: (String) -> Unit, @StringRes error: Int?, enabled: Boolean) {
    AuthField(
        value = digits,
        onValueChange = onChange,
        label = R.string.auth_phone,
        error = error,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        prefix = { Text(stringResource(R.string.auth_phone_prefix)) },
        visualTransformation = PhoneVisualTransformation
    )
}

/** Пароль скрыт точками; глаз справа показывает символы. Клавиатура не подсказывает и не запоминает ввод. */
@Composable
private fun PasswordField(
    password: String,
    onChange: (String) -> Unit,
    @StringRes error: Int?,
    enabled: Boolean,
    onDone: () -> Unit,
    @StringRes hint: Int? = null
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    AuthField(
        value = password,
        onValueChange = onChange,
        label = R.string.auth_password,
        error = error,
        enabled = enabled,
        hint = hint,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password)
                )
            }
        }
    )
}

/** M3 Filled button во всю ширину; во время запроса — индикатор вместо текста и без повторного нажатия. */
@Composable
private fun SubmitButton(@StringRes label: Int, isLoading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(label))
        }
    }
}

/** «Нет аккаунта? Зарегистрироваться» — переход между входом и регистрацией. */
@Composable
private fun SwitchRow(@StringRes question: Int, @StringRes action: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(question),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onClick) { Text(stringResource(action)) }
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun LoginScreenErrorPreview() {
    CoffeeRunTheme(dynamicColor = false) {
        LoginScreen(
            uiState = LoginUiState(phone = "9990000000", password = "secret", passwordError = R.string.auth_wrong_credentials),
            onPhoneChange = {}, onPasswordChange = {}, onSubmit = {}, onMessageShown = {}, onRegister = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun RegisterScreenPreview() {
    CoffeeRunTheme(dynamicColor = false) {
        RegisterScreen(
            uiState = RegisterUiState(name = "Аня", phone = "9990000000", phoneError = R.string.auth_phone_taken),
            onNameChange = {}, onPhoneChange = {}, onPasswordChange = {}, onSubmit = {}, onMessageShown = {}, onLogin = {}, onBack = {}
        )
    }
}
