package com.example.wallpaper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Wallpaper::class, Folder::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallpaper_database"
                )
                .fallbackToDestructiveMigration() // 允许架构改变时销毁旧表重建
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
