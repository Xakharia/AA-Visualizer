package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EqProfileEntity::class,
        DeviceRoutingEntity::class,
        AppRoutingEntity::class,
        CachedLyricsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eqProfileDao(): EqProfileDao
    abstract fun deviceRoutingDao(): DeviceRoutingDao
    abstract fun appRoutingDao(): AppRoutingDao
    abstract fun cachedLyricsDao(): CachedLyricsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aa_visualizer.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
