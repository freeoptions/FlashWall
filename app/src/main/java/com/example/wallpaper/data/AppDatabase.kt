package com.example.wallpaper.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Wallpaper::class, Folder::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE folders ADD COLUMN isScreeningFolder INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallpaper_database"
                )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration() // 允许架构改变时销毁旧表重建
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
