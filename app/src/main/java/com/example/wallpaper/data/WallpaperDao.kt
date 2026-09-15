package com.example.wallpaper.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers WHERE isMarked = 1")
    fun getMarkedWallpapers(): Flow<List<Wallpaper>>

    @Query("SELECT * FROM wallpapers WHERE folderId = :folderId ORDER BY path ASC")
    fun getWallpapersByFolder(folderId: Long): Flow<List<Wallpaper>>

    @Query("SELECT * FROM wallpapers")
    suspend fun getAllWallpapersOnce(): List<Wallpaper>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(wallpaper: Wallpaper)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpapers(wallpapers: List<Wallpaper>)

    @Update
    suspend fun updateWallpaper(wallpaper: Wallpaper)

    @Query("SELECT * FROM wallpapers WHERE path = :path LIMIT 1")
    suspend fun getWallpaperByPath(path: String): Wallpaper?

    @Query("UPDATE wallpapers SET isMarked = :isMarked WHERE path = :path")
    suspend fun markWallpaper(path: String, isMarked: Boolean)

    @Query("SELECT COUNT(*) FROM wallpapers WHERE isMarked = 1")
    fun getTotalMarkedFlow(): Flow<Int>

    @Query("SELECT * FROM folders WHERE isSelected = 1 AND isScreeningFolder = 0")
    suspend fun getSelectedFolders(): List<Folder>

    @Query("SELECT * FROM folders WHERE isScreeningFolder = 0")
    suspend fun getAllFoldersList(): List<Folder>

    @Query("SELECT * FROM folders WHERE isScreeningFolder = 0 ORDER BY position ASC")
    fun getAllFolders(): Flow<List<Folder>>

    // 筛选页需要看到自己的目录记录，也需要看到尚未从轮播库移除的普通目录。
    @Query("SELECT * FROM folders ORDER BY position ASC, id ASC")
    fun getAllFoldersForScreening(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<Folder>)

    @Query("UPDATE folders SET isSelected = :isSelected WHERE isScreeningFolder = 0")
    suspend fun updateAllFoldersSelection(isSelected: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Query("SELECT * FROM folders WHERE uri = :uri AND isScreeningFolder = 0 LIMIT 1")
    suspend fun getFolderByUri(uri: String): Folder?

    @Query("SELECT * FROM folders WHERE uri = :uri AND isScreeningFolder = 1 LIMIT 1")
    suspend fun getScreeningFolderByUri(uri: String): Folder?

    @Transaction
    suspend fun deleteFolderWithWallpapers(folderId: Long) {
        deleteWallpapersByFolderId(folderId)
        deleteFolderById(folderId)
    }

    @Query("UPDATE folders SET isSelected = 0, isScreeningFolder = 1 WHERE id = :folderId")
    suspend fun keepFolderForScreening(folderId: Long)

    @Transaction
    suspend fun deleteFolderFromRotation(folderId: Long, screeningFolderUri: String?) {
        val folder = getFolderById(folderId)
        if (folder?.uri == screeningFolderUri) {
            // 轮播库移除后保留筛选页自己的目录和图片索引。
            keepFolderForScreening(folderId)
        } else {
            deleteFolderWithWallpapers(folderId)
        }
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

    @Query("DELETE FROM wallpapers WHERE folderId = :folderId AND lastSeen < :scanStarted")
    suspend fun deleteStaleWallpapers(folderId: Long, scanStarted: Long)
}
