package ru.coffeerun.data.local.room

import android.database.sqlite.SQLiteException
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.RecipeRepository
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.data.network.dto.RenameRecipeRequest
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.data.session.runInSession
import ru.coffeerun.model.MAX_RECIPE_NAME_LENGTH
import ru.coffeerun.model.Recipe

/** Room хранит подтверждённые сервером рецепты и отдаёт их без сети. */
class RoomRecipeRepository(
    private val api: CoffeeRunApi,
    private val sessions: SessionProvider,
    private val db: CoffeeRunDatabase
) : RecipeRepository {
    private val dao = db.savedRecipes()
    private val mutex = Mutex()

    override fun observe(session: AuthSession) = session.localOwner?.takeIf { session.isLoggedIn }?.let { owner ->
        dao.observe(owner).map { rows ->
            try {
                sessions.withSession(session) {
                    rows.map(SavedRecipe::toRecipe).sortedWith(compareBy(Recipe::name, Recipe::id))
                }
            } catch (_: SessionChangedException) {
                emptyList()
            }
        }.catch { error -> throw asIOException(error) }
    } ?: flowOf(emptyList())

    override suspend fun find(id: String, session: AuthSession) =
        local(session) { owner -> dao.find(owner, id)?.toRecipe() }

    override suspend fun refresh(session: AuthSession) = sync(session) {
        val recipes = api.getRecipes()
        if (recipes.any { !it.isValid() } || recipes.distinctBy(Recipe::id).size != recipes.size) {
            throw IOException("Некорректные рецепты")
        }
        local(session) { owner ->
            dao.clear(owner)
            dao.saveAll(recipes.map { it.toSaved(owner) })
        }
    }

    override suspend fun save(recipe: Recipe, session: AuthSession) = sync(session) {
        if (!recipe.isValid()) throw IOException("Некорректный рецепт")
        api.saveRecipe(recipe.id, recipe)
        local(session) { owner -> dao.save(recipe.toSaved(owner)) }
    }

    override suspend fun rename(id: String, name: String, session: AuthSession): Recipe? = sync(session) {
        if (!name.isValidRecipeName()) throw IOException("Некорректное название рецепта")
        val confirmed = try {
            api.renameRecipe(id, RenameRecipeRequest(name))
        } catch (error: HttpException) {
            if (error.code() != 404) throw error
            local(session) { owner -> dao.remove(owner, id) }
            return@sync null
        }
        if (confirmed.id != id || !confirmed.isValid()) throw IOException("Некорректный рецепт")
        local(session) { owner -> dao.save(confirmed.toSaved(owner)) }
        confirmed
    }

    override suspend fun remove(id: String, session: AuthSession) = sync(session) {
        api.removeRecipe(id)
        local(session) { owner -> dao.remove(owner, id) }
    }

    private suspend fun <T> local(session: AuthSession, action: suspend (String) -> T): T = try {
        if (!session.isLoggedIn || session.localOwner == null) throw SessionChangedException()
        db.withOwner(sessions, session, action)
    } catch (error: Exception) {
        throw asIOException(error)
    }

    private suspend fun <T> sync(session: AuthSession, action: suspend () -> T): T = sessions.runInSession(session) {
        try {
            if (session.localOwner == null) throw SessionChangedException()
            mutex.withLock { action() }
        } catch (error: HttpException) {
            throw IOException("HTTP ${error.code()}", error)
        } catch (error: SerializationException) {
            throw IOException("Некорректный ответ сервера", error)
        }
    }
}

private fun SavedRecipe.toRecipe() = CoffeeRunJson.decodeFromString<Recipe>(payload)
private fun Recipe.toSaved(owner: String) = SavedRecipe(owner, id, CoffeeRunJson.encodeToString(this))
private fun Recipe.isValid() = id.isNotBlank() && productId.isNotBlank() && name.isValidRecipeName()
private fun String.isValidRecipeName() = this == trim() && length in 1..MAX_RECIPE_NAME_LENGTH

private fun asIOException(error: Throwable) = when (error) {
    is SQLiteException, is SerializationException -> IOException("Не удалось прочитать или сохранить рецепты", error)
    else -> error
}
