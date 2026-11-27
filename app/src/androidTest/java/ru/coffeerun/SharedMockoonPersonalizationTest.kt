package ru.coffeerun

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.local.datastore.TokenStore
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.local.room.RoomRecipeRepository
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.createCoffeeRunApi
import ru.coffeerun.data.network.dto.LoginRequest
import ru.coffeerun.data.repository.NetworkAvatarRepository
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.Recipe

@RunWith(AndroidJUnit4::class)
class SharedMockoonPersonalizationTest {
    private class Client(
        val session: AuthSession,
        val api: CoffeeRunApi,
        val recipes: RoomRecipeRepository,
        val avatars: NetworkAvatarRepository
    )

    private suspend fun withClient(baseUrl: String, run: suspend (Client) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "mockoon-client-${UUID.randomUUID()}")
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir() = directory
        }
        val db = Room.inMemoryDatabaseBuilder(target, CoffeeRunDatabase::class.java).build()
        val job = SupervisorJob()
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
                File(directory, "auth.preferences_pb")
            }
            val tokens = TokenStore(preferences, RoomOwnerScope(db, context)::reconcile)
            val api = createCoffeeRunApi(baseUrl, { "en" }, tokens)
            val auth = api.login(LoginRequest("+79990000000", "coffee123"))
            tokens.save(auth.token, checkNotNull(auth.accountId))
            run(Client(tokens.currentSession(), api, RoomRecipeRepository(api, tokens, db),
                NetworkAvatarRepository(api, tokens, db, context)))
        } finally {
            job.cancelAndJoin()
            db.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun twoClientsShareRecipesAndAvatar(): Unit = runBlocking {
        val baseUrl = InstrumentationRegistry.getArguments().getString("mockoonBaseUrl")
        assumeTrue("Pass mockoonBaseUrl to test a running Mockoon", !baseUrl.isNullOrBlank())
        val url = requireNotNull(baseUrl)
        val jpeg = InstrumentationRegistry.getInstrumentation().context.assets.open("avatar.jpg").use { it.readBytes() }
        withClient(url) { first ->
            withClient(url) { second ->
                assertEquals(first.session.localOwner, second.session.localOwner)
                val previousAvatar = first.api.getAvatar()
                val product = DemoMenu.CAPPUCCINO
                val recipe = Recipe(UUID.randomUUID().toString(), "Morning", product.id,
                    checkNotNull(product.defaultOptions()).copy(note = "No sugar"))
                try {
                    first.recipes.save(recipe, first.session)
                    assertNull(second.recipes.find(recipe.id, second.session))
                    second.recipes.refresh(second.session)
                    assertEquals(recipe, second.recipes.find(recipe.id, second.session))

                    val updated = recipe.copy(options = checkNotNull(recipe.options).copy(note = "Extra hot"))
                    first.recipes.save(updated, first.session)
                    val renamed = updated.copy(name = "Weekend")
                    assertEquals(renamed, second.recipes.rename(recipe.id, renamed.name, second.session))
                    first.recipes.refresh(first.session)
                    assertEquals(renamed, first.recipes.find(recipe.id, first.session))
                    first.recipes.remove(recipe.id, first.session)
                    assertNull(second.recipes.rename(recipe.id, "Late edit", second.session))
                    second.recipes.refresh(second.session)
                    assertNull(second.recipes.find(recipe.id, second.session))

                    first.avatars.upload(first.session, jpeg)
                    assertNull(second.avatars.observe(second.session).first())
                    second.avatars.refresh(second.session)
                    val file = checkNotNull(second.avatars.observe(second.session).first())
                    assertArrayEquals(jpeg, file.readBytes())
                    val bitmap = checkNotNull(BitmapFactory.decodeFile(file.path))
                    assertEquals(16, bitmap.width)
                    assertEquals(16, bitmap.height)
                    bitmap.recycle()
                    second.avatars.remove(second.session)
                    first.avatars.refresh(first.session)
                    assertNull(first.avatars.observe(first.session).first())
                } finally {
                    try {
                        first.api.removeRecipe(recipe.id)
                    } finally {
                        if (previousAvatar.jpegBase64 == null) first.api.deleteAvatar()
                        else first.api.putAvatar(previousAvatar)
                    }
                }
            }
        }
    }
}
