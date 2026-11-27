package ru.coffeerun.data

import kotlinx.coroutines.flow.Flow
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.Recipe

interface RecipeRepository {
    fun observe(session: AuthSession): Flow<List<Recipe>>
    suspend fun find(id: String, session: AuthSession): Recipe?
    suspend fun refresh(session: AuthSession)
    suspend fun save(recipe: Recipe, session: AuthSession)
    /** Возвращает актуальный рецепт или null, если он уже удалён. */
    suspend fun rename(id: String, name: String, session: AuthSession): Recipe?
    suspend fun remove(id: String, session: AuthSession)
}
