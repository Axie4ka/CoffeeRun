package ru.coffeerun.data

import ru.coffeerun.StubApi
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import ru.coffeerun.data.session.SessionStateStore
import ru.coffeerun.data.network.CoffeeRunApi
import org.junit.Assert.assertTrue
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.coffeerun.data.network.UserDto
import ru.coffeerun.model.User

/** Профиль: загрузка — IOException при любой ошибке, сохранение — значение [ProfileSaveResult]. */
class NetworkProfileRepositoryTest {
    private val sessions = SessionStateStore("demo-token")
    private fun repo(api: CoffeeRunApi) = NetworkProfileRepository(api, sessions)
    private val denis = UserDto("Денис", "+79990000000")

    private class FakeApi(val answer: suspend () -> UserDto) : StubApi() {
        var sent: UserDto? = null
        override suspend fun getMe(): UserDto = answer()
        override suspend fun updateMe(user: UserDto): UserDto = answer().also { sent = user }
    }

    private fun http(code: Int): () -> UserDto = { throw HttpException(Response.error<UserDto>(code, "".toResponseBody())) }

    @Test
    fun lateGet_afterSameTokenLogin_isCancelled() = runTest {
        val reply = CompletableDeferred<Unit>()
        val repository = repo(FakeApi { withContext(NonCancellable) { reply.await() }; denis })
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repository.getMe() } }
        sessions.replaceToken("demo-token")
        reply.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
    }

    @Test
    fun lateSaveError_afterLogout_isCancelled() = runTest {
        val reply = CompletableDeferred<Unit>()
        val repository = repo(FakeApi {
            withContext(NonCancellable) { reply.await() }
            throw IOException("старый ответ")
        })
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repository.updateMe("Аня", "+79991112233") } }
        sessions.replaceToken(null)
        reply.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
    }

    @Test
    fun lateSuccessfulPut_cannotReplaceProfileAfterNewLogin() = runTest {
        val reply = CompletableDeferred<Unit>()
        val api = object : StubApi() {
            override suspend fun updateMe(user: UserDto) = withContext(NonCancellable) { reply.await(); user }
            override suspend fun getMe() = denis
        }
        val repository = repo(api)
        val old = sessions.currentSession()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repository.updateMe("Аня", "+79991112233") } }
        sessions.replaceToken("demo-token")
        repository.onSessionChanged(sessions.currentSession())
        val current = repository.getMe()
        reply.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(current, repository.confirmed(sessions.currentSession()))
        assertEquals(null, repository.confirmed(old))
    }

    @Test
    fun guest_doesNotSendPersonalRequest() = runTest {
        sessions.replaceToken(null)
        var called = false
        val repository = repo(FakeApi { called = true; denis })
        assertTrue(runCatching { repository.getMe() }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { repository.updateMe("Аня", "+79991112233") }.exceptionOrNull() is CancellationException)
        assertEquals(false, called)
    }

    @Test
    fun getMe_returnsUser() = runTest {
        assertEquals(User("Денис", "+79990000000"), repo(FakeApi { denis }).getMe())
    }

    @Test
    fun getMe_errors_areIOException() = runTest {
        for (answer in listOf(http(500), http(401), { throw SerializationException("нет поля") }, { throw IOException("нет сети") })) {
            val error = runCatching { repo(FakeApi(answer)).getMe() }.exceptionOrNull()
            assertEquals(IOException::class, error!!::class)
        }
    }

    @Test
    fun updateMe_success_sendsBody() = runTest {
        val api = FakeApi { UserDto("Денис К.", "+79990000001") }
        val result = repo(api).updateMe("Денис К.", "+79990000001")
        assertEquals(UserDto("Денис К.", "+79990000001"), api.sent)
        assertEquals(ProfileSaveResult.Success(User("Денис К.", "+79990000001")), result)
    }

    @Test
    fun updateMe_errors_mapToResults() = runTest {
        assertEquals(ProfileSaveResult.InvalidInput, repo(FakeApi(http(400))).updateMe("", "1"))
        assertEquals(ProfileSaveResult.OutcomeUnknown(null), repo(FakeApi(http(500))).updateMe("Аня", "+79991112233"))
        assertEquals(ProfileSaveResult.OutcomeUnknown(null), repo(FakeApi { throw IOException() }).updateMe("Аня", "+79991112233"))
    }

    @Test
    fun successfulPut_publishesCanonicalFieldsWithoutAnotherGet() = runTest {
        val updated = User("Анна", "+79990000001")
        val api = object : StubApi() {
            override suspend fun updateMe(user: UserDto) = UserDto(updated.name, updated.phone)
        }
        val repository = repo(api)
        assertEquals(ProfileSaveResult.Success(updated), repository.updateMe("Анна ", updated.phone))
        assertEquals(updated, repository.observeConfirmed(sessions.currentSession()).first())
        assertEquals(updated, repository.getForEditing())
    }

    @Test
    fun lostPutResponse_rechecksServerBeforeConfirmingTheUpdate() = runTest {
        val updated = User("Анна", "+79990000001")
        var remote = denis
        val api = object : StubApi() {
            override suspend fun updateMe(user: UserDto): UserDto {
                remote = user
                throw IOException("response lost")
            }
            override suspend fun getMe() = remote
        }
        val repository = repo(api)
        assertEquals(ProfileSaveResult.Success(updated), repository.updateMe(updated.name, updated.phone))
        assertEquals(updated, repository.confirmed(sessions.currentSession()))
    }
}
