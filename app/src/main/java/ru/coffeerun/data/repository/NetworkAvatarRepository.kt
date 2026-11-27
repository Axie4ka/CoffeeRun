package ru.coffeerun.data.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.AvatarRepository
import ru.coffeerun.data.local.room.AvatarCacheEntity
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.avatarDirectory
import ru.coffeerun.data.local.room.withOwner
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.dto.AvatarDto
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession

class NetworkAvatarRepository(
    private val api: CoffeeRunApi,
    private val sessions: SessionProvider,
    private val database: CoffeeRunDatabase,
    context: Context
) : AvatarRepository {
    private val context = context.applicationContext
    private val directory = avatarDirectory(this.context)
    private val requests = Mutex()

    override suspend fun prepare(uri: String): ByteArray = withContext(Dispatchers.IO) {
        val source = Uri.parse(uri)
        if (source.scheme != "content") throw IOException("Invalid photo URI")
        val request = ImageRequest.Builder(context)
            .data(source)
            .size(MAX_SIDE)
            .scale(Scale.FIT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val image = (result as? SuccessResult)?.image
            ?: throw IOException("Cannot read photo", (result as? ErrorResult)?.throwable)
        if (image.width <= 0 || image.height <= 0) throw IOException("Invalid photo dimensions")
        val original = image.toBitmap()
        val side = minOf(original.width, original.height)
        val square = Bitmap.createBitmap(original, (original.width - side) / 2, (original.height - side) / 2, side, side)
        val bitmap = if (side <= MAX_SIDE) square else Bitmap.createScaledBitmap(square, MAX_SIDE, MAX_SIDE, true)
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output) || output.size() > MAX_JPEG_BYTES) {
            throw IOException("Cannot encode avatar")
        }
        output.toByteArray()
    }

    override fun observe(session: AuthSession): Flow<File?> {
        val owner = session.localOwner ?: return flowOf(null)
        return database.avatars().observe(owner).map { name ->
            val photo = withContext(Dispatchers.IO) { name?.let { directory.resolve(it).takeIf(File::isFile) } }
            sessions.withSession(session) { photo }
        }.catch { error ->
            if (error is SQLiteException) emit(null) else throw error
        }
    }

    override suspend fun refresh(session: AuthSession) = sync(session) { api.getAvatar() }

    override suspend fun upload(session: AuthSession, jpeg: ByteArray) = sync(session) {
        val payload = withContext(Dispatchers.IO) {
            validate(jpeg)
            Base64.getEncoder().encodeToString(jpeg)
        }
        val response = api.putAvatar(AvatarDto(payload))
        if (response.jpegBase64 == null) throw IOException("Missing uploaded avatar")
        response
    }

    override suspend fun remove(session: AuthSession) = sync(session) {
        api.deleteAvatar()
        AvatarDto(null)
    }

    private suspend fun sync(session: AuthSession, request: suspend () -> AvatarDto) {
        if (session.localOwner == null) throw IOException("Avatar requires an account")
        try {
            sessions.runInSession(session) {
                requests.withLock {
                    val payload = request().jpegBase64
                    val jpeg = withContext(Dispatchers.IO) { payload?.let(::decode) }
                    val savedName = database.withOwner(sessions, session) { owner ->
                        val filename = jpeg?.let(::writePhoto)
                        if (filename == null) database.avatars().remove(owner)
                        else database.avatars().set(AvatarCacheEntity(owner, filename))
                        filename
                    }
                    database.withOwner(sessions, session) { owner ->
                        if (database.avatars().filename(owner) == savedName) {
                            directory.listFiles()?.filter { it.name != savedName }?.forEach(File::delete)
                        }
                    }
                }
            }
        } catch (error: HttpException) {
            throw IOException("HTTP ${error.code()}", error)
        } catch (error: SerializationException) {
            throw IOException("Invalid avatar response", error)
        } catch (error: SQLiteException) {
            throw IOException("Cannot cache avatar", error)
        }
    }

    private fun writePhoto(jpeg: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(jpeg).joinToString("") { "%02x".format(it) }
        val filename = "$hash.jpg"
        val file = directory.resolve(filename)
        if (file.isFile) return filename
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(jpeg)
            atomicFile.finishWrite(output)
        } catch (error: IOException) {
            atomicFile.failWrite(output)
            throw error
        }
        if (!file.isFile) throw IOException("Cannot save avatar file")
        return filename
    }

    private fun decode(payload: String): ByteArray {
        if (payload.length > ((MAX_JPEG_BYTES + 2) / 3) * 4) throw IOException("Avatar is too large")
        val jpeg = try {
            Base64.getDecoder().decode(payload)
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid avatar encoding", error)
        }
        validate(jpeg)
        return jpeg
    }

    private fun validate(jpeg: ByteArray) {
        if (jpeg.size > MAX_JPEG_BYTES) throw IOException("Avatar is too large")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outMimeType != "image/jpeg" || bounds.outWidth !in 1..MAX_SIDE || bounds.outHeight !in 1..MAX_SIDE) {
            throw IOException("Invalid avatar image")
        }
    }

    private companion object {
        const val MAX_SIDE = 512
        const val MAX_JPEG_BYTES = 512 * 1024
    }
}
