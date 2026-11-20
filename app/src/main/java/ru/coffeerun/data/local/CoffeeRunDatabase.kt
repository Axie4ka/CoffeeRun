package ru.coffeerun.data.local

import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.RoomDatabase

@Database(
    entities = [CheckoutAttemptEntity::class, CheckoutAttemptLineEntity::class, CartLineEntity::class,
        LocalOwnerEntity::class, MenuSnapshotEntity::class, SavedOrder::class, OrderHistorySync::class, SavedFavorites::class],
    version = 5,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
    exportSchema = true
)
abstract class CoffeeRunDatabase : RoomDatabase() {
    abstract fun checkoutAttempts(): CheckoutAttemptDao
    abstract fun cart(): CartDao
    abstract fun localOwner(): LocalOwnerDao
    abstract fun menuSnapshots(): MenuSnapshotDao
    abstract fun orderHistory(): OrderHistoryDao
    abstract fun favoritesSnapshots(): FavoritesSnapshotDao
}
