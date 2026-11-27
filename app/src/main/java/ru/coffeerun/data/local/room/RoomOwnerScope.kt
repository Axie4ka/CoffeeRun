package ru.coffeerun.data.local.room

import android.database.sqlite.SQLiteException
import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import java.io.IOException
import java.io.File
import kotlinx.serialization.SerializationException
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.SessionProvider

internal const val GUEST_OWNER = "guest"

internal suspend fun <T> CoffeeRunDatabase.withOwner(
    sessions: SessionProvider, session: AuthSession, action: suspend (String) -> T
): T = withTransaction {
    val owner = session.localOwner ?: GUEST_OWNER
    sessions.withSession(session) { }
    if (localOwner().current() != owner) throw SessionChangedException()
    val result = action(owner)
    sessions.withSession(session) { result }
}

internal suspend fun <T> CoffeeRunDatabase?.withPersonalCache(
    sessions: SessionProvider, session: AuthSession, action: suspend (CoffeeRunDatabase, String) -> T
): T? {
    if (this == null || session.localOwner == null) return null
    return try {
        withOwner(sessions, session) { owner -> action(this, owner) }
    } catch (_: SQLiteException) {
        null
    } catch (_: SerializationException) {
        null
    }
}

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
class RoomOwnerScope(private val db: CoffeeRunDatabase, private val context: Context? = null) {
    suspend fun reconcile(localOwner: String?) {
        val target = localOwner ?: GUEST_OWNER
        try {
            db.withTransaction {
                val previous = db.localOwner().current()
                if (previous == target) return@withTransaction
                if (previous == GUEST_OWNER && localOwner != null) db.cart().transferGuest(target)
                db.cart().clearOtherOwners(target)
                db.checkoutAttempts().clearOtherOwners(target)
                db.orderHistory().clearOtherOrders(target)
                db.orderHistory().clearOtherSync(target)
                db.favoritesSnapshots().clearOtherOwners(target)
                db.avatars().clearOtherOwners(target)
                db.savedRecipes().clearOtherOwners(target)
                db.localOwner().set(LocalOwnerEntity(target))
            }
            context?.let {
                db.withTransaction {
                    val currentOwner = db.localOwner().current() ?: return@withTransaction
                    val filename = db.avatars().filename(currentOwner)
                    avatarDirectory(it).listFiles()?.filter { file -> file.name != filename }?.forEach(File::delete)
                }
            }
        } catch (error: SQLiteException) {
            throw IOException("Cannot switch the local data owner", error)
        }
    }
}
