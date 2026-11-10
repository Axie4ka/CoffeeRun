package ru.coffeerun.data.network

import okhttp3.Call
import okhttp3.Request
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.RequestSession

internal class SessionCallFactory(private val delegate: Call.Factory) : Call.Factory {
    override fun newCall(request: Request): Call {
        val session = request.tag(AuthSession::class.java) ?: RequestSession.current.get()
        val tagged = if (session == null) request else request.newBuilder().tag(AuthSession::class.java, session).build()
        return delegate.newCall(tagged)
    }
}
