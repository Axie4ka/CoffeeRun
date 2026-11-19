package ru.coffeerun.data.network

import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.Invocation
import retrofit2.http.POST
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.RequestSession

internal class SessionCallFactory(private val delegate: Call.Factory) : Call.Factory {
    override fun newCall(request: Request): Call {
        val session = request.tag(AuthSession::class.java) ?: RequestSession.current.get()
        val builder = request.newBuilder().tag(AuthSession::class.java, session)
        val endpoint = request.tag(Invocation::class.java)?.method()?.getAnnotation(POST::class.java)?.value
        if (endpoint == "orders") {
            val body = requireNotNull(request.body)
            // У сервера нет ключа идемпотентности. Даже ответ 503 мог прийти после записи заказа.
            builder.method(request.method, object : RequestBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
            })
        }
        return delegate.newCall(builder.build())
    }
}
