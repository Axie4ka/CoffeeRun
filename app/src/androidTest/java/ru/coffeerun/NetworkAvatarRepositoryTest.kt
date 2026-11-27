package ru.coffeerun

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.network.dto.AvatarDto
import ru.coffeerun.data.repository.NetworkAvatarRepository
import ru.coffeerun.data.session.SessionStateStore

@RunWith(AndroidJUnit4::class)
class NetworkAvatarRepositoryTest {
    @Test
    fun confirmedJpegSurvivesOfflineRestartAndInvalidResponse(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val jpeg = instrumentation.context.assets.open("avatar.jpg").use { it.readBytes() }
        val target = instrumentation.targetContext
        val name = "avatar-test-${UUID.randomUUID()}"
        val files = File(target.cacheDir, name)
        val isolatedContext = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir() = files
        }
        val sessions = SessionStateStore().apply { replaceToken("token", "account:test") }
        val session = sessions.currentSession()
        var remote = AvatarDto(null)
        var offline = false
        val api = object : StubApi() {
            override suspend fun putAvatar(avatar: AvatarDto): AvatarDto {
                remote = avatar
                return avatar
            }

            override suspend fun getAvatar(): AvatarDto {
                if (offline) throw IOException("offline")
                return remote
            }

            override suspend fun deleteAvatar() {
                remote = AvatarDto(null)
            }
        }
        suspend fun withRepository(action: suspend (NetworkAvatarRepository) -> Unit) {
            val db = Room.databaseBuilder(target, CoffeeRunDatabase::class.java, name).build()
            try {
                RoomOwnerScope(db).reconcile(session.localOwner)
                action(NetworkAvatarRepository(api, sessions, db, isolatedContext))
            } finally {
                db.close()
            }
        }
        suspend fun assertCachedPhoto(repository: NetworkAvatarRepository): File {
            val photo = checkNotNull(repository.observe(session).first())
            assertArrayEquals(jpeg, photo.readBytes())
            val bitmap = checkNotNull(BitmapFactory.decodeFile(photo.path))
            assertEquals(16, bitmap.width)
            assertEquals(16, bitmap.height)
            bitmap.recycle()
            return photo
        }
        try {
            withRepository { repository ->
                repository.upload(session, jpeg)
                assertArrayEquals(jpeg, Base64.getDecoder().decode(remote.jpegBase64))
                repository.refresh(session)
                assertCachedPhoto(repository)
            }
            withRepository { repository ->
                offline = true
                assertTrue(runCatching { repository.refresh(session) }.exceptionOrNull() is IOException)
                val confirmed = assertCachedPhoto(repository)
                offline = false
                remote = AvatarDto("/9j/2Q==")
                assertTrue(runCatching { repository.refresh(session) }.exceptionOrNull() is IOException)
                assertEquals(confirmed, assertCachedPhoto(repository))
                repository.remove(session)
                assertNull(repository.observe(session).first())
            }
        } finally {
            target.deleteDatabase(name)
            files.deleteRecursively()
        }
    }
}
