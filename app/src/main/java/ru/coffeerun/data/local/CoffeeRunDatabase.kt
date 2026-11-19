package ru.coffeerun.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CheckoutAttemptEntity::class, CheckoutAttemptLineEntity::class],
    version = 1,
    exportSchema = true
)
abstract class CoffeeRunDatabase : RoomDatabase() {
    abstract fun checkoutAttempts(): CheckoutAttemptDao
}
