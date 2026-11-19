package ru.coffeerun.data.local

import android.database.sqlite.SQLiteException
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import java.io.IOException

internal const val GUEST_OWNER = "guest"

@Entity(tableName = "local_owner")
data class LocalOwnerEntity(val owner: String, @PrimaryKey val id: Int = 1)

@Dao
interface LocalOwnerDao {
    @Query("SELECT owner FROM local_owner WHERE id = 1")
    suspend fun current(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(owner: LocalOwnerEntity)
}

/** DataStore задаёт владельца, маркер SQLite хранит последний применённый переход. */
class RoomOwnerScope(private val db: CoffeeRunDatabase) {
    suspend fun reconcile(localOwner: String?) {
        val target = localOwner ?: GUEST_OWNER
        try {
            db.withTransaction {
                val previous = db.localOwner().current()
                if (previous == target) return@withTransaction
                if (previous == GUEST_OWNER && localOwner != null) db.cart().transferGuest(target)
                db.cart().clearOtherOwners(target)
                db.checkoutAttempts().clearOtherOwners(target)
                db.localOwner().set(LocalOwnerEntity(target))
            }
        } catch (error: SQLiteException) {
            throw IOException("Cannot switch the local data owner", error)
        }
    }
}
