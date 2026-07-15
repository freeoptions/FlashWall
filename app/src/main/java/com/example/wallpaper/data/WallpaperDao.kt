package com.example.wallpaper.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers WHERE isMarked = 1")
    fun getMarkedWallpapers(): Flow<List<Wallpaper>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(wallpaper: Wallpaper)

    @Update
    suspend fun updateWallpaper(wallpaper: Wallpaper)

    @Query("SELECT * FROM wallpapers WHERE path = :path LIMIT 1")
    suspend fun getWallpaperByPath(path: String): Wallpaper?

    @Query("UPDATE wallpapers SET isMarked = :isMarked WHERE path = :path")
    suspend fun markWallpaper(path: String, isMarked: Boolean)

    @Query("SELECT COUNT(*) FROM wallpapers WHERE isMarked = 1")
    fun getTotalMarkedFlow(): Flow<Int>

    @Query("SELECT * FROM folders WHERE isSelected = 1")
    suspend fun getSelectedFolders(): List<Folder>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersList(): List<Folder>

    @Query("SELECT * FROM folders ORDER BY position ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<Folder>)

    @Query("UPDATE folders SET isSelected = :isSelected")
    suspend fun updateAllFoldersSelection(isSelected: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Query("SELECT * FROM folders WHERE uri = :uri LIMIT 1")
    suspend fun getFolderByUri(uri: String): Folder?

    @Transaction
    suspend fun deleteFolderWithWallpapers(folderId: Long) {
        deleteWallpapersByFolderId(folderId)
        deleteFolderById(folderId)
    }

    @Query("SELECT path FROM wallpapers WHERE folderId = :folderId AND isMarked = 1")
    suspend fun getMarkedPathsInFolder(folderId: Long): List<String>

    @Query("DELETE FROM wallpapers WHERE folderId = :folderId")
    suspend fun deleteWallpapersByFolderId(folderId: Long)

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): Folder?

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Delete
    suspend fun deleteWallpaper(wallpaper: Wallpaper)

    @Query("DELETE FROM wallpapers WHERE path = :path")
    suspend fun deleteWallpaperByPath(path: String)
}
