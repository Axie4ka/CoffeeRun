package ru.coffeerun.data.local.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "favorites_snapshot")
data class SavedFavorites(@PrimaryKey val owner: String, val payload: String)

@Dao
interface FavoritesSnapshotDao {
    @Query("SELECT * FROM favorites_snapshot WHERE owner = :owner")
    suspend fun find(owner: String): SavedFavorites?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(snapshot: SavedFavorites)

    @Query("DELETE FROM favorites_snapshot WHERE owner != :owner")
    suspend fun clearOtherOwners(owner: String)
}
