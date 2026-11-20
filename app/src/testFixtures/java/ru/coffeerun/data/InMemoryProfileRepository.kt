package ru.coffeerun.data

import ru.coffeerun.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import ru.coffeerun.data.session.AuthSession

/** Профиль без сервера — для UI-тестов и превью; начинается с демо-пользователя, как мок. */
class InMemoryProfileRepository(user: User = InMemoryAuthRepository.DEMO) : ProfileRepository {
    private val profile = MutableStateFlow(user)
    override fun observeConfirmed(session: AuthSession) = profile
    override suspend fun getMe(): User = profile.value
    override suspend fun updateMe(name: String, phone: String): ProfileSaveResult =
        ProfileSaveResult.Success(User(name, phone).also { profile.value = it })
}
