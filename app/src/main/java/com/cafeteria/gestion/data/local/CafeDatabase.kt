package com.cafeteria.gestion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProductEntity::class, SaleQueueEntity::class, ProductQueueEntity::class],
    version = 3,
    exportSchema = true
)
abstract class CafeDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleQueueDao(): SaleQueueDao
    abstract fun productQueueDao(): ProductQueueDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `product_queue` (
                        `id` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `imageUri` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `lastError` TEXT,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_queue_productId` ON `product_queue` (`productId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `stockIsApproximate` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: CafeDatabase? = null
        fun get(context: Context): CafeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CafeDatabase::class.java,
                "cafeteria-local.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
