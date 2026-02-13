package com.example.myapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotificationEntity::class,
        AppUsageEntity::class,
        MerchantAliasEntity::class,
        SubscriptionEntity::class
    ],
    version = 7
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun merchantAliasDao(): MerchantAliasDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 5 → 6 (no schema changes, just version bump for production)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes — this is a clean version bump for production release.
            }
        }

        // Migration from version 6 → 7 (add merchant_aliases and subscriptions tables)
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create merchant_aliases table for learned merchant categories
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merchant_aliases (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchantName TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        category TEXT NOT NULL,
                        subcategory TEXT,
                        userNote TEXT,
                        timesUsed INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        lastUsedAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_merchant_aliases_normalizedName ON merchant_aliases(normalizedName)"
                )

                // Create subscriptions table for detected recurring payments
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subscriptions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchantName TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        amount REAL NOT NULL,
                        frequency TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'Subscription',
                        lastChargedAt INTEGER NOT NULL,
                        nextExpectedAt INTEGER NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        timesDetected INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_subscriptions_normalizedName ON subscriptions(normalizedName)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()  // Safety net for dev → prod transition
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
