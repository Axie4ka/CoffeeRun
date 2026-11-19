package ru.coffeerun.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cart_line", indices = [Index(value = ["owner", "productId", "unitPrice", "optionsJson"], unique = true)])
data class CartLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val owner: String,
    val productId: String,
    val unitPrice: Int,
    val quantity: Int,
    val productJson: String,
    val optionsJson: String
)

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_line WHERE owner = :owner ORDER BY id")
    suspend fun snapshot(owner: String): List<CartLineEntity>

    @Query("SELECT * FROM cart_line WHERE owner = :owner ORDER BY id")
    fun observe(owner: String): Flow<List<CartLineEntity>>

    @Insert
    suspend fun insert(line: CartLineEntity): Long

    @Query("UPDATE cart_line SET owner = :owner WHERE owner = 'guest'")
    suspend fun transferGuest(owner: String)

    @Query("DELETE FROM cart_line WHERE owner != :owner")
    suspend fun clearOtherOwners(owner: String)
}
