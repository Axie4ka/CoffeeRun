package ru.coffeerun.data.local.room

import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CheckoutAttemptEntity::class, CheckoutAttemptLineEntity::class, CartLineEntity::class,
        LocalOwnerEntity::class, MenuSnapshotEntity::class, SavedOrder::class, OrderHistorySync::class,
        SavedFavorites::class, SavedRecipe::class, AvatarCacheEntity::class],
    version = 7,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = CleanClosedCheckoutAttempts::class), AutoMigration(from = 6, to = 7)],
    exportSchema = true
)
abstract class CoffeeRunDatabase : RoomDatabase() {
    abstract fun checkoutAttempts(): CheckoutAttemptDao
    abstract fun cart(): CartDao
    abstract fun localOwner(): LocalOwnerDao
    abstract fun menuSnapshots(): MenuSnapshotDao
    abstract fun orderHistory(): OrderHistoryDao
    abstract fun favoritesSnapshots(): FavoritesSnapshotDao
    abstract fun savedRecipes(): SavedRecipeDao
    abstract fun avatars(): AvatarCacheDao
}

class CleanClosedCheckoutAttempts : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM checkout_attempt_line WHERE attemptId IN (SELECT id FROM checkout_attempt WHERE orderNumber IS NOT NULL OR dismissed = 1)")
        db.execSQL("DELETE FROM checkout_attempt WHERE orderNumber IS NOT NULL OR dismissed = 1")
    }
}
