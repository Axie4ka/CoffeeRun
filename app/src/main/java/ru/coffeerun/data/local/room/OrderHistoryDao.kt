package ru.coffeerun.data.local.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import ru.coffeerun.data.network.CoffeeRunJson
import ru.coffeerun.model.Order

@Entity(tableName = "saved_order", primaryKeys = ["owner", "number"])
data class SavedOrder(val owner: String, val number: Int, val payload: String, val confirmedAtMillis: Long? = null)

internal fun Order.saved(owner: String) =
    SavedOrder(owner, number, CoffeeRunJson.encodeToString(this), confirmedAtMillis)

@Entity(tableName = "order_history_sync")
data class OrderHistorySync(@PrimaryKey val owner: String, val fetchedAtMillis: Long)

@Dao
interface OrderHistoryDao {
    @Query("SELECT * FROM saved_order WHERE owner = :owner")
    suspend fun orders(owner: String): List<SavedOrder>

    @Query("SELECT * FROM saved_order WHERE owner = :owner AND number = :number")
    suspend fun find(owner: String, number: Int): SavedOrder?

    @Query("SELECT * FROM order_history_sync WHERE owner = :owner")
    suspend fun sync(owner: String): OrderHistorySync?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(orders: List<SavedOrder>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSync(sync: OrderHistorySync)

    @Query("DELETE FROM saved_order WHERE owner = :owner AND number = :number")
    suspend fun remove(owner: String, number: Int)

    @Query("DELETE FROM saved_order WHERE owner = :owner")
    suspend fun clear(owner: String)

    @Query("DELETE FROM saved_order WHERE owner != :owner")
    suspend fun clearOtherOrders(owner: String)

    @Query("DELETE FROM order_history_sync WHERE owner != :owner")
    suspend fun clearOtherSync(owner: String)
}
