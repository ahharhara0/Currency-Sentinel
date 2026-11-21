package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VaultEntity::class, CatalogProfileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SentinelDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun catalogProfileDao(): CatalogProfileDao

    companion object {
        @Volatile
        private var INSTANCE: SentinelDatabase? = null

        fun getInstance(context: Context): SentinelDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SentinelDatabase::class.java,
                    "currency_sentinel_v3.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
