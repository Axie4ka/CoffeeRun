package ru.coffeerun.data.local.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.io.File
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "avatar_cache")
data class AvatarCacheEntity(@PrimaryKey val owner: String, val filename: String)

@Dao
interface AvatarCacheDao {
    @Query("SELECT filename FROM avatar_cache WHERE owner = :owner")
    fun observe(owner: String): Flow<String?>

    @Query("SELECT filename FROM avatar_cache WHERE owner = :owner")
    suspend fun filename(owner: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(avatar: AvatarCacheEntity)

    @Query("DELETE FROM avatar_cache WHERE owner = :owner")
    suspend fun remove(owner: String)

    @Query("DELETE FROM avatar_cache WHERE owner != :owner")
    suspend fun clearOtherOwners(owner: String)
}

internal fun avatarDirectory(context: Context) = File(context.noBackupFilesDir, "avatars")
