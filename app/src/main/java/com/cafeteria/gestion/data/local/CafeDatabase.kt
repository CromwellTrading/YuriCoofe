package com.cafeteria.gestion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProductEntity::class, SaleQueueEntity::class, ProductQueueEntity::class, AdminOperationEntity::class, TransferBundleEntity::class],
    version = 7,
    exportSchema = true
)
abstract class CafeDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleQueueDao(): SaleQueueDao
    abstract fun productQueueDao(): ProductQueueDao
    abstract fun adminOperationDao(): AdminOperationDao
    abstract fun transferBundleDao(): TransferBundleDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `inventoryRevision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `admin_operation_queue` (
                        `id` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `businessId` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `catalogRevision` INTEGER,
                        `inventoryRevision` INTEGER,
                        `occurredAt` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `localSequence` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `lastError` TEXT,
                        `serverInventoryRevision` INTEGER,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_admin_operation_queue_productId` ON `admin_operation_queue` (`productId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_admin_operation_queue_deviceId_localSequence` ON `admin_operation_queue` (`deviceId`,`localSequence`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `transfer_bundles` (
                        `bundleId` TEXT NOT NULL,
                        `sourceDeviceId` TEXT NOT NULL,
                        `destinationDeviceId` TEXT,
                        `direction` TEXT NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `operationIdsJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `encryptedPayloadAtRest` TEXT NOT NULL,
                        `lastError` TEXT,
                        PRIMARY KEY(`bundleId`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_bundles_sourceDeviceId` ON `transfer_bundles` (`sourceDeviceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_bundles_createdAtEpochMs` ON `transfer_bundles` (`createdAtEpochMs`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sale_queue` ADD COLUMN `businessId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `product_queue` ADD COLUMN `businessId` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `catalogRevision` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: CafeDatabase? = null
        fun get(context: Context): CafeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CafeDatabase::class.java,
                "cafeteria-local.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { instance = it }
        }
    }
}
