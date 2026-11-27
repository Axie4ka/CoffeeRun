package ru.coffeerun.data

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.coffeerun.data.session.AuthSession

/** Keeps a prepared photo available for retry during the session that created it. */
class AvatarUploadDraft {
    private val pending = AtomicReference<Pair<AuthSession, ByteArray>?>(null)

    fun get(session: AuthSession): ByteArray? = pending.get()?.takeIf { it.first == session }?.second
    fun save(session: AuthSession, jpeg: ByteArray) { pending.set(session to jpeg) }
    fun clear(session: AuthSession) { pending.updateAndGet { it?.takeUnless { draft -> draft.first == session } } }
    fun retain(session: AuthSession) { pending.updateAndGet { it?.takeIf { draft -> draft.first == session } } }
}

interface AvatarRepository {
    suspend fun prepare(uri: String): ByteArray
    fun observe(session: AuthSession): Flow<File?>
    suspend fun refresh(session: AuthSession)
    suspend fun upload(session: AuthSession, jpeg: ByteArray)
    suspend fun remove(session: AuthSession)

    object Unavailable : AvatarRepository {
        override suspend fun prepare(uri: String): ByteArray = throw IOException()
        override fun observe(session: AuthSession): Flow<File?> = flowOf(null)
        override suspend fun refresh(session: AuthSession) = Unit
        override suspend fun upload(session: AuthSession, jpeg: ByteArray): Unit = throw IOException()
        override suspend fun remove(session: AuthSession): Unit = throw IOException()
    }
}
