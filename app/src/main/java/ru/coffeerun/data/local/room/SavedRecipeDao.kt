package ru.coffeerun.data.local.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_recipes", primaryKeys = ["owner", "id"])
data class SavedRecipe(val owner: String, val id: String, val payload: String)

@Dao
interface SavedRecipeDao {
    @Query("SELECT * FROM saved_recipes WHERE owner = :owner ORDER BY id")
    fun observe(owner: String): Flow<List<SavedRecipe>>

    @Query("SELECT * FROM saved_recipes WHERE owner = :owner AND id = :id")
    suspend fun find(owner: String, id: String): SavedRecipe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(recipe: SavedRecipe)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(recipes: List<SavedRecipe>)

    @Query("DELETE FROM saved_recipes WHERE owner = :owner AND id = :id")
    suspend fun remove(owner: String, id: String)

    @Query("DELETE FROM saved_recipes WHERE owner = :owner")
    suspend fun clear(owner: String)

    @Query("DELETE FROM saved_recipes WHERE owner != :owner")
    suspend fun clearOtherOwners(owner: String)
}
