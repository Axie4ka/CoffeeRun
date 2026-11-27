package ru.coffeerun

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.local.room.CoffeeRunDatabase
import ru.coffeerun.data.local.room.RoomOwnerScope
import ru.coffeerun.data.local.room.RoomRecipeRepository
import ru.coffeerun.data.network.dto.RenameRecipeRequest
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Recipe

@RunWith(AndroidJUnit4::class)
class RoomRecipeRepositoryTest {
    @Test
    fun saveEditDeleteAndKeepConfirmedRecipesAfterRestartOnlyForTheirOwner(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "recipes-test-${UUID.randomUUID()}.db"
        val firstOwner = AuthSession(1, true, "account:first")
        val secondOwner = AuthSession(2, true, "account:second")
        val sessions = object : SessionProvider {
            override val sessions = MutableStateFlow(firstOwner)
            override suspend fun currentSession() = sessions.value
            override fun <T> withSession(session: AuthSession, action: () -> T): T {
                if (session != sessions.value) throw SessionChangedException()
                return action()
            }
        }
        val remote = mutableMapOf<String, Recipe>()
        var offline = false
        val api = object : StubApi() {
            override suspend fun getRecipes(): List<Recipe> {
                if (offline) throw IOException("offline")
                return remote.values.toList()
            }
            override suspend fun saveRecipe(id: String, recipe: Recipe) {
                if (offline) throw IOException("offline")
                remote[id] = recipe
            }
            override suspend fun removeRecipe(id: String) {
                if (offline) throw IOException("offline")
                remote.remove(id)
            }
            override suspend fun renameRecipe(id: String, request: RenameRecipeRequest): Recipe {
                if (offline) throw IOException("offline")
                val current = remote[id] ?: throw HttpException(Response.error<Recipe>(404, "".toResponseBody()))
                return current.copy(name = request.name).also { remote[id] = it }
            }
        }
        val product = DemoMenu.CAPPUCCINO
        val recipe = Recipe(
            id = UUID.randomUUID().toString(),
            name = "Morning coffee",
            productId = product.id,
            options = checkNotNull(product.defaultOptions()).copy(
                size = DrinkSize.LARGE, milk = MilkType.OAT, isIced = true,
                extras = setOf(Extra.VANILLA), note = "Less foam"
            )
        )
        val newOptions = checkNotNull(recipe.options).copy(milk = MilkType.ALMOND)
        val edited = recipe.copy(name = "Afternoon coffee", options = newOptions)
        suspend fun withRepository(
            session: AuthSession,
            action: suspend (RoomRecipeRepository) -> Unit
        ) {
            val db = Room.databaseBuilder(context, CoffeeRunDatabase::class.java, databaseName).build()
            try {
                RoomOwnerScope(db).reconcile(session.localOwner)
                sessions.sessions.value = session
                action(RoomRecipeRepository(api, sessions, db))
            } finally {
                db.close()
            }
        }
        try {
            withRepository(firstOwner) { repository ->
                repository.save(recipe, firstOwner)
                assertEquals(recipe, remote[recipe.id])
                remote[recipe.id] = recipe.copy(options = newOptions)
                assertEquals(edited, repository.rename(recipe.id, edited.name, firstOwner))
                assertEquals(listOf(edited), repository.observe(firstOwner).first())
            }
            offline = true
            withRepository(firstOwner) { repository ->
                assertTrue(runCatching { repository.refresh(firstOwner) }.exceptionOrNull() is IOException)
                assertEquals(edited, repository.find(edited.id, firstOwner))
                assertTrue(runCatching { repository.remove(edited.id, firstOwner) }.exceptionOrNull() is IOException)
                assertTrue(runCatching { repository.rename(edited.id, "Late coffee", firstOwner) }.exceptionOrNull() is IOException)
                assertEquals(edited, repository.find(edited.id, firstOwner))
                offline = false
                remote.remove(edited.id)
                assertNull(repository.rename(edited.id, "Late coffee", firstOwner))
                assertNull(repository.find(edited.id, firstOwner))
                assertTrue(remote.isEmpty())
                repository.save(edited, firstOwner)
                repository.remove(edited.id, firstOwner)
                assertTrue(repository.observe(firstOwner).first().isEmpty())
                assertTrue(remote.isEmpty())
                repository.save(edited, firstOwner)
            }
            withRepository(secondOwner) { repository ->
                assertTrue(repository.observe(secondOwner).first().isEmpty())
                assertTrue(runCatching { repository.find(edited.id, firstOwner) }.exceptionOrNull() is SessionChangedException)
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
