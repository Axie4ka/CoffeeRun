package ru.coffeerun.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.SessionStateStore

class SessionStateStoreTest {
    @Test fun restoredOwnerFromAnotherProcess_isNotCurrentSession() = runTest {
        val saved = SessionStateStore("same-token").currentSession()
        val restarted = SessionStateStore("same-token")
        val error = runCatching { restarted.withSession(saved) { } }.exceptionOrNull()
        assertTrue("Счётчик нового процесса не должен подтвердить старого владельца навигации", error is SessionChangedException)
    }
}
