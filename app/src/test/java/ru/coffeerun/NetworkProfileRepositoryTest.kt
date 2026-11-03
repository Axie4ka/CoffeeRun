package ru.coffeerun

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.coffeerun.data.NetworkProfileRepository
import ru.coffeerun.data.ProfileSaveResult
import ru.coffeerun.data.network.UserDto
import ru.coffeerun.model.User

/** Профиль: загрузка — IOException при любой ошибке, сохранение — значение [ProfileSaveResult]. */
class NetworkProfileRepositoryTest {
    private val denis = UserDto("Денис", "+79990000000")

    private class FakeApi(val answer: () -> UserDto) : StubApi() {
        var sent: UserDto? = null
        override suspend fun getMe(): UserDto = answer()
        override suspend fun updateMe(user: UserDto): UserDto = answer().also { sent = user }
    }

    private fun http(code: Int): () -> UserDto = { throw HttpException(Response.error<UserDto>(code, "".toResponseBody())) }

    @Test
    fun getMe_returnsUser() = runTest {
        assertEquals(User("Денис", "+79990000000"), NetworkProfileRepository(FakeApi { denis }).getMe())
    }

    @Test
    fun getMe_errors_areIOException() = runTest {
        for (answer in listOf(http(500), http(401), { throw SerializationException("нет поля") }, { throw IOException("нет сети") })) {
            val error = runCatching { NetworkProfileRepository(FakeApi(answer)).getMe() }.exceptionOrNull()
            assertEquals(IOException::class, error!!::class)
        }
    }

    @Test
    fun updateMe_success_sendsBody() = runTest {
        val api = FakeApi { UserDto("Денис К.", "+79990000001") }
        val result = NetworkProfileRepository(api).updateMe("Денис К.", "+79990000001")
        assertEquals(UserDto("Денис К.", "+79990000001"), api.sent)
        assertEquals(ProfileSaveResult.Success(User("Денис К.", "+79990000001")), result)
    }

    @Test
    fun updateMe_errors_mapToResults() = runTest {
        assertEquals(ProfileSaveResult.InvalidInput, NetworkProfileRepository(FakeApi(http(400))).updateMe("", "1"))
        assertEquals(ProfileSaveResult.Unavailable, NetworkProfileRepository(FakeApi(http(500))).updateMe("Аня", "+79991112233"))
        assertEquals(ProfileSaveResult.Unavailable, NetworkProfileRepository(FakeApi { throw IOException() }).updateMe("Аня", "+79991112233"))
    }
}
