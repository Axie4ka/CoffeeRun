package ru.coffeerun.data.local

import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.RoomDatabase

@Database(
    entities = [CheckoutAttemptEntity::class, CheckoutAttemptLineEntity::class, CartLineEntity::class,
        LocalOwnerEntity::class, MenuSnapshotEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true
)
abstract class CoffeeRunDatabase : RoomDatabase() {
    abstract fun checkoutAttempts(): CheckoutAttemptDao
    abstract fun cart(): CartDao
    abstract fun localOwner(): LocalOwnerDao
    abstract fun menuSnapshots(): MenuSnapshotDao
}
