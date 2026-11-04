package ru.coffeerun.data

import ru.coffeerun.model.User

/** Профиль без сервера — для UI-тестов и превью; начинается с демо-пользователя, как мок. */
class InMemoryProfileRepository(private var user: User = InMemoryAuthRepository.DEMO) : ProfileRepository {
    override suspend fun getMe(): User = user
    override suspend fun updateMe(name: String, phone: String): ProfileSaveResult =
        ProfileSaveResult.Success(User(name, phone).also { user = it })
}
