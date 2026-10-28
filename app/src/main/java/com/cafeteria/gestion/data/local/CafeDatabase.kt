package com.cafeteria.gestion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProductEntity::class, SaleQueueEntity::class],
    version = 1,
    exportSchema = true
)
abstract class CafeDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleQueueDao(): SaleQueueDao

    companion object {
        @Volatile private var instance: CafeDatabase? = null
        fun get(context: Context): CafeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CafeDatabase::class.java,
                "cafeteria-local.db"
            ).build().also { instance = it }
        }
    }
}
