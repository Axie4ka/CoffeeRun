package ru.coffeerun.ui.profile

import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.R
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.coffeerun.data.InMemoryProfileRepository
import ru.coffeerun.data.ProfileRepository
import ru.coffeerun.data.ProfileSaveResult
import ru.coffeerun.model.User

/** «Изменить профиль»: начальные значения, проверки, ответы сервера, смерть процесса. */
class EditProfileViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun vm(repo: ProfileRepository = InMemoryProfileRepository(), saved: SavedStateHandle = SavedStateHandle()) =
        EditProfileViewModel(repo, saved)

    /** Сервер отвечает на сохранение всегда одно и то же. */
    private fun answering(result: ProfileSaveResult) = object : ProfileRepository {
        override suspend fun getMe() = User("Денис", "+79990000000")
        override suspend fun updateMe(name: String, phone: String) = result
    }

    @Test
    fun startsWithCurrentProfile() {
        val state = vm().uiState.value
        assertEquals("Денис", state.name)
        assertEquals("9990000000", state.phone)
    }

    @Test
    fun validation_blocksSave() {
        val vm = vm()
        vm.onNameChange("  ")
        vm.onPhoneChange("")
        vm.onPhoneChange("5551234567")
        vm.save()
        assertEquals(R.string.auth_name_empty, vm.uiState.value.nameError)
        assertEquals(R.string.auth_phone_invalid, vm.uiState.value.phoneError)
        assertFalse(vm.uiState.value.isDone)
    }

    @Test
    fun save_sendsTrimmedNameAndFullPhone() = runTest {
        val repo = InMemoryProfileRepository()
        val vm = vm(repo)
        vm.onNameChange("  Денис К. ")
        vm.save()
        assertTrue(vm.uiState.value.isDone)
        assertEquals(User("Денис К.", "+79990000000"), repo.getMe())
    }

    @Test
    fun serverErrors_becomeMessages_inputKept() {
        val invalid = vm(answering(ProfileSaveResult.InvalidInput)).apply { save() }
        assertEquals(R.string.auth_invalid, invalid.uiState.value.message)
        val offline = vm(answering(ProfileSaveResult.Unavailable)).apply { onNameChange("Аня"); save() }
        assertEquals(R.string.auth_unavailable, offline.uiState.value.message)
        assertEquals("Аня", offline.uiState.value.name)
        assertFalse(offline.uiState.value.isSaving)
        offline.onMessageShown()
        assertNull(offline.uiState.value.message)
    }

    @Test
    fun unknownSave_showsConfirmedFieldsAndKeepsDraft() {
        val confirmed = User("Денис", "+79990000000")
        val vm = vm(answering(ProfileSaveResult.OutcomeUnknown(confirmed)))
        vm.onNameChange("Денис К.")
        vm.save()
        assertEquals("Денис К.", vm.uiState.value.name)
        assertEquals(confirmed, vm.uiState.value.lastConfirmed)
        assertEquals(R.string.profile_save_outcome_unknown, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isDone)
    }

    @Test
    fun cancelledSave_clearsBusy_withoutSuccess() = runTest {
        val reply = CompletableDeferred<Unit>()
        val repo = object : ProfileRepository {
            override suspend fun getMe() = User("Денис", "+79990000000")
            override suspend fun updateMe(name: String, phone: String): ProfileSaveResult {
                reply.await()
                throw CancellationException("смена входа")
            }
        }
        val vm = vm(repo)
        vm.save()
        assertTrue(vm.uiState.value.isSaving)
        reply.complete(Unit)
        assertFalse(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.isDone)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun edits_surviveProcessDeath() {
        val saved = SavedStateHandle()
        vm(saved = saved).onNameChange("Аня")
        assertEquals("Аня", vm(saved = saved).uiState.value.name)
    }
}
