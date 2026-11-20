package ru.coffeerun.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

// Товары и цены приходят вместе. Один снимок не требует объединять версии разных таблиц.
@Entity(tableName = "menu_snapshot")
data class MenuSnapshotEntity(
    @PrimaryKey val language: String,
    val payload: String,
    val fetchedAtMillis: Long
)

@Dao
interface MenuSnapshotDao {
    @Query("SELECT * FROM menu_snapshot WHERE language = :language")
    suspend fun find(language: String): MenuSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(snapshot: MenuSnapshotEntity)
}
