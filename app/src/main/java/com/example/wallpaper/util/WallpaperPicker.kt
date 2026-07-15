package com.example.wallpaper.util

import android.content.Context
import android.provider.MediaStore
import com.example.wallpaper.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WallpaperPicker(private val context: Context) {

    suspend fun getRandomWallpaper(): String? {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val selectedFolders = db.wallpaperDao().getSelectedFolders()
            if (selectedFolders.isEmpty()) return@withContext null

            val selection = StringBuilder()
            val selectionArgs = mutableListOf<String>()

            selectedFolders.forEachIndexed { index, folder ->
                if (index > 0) selection.append(" OR ")
                selection.append("${MediaStore.Images.Media.DATA} LIKE ?")
                selectionArgs.add("${folder.uri}%")
            }

            val projection = arrayOf(MediaStore.Images.Media.DATA)

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "($selection) AND (${MediaStore.Images.Media.MIME_TYPE} LIKE 'image/%')",
                selectionArgs.toTypedArray(),
                null
            )

            cursor?.use { cursor ->
                val count = cursor.count
                if (count == 0) return@withContext null

                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val visited = mutableSetOf<Int>()
                val randomAttempts = minOf(count, 25)

                repeat(randomAttempts) {
                    val randomIndex = (0 until count).random()
                    visited.add(randomIndex)
                    if (cursor.moveToPosition(randomIndex)) {
                        val path = cursor.getString(dataColumn)
                        if (isExistingImagePath(path)) {
                            return@withContext toCoilFilePath(path)
                        }
                    }
                }

                for (index in 0 until count) {
                    if (index in visited) continue
                    if (cursor.moveToPosition(index)) {
                        val path = cursor.getString(dataColumn)
                        if (isExistingImagePath(path)) {
                            return@withContext toCoilFilePath(path)
                        }
                    }
                }
            }
            null
        }
    }

    private fun isExistingImagePath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return file.exists() && file.isFile && file.canRead()
    }

    private fun toCoilFilePath(path: String): String {
        return if (path.startsWith("/")) "file://$path" else path
    }
}
