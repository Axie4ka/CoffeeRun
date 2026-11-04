package ru.coffeerun.ui.auth

import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.R
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.AuthRepository
import ru.coffeerun.data.AuthResult
import ru.coffeerun.data.InMemoryAuthRepository

/** Вход и регистрация: проверка полей, ответы сервера и что переживает смерть процесса. */
class AuthViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val demoPhone = "9990000000"
    private val demoPassword = InMemoryAuthRepository.DEMO_PASSWORD

    /** Сервер недоступен на любой запрос. */
    private val offline = object : AuthRepository {
        override val isLoggedIn = flowOf(false)
        override suspend fun login(phone: String, password: String) = AuthResult.Unavailable
        override suspend fun register(name: String, phone: String, password: String) = AuthResult.Unavailable
        override suspend fun logout() = Unit
    }

    @Test
    fun login_errorsOnlyAfterSubmit_thenClearedByEditing() {
        val vm = LoginViewModel(InMemoryAuthRepository(), SavedStateHandle())
        vm.onPhoneChange("999")
        assertNull("до нажатия ошибок нет", vm.uiState.value.phoneError)
        vm.submit()
        assertEquals(R.string.auth_phone_invalid, vm.uiState.value.phoneError)
        assertEquals(R.string.auth_password_empty, vm.uiState.value.passwordError)
        vm.onPhoneChange(demoPhone)
        assertNull(vm.uiState.value.phoneError)
        assertFalse("с ошибками запрос не уходит", vm.uiState.value.isDone)
    }

    /** Как клавиатура: каждая нажатая цифра дописывается к тому, что уже в поле. */
    private fun LoginViewModel.typePhone(keys: String) = keys.forEach { onPhoneChange(uiState.value.phone + it) }

    @Test
    fun login_phonePasteAndTyping_dropCountryCode() {
        val vm = LoginViewModel(InMemoryAuthRepository(), SavedStateHandle())
        vm.onPhoneChange("+7 (999) 000-00-00")
        assertEquals("вставили номер с +7", demoPhone, vm.uiState.value.phone)
        vm.onPhoneChange("")
        vm.onPhoneChange("8 999 000 00 00")
        assertEquals("вставили номер с 8", demoPhone, vm.uiState.value.phone)
        vm.onPhoneChange("")
        vm.typePhone("89990000000")
        assertEquals("набрали с 8 по одной цифре", demoPhone, vm.uiState.value.phone)
        vm.onPhoneChange("")
        vm.typePhone("79990000000")
        assertEquals("набрали с 7 по одной цифре", demoPhone, vm.uiState.value.phone)
        vm.typePhone("5")
        assertEquals("лишняя цифра в конце отбрасывается", demoPhone, vm.uiState.value.phone)
    }

    @Test
    fun login_phoneMustBeMobile() {
        val vm = LoginViewModel(InMemoryAuthRepository(), SavedStateHandle())
        vm.typePhone("5551234567")
        vm.onPasswordChange(demoPassword)
        vm.submit()
        assertEquals("городской или неверный номер не уходит на сервер", R.string.auth_phone_invalid, vm.uiState.value.phoneError)
        assertFalse(vm.uiState.value.isDone)
    }

    @Test
    fun login_wrongPassword_errorUnderPassword_clearedWhenTyping() {
        val vm = LoginViewModel(InMemoryAuthRepository(), SavedStateHandle())
        vm.onPhoneChange(demoPhone)
        vm.onPasswordChange("wrong12")
        vm.submit()
        assertEquals(R.string.auth_wrong_credentials, vm.uiState.value.passwordError)
        vm.onPasswordChange(demoPassword)
        assertNull(vm.uiState.value.passwordError)
        vm.submit()
        assertTrue(vm.uiState.value.isDone)
    }

    @Test
    fun login_offline_snackbarAndInputKept() {
        val vm = LoginViewModel(offline, SavedStateHandle())
        vm.onPhoneChange(demoPhone)
        vm.onPasswordChange(demoPassword)
        vm.submit()
        val state = vm.uiState.value
        assertEquals(R.string.auth_unavailable, state.message)
        assertEquals(demoPhone, state.phone)
        assertEquals(demoPassword, state.password)
        assertFalse(state.isLoading)
        vm.onMessageShown()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun login_phoneSurvivesProcessDeath_passwordDoesNot() {
        val saved = SavedStateHandle()
        LoginViewModel(InMemoryAuthRepository(), saved).apply {
            onPhoneChange(demoPhone)
            onPasswordChange(demoPassword)
        }
        val restored = LoginViewModel(InMemoryAuthRepository(), saved).uiState.value
        assertEquals(demoPhone, restored.phone)
        assertEquals("", restored.password)
        assertFalse("пароль не попадает в SavedStateHandle", saved.keys().any { saved.get<Any>(it) == demoPassword })
    }

    @Test
    fun register_validation() {
        val vm = RegisterViewModel(InMemoryAuthRepository(), SavedStateHandle())
        vm.onNameChange("  ")
        vm.onPhoneChange("999000")
        vm.onPasswordChange("123")
        vm.submit()
        val s = vm.uiState.value
        assertEquals(R.string.auth_name_empty, s.nameError)
        assertEquals(R.string.auth_phone_invalid, s.phoneError)
        assertEquals(R.string.auth_password_short, s.passwordError)
    }

    @Test
    fun register_phoneTaken_errorUnderPhone() {
        val vm = RegisterViewModel(InMemoryAuthRepository(), SavedStateHandle())
        vm.onNameChange("Аня")
        vm.onPhoneChange(demoPhone)
        vm.onPasswordChange("secret1")
        vm.submit()
        assertEquals(R.string.auth_phone_taken, vm.uiState.value.phoneError)
        vm.onPhoneChange("9991112233")
        assertNull(vm.uiState.value.phoneError)
        vm.submit()
        assertTrue(vm.uiState.value.isDone)
    }

    @Test
    fun register_nameAndPhoneSurviveProcessDeath() {
        val saved = SavedStateHandle()
        RegisterViewModel(InMemoryAuthRepository(), saved).apply {
            onNameChange("Аня")
            onPhoneChange("9991112233")
            onPasswordChange("secret1")
        }
        val restored = RegisterViewModel(InMemoryAuthRepository(), saved).uiState.value
        assertEquals("Аня", restored.name)
        assertEquals("9991112233", restored.phone)
        assertEquals("", restored.password)
    }

    @Test
    fun phone_formatAndCursorMapping() {
        assertEquals("999 000-00-00", formatPhone(demoPhone))
        assertEquals("999 0", formatPhone("9990"))
        assertEquals("+7 999 000-00-00", displayPhone("+79990000000"))
        assertEquals("номер не по контракту — как есть", "12345", displayPhone("12345"))
        val mapping = PhoneVisualTransformation.filter(AnnotatedString(demoPhone)).offsetMapping
        // Позиция в цифрах ↔ позиция в «999 000-00-00»
        for ((digits, shown) in listOf(0 to 0, 3 to 3, 4 to 5, 6 to 7, 7 to 9, 8 to 10, 9 to 12, 10 to 13)) {
            assertEquals("цифра $digits", shown, mapping.originalToTransformed(digits))
            assertEquals("символ $shown", digits, mapping.transformedToOriginal(shown))
        }
    }
}
