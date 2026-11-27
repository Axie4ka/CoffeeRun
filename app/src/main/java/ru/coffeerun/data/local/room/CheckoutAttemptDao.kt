package ru.coffeerun.data.local.room

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "checkout_attempt", indices = [Index("owner", "orderNumber")])
data class CheckoutAttemptEntity(
    @PrimaryKey val id: String,
    val owner: String,
    val shop: String,
    val pickupAtMillis: Long?,
    val orderNumber: Int? = null,
    val dismissed: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val keyed: Boolean = false
)

@Entity(
    tableName = "checkout_attempt_line",
    primaryKeys = ["attemptId", "position"],
    foreignKeys = [ForeignKey(
        entity = CheckoutAttemptEntity::class,
        parentColumns = ["id"],
        childColumns = ["attemptId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CheckoutAttemptLineEntity(
    val attemptId: String,
    val position: Int,
    val cartLineId: Int,
    val productId: String,
    val quantity: Int,
    val unitPrice: Int,
    val optionsJson: String?,
    @androidx.room.ColumnInfo(defaultValue = "0") val discount: Int = 0
)

data class StoredCheckoutAttempt(
    @Embedded val attempt: CheckoutAttemptEntity,
    @Relation(parentColumn = "id", entityColumn = "attemptId")
    val lines: List<CheckoutAttemptLineEntity>
)

@Dao
abstract class CheckoutAttemptDao {
    @Query("""
        DELETE FROM checkout_attempt
        WHERE owner != :owner
          AND NOT (owner LIKE 'account:%' AND keyed = 1 AND orderNumber IS NULL AND dismissed = 0)
    """)
    abstract suspend fun clearOtherOwners(owner: String)

    @Transaction
    @Query("SELECT * FROM checkout_attempt WHERE owner = :owner AND orderNumber IS NULL AND dismissed = 0 LIMIT 1")
    abstract suspend fun active(owner: String): StoredCheckoutAttempt?

    @Query("SELECT keyed FROM checkout_attempt WHERE owner = :owner AND orderNumber IS NULL AND dismissed = 0 LIMIT 1")
    abstract fun observeKeyedAttempt(owner: String): Flow<Boolean?>

    @Query("DELETE FROM checkout_attempt WHERE owner = :owner AND (orderNumber IS NOT NULL OR dismissed = 1)")
    abstract suspend fun clearClosed(owner: String)

    @Insert
    protected abstract suspend fun insertAttempt(attempt: CheckoutAttemptEntity)

    @Insert
    protected abstract suspend fun insertLines(lines: List<CheckoutAttemptLineEntity>)

    @Transaction
    open suspend fun begin(attempt: CheckoutAttemptEntity, lines: List<CheckoutAttemptLineEntity>): Boolean {
        require(attempt.orderNumber == null && !attempt.dismissed)
        require(lines.isNotEmpty() && lines.all { it.attemptId == attempt.id })
        if (active(attempt.owner) != null) return false
        clearClosed(attempt.owner)
        insertAttempt(attempt)
        insertLines(lines)
        return true
    }

    @Query("""
        UPDATE checkout_attempt SET orderNumber = :number
        WHERE owner = :owner AND id = :attemptId AND orderNumber IS NULL AND dismissed = 0
    """)
    abstract suspend fun complete(owner: String, attemptId: String, number: Int): Int

    @Query("DELETE FROM checkout_attempt WHERE owner = :owner AND id = :attemptId AND orderNumber IS NULL AND dismissed = 0")
    abstract suspend fun reject(owner: String, attemptId: String): Int

}
