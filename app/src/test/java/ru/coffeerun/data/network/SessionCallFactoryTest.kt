package ru.coffeerun.data.network

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.RequestSession

class SessionCallFactoryTest {
    private val factory = SessionCallFactory(OkHttpClient())
    private val request = Request.Builder().url("https://example.invalid/orders").build()

    @Test
    fun coroutineSession_isCapturedOnCall_andContextIsRestored() = runTest {
        val session = AuthSession(5, true)
        val call = withContext(RequestSession.current.asContextElement(session)) { factory.newCall(request) }
        assertEquals(session, call.request().tag(AuthSession::class.java))
        assertNull(factory.newCall(request).request().tag(AuthSession::class.java))
    }
}
